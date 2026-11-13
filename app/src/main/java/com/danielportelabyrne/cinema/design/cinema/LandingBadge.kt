package com.danielportelabyrne.cinema.design.cinema

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import kotlin.math.PI
import kotlin.math.sin

/**
 * The corner of Home's landing screen that makes it a branded poster: a row of tall rounded columns in the stripe colours
 * hanging from the top edge, their lower ends rising and falling in a slow wave, with the Cinema mark and its name over
 * them at the right. The columns fade out toward the left so they never meet the featured title's words. Sizes are mockup pixels.
 */
class LandingBadge(context: Context) : FrameLayout(context) {
    private val waves = Waves(context)

    init {
        isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; clipChildren = false
        addView(waves, LayoutParams(-1, -1))
        val brand = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(context).apply { setImageResource(R.drawable.cinema_mark) }, LinearLayout.LayoutParams(Cinema.px(context, 60), Cinema.px(context, 60)))
        brand.addView(Cinema.text(context, "Cinema", 40, Cinema.CREAM, serif = true, weight = 500, tracking = -0.02f).apply {
            maxLines = 1; setShadowLayer(Cinema.pxf(context, 16), 0f, Cinema.pxf(context, 2), 0xAA000000.toInt())
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 16) })
        addView(brand, LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { topMargin = Cinema.px(context, 34); rightMargin = Cinema.px(context, 120) })
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); waves.start() }
    override fun onDetachedFromWindow() { waves.stop(); super.onDetachedFromWindow() }

    /** The columns. One animated value, the wave's phase; each column's length is read from it. */
    private class Waves(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        private var phase = 0f
        private var animator: ValueAnimator? = null
        private val gap = Cinema.pxf(context, 12)
        private val w = Cinema.pxf(context, 28)

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun hasOverlappingRendering() = false

        fun start() {
            if (!Motion.animationsOn(this) || animator != null) return
            animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 14_000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        fun stop() { animator?.cancel(); animator = null }

        override fun onDraw(canvas: Canvas) {
            val stripes = Cinema.STRIPES
            val count = ((width + gap) / (w + gap)).toInt().coerceAtLeast(1)
            val tall = height.toFloat()
            // Right-aligned, so the columns end at the screen's edge and thin out leftwards.
            val x0 = width - count * (w + gap) + gap
            for (i in 0 until count) {
                val t = i / (count - 1f).coerceAtLeast(1f)             // 0 left, 1 right
                val wave = 0.5f + 0.5f * sin(phase + i * 0.62f) * (0.6f + 0.4f * sin(phase * 0.5f + i * 0.21f))
                val length = tall * (0.46f + 0.54f * wave) * (0.55f + 0.45f * t)
                val left = x0 + i * (w + gap)
                // Strongest at the right, gone at the far left.
                paint.color = Cinema.alpha(stripes[(i + (count - 1 - i) / 3) % stripes.size], 0.10f + 0.80f * t * t)
                box.set(left, -w, left + w, length)
                canvas.drawRoundRect(box, w / 2, w / 2, paint)
            }
        }
    }
}
