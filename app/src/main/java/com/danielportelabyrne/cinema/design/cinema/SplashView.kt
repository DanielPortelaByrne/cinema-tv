package com.danielportelabyrne.cinema.design.cinema

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaPlayer
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.graphics.PathParser
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min

/**
 * The branded opening. The 70s stripes glide in flat from the left, then each one feeds around into its ring (the
 * ribbon winds clockwise as its tail shortens) until the five bands have become the Cinema mark. The play triangle
 * pops in, a light crosses the mark, the mark slides left and the wordmark comes out from behind it, and the lot drifts
 * forward and fades into the app. Any key skips it. The mark and wordmark are the app's own (cinema_mark.xml, and
 * Fraunces as the Movies page draws it); everything runs off one clock ([Timeline], shared with [SplashJingle]) so
 * the sound and the picture stay in step.
 */
class SplashView(context: Context) : View(context) {
    // The mark's rings, outermost first, in the mark's own 56-unit box: mid-ring radius and band width. The centre
    // "ring" is the solid saffron disc (a 7.2-wide band wound at radius 3.6).
    private val radii = floatArrayOf(24.5f, 19.5f, 14.5f, 9.5f, 3.6f)
    private val widths = floatArrayOf(5f, 5f, 5f, 5f, 7.2f)
    private val colors = Cinema.STRIPES.reversedArray()   // maroon outside, saffron inside, as in cinema_mark.xml
    private val dark = Cinema.BG

    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweep = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triangle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B0D06.toInt(); style = Paint.Style.FILL }
    private val trianglePath: Path = PathParser.createPathFromPathData("M25.2,24.2v7.6a1,1 0 0,0 1.5,0.86l6.3,-3.8a1,1 0 0,0 0,-1.72l-6.3,-3.8a1,1 0 0,0 -1.5,0.86z")
    private val ribbon = Path()
    private val oval = RectF()
    private val clip = Path()
    private val bounds = RectF()

    private val overshoot = OvershootInterpolator(2.2f)
    private val glide = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val wind = PathInterpolator(0.45f, 0f, 0.2f, 1f)
    private val dimmer = ArgbEvaluator()
    private val bandWidth = 26f
    // Built once and moved with the canvas, so drawing a frame allocates nothing.
    private val sweepShader = LinearGradient(0f, 0f, bandWidth, 0f, intArrayOf(0x00FFF3D0, 0x88FFF3D0.toInt(), 0x00FFF3D0), null, Shader.TileMode.CLAMP)
    private var glowShader: RadialGradient? = null
    private var washShader: RadialGradient? = null
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wordmark: TextView? = null
    private var markPx = 0f
    private var gapPx = 0f

    private var clockMs = 0f
    private var player: MediaPlayer? = null
    private var finishing = false
    private var onCovered: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var covered = false
    private var leaving = false
    private var animator: ValueAnimator? = null

    /** True while the opening still owns the screen; once it starts to fade out the app underneath takes keys again. */
    val isShowing: Boolean get() = parent != null && !finishing && !leaving

    init {
        isClickable = true
        isFocusable = false
        setBackgroundColor(context.getColor(R.color.ink))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        markPx = min(w, h) * 0.30f
        gapPx = markPx * 18f / 56f
        // A bell-shaped falloff in the glow's own colour (fading to transparent *black* would darken the rim), reaching zero well
        // inside the circle it is drawn in, so no outer edge shows.
        val orange = Cinema.STRIPE_ORANGE and 0x00FFFFFF
        val bell = floatArrayOf(1f, 0.74f, 0.45f, 0.22f, 0.09f, 0.03f, 0f)
        glowShader = RadialGradient(0f, 0f, markPx * GLOW_RADIUS, IntArray(bell.size) { orange or ((bell[it] * 255).toInt() shl 24) },
            FloatArray(bell.size) { it / (bell.size - 1f) }, Shader.TileMode.CLAMP)
        // The screen-filling wash is the same bell at unit scale, resized with the canvas as it spreads.
        washShader = RadialGradient(0f, 0f, WASH_UNIT, IntArray(bell.size) { orange or ((bell[it] * 255).toInt() shl 24) },
            FloatArray(bell.size) { it / (bell.size - 1f) }, Shader.TileMode.CLAMP)
        // The wordmark exactly as the Movies page sets it beside the mark: Fraunces 500, 36 on a 56 mark, tracked -0.02.
        val textPx = markPx * 36f / 56f
        wordmark = Cinema.text(context, "Cinema", textPx * 2f / resources.displayMetrics.density, Cinema.CREAM, serif = true, weight = 500, tracking = -0.02f).apply {
            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    /**
     * Gets the sound and the animation ready; both begin together on the first frame actually drawn, so a slow first
     * layout or a busy main thread cannot leave the sound running ahead of a picture that has not appeared.
     * [covered] runs once the picture is holding at full strength, which is when the app underneath is built (that
     * keeps the main thread busy, so it must not happen while this is animating); the fade over it starts a moment
     * later. [done] runs once the view has gone.
     */
    fun start(covered: () -> Unit, done: () -> Unit) {
        onCovered = covered
        onDone = done
        player = SplashJingle.prepare(context)
        animator = ValueAnimator.ofFloat(0f, Timeline.FADE_START.toFloat()).apply {
            duration = Timeline.FADE_START.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { clockMs = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { cover(Timeline.FADE_LENGTH.toLong(), 150) } })
        }
    }

    private var began = false

    /** Skips ahead: the sound stops and the picture fades out quickly. */
    fun skip() {
        if (finishing || leaving) return
        animator?.removeAllListeners()
        animator?.cancel()
        stopSound()
        cover(Motion.FADE_MS + 120, 0)
    }

    /** The picture is holding: let the app build underneath, then fade away over it. */
    private fun cover(fadeMs: Long, waitMs: Long) {
        if (covered) return
        covered = true
        onCovered?.invoke()
        onCovered = null
        postDelayed({ fadeOut(fadeMs) }, waitMs)
    }

    private fun fadeOut(fadeMs: Long) {
        if (finishing) return
        leaving = true
        animator?.removeAllListeners()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs
            interpolator = Motion.SETTLE
            addUpdateListener { val p = it.animatedValue as Float; alpha = 1f - p; scaleX = 1f + 0.08f * p; scaleY = scaleX }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { finish() } })
            start()
        }
    }

    private fun finish() {
        finishing = true
        stopSound()
        remove()
    }

    private fun remove() {
        (parent as? ViewGroup)?.removeView(this)
        onDone?.invoke()
        onDone = null
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
    }

    override fun onDetachedFromWindow() {
        animator?.removeAllListeners()
        animator?.cancel()
        stopSound()
        super.onDetachedFromWindow()
    }

    private fun progress(from: Int, length: Int) = ((clockMs - from) / length).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        if (!began && !finishing) {
            began = true
            // Starting from the draw pass: the clock is 0 for this frame and runs from the next one.
            post { if (!finishing) { player?.start(); animator?.start() } }
        }
        val w = width.toFloat()
        val h = height.toFloat()
        // The room dims: the app's ink ground sinks to cinema black over the first beat.
        canvas.drawColor(dimmer.evaluate(progress(0, Timeline.SLIDE_START), context.getColor(R.color.ink), dark) as Int)

        val u = markPx / 56f
        val cy = h * 0.46f
        val word = wordmark
        val textW = word?.measuredWidth?.toFloat() ?: 0f
        val lockupW = markPx + gapPx + textW
        // The mark starts centred; once it is built it slides left to make room for the wordmark beside it.
        val q = Motion.SETTLE.getInterpolation(progress(Timeline.LOCKUP, Timeline.LOCKUP_LENGTH))
        val mx = w / 2f + q * (-lockupW / 2f + markPx / 2f)

        // The impact: an orange bloom behind the mark as the last ring closes, decaying over a second.
        val sinceHit = clockMs - Timeline.IMPACT
        if (sinceHit > 0) {
            val bloom = (1f - progress(Timeline.IMPACT, 1100)).let { it * it } * 0.40f
            glow.shader = glowShader
            glow.alpha = (bloom * 255).toInt()
            canvas.save(); canvas.translate(mx, cy); canvas.drawCircle(0f, 0f, markPx * GLOW_RADIUS, glow); canvas.restore()
        }

        // The glow: from the hit a faint warm wash spreads out from the logo until it fills the whole screen, then stays
        // there, holding steady until the picture fades. Kept low on purpose so it reads as light, not as a colour.
        val washProgress = progress(Timeline.IMPACT, 1100)
        if (washProgress > 0f) {
            val reach = markPx * 1.2f + (hypot(w, h) * 1.05f - markPx * 1.2f) * Motion.SWOOSH.getInterpolation(washProgress)
            wash.shader = washShader
            wash.alpha = (min(1f, washProgress * 5f) * WASH_STRENGTH * 255).toInt()
            canvas.save(); canvas.translate(w / 2f, cy); canvas.scale(reach / WASH_UNIT, reach / WASH_UNIT); canvas.drawCircle(0f, 0f, WASH_UNIT, wash); canvas.restore()
        }

        // The stripes. Each is a ribbon of one ring's circumference: it glides in flat along the line that is the top of
        // its ring, then winds clockwise onto the ring, the flat tail shortening as the wound arc grows. The stack
        // starts lower so the five flat bands sit centred, and settles up as they wind in.
        val wrapAll = wind.getInterpolation(progress(Timeline.WRAP_START, Timeline.IMPACT - Timeline.WRAP_START))
        val centreY = cy + (1f - wrapAll) * 13.5f * u
        for (i in radii.indices) {
            val r = radii[i] * u
            val length = (2.0 * PI * r).toFloat()
            val slide = glide.getInterpolation(progress(Timeline.SLIDE_START + i * Timeline.SLIDE_STEP, Timeline.SLIDE_LENGTH))
            if (slide <= 0f) continue
            val wound = wind.getInterpolation(progress(Timeline.WRAP_START + i * Timeline.WRAP_STEP, Timeline.WRAP_LENGTH))
            val x = mx + -(1f - slide) * (mx + 10f * u)
            val y = centreY - r
            band.color = colors[i]
            band.strokeWidth = widths[i] * u
            if (wound >= 1f) {
                // A finished ring is a plain circle: arcTo with a 360 degree sweep can come out empty.
                canvas.drawCircle(x, centreY, r, band)
                continue
            }
            ribbon.reset()
            ribbon.moveTo(x - length * (1f - wound), y)
            ribbon.lineTo(x, y)
            if (wound > 0f) {
                oval.set(x - r, centreY - r, x + r, centreY + r)
                ribbon.arcTo(oval, -90f, 360f * wound)
            }
            canvas.drawPath(ribbon, band)
        }

        // Back in the mark's own coordinates for the triangle and the light.
        canvas.save()
        canvas.translate(mx - 28f * u, cy - 28f * u)
        canvas.scale(u, u)
        val tp = progress(Timeline.TRIANGLE, 260)
        if (tp > 0f) {
            val s = overshoot.getInterpolation(tp)
            canvas.save()
            canvas.scale(s, s, 29.6f, 28f)
            canvas.drawPath(trianglePath, triangle)
            canvas.restore()
        }
        // A band of light crosses the finished mark once, clipped to its outer ring.
        val sp = progress(Timeline.IMPACT + 60, 520)
        if (sp in 0.001f..0.999f) {
            clip.reset(); clip.addCircle(28f, 28f, 27f, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            canvas.rotate(-18f, 28f, 28f)
            canvas.translate(-bandWidth + (56f + bandWidth * 2) * sp, 0f)
            sweep.shader = sweepShader
            canvas.drawRect(0f, -20f, bandWidth, 76f, sweep)
            canvas.restore()
        }
        canvas.restore()

        // The wordmark comes out from behind the mark as it slides left.
        if (word != null && q > 0f) {
            val textLeft = w / 2f - lockupW / 2f + markPx + gapPx
            val x = textLeft - (1f - q) * (gapPx + textW * 0.35f)
            canvas.save()
            canvas.clipRect(mx + markPx / 2f, 0f, w, h)
            bounds.set(x, cy - word.measuredHeight / 2f, x + textW, cy + word.measuredHeight / 2f)
            canvas.saveLayerAlpha(bounds, (q * 255).toInt())
            canvas.translate(x, cy - word.measuredHeight / 2f)
            word.draw(canvas)
            canvas.restore()
            canvas.restore()
        }
    }

    companion object {
        /** The bloom behind the mark, in mark widths. Wide, because it fades out before reaching its rim. */
        private const val GLOW_RADIUS = 2.1f

        /** The screen-filling wash: the unit its gradient is built at, and how strong it is at the centre (0..1). */
        private const val WASH_UNIT = 1000f
        private const val WASH_STRENGTH = 0.11f

        /**
         * Puts the splash over [root], unless it would only get in the way: a restored screen (rotation, process
         * restore), a launch that is already heading somewhere (Continue watching, debug smoke tests), or animations
         * switched off in the system's accessibility settings. Returns the view if it is showing.
         */
        fun show(root: ViewGroup, skip: Boolean, covered: () -> Unit = {}, done: () -> Unit = {}): SplashView? {
            if (skip || !Motion.animationsOn(root)) return null
            val view = SplashView(root.context)
            root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.start(covered, done)
            return view
        }
    }
}
