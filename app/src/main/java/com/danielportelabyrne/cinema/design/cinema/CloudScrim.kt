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
