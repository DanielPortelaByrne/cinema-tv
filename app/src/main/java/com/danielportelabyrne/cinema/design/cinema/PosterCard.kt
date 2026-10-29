package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/**
 * A Cinema poster card (`.card`): a rounded poster with its name and a quiet subline beneath.
 *
 * Focused, the whole card grows to 1.1 about a point a third of the way down the poster, a soft saffron glow blooms
 * behind the poster with a deeper shadow (no outline), the name turns cream and semibold and the subline
 * ("2026 · Animation") fades in. Sizes are in mockup pixels; [titled] false leaves the text out (the Top 10 row).
 * Parents must not clip children, as the glow and the grown card reach past the card's bounds.
 */
class PosterCard(context: Context, posterW: Int, posterH: Int, titled: Boolean = true, radiusPx: Float = 16f) : LinearLayout(context) {
    val poster: FrameLayout
    val image: ImageView
    val title: TextView?
    val subline: TextView?
    var onFocus: ((Boolean) -> Unit)? = null

    private val radius = Cinema.pxf(context, radiusPx)
    // No outlines in the Cinema language: focus is a soft saffron glow behind the poster, never a ring.
    private val posterWidth = Cinema.px(context, posterW)
    private val posterHeight = Cinema.px(context, posterH)
    // The focus bloom and the resting shadow are painted once per size (Glows) and drawn as bitmaps: as a live blur and
    // an elevation shadow on every card they were most of the GPU's work while a row scrolled.
    private val bloomSpread = Cinema.pxf(context, 6)
    private val bloom = Glows.roundRect(posterWidth + 2 * bloomSpread.toInt(), posterHeight + 2 * bloomSpread.toInt(), radius + bloomSpread,
        Cinema.pxf(context, 34), Cinema.alpha(Cinema.SAFFRON, 0.42f))
    private val restElevation = Cinema.pxf(context, 14)
    private val shadow = Glows.shadow(posterWidth, posterHeight, radius, restElevation)
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        orientation = VERTICAL
        isFocusable = true; isClickable = true
        clipChildren = false; clipToPadding = false
        setWillNotDraw(false)
        poster = FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = radius; setColor(Cinema.PLACEHOLDER) }
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
            clipToOutline = true
            elevation = 0f   // the resting shadow is drawn by the card; only the focused card gets a real one
            // The poster's lower edge darkens a touch (`.card .p::after`), so a badge or the next row reads against it.
            foreground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0, 0x40000000))
        }
        image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        poster.addView(image, FrameLayout.LayoutParams(-1, -1))
        addView(poster, LayoutParams(Cinema.px(context, posterW), posterHeight))
        if (titled) {
            title = Cinema.text(context, "", 21, Cinema.CARD_TITLE, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            subline = Cinema.text(context, "", 17, Cinema.DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; alpha = 0f }
            addView(title, LayoutParams(Cinema.px(context, posterW), -2).apply { topMargin = Cinema.px(context, 16) })
            addView(subline, LayoutParams(Cinema.px(context, posterW), -2).apply { topMargin = Cinema.px(context, 2) })
        } else { title = null; subline = null }
    }

    /** Big initials on the placeholder, for a title with no poster at all. */
    fun placeholder(name: String) {
        val initials = name.filter { it.isLetterOrDigit() || it == ' ' }.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }
        poster.addView(Cinema.text(context, initials, 52, Cinema.alpha(Cinema.CREAM, 0.5f), serif = true).apply { gravity = Gravity.CENTER }, 0, FrameLayout.LayoutParams(-1, -1))
    }

    /** A small dark pill top left ("EVA", "#3"): whose diary a poster came from, or its place in a list. */
    fun chip(text: String) {
        poster.addView(Cinema.text(context, text, 14, Cinema.CREAM, weight = 700, tracking = 0.14f, caps = true).apply {
            setPadding(Cinema.px(context, 11), Cinema.px(context, 5), Cinema.px(context, 11), Cinema.px(context, 5))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 16); setColor(0xC70C0806.toInt()) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(Cinema.px(context, 10), Cinema.px(context, 10), 0, 0) })
    }

    /** The "IN LIBRARY" badge (`.badge`): dark glass pill, saffron tracked capitals and a check, bottom left. */
    fun badge() {
        val pill = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(context, 9), Cinema.px(context, 6), Cinema.px(context, 12), Cinema.px(context, 6))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 20); setColor(0xD10C0806.toInt()) }
        }
        pill.addView(Cinema.icon(context, R.drawable.ic_cin_check, Cinema.SAFFRON), LayoutParams(Cinema.px(context, 15), Cinema.px(context, 15)).apply { marginEnd = Cinema.px(context, 7) })
        pill.addView(Cinema.text(context, "IN LIBRARY", 14, Cinema.SAFFRON, weight = 700, tracking = 0.14f))
        poster.addView(pill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Cinema.px(context, 12), 0, 0, Cinema.px(context, 12)) })
    }

    private var watcherRow: LinearLayout? = null

    /**
     * Little round badges, one per member who has watched the film, overlapping in the bottom right corner (at most four).
     * Each is the member's picture, or their initial until the picture comes. Calling again replaces them.
     */
    fun watchers(people: List<Pair<String, Bitmap?>>) {
        watcherRow?.let { poster.removeView(it) }
        watcherRow = null
        if (people.isEmpty()) return
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; clipChildren = false }
        people.take(4).forEachIndexed { i, (name, picture) ->
            row.addView(AvatarView(context, 44).apply { set(name, picture) },
                LinearLayout.LayoutParams(Cinema.px(context, 44), Cinema.px(context, 44)).apply { if (i > 0) marginStart = Cinema.px(context, -12) })
        }
        poster.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, Cinema.px(context, 12), Cinema.px(context, 12)) })
        watcherRow = row
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = posterHeight * 0.35f
    }

    /** The picture, already shaded at its foot (RemoteImages' poster variant), so the live gradient over it can go. */
    fun setShadedPoster(bitmap: android.graphics.Bitmap) { image.setImageBitmap(bitmap); poster.foreground = null }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val scale = if (gainFocus) 1.1f else 1f
        val duration = if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
        // Scale animates; depth is set once (an animated translationZ re-shadowed the card every frame).
        translationZ = if (gainFocus) Cinema.pxf(context, 20) else 0f
        if (Motion.animationsOn(this)) animate().scaleX(scale).scaleY(scale).setDuration(duration).setInterpolator(Motion.SETTLE).start()
        else { scaleX = scale; scaleY = scale }
        poster.elevation = if (gainFocus) Cinema.pxf(context, 34) else 0f
        title?.let { t -> t.setTextColor(if (gainFocus) Cinema.CREAM else Cinema.CARD_TITLE); Cinema.weight(t, 21, serif = false, weight = if (gainFocus) 600 else 500) }
        subline?.animate()?.alpha(if (gainFocus && !subline.text.isNullOrEmpty()) 1f else 0f)?.setDuration(duration)?.start()
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) {
            // A warm bloom behind the poster, a little larger than it, so the focused card seems lit from behind.
            val s = bloomSpread.toInt()
            canvas.drawBitmap(bloom.bitmap, (poster.left - s - bloom.pad).toFloat(), (poster.top - s - bloom.pad).toFloat(), shadowPaint)
        } else {
            canvas.drawBitmap(shadow.bitmap, (poster.left - shadow.pad).toFloat(), poster.top - shadow.pad + Glows.shadowOffset(restElevation), shadowPaint)
        }
        super.dispatchDraw(canvas)
    }
}
