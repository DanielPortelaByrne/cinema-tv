package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The decorative 70s ribbon (`ribbon()` in the mockups): one path stroked five times, widest maroon to narrowest
 * saffron, so it reads as nested stripes. [path] is in the mockups' 1920-wide frame and scaled to the view's width;
 * [fade] masks it (the ribbon fades out toward the content). Painted once per size into a cached bitmap.
 */
class RibbonView(context: Context, private val path: Path, private val widthPx: Float, private val opacity: Float, private val fade: Fade) : View(context) {
    /** Which way the ribbon fades: stops are (position along the screen, alpha). */
    class Fade(val horizontal: Boolean, val stops: List<Pair<Float, Float>>)

    private var cache: Bitmap? = null

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** One bitmap, drawn once: its alpha applies straight to that draw, with no offscreen layer each frame. */
    override fun hasOverlappingRendering() = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cache?.recycle(); cache = null
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val scale = w / 1920f
        val scaled = Path(path).apply { transform(Matrix().apply { setScale(scale, scale) }) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.BUTT }
        val rings = Cinema.RINGS
        rings.forEachIndexed { i, color ->
            stroke.color = color
            stroke.strokeWidth = widthPx * scale * (rings.size - i) / rings.size
            c.drawPath(scaled, stroke)
        }
        // The mask runs across the whole screen frame (1920 x 1080, scaled), as in the mockup.
        val frameH = 1080f * scale
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = if (fade.horizontal) LinearGradient(0f, 0f, w.toFloat(), 0f, fade.stops.map { Cinema.alpha(-0x1000000, it.second) }.toIntArray(), fade.stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
                     else LinearGradient(0f, 0f, 0f, frameH, fade.stops.map { Cinema.alpha(-0x1000000, it.second) }.toIntArray(), fade.stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        cache = bmp
        alpha = opacity
    }

    override fun onDraw(canvas: Canvas) { cache?.let { canvas.drawBitmap(it, 0f, 0f, null) } }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); cache?.recycle(); cache = null }

    companion object {
        /** Downloads: a loop entering from the right edge, top right (`M1990 82H1640A62 62 0 0 0 1640 206H1990`). */
        fun downloads(context: Context, opacity: Float = 0.85f) = RibbonView(context, Path().apply {
            moveTo(1990f, 82f); lineTo(1640f, 82f)
            arcTo(android.graphics.RectF(1578f, 82f, 1702f, 206f), 270f, -180f)
            lineTo(1990f, 206f)
        }, 90f, opacity, Fade(true, listOf(0f to 0f, 0.45f to 0f, 0.8f to 1f, 1f to 1f)))

        /** Stream loading: a stepped ribbon falling from the top left (`M300 -60V150A80 80 0 0 0 380 230H560A80 80 0 0 1 640 310V560`). */
        fun loading(context: Context) = RibbonView(context, Path().apply {
            moveTo(300f, -60f); lineTo(300f, 150f)
            arcTo(android.graphics.RectF(300f, 70f, 460f, 230f), 180f, -90f)
            lineTo(560f, 230f)
            arcTo(android.graphics.RectF(480f, 230f, 640f, 390f), 270f, 90f)
            lineTo(640f, 560f)
        }, 100f, 0.95f, Fade(false, listOf(0f to 1f, 0.22f to 1f, 0.5f to 0f, 1f to 0f)))
    }
}
