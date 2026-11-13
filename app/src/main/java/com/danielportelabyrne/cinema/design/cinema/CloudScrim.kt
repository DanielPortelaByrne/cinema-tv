package com.danielportelabyrne.cinema.design.cinema

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A soft, irregular dark cloud to sit behind a featured title's words, in place of a broad side fade: a handful of
 * overlapping ellipses of the page's ground colour, each strongest in its middle and gone at its edge, so the words
 * read while the rest of the picture is left alone. Each blob is (centre x, centre y, radius x, radius y) as fractions of
 * the drawable's bounds. Painted once per size into a bitmap half the size.
 */
class CloudScrim(private val blobs: List<FloatArray> = DEFAULT, private val peak: Float = 0.7f) : Drawable() {
    private var cache: Bitmap? = null
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    override fun onBoundsChange(b: Rect) {
        cache = null
        val sw = (b.width() / 2).coerceAtLeast(0); val sh = (b.height() / 2).coerceAtLeast(0)
        if (sw == 0 || sh == 0) return
        val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(Cinema.alpha(Cinema.BG, peak), Cinema.alpha(Cinema.BG, peak * 0.72f), Cinema.alpha(Cinema.BG, peak * 0.3f), Cinema.alpha(Cinema.BG, 0f)),
            floatArrayOf(0f, 0.38f, 0.7f, 1f), Shader.TileMode.CLAMP)
        for (blob in blobs) {
            c.save()
            c.translate(blob[0] * sw, blob[1] * sh)
            c.scale(blob[2] * sw, blob[3] * sh)   // a unit circle becomes the ellipse, and so does its gradient
            c.drawCircle(0f, 0f, 1f, p)
            c.restore()
        }
        bmp.prepareToDraw()
        cache = bmp
    }

    override fun draw(canvas: Canvas) { cache?.let { canvas.drawBitmap(it, null, bounds, blit) } }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    companion object {
        /**
         * For Home's landing: the same lumpy mass sitting lower and thinner, with nothing at the very top, so the picture
         * shows above the title and the dark falls away like weather rather than standing as a column.
         */
        val HOME = listOf(
            floatArrayOf(0.28f, 0.42f, 0.36f, 0.26f),
            floatArrayOf(0.50f, 0.34f, 0.24f, 0.16f),
            floatArrayOf(0.20f, 0.64f, 0.26f, 0.22f),
            floatArrayOf(0.46f, 0.56f, 0.20f, 0.20f),
            floatArrayOf(0.02f, 0.50f, 0.16f, 0.34f),
            floatArrayOf(0.32f, 0.78f, 0.18f, 0.12f)
        )

        /** For a 1400 x 1080 box over the featured title's words: a lumpy mass, wider at the title, narrower at the buttons. */
        val DEFAULT = listOf(
            floatArrayOf(0.30f, 0.33f, 0.40f, 0.30f),
            floatArrayOf(0.55f, 0.22f, 0.28f, 0.22f),
            floatArrayOf(0.22f, 0.58f, 0.28f, 0.24f),
            floatArrayOf(0.50f, 0.50f, 0.24f, 0.24f),
            floatArrayOf(0.04f, 0.42f, 0.20f, 0.38f),
            floatArrayOf(0.36f, 0.72f, 0.20f, 0.14f)
        )
    }
}

/**
 * The dark that lets Home's featured words read, shaped like weather rather than a column: one big ellipse centred low at
 * the left edge, deepest in the corner and thinning along a smooth curve up and across the picture, so the sky and the face
 * stay clear and the edge of the dark sweeps diagonally instead of standing as a vertical band. Centre and radii are
 * fractions of the drawable's width and height. Painted once per size into a bitmap a quarter the size and scaled up (it is
 * all soft gradient), so there is nothing to draw each frame.
 */
class ArcScrim(
    private val cx: Float = 0.0f, private val cy: Float = 1.02f, private val rx: Float = 0.84f, private val ry: Float = 1.12f,
    private val stops: List<Pair<Float, Float>> = DEFAULT
) : Drawable() {
    private var cache: Bitmap? = null
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    override fun onBoundsChange(b: Rect) {
        cache = null
        val sw = (b.width() / 4).coerceAtLeast(0); val sh = (b.height() / 4).coerceAtLeast(0)
        if (sw == 0 || sh == 0) return
        val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = RadialGradient(0f, 0f, 1f, stops.map { Cinema.alpha(Cinema.BG, it.second) }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
        val c = Canvas(bmp)
        c.translate(cx * sw, cy * sh)
        c.scale(rx * sw, ry * sh)   // a unit circle becomes the ellipse, and so does its gradient
        c.drawRect(-cx / rx - 1f, -cy / ry - 1f, (1f - cx) / rx + 1f, (1f - cy) / ry + 1f, p)
        bmp.prepareToDraw()
        cache = bmp
    }

    override fun draw(canvas: Canvas) { cache?.let { canvas.drawBitmap(it, null, bounds, blit) } }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    companion object {
        /** (distance from the centre as a share of the radius, how dark): eased at both ends so no edge shows. */
        val DEFAULT = listOf(0f to 0.95f, 0.25f to 0.9f, 0.5f to 0.78f, 0.7f to 0.58f, 0.85f to 0.3f, 0.95f to 0.08f, 1f to 0f)
    }
}
