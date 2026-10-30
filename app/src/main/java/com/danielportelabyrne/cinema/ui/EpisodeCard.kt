package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
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
import com.danielportelabyrne.cinema.design.cinema.StripeDrawable

/**
 * An episode title card (`docs/ui-review/series-episodes`, `.ep`): a 16:9 still with the episode chip, a status badge
 * and a Play and a Download disc on it, then the title, "runtime · air date", and the overview on the lit card only.
 *
 * The discs are the focus stops; the card is lit (still grown 1.06, cream ring, saffron glow) while either has focus.
 * An unaired episode has no discs, so the card itself is its single focus stop. Sizes are mockup pixels.
 * Parents must not clip children: the ring, the glow, the grown still and the tooltip all reach past the card.
 */
class EpisodeCard(context: Context, val number: Int, name: String, overview: String?) : FrameLayout(context) {
    val play = ActionDisc(context, 78, R.drawable.ic_cin_play, 34, nudge = 2f)
    val download = ActionDisc(context, 62, R.drawable.ic_cin_dl, 28)
    /** Deletes this episode's file. Shown only while the card has focus and the episode is downloaded. */
    val delete = ActionDisc(context, 56, R.drawable.ic_cin_trash, 26)
    /** Whether the episode is on disk, so it can be deleted. */
    var deletable = false
        set(v) { field = v; updateDelete() }
    var onDelete: (() -> Unit)? = null
    /** Called once the card lights up or goes out, so the page can scroll it into view. */
    var onLit: ((Boolean) -> Unit)? = null
    /** Shown above the Download disc while it has focus ("Download S2 E4"); null for none. */
    var tipText: String? = null
        set(value) { field = value; tip.text = value.orEmpty(); refreshTip() }

    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
    private val still = FrameLayout(context)
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val placeholder = FrameLayout(context).apply { visibility = GONE }
    private val chip = Cinema.text(context, "E$number", 15, Cinema.CREAM, weight = 700, tracking = 0.14f)
    private val badge = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = GONE }
    private val badgeCheck = Cinema.icon(context, R.drawable.ic_cin_check, Cinema.SAFFRON)
    private val badgeText = Cinema.text(context, "", 13, Cinema.SAFFRON, weight = 700, tracking = 0.14f).apply { maxLines = 1 }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    private val airs = Cinema.text(context, "", 18, Cinema.CREAM, weight = 600, tracking = 0.12f, caps = true).apply { visibility = GONE }
    // Watch progress along the still's foot, saffron over a dark track like the Continue row.
    private val track = View(context).apply { setBackgroundColor(0x66000000); visibility = GONE }
    private val fill = View(context).apply { setBackgroundColor(Cinema.SAFFRON); visibility = GONE }
    private val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = Cinema.text(context, name, 23, Cinema.CARD_TITLE, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val meta = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val metaTexts = ArrayList<TextView>()
    private val overviewText = Cinema.text(context, overview.orEmpty(), 18, Cinema.READING, weight = 300).apply {
        // Two lines are always reserved, so the rail does not jump when the overview appears.
        setLines(2); ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f); alpha = 0f
    }
    private val tip = Cinema.text(context, "", 16, Cinema.INK, weight = 600, tracking = 0.06f).apply {
        setPadding(p(12), p(6), p(12), p(6)); background = Cinema.glass(context, 8f, Cinema.SAFFRON); visibility = GONE; maxLines = 1
    }

    private val stillW = p(400)
    private val stillH = p(225)
    private val radius = pf(18)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Cinema.alpha(Cinema.SAFFRON, 0.24f)
        if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(pf(45), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val box = RectF()
    private var lit = false
    private var unaired = false
    /** Unaired with nothing to press: the card itself takes focus. */
    private var cardStop = false

    init {
        clipChildren = false; clipToPadding = false; setWillNotDraw(false)
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        contentDescription = "Episode $number, $name"

        still.background = GradientDrawable().apply { cornerRadius = radius; setColor(Cinema.PLACEHOLDER) }
        still.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
        still.clipToOutline = true
        still.elevation = pf(14)
        still.addView(placeholder, LayoutParams(-1, -1))
        still.addView(image, LayoutParams(-1, -1))
        // The scrim: dark at the top (for the chip and badge) fading out by 28%, and dark again at the foot.
        still.addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Cinema.alpha(Cinema.BG, 0.45f), 0)) },
            LayoutParams(-1, (stillH * 0.28f).toInt(), Gravity.TOP))
        still.addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, Cinema.alpha(Cinema.BG, 0.55f))) },
            LayoutParams(-1, (stillH * 0.4f).toInt(), Gravity.BOTTOM))
        chip.setPadding(p(12), p(6), p(12), p(6)); chip.background = Cinema.glass(context, 16f, 0xA80C0806.toInt())
        still.addView(chip, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { leftMargin = p(14); topMargin = p(12) })
        badge.setPadding(p(10), p(7), p(12), p(7))
        badge.addView(badgeCheck, LinearLayout.LayoutParams(p(14), p(14)).apply { marginEnd = p(7) })
        badge.addView(badgeText)
        still.addView(badge, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = p(12); topMargin = p(12) })
        // Discs are larger than they look (room for the focus rings), so the 22 px gap is taken back off their margins.
        actions.addView(play, LinearLayout.LayoutParams(play.box, play.box))
        actions.addView(download, LinearLayout.LayoutParams(download.box, download.box).apply { marginStart = p(22) - play.pad - download.pad })
        still.addView(actions, LayoutParams(-1, -1))
        airs.setPadding(p(18), p(10), p(18), p(10)); airs.background = Cinema.glass(context, 22f, 0xA80C0806.toInt())
        still.addView(airs, LayoutParams(-2, -2, Gravity.CENTER))
        still.addView(track, LayoutParams(-1, p(6), Gravity.BOTTOM))
        still.addView(fill, LayoutParams(0, p(6), Gravity.BOTTOM or Gravity.START))
        column.addView(still, LinearLayout.LayoutParams(stillW, stillH))

        texts.addView(title, LinearLayout.LayoutParams(stillW, -2))
        texts.addView(meta, LinearLayout.LayoutParams(stillW, -2).apply { topMargin = p(7) })
        texts.addView(overviewText, LinearLayout.LayoutParams(stillW, -2).apply { topMargin = p(10) })
        column.addView(texts, LinearLayout.LayoutParams(stillW, -2).apply { topMargin = p(20) })
        addView(column, LayoutParams(stillW, -2))
        addView(tip, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = p(4) })
        tip.translationY = -pf(18)

        play.onFocus = { post { refresh() } }
        download.onFocus = { post { refresh() } }
        delete.onFocus = { post { refresh() } }
        delete.setOnClickListener { onDelete?.invoke() }
        still.addView(delete, LayoutParams(delete.box, delete.box, Gravity.BOTTOM or Gravity.END).apply { rightMargin = p(12); bottomMargin = p(16) })
        buildPlaceholder()
    }

    /** Watched all the way through: the Play disc shows a tick instead (it still plays the episode again). */
    var watched = false
        set(value) { field = value; showPlayIcon() }

    private fun showPlayIcon() {
        if (watched) play.set(R.drawable.ic_cin_check, Cinema.SAFFRON, ring = false, progress = null) else play.set(R.drawable.ic_cin_play, Cinema.CREAM, ring = false, progress = null)
        play.contentDescription = if (watched) "Watched. Play episode $number again" else "Play episode $number"
    }

    /** Every focus stop on the card, left to right. */
    val focusStops: List<View> get() = if (cardStop) listOf(this) else listOfNotNull(
        play.takeIf { it.visibility == VISIBLE && it.isFocusable }, download.takeIf { it.visibility == VISIBLE && it.isFocusable })

    /** Where Down from the season bar lands. */
    val firstStop: View get() = focusStops.firstOrNull() ?: this

    private fun stopsAtCard(look: CardLook) = look.unaired && !look.downloadFocusable && !look.playShown

    /** Whether [view] is still a focus stop once [look] is bound. */
    fun keepsStop(view: View, look: CardLook): Boolean = when (view) {
        this -> stopsAtCard(look)
        play -> !stopsAtCard(look) && look.playShown
        download -> !stopsAtCard(look) && look.downloadShown && look.downloadFocusable
        else -> false
    }

    /** Shows [look]: the badge, the discs, and for an unaired episode the greyed still and [airsLabel]. */
    fun bind(look: CardLook, airsLabel: String?) {
        unaired = look.unaired
        cardStop = stopsAtCard(look)
        isFocusable = cardStop; isClickable = cardStop
        badge.visibility = if (look.badge != null) VISIBLE else GONE
        badgeText.text = look.badge.orEmpty()
        val accent = if (look.badgeAccent) Cinema.SAFFRON else Cinema.MUTED
        badgeText.setTextColor(accent)
        badgeCheck.visibility = if (look.state == CardState.LIBRARY) VISIBLE else GONE
        badge.background = GradientDrawable().apply {
            cornerRadius = pf(16); setColor(0xB80C0806.toInt())
            setStroke(Math.max(1, p(1)), if (look.badgeAccent) Cinema.alpha(Cinema.SAFFRON, 0.5f) else Cinema.alpha(Cinema.CREAM, 0.2f))
        }

        actions.visibility = if (cardStop) GONE else VISIBLE
        play.visibility = if (look.playShown) VISIBLE else GONE
        play.isFocusable = look.playShown
        download.visibility = if (look.downloadShown) VISIBLE else GONE
        deletable = look.state == CardState.LIBRARY
        download.isFocusable = look.downloadFocusable
        download.isClickable = look.downloadFocusable
        when (look.state) {
            CardState.LIBRARY -> download.set(R.drawable.ic_cin_check, Cinema.SAFFRON, ring = false, progress = null, alpha = 0.9f)
            CardState.REQUESTED, CardState.UNAIRED_REQUESTED -> download.set(R.drawable.ic_cin_clock, Cinema.SAFFRON, ring = false, progress = null)
            CardState.DOWNLOADING -> download.set(R.drawable.ic_cin_dl, Cinema.SAFFRON, ring = true, progress = look.progress, iconMock = 26)
            else -> download.set(R.drawable.ic_cin_dl, Cinema.CREAM, ring = false, progress = null)
        }
        showPlayIcon()
        download.contentDescription = when (look.state) {
            CardState.LIBRARY -> "In your library"
            CardState.REQUESTED -> "Requested"
            CardState.UNAIRED_REQUESTED -> "Requested. Downloads when it airs"
            CardState.UNAIRED -> "Download episode $number when it airs"
            CardState.DOWNLOADING -> look.badge?.lowercase() ?: "Downloading"
            else -> "Download episode $number"
        }

        airs.visibility = if (unaired && airsLabel != null) VISIBLE else GONE
        // With a disc to press, the label moves to the still's foot so the two do not overlap; alone, it stays centred.
        (airs.layoutParams as LayoutParams).let { lp ->
            lp.gravity = if (cardStop) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = if (cardStop) 0 else p(16)
            airs.layoutParams = lp
        }
        airs.text = airsLabel?.let { "Airs $it" }.orEmpty()
        val filter = if (unaired) ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0.15f); postConcat(ColorMatrix().apply { setScale(0.55f, 0.55f, 0.55f, 1f) })
        }) else null
        image.colorFilter = filter
        placeholder.alpha = if (unaired) 0.6f else 1f
        refresh()
    }

    /** "52 min · 32 min left", "6 Apr 2025", with faint dots between. */
    fun setMeta(parts: List<String>) {
        meta.removeAllViews(); metaTexts.clear()
        parts.forEachIndexed { i, part ->
            if (i > 0) meta.addView(Cinema.dot(context, Cinema.DIM, 4), LinearLayout.LayoutParams(p(4), p(4)).apply { marginStart = p(10); marginEnd = p(10) })
            val t = Cinema.text(context, part, 18, if (lit) Cinema.MUTED else Cinema.DIM).apply { maxLines = 1 }
            metaTexts += t; meta.addView(t)
        }
    }

    /** The stripe bar along the still's foot for a part-watched episode; null hides it. */
    fun setResume(fraction: Float?) {
        val on = fraction != null && fraction > 0f
        track.visibility = if (on) VISIBLE else GONE
        fill.visibility = if (on) VISIBLE else GONE
        if (on) (fill.layoutParams as LayoutParams).width = (stillW * fraction!!.coerceIn(0f, 1f)).toInt().coerceAtLeast(p(6))
        fill.requestLayout()
    }

    fun setStill(bitmap: Bitmap) { image.setImageBitmap(bitmap); placeholder.visibility = GONE }

    /** No still anywhere: the espresso placeholder with the big episode number and a stripe foot. */
    fun showPlaceholder() { image.setImageDrawable(null); placeholder.visibility = VISIBLE }

    private fun buildPlaceholder() {
        placeholder.background = object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val b = bounds
                paint.shader = android.graphics.LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), 0xFF2A1D16.toInt(), 0xFF140E0B.toInt(), android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(RectF(b), paint)
                paint.shader = android.graphics.RadialGradient(b.left + b.width() * 0.8f, b.top.toFloat(), b.width() * 0.6f, Cinema.alpha(Cinema.TERRA, 0.22f), 0, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(RectF(b), paint)
                paint.shader = null
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
        }
        placeholder.addView(Cinema.text(context, "$number", 120, Cinema.alpha(Cinema.CREAM, 0.1f), serif = true, tracking = -0.04f).apply { maxLines = 1 },
            LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = p(26); bottomMargin = p(4) })
        placeholder.addView(View(context).apply { background = StripeDrawable(horizontalBands = true, radius = 0f); alpha = 0.55f }, LayoutParams(-1, p(12), Gravity.BOTTOM))
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        post { refresh() }
    }

    /** Lights or dims the card to match where focus is. */
    private fun refresh() {
        val now = hasFocus()
        refreshTip()
        updateDelete()
        if (now == lit) return
        lit = now
        val duration = if (now) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
        val scale = if (now) 1.06f else 1f
        val shift = if (now) pf(8) else 0f
        if (Motion.animationsOn(this)) {
            still.animate().scaleX(scale).scaleY(scale).setDuration(duration).setInterpolator(Motion.SETTLE).setUpdateListener { invalidate() }.start()
            texts.animate().translationY(shift).setDuration(duration).setInterpolator(Motion.SETTLE).start()
            overviewText.animate().alpha(if (now) 1f else 0f).setDuration(duration).start()
        } else {
            still.scaleX = scale; still.scaleY = scale; texts.translationY = shift; overviewText.alpha = if (now) 1f else 0f
        }
        still.elevation = pf(if (now) 34 else 14)
        title.setTextColor(if (now) Cinema.CREAM else Cinema.CARD_TITLE)
        Cinema.weight(title, 23, serif = false, weight = if (now) 600 else 500)
        metaTexts.forEach { it.setTextColor(if (now) Cinema.MUTED else Cinema.DIM) }
        invalidate()
        onLit?.invoke(now)
    }

    /** The delete disc shows while this card has focus, and only for an episode that is on disk. */
    private fun updateDelete() {
        val show = deletable && hasFocus()
        delete.visibility = if (show) VISIBLE else GONE
        delete.isFocusable = show; delete.isClickable = show
    }

    private fun refreshTip() { tip.visibility = if (download.isFocused && !tipText.isNullOrEmpty()) VISIBLE else GONE }

    override fun dispatchDraw(canvas: Canvas) {
        if (lit) {
            // The still's drawn edge, as it has grown about its centre.
            val s = still.scaleX
            val left = column.left + still.left; val top = column.top + still.top
            val cx = left + still.width / 2f; val cy = top + still.height / 2f
            val hw = still.width * s / 2f; val hh = still.height * s / 2f
            val r = radius * s
            box.set(cx - hw, cy - hh, cx + hw, cy + hh)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                // The bloom is painted once at the still's resting size (Glows) and scaled with it, not blurred live.
                val g = com.danielportelabyrne.cinema.design.cinema.Glows.roundRect(still.width, still.height, radius, pf(45), glow.color)
                val save = canvas.save()
                canvas.scale(s, s, cx, cy)
                canvas.drawBitmap(g.bitmap, cx - still.width / 2f - g.pad, cy - still.height / 2f - g.pad, null)
                canvas.restoreToCount(save)
            }
            // 4 px cream, then 5 px of saffron at 28% beyond it (`box-shadow: 0 0 0 4px cream, 0 0 0 9px`).
            ringPaint.color = Cinema.alpha(Cinema.SAFFRON, 0.28f); ringPaint.strokeWidth = pf(5)
            val o2 = pf(4) + pf(5) / 2f
            canvas.drawRoundRect(box.left - o2, box.top - o2, box.right + o2, box.bottom + o2, r + o2, r + o2, ringPaint)
            ringPaint.color = Cinema.CREAM; ringPaint.strokeWidth = pf(4)
            val o1 = pf(2)
            canvas.drawRoundRect(box.left - o1, box.top - o1, box.right + o1, box.bottom + o1, r + o1, r + o1, ringPaint)
        }
        super.dispatchDraw(canvas)
    }

    private fun p(v: Number) = Cinema.px(context, v)
    private fun pf(v: Number) = Cinema.pxf(context, v)
}

