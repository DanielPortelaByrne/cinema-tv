package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.Glows
import com.danielportelabyrne.cinema.design.cinema.WaveBar

/**
 * A Continue watching card: a 16:9 still with the title and what is left over its foot, and the signature wavy bar
 * beneath it showing how far in it is. [featured] is the row's large lead card (the title watched last), with a
 * RESUME kicker and a bigger name. Focused, the card grows, a warm glow blooms behind the still and a ringed play disc
 * rises in its middle. Parents must not clip children, as the glow and the grown card reach past the card's bounds.
 */
class ResumeCard(context: Context, private val featured: Boolean = false) : LinearLayout(context) {
    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val title = Cinema.text(context, "", if (featured) 50 else 24, Cinema.CREAM, serif = true, weight = 500).apply {
        maxLines = if (featured) 2 else 1; ellipsize = TextUtils.TruncateAt.END
    }
    val meta = Cinema.text(context, "", if (featured) 22 else 17, Cinema.alpha(Cinema.CREAM, 0.74f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    /** The episode or other subtitle, shown in saffron above the name on the featured card. */
    val kicker = Cinema.text(context, "Resume", 16, Cinema.SAFFRON, weight = 700, tracking = 0.2f, caps = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    var onFocus: ((Boolean) -> Unit)? = null

    private val stillW = Cinema.px(context, if (featured) W_FEATURED else W_SMALL)
    private val stillH = Cinema.px(context, if (featured) H_FEATURED else H_SMALL)
    private val radius = Cinema.pxf(context, if (featured) 26 else 18)
    private val still = FrameLayout(context)
    private val bar = WaveBar(context, amplitudePx = if (featured) 6f else 5f, wavelengthPx = if (featured) 70f else 56f, thicknessPx = if (featured) 12f else 10f)
    private val disc = PlayDisc(context).apply { alpha = 0f; scaleX = 0.8f; scaleY = 0.8f }
    private val bloomSpread = Cinema.pxf(context, 8)
    private val bloom by lazy { Glows.roundRect(stillW + 2 * bloomSpread.toInt(), stillH + 2 * bloomSpread.toInt(), radius + bloomSpread,
        Cinema.pxf(context, 40), Cinema.alpha(Cinema.STRIPE_ORANGE, 0.38f)) }
    private val restElevation = Cinema.pxf(context, 16)
    private val shadow = Glows.shadow(stillW, stillH, radius, restElevation)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        orientation = VERTICAL
        isFocusable = true; isClickable = true
        clipChildren = false; clipToPadding = false
        setWillNotDraw(false)
        still.background = GradientDrawable().apply { cornerRadius = radius; setColor(Cinema.PLACEHOLDER) }
        still.clipToOutline = true
        still.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) }
        }
        still.addView(image, FrameLayout.LayoutParams(-1, -1))
        // A scrim from the middle down, so the words read on any picture.
        still.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0x26000000, 0xE0080605.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val words = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.BOTTOM or Gravity.START
            val side = Cinema.px(context, if (featured) 44 else 22)
            setPadding(side, 0, side, Cinema.px(context, if (featured) 38 else 18))
        }
        if (featured) words.addView(kicker, LayoutParams(-2, -2).apply { bottomMargin = Cinema.px(context, 8) })
        words.addView(title, LayoutParams(-1, -2))
        words.addView(meta, LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 6) })
        still.addView(words, FrameLayout.LayoutParams(-1, -1))
        val discSize = Cinema.px(context, if (featured) 140 else 84)
        still.addView(disc, FrameLayout.LayoutParams(discSize, discSize, if (featured) Gravity.END or Gravity.TOP else Gravity.CENTER).apply {
            if (featured) { topMargin = Cinema.px(context, 26); rightMargin = Cinema.px(context, 26) } else bottomMargin = Cinema.px(context, 34)
        })
        addView(still, LayoutParams(stillW, stillH))
        addView(bar, LayoutParams(stillW, Cinema.px(context, if (featured) BAR_FEATURED else BAR_SMALL)).apply { topMargin = Cinema.px(context, BAR_GAP) })
    }

    /** How far through the title it is, 0 to 1. */
    fun setProgress(fraction: Float) { bar.progress = fraction.coerceIn(0f, 1f) }

    fun setStill(bitmap: Bitmap) = image.setImageBitmap(bitmap)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f; pivotY = stillH * 0.45f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val scale = if (gainFocus) (if (featured) 1.04f else 1.07f) else 1f
        val ms = if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
        translationZ = if (gainFocus) Cinema.pxf(context, 20) else 0f
        bar.lit = gainFocus
        if (Motion.animationsOn(this)) {
            animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(Motion.SETTLE).start()
            disc.animate().alpha(if (gainFocus) 1f else 0f).scaleX(if (gainFocus) 1f else 0.8f).scaleY(if (gainFocus) 1f else 0.8f).setDuration(ms + 60).setInterpolator(Motion.SETTLE).start()
        } else { scaleX = scale; scaleY = scale; disc.alpha = if (gainFocus) 1f else 0f; disc.scaleX = 1f; disc.scaleY = 1f }
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) {
            val s = bloomSpread.toInt()
            canvas.drawBitmap(bloom.bitmap, (still.left - s - bloom.pad).toFloat(), (still.top - s - bloom.pad).toFloat(), paint)
        } else {
            canvas.drawBitmap(shadow.bitmap, (still.left - shadow.pad).toFloat(), still.top - shadow.pad + Glows.shadowOffset(restElevation), paint)
        }
        super.dispatchDraw(canvas)
    }

    companion object {
        /** Mockup sizes: the lead card's still, a small card's still, the wavy bar under each and the gap above it. */
        const val W_FEATURED = 816; const val H_FEATURED = 459
        const val W_SMALL = 360; const val H_SMALL = 203
        const val BAR_FEATURED = 34; const val BAR_SMALL = 26; const val BAR_GAP = 12
        /** Height of a whole card (still, gap and bar). */
        fun heightOf(featured: Boolean) = if (featured) H_FEATURED + BAR_GAP + BAR_FEATURED else H_SMALL + BAR_GAP + BAR_SMALL
    }

    /** A cream play disc ringed in the stripe set, as the player's pause disc is, sized to its view. */
    private class PlayDisc(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val play = context.getDrawable(R.drawable.ic_cin_play)!!.mutate().apply { setTint(0xFF1B120C.toInt()) }
        init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            val step = width * 0.045f
            val r = c - step * 5
            for (i in 5 downTo 1) { paint.color = Cinema.RINGS[5 - i]; canvas.drawCircle(c, c, r + step * i, paint) }
            paint.color = Cinema.CREAM; canvas.drawCircle(c, c, r, paint)
            val s = r * 0.46f
            val nudge = r * 0.08f   // a play triangle looks centred a little right of the middle
            play.setBounds((c - s + nudge).toInt(), (c - s).toInt(), (c + s + nudge).toInt(), (c + s).toInt()); play.draw(canvas)
        }
    }
}
