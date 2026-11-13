package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.movies.HistoryDay
import com.danielportelabyrne.cinema.data.movies.HistoryItem
import com.danielportelabyrne.cinema.data.movies.HistoryTimeline
import com.danielportelabyrne.cinema.data.movies.RecentCard
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The History page's timeline: a perforated rail down the left, a saffron marker for each day, and under it each stop, a
 * film or a run of episodes. OK on a run opens it in place to the episodes of that run, hung off the same rail; OK on a
 * film or an episode opens its page, as OK on Home's Recently watched does. Built from [HistoryDay]s only; it decides
 * nothing about grouping.
 *
 * [art] hands the card's picture to [onBitmap] (the panel knows the image headers); [open] opens the title's page;
 * [reveal] scrolls a focused row into view.
 */
class HistoryTimelineView(
    private val context: Context,
    private val art: (RecentCard, (Bitmap) -> Unit) -> Unit,
    private val open: (RecentCard) -> Unit,
    private val reveal: (View) -> Unit
) {
    private fun px(n: Number) = Cinema.px(context, n)

    fun build(days: List<HistoryDay>, nowMs: Long = System.currentTimeMillis()): LinearLayout {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        days.forEachIndexed { d, day ->
            column.addView(dayHeading(day.label, day.items.size))
            day.items.forEachIndexed { i, item ->
                val last = d == days.lastIndex && i == day.items.lastIndex
                column.addView(stop(item, last, nowMs))
            }
        }
        return column
    }

    private fun dayHeading(label: String, count: Int): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        row.addView(Rail(context, Rail.Mark.DAY, px(26), end = false), LinearLayout.LayoutParams(px(RAIL_W), px(64)))
        row.addView(Cinema.text(context, label, 36, Cinema.CREAM, serif = true, weight = 500, tracking = -0.01f).apply { maxLines = 1 })
        row.addView(Cinema.text(context, if (count == 1) "1 stop" else "$count stops", 18, Cinema.DIM, weight = 600, tracking = 0.14f, caps = true),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(20) })
        return row
    }

    private fun stop(item: HistoryItem, last: Boolean, nowMs: Long): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val mark = if (item is HistoryItem.Run) Rail.Mark.RUN else Rail.Mark.FILM
        row.addView(Rail(context, mark, px(STILL_H / 2 + 6), end = last), LinearLayout.LayoutParams(px(RAIL_W), -1))
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(0, px(6), 0, px(14)) }
        row.addView(body, LinearLayout.LayoutParams(-1, -2))
        when (item) {
            is HistoryItem.Film -> body.addView(leadRow(item.card, "Film", item.card.meta, timeOf(item, nowMs), expandable = false, count = 0))
            is HistoryItem.Run -> {
                val lead = leadRow(item.lead, "Series", item.summary, timeOf(item, nowMs), expandable = true, count = item.cards.size)
                body.addView(lead)
                val episodes = episodeList(item).apply { visibility = View.GONE }
                body.addView(episodes)
                val chevron = lead.tag as ImageView
                lead.setOnClickListener {
                    val on = episodes.visibility != View.VISIBLE
                    episodes.visibility = if (on) View.VISIBLE else View.GONE
                    chevron.rotation = if (on) 90f else 0f
                    lead.contentDescription = "${item.title}, ${item.summary}, " + if (on) "collapse" else "show episodes"
                }
                lead.contentDescription = "${item.title}, ${item.summary}, show episodes"
            }
        }
        return row
    }

    /** The newest time of the stop, and from when it began when it ran on within one day: "20:05 – 21:40". */
    private fun timeOf(item: HistoryItem, nowMs: Long): String {
        val end = HistoryTimeline.clock(item.at)
        if (item.cards.size < 2) return end
        val first = item.cards.last().entry.playedAt
        val sameDay = HistoryTimeline.dayLabel(first, nowMs) == HistoryTimeline.dayLabel(item.at, nowMs)
        val start = HistoryTimeline.clock(first)
        return if (sameDay && start.isNotBlank() && start != end) "$start – $end" else end
    }

    /** The big row: picture, kicker, name, what it is, and when. A run's carries how many episodes it holds and a chevron. */
    private fun leadRow(c: RecentCard, kicker: String, meta: String, time: String, expandable: Boolean, count: Int): LinearLayout {
        val row = FocusRow(context, radius = 20)
        row.setPadding(px(12), px(12), px(28), px(12))
        val still = FrameLayout(context).apply { background = placeholder(14) ; clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        still.addView(image, FrameLayout.LayoutParams(-1, -1))
        if (expandable && count > 1) still.addView(Cinema.text(context, "$count", 20, Cinema.INK, weight = 700).apply {
            gravity = Gravity.CENTER; setPadding(px(12), px(2), px(12), px(2))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 14); setColor(Cinema.SAFFRON) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, px(10), px(10)) })
        row.addView(still, LinearLayout.LayoutParams(px(STILL_W), px(STILL_H)))
        art(c) { b -> image.setImageBitmap(b) }

        val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        words.addView(Cinema.text(context, kicker, 16, if (expandable) Cinema.SAFFRON else Cinema.MUTED, weight = 700, tracking = 0.2f, caps = true))
        words.addView(Cinema.text(context, c.title, 34, Cinema.CREAM, serif = true, weight = 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
        if (meta.isNotBlank()) words.addView(Cinema.text(context, meta, 22, Cinema.alpha(Cinema.CREAM, 0.74f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
        if (time.isNotBlank()) words.addView(Cinema.text(context, time, 20, Cinema.DIM, weight = 600, tracking = 0.04f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        row.addView(words, LinearLayout.LayoutParams(px(WORDS_W), -2).apply { marginStart = px(28) })
        if (expandable) {
            val chevron = Cinema.icon(context, R.drawable.ic_cin_chev, Cinema.SAFFRON)
            row.addView(chevron, LinearLayout.LayoutParams(px(30), px(30)).apply { marginStart = px(16) })
            row.tag = chevron
        } else row.setOnClickListener { open(c) }
        row.onFocus = { reveal(row) }
        return row
    }

    /** The episodes of one run and only those, newest first, hung off a branch line of their own. */
    private fun episodeList(run: HistoryItem.Run): LinearLayout {
        val box = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; setPadding(px(40), px(4), 0, 0) }
        box.addView(View(context).apply { background = GradientDrawable().apply { setColor(Cinema.alpha(Cinema.SAFFRON, 0.4f)); cornerRadius = Cinema.pxf(context, 2) } },
            LinearLayout.LayoutParams(px(3), -1))
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; setPadding(px(18), 0, 0, 0) }
        box.addView(list, LinearLayout.LayoutParams(-1, -2))
        run.cards.forEach { c -> list.addView(episodeRow(c)) }
        return box
    }

    private fun episodeRow(c: RecentCard): View {
        val row = FocusRow(context, radius = 16)
        row.setPadding(px(10), px(8), px(24), px(8))
        val still = FrameLayout(context).apply { background = placeholder(10); clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        still.addView(image, FrameLayout.LayoutParams(-1, -1))
        row.addView(still, LinearLayout.LayoutParams(px(170), px(96)))
        art(c) { b -> image.setImageBitmap(b) }
        val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val code = c.entry.episode?.let { "Episode $it" } ?: c.code.orEmpty()
        words.addView(Cinema.text(context, code, 16, Cinema.SAFFRON, weight = 700, tracking = 0.18f, caps = true))
        words.addView(Cinema.text(context, c.episodeTitle ?: c.title, 26, Cinema.CREAM, serif = true, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
        val sub = listOf(if (c.watched) "Watched" else c.minutesLeft?.let { if (it >= 60) "${it / 60}h ${it % 60}m left" else "$it min left" },
            HistoryTimeline.clock(c.entry.playedAt)).filter { !it.isNullOrBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) words.addView(Cinema.text(context, sub, 19, Cinema.alpha(Cinema.CREAM, 0.7f)).apply { maxLines = 1 },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
        row.addView(words, LinearLayout.LayoutParams(px(560), -2).apply { marginStart = px(22) })
        row.contentDescription = listOf(c.title, c.code, c.episodeTitle, sub).filter { !it.isNullOrBlank() }.joinToString(", ")
        row.setOnClickListener { open(c) }
        row.onFocus = { reveal(row) }
        return row
    }

    private fun placeholder(radius: Int) = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, radius); setColor(Cinema.PLACEHOLDER) }

    /** A focusable row that lights as the app's other rows do: a warm wash and a saffron edge. */
    private class FocusRow(context: Context, private val radius: Int) : LinearLayout(context) {
        var onFocus: (() -> Unit)? = null
        private val rest = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, radius); setColor(Cinema.alpha(Cinema.CREAM, 0.03f)) }
        private val lit = GradientDrawable().apply {
            cornerRadius = Cinema.pxf(context, radius); setColor(Cinema.alpha(Cinema.CREAM, 0.11f)); setStroke(Cinema.px(context, 3), Cinema.SAFFRON)
        }
        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; clipChildren = false
            background = rest
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            background = if (gainFocus) lit else rest
            if (gainFocus) onFocus?.invoke()
        }
    }

    /** The rail: a perforated line the full height of its row, with a marker at [dotY]: a day, a run of episodes, or a film. */
    private class Rail(context: Context, private val mark: Mark, private val dotY: Int, private val end: Boolean) : View(context) {
        enum class Mark { DAY, RUN, FILM }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun px(n: Number) = Cinema.pxf(context, n)
        init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        override fun onDraw(canvas: Canvas) {
            val x = width / 2f
            // Film-stock perforations: a dashed line, a little broader than a line would be.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = px(6); paint.color = Cinema.alpha(Cinema.CREAM, 0.13f)
            paint.pathEffect = DashPathEffect(floatArrayOf(px(9), px(9)), 0f)
            canvas.drawLine(x, 0f, x, if (end) dotY.toFloat() else height.toFloat(), paint)
            paint.pathEffect = null
            val y = dotY.toFloat()
            when (mark) {
                Mark.DAY -> {
                    paint.style = Paint.Style.FILL; paint.color = Cinema.alpha(Cinema.SAFFRON, 0.22f); canvas.drawCircle(x, y, px(20), paint)
                    paint.color = Cinema.SAFFRON; canvas.drawCircle(x, y, px(11), paint)
                }
                Mark.RUN -> {
                    paint.style = Paint.Style.FILL; paint.color = Cinema.BG; canvas.drawCircle(x, y, px(11), paint)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = px(4); paint.color = Cinema.SAFFRON; canvas.drawCircle(x, y, px(9), paint)
                }
                Mark.FILM -> {
                    paint.style = Paint.Style.FILL; paint.color = Cinema.CREAM; canvas.drawCircle(x, y, px(7), paint)
                }
            }
        }
    }

    companion object {
        const val RAIL_W = 64
        const val STILL_W = 288
        const val STILL_H = 162
        const val WORDS_W = 760
    }
}
