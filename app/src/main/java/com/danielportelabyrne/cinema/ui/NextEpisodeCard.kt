package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.StripeDrawable
import kotlin.math.ceil

/**
 * "Up next" in the Cinema look: a dark glass panel low on the right (the credits stay in view), the striped eyebrow,
 * the episode's still with a stripe bar filling along its foot as the countdown runs, its name in Fraunces, and Play
 * next (with the countdown ring) beside Stay here. Only the ring and the bar redraw each frame.
 */
class NextEpisodeCard(context: Context, play: () -> Unit, cancel: () -> Unit) : FrameLayout(context) {
    private fun p(mock: Int) = Cinema.px(context, mock)

    val artwork = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 18); setColor(Cinema.PLACEHOLDER) }
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 18)) } }
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = Cinema.text(context, "", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true)
    private val episodeTitle = Cinema.text(context, "", 40, Cinema.CREAM, serif = true, tracking = -0.01f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val episodeMeta = Cinema.text(context, "", 20, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val bar = CountdownBar(context)
    private val ring = CountdownRing(context)
    private val primary = CinemaButton(context, CinemaButton.Kind.PRIMARY, "Play next", null).apply {
        addView(ring, 0, LinearLayout.LayoutParams(Cinema.px(context, 46), Cinema.px(context, 46)).apply { marginEnd = Cinema.px(context, 14) })
        setPadding(Cinema.px(context, 16), 0, Cinema.px(context, 34), 0)
        setOnClickListener { play() }
    }
    private val stay = CinemaButton(context, CinemaButton.Kind.GLASS, "Stay here", null).apply { setOnClickListener { cancel() } }

    init {
        visibility = View.GONE
        clipChildren = false; clipToPadding = false
        // A soft fade from the right edge, so the panel sits on the picture without a hard box.
        addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, Cinema.alpha(Cinema.BG, 0.35f), Cinema.alpha(Cinema.BG, 0.7f)))
        }, LayoutParams(p(900), -1, Gravity.END))
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(p(32), p(30), p(32), p(32))
            background = Cinema.glass(context, 34f, 0xD9120D0A.toInt())
            elevation = Cinema.pxf(context, 24)
        }
        val eyebrow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        eyebrow.addView(View(context).apply { background = StripeDrawable(horizontalBands = true, radius = Cinema.pxf(context, 5)) }, LinearLayout.LayoutParams(p(40), p(10)).apply { marginEnd = p(14) })
        eyebrow.addView(Cinema.text(context, "Up next", 17, Cinema.SAFFRON, weight = 600, tracking = 0.24f, caps = true))
        eyebrow.addView(Cinema.dot(context, Cinema.SAFFRON, 5, alpha = 0.6f), LinearLayout.LayoutParams(p(5), p(5)).apply { marginStart = p(14); marginEnd = p(14) })
        eyebrow.addView(label)
        card.addView(eyebrow)
        val still = FrameLayout(context)
        still.addView(artwork, LayoutParams(-1, -1))
        still.addView(bar, LayoutParams(-1, p(7), Gravity.BOTTOM))
        card.addView(still, LinearLayout.LayoutParams(p(496), p(279)).apply { topMargin = p(22) })
        card.addView(episodeTitle, LinearLayout.LayoutParams(p(496), -2).apply { topMargin = p(22) })
        card.addView(episodeMeta, LinearLayout.LayoutParams(p(496), -2).apply { topMargin = p(6) })
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        actions.addView(primary, LinearLayout.LayoutParams(-2, p(76)))
        actions.addView(stay, LinearLayout.LayoutParams(-2, p(76)).apply { marginStart = p(18) })
        card.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(28) })
        addView(card, LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { marginEnd = p(96); bottomMargin = p(96) })
        primary.setOnFocusChangeListener { _, f -> ring.lit = f }
    }

    fun bind(item: JellyfinItem) = bindEpisode(
        item.episodeLabel().orEmpty(), item.name, listOfNotNull(item.seriesName?.takeIf { it.isNotBlank() }, item.runtimeMin?.let { "$it min" }).joinToString(" · "))

    /** An episode by its parts: "S2 E4", its title, and its line of meta. Streamed episodes come this way. */
    fun bindEpisode(code: String, title: String, meta: String) {
        artwork.setImageDrawable(null)
        episodeTitle.text = title
        label.text = code
        episodeMeta.text = meta
        contentDescription = "Up next: $title"
    }

    fun showCard() {
        if (visibility == View.VISIBLE) return
        visibility = View.VISIBLE
        if (Motion.animationsOn(this)) {
            alpha = 0f; translationX = Cinema.pxf(context, 24)
            animate().alpha(1f).translationX(0f).setDuration(Motion.SHEET_IN_MS).setInterpolator(Motion.SETTLE).start()
        }
        primary.requestFocus()
        post { if (visibility == View.VISIBLE) primary.requestFocus() }
    }

    fun countdown(remainingMs: Long?, fraction: Float) {
        val seconds = remainingMs?.let { ceil(it / 1000.0).toInt() }
        ring.update(fraction, seconds?.toString() ?: "▶")
        bar.fill = if (remainingMs == null) 0f else 1f - fraction
        val description = if (seconds != null) "Play next episode, starts in $seconds seconds" else "Play next episode"
        if (primary.contentDescription != description) primary.contentDescription = description
    }

    fun hideCard() { animate().cancel(); visibility = View.GONE; alpha = 1f; translationX = 0f }
}

/** The ring on Play next: a faint track, the time left in saffron (terracotta once focused), the seconds inside. */
private class CountdownRing(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private var fraction = 1f
    private var label = ""
    var lit = false
        set(value) { field = value; invalidate() }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(progress: Float, value: String) { fraction = progress.coerceIn(0f, 1f); label = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val stroke = Cinema.pxf(context, 4)
        val ink = if (lit) Cinema.INK else Cinema.CREAM
        arc.set(stroke, stroke, width - stroke, height - stroke)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Cinema.alpha(ink, 0.2f); canvas.drawOval(arc, paint)
        paint.color = if (lit) Cinema.STRIPE_TERRA else Cinema.SAFFRON
        canvas.drawArc(arc, -90f, fraction * 360f, false, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.color = ink
        paint.textSize = Cinema.pxf(context, 21); paint.typeface = Cinema.typeface(context, serif = false); paint.isFakeBoldText = true
        canvas.drawText(label, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}

/** The stripe bar along the still's foot: how far the countdown has gone. */
private class CountdownBar(context: Context) : View(context) {
    var fill = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    private val track = Paint().apply { color = Cinema.alpha(Cinema.CREAM, 0.2f) }
    private val stripes = StripeDrawable(horizontalBands = true, radius = 0f)

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), track)
        val w = (width * fill).toInt()
        if (w > 0) { stripes.setBounds(0, 0, w, height); stripes.draw(canvas) }
    }
}
