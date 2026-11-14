package com.danielportelabyrne.cinema.design.cinema

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.PathParser
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * The branded opening, "the velvet skip". The mark is built like a cut-paper print being registered: its five discs
 * fall onto the screen as a slipped stack, each landing making its own note (big discs play low, small ones high).
 * The three outer discs land together on the bass pluck; the orange disc lands on the first piano note and the saffron
 * one on the second, skidding a little further out. Then the stack hangs there, off register and slowly slipping, through
 * the silence, until on the late landing it snaps into register: the discs become the flat Cinema mark, a ripple runs
 * out through the rings, the play triangle prints with the chord's top note, and the wordmark slides out from behind.
 *
 * Every frame is a pure function of one clock, read from the sound as it leaves the speaker ([SplashJingle.Voice],
 * [IdentClock]), so the picture and the notes cannot drift apart. Any key skips it. The mark and wordmark are the app's
 * own (cinema_mark.xml, and Fraunces as the Movies page draws it).
 */
class SplashView(context: Context) : View(context) {
    /** The discs of cinema_mark.xml, outermost first, in the mark's 56-unit box. */
    private val radii = floatArrayOf(27f, 22f, 17f, 12f, 7.2f)
    private val colors = Cinema.STRIPES.reversedArray()   // maroon outside, saffron inside
    /** How far along the slip each disc lies in the stack, in mark units: authored, slightly uneven steps. */
    private val slip = floatArrayOf(0f, 3.0f, 6.6f, 9.9f, 13.8f)
    private val dark = Cinema.BG

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF000000.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweep = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triangle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B0D06.toInt(); style = Paint.Style.FILL }
    private val trianglePath: Path = PathParser.createPathFromPathData("M25.2,24.2v7.6a1,1 0 0,0 1.5,0.86l6.3,-3.8a1,1 0 0,0 0,-1.72l-6.3,-3.8a1,1 0 0,0 -1.5,0.86z")
    private val clip = Path()
    private val bounds = RectF()

    private val dimmer = ArgbEvaluator()
    private val bandWidth = 26f
    // Built once and moved with the canvas, so drawing a frame allocates nothing.
    private val sweepShader = LinearGradient(0f, 0f, bandWidth, 0f, intArrayOf(0x00FFF3D0, 0x66FFF3D0, 0x00FFF3D0), null, Shader.TileMode.CLAMP)
    private var glowShader: RadialGradient? = null
    private var washShader: RadialGradient? = null
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wordmark: android.widget.TextView? = null
    private var markPx = 0f
    private var gapPx = 0f

    private var clockMs = 0f
    private var clock = IdentClock()
    private var voice: SplashJingle.Voice? = null
    private var running = false
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
     * Gets the sound ready; it and the picture begin together just after the first frame is actually drawn, and the
     * picture then waits for the sound to really be heard before it moves, so a slow first layout, a busy main thread or
     * a TV's output latency cannot put them out of step. [covered] runs once the picture is holding at full strength,
     * which is when the app underneath is built (that keeps the main thread busy, so it must not happen while this is
     * animating); the fade over it starts a moment later. [done] runs once the view has gone.
     */
    fun start(covered: () -> Unit, done: () -> Unit) {
        onCovered = covered
        onDone = done
        voice = SplashJingle.prepare(context)
        clock = if (voice == null) IdentClock(waitMs = -1.0) else IdentClock()
    }

    private var began = false

    /** Skips ahead: the sound stops (without a click) and the picture fades out quickly. */
    fun skip() {
        if (finishing || leaving) return
        running = false
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
        voice?.stop()
        voice = null
    }

    override fun onDetachedFromWindow() {
        running = false
        animator?.removeAllListeners()
        animator?.cancel()
        stopSound()
        super.onDetachedFromWindow()
    }

    private fun progress(from: Int, length: Int) = ((clockMs - from) / length).coerceIn(0f, 1f)

    /** A gentle press and one small rebound after a landing at [atMs]: [depth] is how far it gives, [periodMs] its swing. */
    private fun settle(atMs: Int, depth: Float, periodMs: Float, decayMs: Float): Float {
        val t = clockMs - atMs
        if (t < 0f) return 1f
        return 1f - depth * exp(-t / decayMs) * cos(2f * PI.toFloat() * t / periodMs)
    }

    override fun onDraw(canvas: Canvas) {
        if (!began && !finishing) {
            began = true
            // Started from the draw pass: from the next frame the clock waits for the sound to be heard, then follows it.
            post { if (!finishing && !leaving) { voice?.play(); running = true; invalidate() } }
        }
        if (running) {
            clockMs = clock.tick(System.nanoTime(), voice?.positionMs()).toFloat()
            if (clockMs >= Timeline.FADE_START) { running = false; post { cover(Timeline.FADE_LENGTH.toLong(), 150) } }
            else postInvalidateOnAnimation()
        }
        drawFrame(canvas)
    }

    /** One frame of the opening at [clockMs]. */
    private fun drawFrame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // The room dims: the app's ink ground sinks to cinema black as the first discs fall.
        canvas.drawColor(dimmer.evaluate(progress(0, Timeline.BUP), context.getColor(R.color.ink), dark) as Int)

        val u = markPx / 56f
        val cy = h * 0.46f
        val word = wordmark
        val textW = word?.measuredWidth?.toFloat() ?: 0f
        val lockupW = markPx + gapPx + textW
        // The mark is built centred; once it is registered it slides left to make room for the wordmark beside it.
        val q = Motion.SETTLE.getInterpolation(progress(Timeline.LOCKUP, Timeline.LOCKUP_LENGTH))
        val mx = w / 2f + q * (-lockupW / 2f + markPx / 2f)

        // The landing: a low warm bloom behind the mark, and a faint wash that spreads to fill the screen and stays.
        val sinceDum = clockMs - Timeline.DUM
        if (sinceDum > 0) {
            val bloom = (1f - progress(Timeline.DUM, 1100)).let { it * it } * 0.30f
            glow.shader = glowShader
            glow.alpha = (bloom * 255).toInt()
            canvas.save(); canvas.translate(mx, cy); canvas.drawCircle(0f, 0f, markPx * GLOW_RADIUS, glow); canvas.restore()
            val spread = progress(Timeline.DUM, 1100)
            val reach = markPx * 1.2f + (hypot(w, h) * 1.05f - markPx * 1.2f) * Motion.SWOOSH.getInterpolation(spread)
            wash.shader = washShader
            wash.alpha = (min(1f, spread * 5f) * WASH_STRENGTH * 255).toInt()
            canvas.save(); canvas.translate(w / 2f, cy); canvas.scale(reach / WASH_UNIT, reach / WASH_UNIT); canvas.drawCircle(0f, 0f, WASH_UNIT, wash); canvas.restore()
        }

        // How out of register the stack is: fully slipped once built, creeping a touch further through the silence (the
        // held breath), then pulled into register, accelerating, to arrive exactly on the landing.
        val creep = smooth(progress(Timeline.DIT + 140, Timeline.DUM - Timeline.SNAP - Timeline.DIT - 140))
        val snap = progress(Timeline.DUM - Timeline.SNAP, Timeline.SNAP).pow(2.6f)
        val outOfRegister = (1f + 0.07f * creep) * (1f - snap)

        canvas.save()
        canvas.translate(mx, cy)
        canvas.scale(u, u)
        for (i in radii.indices) {
            // When each disc lands, how far it falls (from above the screen, so it starts larger), and its settle.
            val lands = when (i) { 3 -> Timeline.DI; 4 -> Timeline.DIT; else -> Timeline.BUP }
            val fall = if (i < 3) Timeline.DROP else Timeline.PIANO_DROP
            val d = progress(lands - fall, fall)
            if (d <= 0f) continue
            val height = 1f - d * d                     // falling: it gathers speed and lands on its note
            val light = i >= 3
            val lift = 1f + height * (if (light) 0.55f else 0.32f)
            val press = if (light) settle(lands, 0.06f, 110f, 34f) else settle(lands, 0.035f, 150f, 45f)
            // The ripple of the landing, from the centre out: each ring swells a little in turn and settles.
            val ripple = 1f + 0.018f * pulse(clockMs - Timeline.DUM - 22f * (radii.size - 1 - i), 42f)
            // The saffron disc skids a little further along the slip after it lands: the crushed note slipping up.
            val skid = if (i == 4) (smooth(progress(Timeline.DIT, 150)) - 1f) * 0.9f else 0f
            val along = ((slip[i] + skid) - SLIP_CENTRE) * outOfRegister
            val x = along * SLIP_X
            val y = along * SLIP_Y
            val r = radii[i] * lift * press * ripple
            val alpha = min(1f, d * 2.6f)
            // Its shadow on the disc below, cut-paper sharp: deeper and softer while it is in the air, gone once the
            // print is registered and flat.
            if (i > 0) {
                val off = 0.45f + 2.6f * height
                shade.alpha = (alpha * (0.30f - 0.16f * height) * (1f - snap) * 255).toInt()
                canvas.drawCircle(x + off * 0.55f, y + off * 0.85f, r, shade)
            }
            disc.color = colors[i]
            disc.alpha = (alpha * 255).toInt()
            canvas.drawCircle(x, y, r, disc)
        }

        // The play triangle prints with the chord's top note: a quick fade, a short settle from a little small.
        val tp = progress(Timeline.TRIANGLE, 160)
        if (tp > 0f) {
            val s = 0.86f + 0.14f * Motion.SETTLE.getInterpolation(tp)
            triangle.alpha = (min(1f, tp * 2.7f) * 255).toInt()
            canvas.save()
            canvas.translate(-28f, -28f)
            canvas.scale(s, s, 29.6f, 28f)
            canvas.drawPath(trianglePath, triangle)
            canvas.restore()
        }
        // A band of light crosses the finished mark once, clipped to its outer ring.
        val sp = progress(Timeline.DUM + 120, 560)
        if (sp in 0.001f..0.999f) {
            clip.reset(); clip.addCircle(0f, 0f, 27f, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            canvas.rotate(-18f)
            canvas.translate(-28f - bandWidth + (56f + bandWidth * 2) * sp, -28f)
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
        private const val WASH_STRENGTH = 0.10f

        /** The slip runs up and to the right (35 degrees), centred so the slipped stack still sits in the middle. */
        private val SLIP_X = cos(35.0 * PI / 180).toFloat()
        private val SLIP_Y = -kotlin.math.sin(35.0 * PI / 180).toFloat()
        private const val SLIP_CENTRE = 6.2f

        private fun smooth(t: Float) = t * t * (3f - 2f * t)

        /** 0 before [t] reaches 0, rising to 1 at [peakMs] and falling away after: one soft swell. */
        private fun pulse(t: Float, peakMs: Float) = if (t <= 0f) 0f else (t / peakMs) * exp(1f - t / peakMs)

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
