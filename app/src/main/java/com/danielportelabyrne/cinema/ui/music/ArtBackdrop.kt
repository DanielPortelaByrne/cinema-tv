package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import com.danielportelabyrne.cinema.data.music.Backdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The cover, blurred to colour alone, filling the screen: one copy turns slowly under three softer ones that circle
 * and turn the other way, darkened so white text reads over it. A new cover fades in over the old. Everything is
 * always moving, so nothing marks the panel.
 */
class ArtBackdrop(context: Context) : View(context) {
    /** A blurred cover: the whole square, and the same with its edges faded for the floating copies. */
    private class Layers(val base: Bitmap, val soft: Bitmap)

    private var current: Layers? = null
    private var previous: Layers? = null
    private var fadeStart = 0L
    private var timeMs = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint().apply { color = SHADE }

    /** Blocking pixel work: call off the main thread, then hand the result to [show]. */
    fun prepare(cover: Bitmap): Any {
        val small = Bitmap.createScaledBitmap(cover, 40, 40, true)
        val px = IntArray(40 * 40).also { small.getPixels(it, 0, 40, 0, 0, 40, 40) }
        Backdrop.blur(px, 40, 40, 3)
        Backdrop.tint(px)
        val mid = Bitmap.createScaledBitmap(Bitmap.createBitmap(px, 40, 40, Bitmap.Config.ARGB_8888), SIZE, SIZE, true)
        val big = IntArray(SIZE * SIZE).also { mid.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        Backdrop.blur(big, SIZE, SIZE, SIZE / 24)
        val base = Bitmap.createBitmap(big, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Backdrop.roundOff(big, SIZE, SIZE)
        return Layers(base, Bitmap.createBitmap(big, SIZE, SIZE, Bitmap.Config.ARGB_8888))
    }

    fun show(prepared: Any) {
        previous = current
        current = prepared as Layers
        fadeStart = SystemClock.elapsedRealtime()
        invalidate()
    }

    fun advance(nowMs: Long) { timeMs = nowMs; invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BASE)
        val now = current ?: return
        val f = ((SystemClock.elapsedRealtime() - fadeStart) / FADE_MS.toFloat()).coerceIn(0f, 1f)
        if (f >= 1f) previous = null
        previous?.let { draw(canvas, it, 1f) }
        draw(canvas, now, f)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
    }

    private fun draw(canvas: Canvas, l: Layers, alpha: Float) {
        val t = timeMs / 1000.0
        val w = width.toFloat(); val h = height.toFloat()
        val diagonal = hypot(w, h)
        // The base turns about the centre, big enough that no edge ever shows.
        layer(canvas, l.base, w / 2, h / 2, diagonal * 1.08f, (t * 360 / 120).toFloat(), alpha)
        FLOATS.forEachIndexed { i, (size, period, orbit) ->
            val a = 2 * PI * t / period + i * 2.1
            val cx = w * (0.5f + orbit * cos(a).toFloat())
            val cy = h * (0.5f + orbit * 0.8f * sin(a * 1.3).toFloat())
            val spin = (if (i % 2 == 0) -1 else 1) * (t * 360 / (period * 0.7)).toFloat()
            layer(canvas, l.soft, cx, cy, diagonal * size, spin, alpha * 0.85f)
        }
    }

    private fun layer(canvas: Canvas, b: Bitmap, cx: Float, cy: Float, side: Float, degrees: Float, alpha: Float) {
        paint.alpha = (255 * alpha).toInt()
        canvas.save()
        canvas.translate(cx, cy); canvas.rotate(degrees)
        val s = side / SIZE
        canvas.scale(s, s)
        canvas.drawBitmap(b, -SIZE / 2f, -SIZE / 2f, paint)
        canvas.restore()
    }

    companion object {
        private const val SIZE = 256
        private const val FADE_MS = 1_500L
        private const val BASE = 0xFF0B0807.toInt()
        private const val SHADE = 0x5C000000
        /** The floating copies: size against the screen's diagonal, seconds per loop, how far they wander. */
        private val FLOATS = listOf(Triple(0.75f, 70.0, 0.22f), Triple(0.62f, 53.0, 0.28f), Triple(0.55f, 89.0, 0.18f))
    }
}