/**
 * A round action on an episode still (`.ab`): dark glass with a cream icon at rest; focused, it fills cream with an
 * ink icon inside four stripe rings and a drop shadow, like the player's pause disc. It can carry a saffron progress
 * ring (a download). The view is larger than the disc by [pad] on every side, to hold the rings and shadow.
 */
class ActionDisc(context: Context, private val discMock: Int, iconRes: Int, private var iconMock: Int, private val nudge: Float = 0f) : View(context) {
    val pad = Cinema.px(context, PAD)
    val box = Cinema.px(context, discMock) + 2 * pad
    var onFocus: ((Boolean) -> Unit)? = null

    private var icon: Drawable = context.getDrawable(iconRes)!!.mutate()
    private var iconColor = Cinema.CREAM
    private var ring = false
    private var progress: Float? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 18), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val arc = RectF()

    init { isFocusable = true; isClickable = true }

    /** The icon and its resting colour, and an optional progress ring ([progress] null with [ring] shows a quarter). */
    fun set(iconRes: Int, color: Int, ring: Boolean, progress: Float?, alpha: Float = 1f, iconMock: Int = this.iconMock) {
        icon = context.getDrawable(iconRes)!!.mutate()
        iconColor = color; this.ring = ring; this.progress = progress; this.iconMock = iconMock
        this.alpha = alpha
        invalidate()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = Cinema.pxf(context, discMock / 2f)
        val focused = isFocused
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // The disc's drop shadow, painted once per size (Glows) instead of blurred live for every disc every frame.
            val sr = r + Cinema.pxf(context, if (focused) 16 else 2)
            val d = (2 * sr).toInt()
            val g = com.danielportelabyrne.cinema.design.cinema.Glows.roundRect(d, d, sr, Cinema.pxf(context, 18), Cinema.alpha(0xFF000000.toInt(), if (focused) 0.6f else 0.4f))
            canvas.drawBitmap(g.bitmap, cx - d / 2f - g.pad, cy + Cinema.pxf(context, if (focused) 12 else 6) - d / 2f - g.pad, null)
        }
        if (focused) {
            // Outermost first: red, terracotta, orange, saffron, five mockup pixels each, then the cream disc.
            val step = Cinema.pxf(context, 5)
            for (i in 4 downTo 1) { paint.color = Cinema.STRIPES[i - 1]; canvas.drawCircle(cx, cy, r + step * i, paint) }
            paint.color = Cinema.CREAM; canvas.drawCircle(cx, cy, r, paint)
        } else {
            paint.color = 0x940C0806.toInt()   // rgba(12,8,6,.58)
            canvas.drawCircle(cx, cy, r, paint)
            stroke.color = Cinema.alpha(Cinema.CREAM, 0.3f); stroke.strokeWidth = Cinema.pxf(context, 1.5f)
            canvas.drawCircle(cx, cy, r - stroke.strokeWidth / 2f, stroke)
        }
        if (ring) {
            val rr = r - Cinema.pxf(context, 1)
            arc.set(cx - rr, cy - rr, cx + rr, cy + rr)
            stroke.color = Cinema.alpha(if (focused) Cinema.INK else Cinema.CREAM, 0.18f); stroke.strokeWidth = Cinema.pxf(context, 3)
            canvas.drawCircle(cx, cy, rr, stroke)
            stroke.color = Cinema.SAFFRON; stroke.strokeWidth = Cinema.pxf(context, 3.5f)
            canvas.drawArc(arc, -90f, 360f * (progress ?: 0.25f).coerceIn(0.02f, 1f), false, stroke)
        }
        val s = Cinema.pxf(context, iconMock / 2f)
        val dx = Cinema.pxf(context, nudge)
        icon.setTint(if (focused) Cinema.INK else iconColor)
        icon.setBounds((cx - s + dx).toInt(), (cy - s).toInt(), (cx + s + dx).toInt(), (cy + s).toInt())
        icon.draw(canvas)
    }

    private companion object { const val PAD = 30 }
}
