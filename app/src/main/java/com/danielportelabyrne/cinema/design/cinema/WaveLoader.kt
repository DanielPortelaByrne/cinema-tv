package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.data.player.PlayerMath
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import kotlin.math.PI
import kotlin.math.sin

/** The three stacked bands of the wavy scrubber, widest first: maroon, terracotta, saffron. */
private fun bands(thick: Float): List<Paint> = listOf(Cinema.MAROON to 1f, Cinema.STRIPE_TERRA to 0.667f, Cinema.STRIPE_SAFFRON to 0.333f).map { (c, f) ->
    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = thick * f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
}

/**
 * The wavy scrubber in miniature, for a card: watched in the three hard-stepped bands, unwatched as a faint cream band,
 * and a small cream bulb with a soft glow at the point reached. Sizes are mockup pixels. Not focusable.
 */
class WaveBar(context: Context, amplitudePx: Float = 5f, wavelengthPx: Float = 56f, thicknessPx: Float = 10f) : View(context) {
    var progress = 0f
        set(value) { val v = value.coerceIn(0f, 1f); if (v != field) { field = v; invalidate() } }
    /** A brighter bulb while the card it belongs to has focus. */
    var lit = false
        set(value) { if (value != field) { field = value; invalidate() } }

    private val path = Path()
    private val amp = Cinema.pxf(context, amplitudePx)
    private val wave = Cinema.pxf(context, wavelengthPx)
    private val thick = Cinema.pxf(context, thicknessPx)
    private val bulbR = thick * 0.75f
    private val inset = bulbR + Cinema.pxf(context, 2)
    private val watched = bands(thick)
    private val ahead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Cinema.alpha(Cinema.CREAM, 0.16f); style = Paint.Style.STROKE; strokeWidth = thick * 0.55f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val bulb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7EFE0.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        path.reset()
        PlayerMath.waveSamples(inset, w - inset, Cinema.pxf(context, 3)).forEachIndexed { i, x ->
            val y = PlayerMath.waveY(x, inset, h / 2f, amp, wave)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        glow.shader = RadialGradient(0f, 0f, bulbR * 3f, intArrayOf(Cinema.alpha(Cinema.CREAM, 0.5f), Cinema.alpha(Cinema.CREAM, 0f)), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val px = PlayerMath.playheadX(progress, inset, width - inset)
        canvas.save(); canvas.clipRect(px, 0f, width.toFloat(), height.toFloat()); canvas.drawPath(path, ahead); canvas.restore()
        if (progress <= 0f) return
        canvas.save(); canvas.clipRect(0f, 0f, px, height.toFloat()); watched.forEach { canvas.drawPath(path, it) }; canvas.restore()
        val py = PlayerMath.waveY(px, inset, height / 2f, amp, wave)
        if (lit) { canvas.save(); canvas.translate(px, py); canvas.drawCircle(0f, 0f, bulbR * 3f, glow); canvas.restore() }
        canvas.drawCircle(px, py, if (lit) bulbR * 1.15f else bulbR, bulb)
    }
}

/**
 * Cinema's loading mark, in place of a spinner: a ribbon of the scrubber's three bands runs along a gently swaying wave,
 * led by the cream playhead bulb and fading out behind it, again and again. With animations off it holds a still frame.
 * Draws itself only while it is on screen.
 */
class WaveLoader(context: Context, private val amplitudePx: Float = 12f, private val wavelengthPx: Float = 120f, thicknessPx: Float = 18f) : View(context) {
    private val amp get() = Cinema.pxf(context, amplitudePx)
    private val wave get() = Cinema.pxf(context, wavelengthPx)
    private val thick = Cinema.pxf(context, thicknessPx)
    private val bulbR = thick * 0.72f
    private val watched = bands(thick)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Cinema.alpha(Cinema.CREAM, 0.1f); style = Paint.Style.STROKE; strokeWidth = thick * 0.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val bulb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7EFE0.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val inset get() = bulbR * 3.4f   // room for the glow at either end
    /** The glow round the head, never larger than the view can hold (or it is cut into a box). */
    private var glowR = 0f

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; contentDescription = "Loading" }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        glowR = minOf(bulbR * 3.2f, h / 2f - amp); glow.shader = RadialGradient(0f, 0f, glowR, intArrayOf(Cinema.alpha(Cinema.CREAM, 0.6f), Cinema.alpha(Cinema.CREAM, 0.16f), Cinema.alpha(Cinema.CREAM, 0f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    private val visible = android.graphics.Rect()
    /** One unit gradient per band (clear to the band's colour), moved and stretched to the tail each frame: no layer. */
    private val tailShaders = watched.map { p -> LinearGradient(0f, 0f, 1f, 0f, intArrayOf(Cinema.alpha(p.color, 0f), p.color, p.color), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP) }
    private val shaderMatrix = android.graphics.Matrix()

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val animate = Motion.animationsOn(this)
        val now = if (animate) SystemClock.uptimeMillis() else 600L
        val x0 = inset; val x1 = width - inset; val span = x1 - x0
        // The wave sways (its phase drifts) and breathes a little, so even the faint track is alive.
        val phase = (now % 2400L) / 2400f * wave
        val a = amp * (0.8f + 0.2f * sin(now / 900.0 * PI).toFloat())
        val base = height / 2f
        fun y(x: Float) = base + a * sin((x - x0 + phase) / wave * 2 * PI).toFloat()
        path.reset()
        var x = x0; var first = true
        val step = Cinema.pxf(context, 4)
        while (x <= x1) { if (first) path.moveTo(x, y(x)) else path.lineTo(x, y(x)); first = false; x += step }
        path.lineTo(x1, y(x1))
        canvas.drawPath(path, track)
        // One sweep every 1.8 s, wholly inside the bar: the head eases from the left end to the right, the tail sets off a
        // little later and catches it up there, so the ribbon grows out of one end and is drawn into the other.
        val t = (now % 1800L) / 1800f
        fun ease(v: Float) = (1 - kotlin.math.cos(v.coerceIn(0f, 1f) * PI).toFloat()) / 2f
        val head = x0 + span * ease(t / 0.68f)
        val tail = x0 + span * ease((t - 0.32f) / 0.68f)
        val length = head - tail
        if (length > thick * 2) {   // shorter, the clip would leave a sliver of the stroke standing upright
            shaderMatrix.setScale(length, 1f); shaderMatrix.postTranslate(tail, 0f)
            canvas.save()
            canvas.clipRect(tail, 0f, head, height.toFloat())
            watched.forEachIndexed { i, p -> tailShaders[i].setLocalMatrix(shaderMatrix); p.shader = tailShaders[i]; canvas.drawPath(path, p) }
            canvas.restore()
        }
        // The bulb leads the ribbon, fading in as it grows and out as it is drawn in.
        val shown = (length / (span * 0.12f)).coerceIn(0f, 1f)
        if (shown > 0f) {
            val hy = y(head)
            glow.alpha = (shown * 255).toInt(); bulb.alpha = glow.alpha
            canvas.save(); canvas.translate(head, hy); canvas.drawCircle(0f, 0f, glowR, glow); canvas.restore()
            canvas.drawCircle(head, hy, bulbR * (0.7f + 0.3f * shown), bulb)
        }
        // Only keeps moving while some of it is on screen: a loader scrolled out of view costs nothing.
        if (animate && isAttachedToWindow && isShown) {
            if (getLocalVisibleRect(visible)) postInvalidateOnAnimation() else postInvalidateDelayed(250)
        }
    }

    companion object {
        /** The loader with a quiet word beside it ("Loading your library"), for a row or a page still on its way. */
        fun withLabel(context: Context, label: String, widthPx: Int = 180, sizePx: Number = 22): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(WaveLoader(context, amplitudePx = 8f, wavelengthPx = 80f, thicknessPx = 12f), LinearLayout.LayoutParams(Cinema.px(context, widthPx), Cinema.px(context, 56)))
            addView(Cinema.text(context, label, sizePx, Cinema.MUTED), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 18) })
        }
    }
}
