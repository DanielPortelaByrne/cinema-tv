package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.data.movies.ChannelGuide
import com.danielportelabyrne.cinema.data.movies.ChannelLook
import com.danielportelabyrne.cinema.data.movies.ChannelSummary
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.PlaylistSlot
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A playlist drawn as a TV channel: the painted tile on Home, the numbered row and the preview on the Channels page.
 * Every channel keeps one colour everywhere ([ChannelGuide.look]); the stripe band is Cinema's own, used like a test card.
 * Sizes are in mockup pixels, like the rest of Cinema.
 */
object ChannelViews {
    const val TILE_W = 400
    const val TILE_H = 226

    /** A channel's colour with a pale sun, stripes or arc cut into it. */
    class Art(private val look: ChannelLook, private val radius: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val clip = android.graphics.Path().apply { addRoundRect(RectF(b), radius, radius, android.graphics.Path.Direction.CW) }
            canvas.save(); canvas.clipPath(clip)
            paint.color = look.colour; canvas.drawRect(RectF(b), paint)
            // Saffron is the light colour: its shapes are pale cream, so they need to be stronger to show.
            paint.color = android.graphics.Color.argb(if (look.darkText) 100 else 46, 246, 236, 219)
            when (look.motif) {
                0 -> canvas.drawCircle(b.left + w * 0.84f, b.top + h * 0.2f, h * 0.5f, paint)
                1 -> { canvas.drawRect(b.left + w * 0.62f, b.top.toFloat(), b.left + w * 0.72f, b.bottom.toFloat(), paint); canvas.drawRect(b.left + w * 0.78f, b.top.toFloat(), b.left + w * 0.84f, b.bottom.toFloat(), paint) }
                else -> canvas.drawCircle(b.left + w * 0.08f, b.top + h * 1.0f, h * 0.6f, paint)
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun onColour(look: ChannelLook) = if (look.darkText) Cinema.INK else Cinema.CREAM

    /** "CH 1": a small tag, in the channel's own text colour. */
    fun chip(context: Context, number: Int, look: ChannelLook, sizeMock: Int = 20): TextView =
        Cinema.text(context, "CH $number", sizeMock, onColour(look), weight = 600, tracking = 0.03f).apply {
            setPadding(Cinema.px(context, 12), Cinema.px(context, 3), Cinema.px(context, 12), Cinema.px(context, 3))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 7); setColor(Cinema.alpha(onColour(look), if (look.darkText) 0.14f else 0.18f)) }
        }

    /** The five stripes side by side: the test-card band. */
    fun band(context: Context, heightMock: Int): View =
        View(context).apply { background = StripeDrawable(horizontalBands = false, radius = 0f) }

    /**
     * The ident shown once on tuning in: the channel's colour from edge to edge, its number as a ghost, "Now tuning", its name
     * and the first programme, with the stripe band along the foot. The loading screen sits under it until the picture is ready.
     */
    fun ident(context: Context, number: Int, station: String, programme: String): View {
        val look = ChannelGuide.look(number)
        val ink = onColour(look)
        val root = FrameLayout(context).apply { background = GradientDrawable().apply { setColor(look.colour) }; isClickable = true }
        root.addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.alpha(Cinema.CREAM, 0.16f)) } },
            FrameLayout.LayoutParams(Cinema.px(context, 760), Cinema.px(context, 760), Gravity.TOP or Gravity.END).apply { topMargin = Cinema.px(context, -140); rightMargin = Cinema.px(context, -20) })
        root.addView(Cinema.text(context, number.toString(), 520, Cinema.alpha(ink, 0.2f), serif = true, weight = 500, tracking = -0.05f),
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { leftMargin = Cinema.px(context, 150); topMargin = Cinema.px(context, -90) })
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Cinema.text(context, "NOW TUNING", 26, if (look.darkText) Cinema.INK else Cinema.INK, weight = 600, tracking = 0.1f))
        column.addView(Cinema.title(context, station, if (station.length > 16) 96 else 130, maxLines = 2), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 14) })
        column.addView(Cinema.text(context, "Starting with $programme", 32, Cinema.alpha(ink, 0.9f)).apply { maxLines = 2 }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 22) })
        root.addView(column, FrameLayout.LayoutParams(Cinema.px(context, 1400), -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = Cinema.px(context, 184); topMargin = Cinema.px(context, 120) })
        root.addView(band(context, 34), FrameLayout.LayoutParams(-1, Cinema.px(context, 34), Gravity.BOTTOM))
        return root
    }

    /** The saffron "ON AIR" tag with a terracotta dot: the channel tuned to last, still going. */
    fun onAirBadge(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(Cinema.px(context, 14), Cinema.px(context, 5), Cinema.px(context, 16), Cinema.px(context, 5))
        background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 200); setColor(Cinema.SAFFRON) }
        addView(Cinema.dot(context, Cinema.TERRA, 10), LinearLayout.LayoutParams(Cinema.px(context, 10), Cinema.px(context, 10)).apply { marginEnd = Cinema.px(context, 8) })
        addView(Cinema.text(context, "ON AIR", 17, Cinema.INK, weight = 700, tracking = 0.08f))
    }

    /** Cinema's pill: terracotta for the one call to action, glass otherwise, cream when focused. */
    fun pill(context: Context, label: String, accent: Boolean): TextView {
        val r = Cinema.pxf(context, 200)
        val rest = if (accent) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFEB8A56.toInt(), 0xFFD4623A.toInt())).apply { cornerRadius = r }
                   else GradientDrawable().apply { cornerRadius = r; setColor(Cinema.GLASS_STRONG) }
        val lit = GradientDrawable().apply { cornerRadius = r; setColor(Cinema.CREAM) }
        val restText = if (accent) 0xFF1D0D05.toInt() else Cinema.CREAM
        return Cinema.text(context, label, 26, restText, weight = 600).apply {
            gravity = Gravity.CENTER; isFocusable = true; isClickable = true; maxLines = 1
            setPadding(Cinema.px(context, 34), 0, Cinema.px(context, 34), 0)
            background = TransitionDrawable(arrayOf(rest, lit)).apply { isCrossFadeEnabled = true }
            setOnFocusChangeListener { v, focused ->
                val d = v.background as TransitionDrawable
                if (focused) d.startTransition(120) else d.reverseTransition(120)
                (v as TextView).setTextColor(if (focused) Cinema.INK else restText)
            }
        }
    }

    /** A channel's tile on Home: its colour, a big ghost number, what is up next, and "Tune in" once focused. */
    fun tile(context: Context, channel: ChannelSummary, line: String, onAir: Boolean): PosterCard {
        val look = channel.look
        val ink = onColour(look)
        val card = PosterCard(context, TILE_W, TILE_H, titled = false, radiusPx = 24f, focusScale = 1.07f)
        card.contentDescription = "Channel ${channel.number}, ${channel.name}"
        card.poster.background = Art(look, Cinema.pxf(context, 24))
        card.poster.foreground = null
        card.poster.addView(Cinema.text(context, channel.number.toString(), 260, Cinema.alpha(ink, 0.2f), serif = true, weight = 500, tracking = -0.04f),
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Cinema.px(context, 14); bottomMargin = Cinema.px(context, -62) })
        card.poster.addView(chip(context, channel.number, look), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(Cinema.px(context, 26), Cinema.px(context, 22), 0, 0) })
        val badge = onAirBadge(context).also { it.visibility = if (onAir) View.VISIBLE else View.GONE }
        card.poster.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, Cinema.px(context, 22), Cinema.px(context, 22), 0) })
        val tune = Cinema.text(context, "Tune in", 21, Cinema.INK, weight = 600).apply {
            visibility = View.GONE
            setPadding(Cinema.px(context, 20), Cinema.px(context, 7), Cinema.px(context, 20), Cinema.px(context, 7))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 200); setColor(Cinema.CREAM) }
        }
        card.poster.addView(tune, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, Cinema.px(context, 20), Cinema.px(context, 22), 0) })
        card.poster.addView(Cinema.text(context, channel.name, 40, ink, serif = true, weight = 500, tracking = -0.01f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Cinema.px(context, 28), 0, Cinema.px(context, 20), Cinema.px(context, 58)) })
        card.poster.addView(Cinema.text(context, line, 21, Cinema.alpha(ink, 0.85f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Cinema.px(context, 28), 0, Cinema.px(context, 20), Cinema.px(context, 26)) })
        val stripe = band(context, 10).also { it.visibility = View.GONE }
        card.poster.addView(stripe, FrameLayout.LayoutParams(-1, Cinema.px(context, 10), Gravity.BOTTOM))
        card.setOnFocusChangeListener { _, focused ->
            tune.visibility = if (focused) View.VISIBLE else View.GONE
            badge.visibility = if (onAir && !focused) View.VISIBLE else View.GONE
            stripe.visibility = if (focused) View.VISIBLE else View.GONE
        }
        return card
    }

    /** The last card of the row: opens the Channels page, where playlists are made and edited. */
    fun moreCard(context: Context, hasChannels: Boolean): PosterCard {
        val card = PosterCard(context, if (hasChannels) 360 else TILE_W, TILE_H, titled = false, radiusPx = 24f, focusScale = 1.07f)
        card.contentDescription = if (hasChannels) "More playlists" else "Make your first channel"
        card.poster.background = Cinema.glass(context, 24f, Cinema.GLASS)
        card.poster.foreground = null
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val plus = FrameLayout(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.GLASS_STRONG) } }
        plus.addView(Cinema.text(context, "+", 52, Cinema.SAFFRON, weight = 300).apply { gravity = Gravity.CENTER; includeFontPadding = false }, FrameLayout.LayoutParams(-1, -1))
        column.addView(plus, LinearLayout.LayoutParams(Cinema.px(context, 76), Cinema.px(context, 76)))
        val title = Cinema.text(context, if (hasChannels) "More playlists" else "Make a channel", 34, Cinema.CREAM, serif = true, tracking = -0.01f).apply { gravity = Gravity.CENTER }
        val sub = Cinema.text(context, if (hasChannels) "Create or edit your channels" else "Shows and films, one after another", 20, Cinema.MUTED).apply { gravity = Gravity.CENTER }
        column.addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 12) })
        column.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 4) })
        card.poster.addView(column, FrameLayout.LayoutParams(-1, -1))
        card.setOnFocusChangeListener { _, focused ->
            card.poster.background = Cinema.glass(context, 24f, if (focused) Cinema.CREAM else Cinema.GLASS)
            title.setTextColor(if (focused) Cinema.INK else Cinema.CREAM)
            sub.setTextColor(if (focused) Cinema.INK else Cinema.MUTED)
        }
        return card
    }

    /** A rounded picture slot that fills with a poster when it arrives. */
    private fun picture(context: Context, wMock: Int, hMock: Int, radiusMock: Float, placeholder: Int): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(placeholder)
        val r = Cinema.pxf(context, radiusMock)
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) } }
        clipToOutline = true
    }

    /** A channel on the Channels page: its number block, name and what is on it, a stack of its posters; cream when focused. */
    class Row(context: Context, val channel: ChannelSummary, detail: String, onAir: Boolean) : LinearLayout(context) {
        private val rest = Cinema.glass(context, 26f, Cinema.GLASS)
        private val lit = Cinema.glass(context, 26f, Cinema.CREAM)
        private val name: TextView
        private val line: TextView
        private val air: TextView?
        private val thumbs = ArrayList<ImageView>()
        var onFocus: ((Boolean) -> Unit)? = null

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding(Cinema.px(context, 34), 0, Cinema.px(context, 34), 0)
            background = rest
            contentDescription = "Channel ${channel.number}, ${channel.name}"
            val look = channel.look
            val number = FrameLayout(context).apply { background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 22); setColor(look.colour) } }
            number.addView(Cinema.text(context, channel.number.toString(), 42, onColour(look), serif = true, weight = 500).apply { gravity = Gravity.CENTER; includeFontPadding = false }, FrameLayout.LayoutParams(-1, -1))
            addView(number, LayoutParams(Cinema.px(context, 80), Cinema.px(context, 80)).apply { marginEnd = Cinema.px(context, 26) })
            val text = LinearLayout(context).apply { orientation = VERTICAL }
            name = Cinema.text(context, channel.name, 28, Cinema.CREAM, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            line = Cinema.text(context, detail, 20, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            text.addView(name); text.addView(line, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 5) })
            addView(text, LayoutParams(0, -2, 1f))
            air = if (onAir) Cinema.text(context, "ON AIR", 17, Cinema.SAFFRON, weight = 700, tracking = 0.08f).apply {
                setPadding(0, 0, Cinema.px(context, 22), 0)
            }.also { addView(it) } else null
            channel.playlist.slots.take(4).forEachIndexed { i, _ ->
                val pic = picture(context, 46, 66, 9f, 0xFF2A1F19.toInt())
                thumbs += pic
                addView(pic, LayoutParams(Cinema.px(context, 46), Cinema.px(context, 66)).apply { if (i > 0) marginStart = Cinema.px(context, -12) })
            }
        }

        /** The poster for the [index]th programme of the stack. */
        fun setPoster(index: Int, bitmap: Bitmap) { thumbs.getOrNull(index)?.setImageBitmap(bitmap) }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            background = if (gainFocus) lit else rest
            name.setTextColor(if (gainFocus) Cinema.INK else Cinema.CREAM)
            line.setTextColor(if (gainFocus) 0xFF5A4A3C.toInt() else Cinema.MUTED)
            air?.setTextColor(if (gainFocus) Cinema.TERRA else Cinema.SAFFRON)
            onFocus?.invoke(gainFocus)
        }
    }

    /** "New channel" at the foot of the list. */
    fun newChannelRow(context: Context, onSelect: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding(Cinema.px(context, 34), 0, Cinema.px(context, 34), 0)
        }
        val rest = Cinema.glass(context, 26f, Cinema.GLASS); val lit = Cinema.glass(context, 26f, Cinema.CREAM)
        row.background = rest
        val plus = FrameLayout(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.GLASS_STRONG) } }
        plus.addView(Cinema.text(context, "+", 40, Cinema.SAFFRON, weight = 300).apply { gravity = Gravity.CENTER; includeFontPadding = false }, FrameLayout.LayoutParams(-1, -1))
        row.addView(plus, LinearLayout.LayoutParams(Cinema.px(context, 60), Cinema.px(context, 60)).apply { marginEnd = Cinema.px(context, 26) })
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = Cinema.text(context, "New channel", 28, Cinema.CREAM, weight = 500)
        val sub = Cinema.text(context, "Name it, or recreate a classic lineup", 20, Cinema.MUTED)
        text.addView(title); text.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 4) })
        row.addView(text)
        row.setOnFocusChangeListener { _, f ->
            row.background = if (f) lit else rest
            title.setTextColor(if (f) Cinema.INK else Cinema.CREAM); sub.setTextColor(if (f) 0xFF5A4A3C.toInt() else Cinema.MUTED)
        }
        row.setOnClickListener { onSelect() }
        return row
    }

    /**
     * The right-hand side of the Channels page: the focused channel's painted card, what is on next (in the order it will
     * play), and the two buttons. [loadPoster] fetches a picture by address.
     */
    class Preview(context: Context, private val loadPoster: (url: String, maxWidth: Int, done: (Bitmap) -> Unit) -> Unit) : LinearLayout(context) {
        private val info = LinearLayout(context).apply { orientation = VERTICAL; clipChildren = false }
        val tune: TextView = pill(context, "Tune in", accent = true)
        val edit: TextView = pill(context, "Edit lineup", accent = false)

        init {
            orientation = VERTICAL; clipChildren = false; clipToPadding = false
            addView(info, LayoutParams(-1, -2))
            val buttons = LinearLayout(context).apply { orientation = HORIZONTAL }
            buttons.addView(tune, LayoutParams(Cinema.px(context, 300), Cinema.px(context, 86)))
            buttons.addView(edit, LayoutParams(Cinema.px(context, 240), Cinema.px(context, 86)).apply { marginStart = Cinema.px(context, 24) })
            addView(buttons, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 36) })
        }

        /** Shows [channel]. [upcoming] is the order the next programmes will play in. */
        fun show(channel: ChannelSummary, upcoming: List<PlaylistSlot>, onAir: Boolean, classic: Boolean = false) {
            val context = context
            val look = channel.look
            val ink = onColour(look)
            info.removeAllViews()
            val art = FrameLayout(context).apply {
                background = Art(look, Cinema.pxf(context, 30))
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 30)) } }
                clipToOutline = true
            }
            art.addView(Cinema.text(context, channel.number.toString(), 400, Cinema.alpha(ink, 0.16f), serif = true, weight = 500, tracking = -0.04f),
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Cinema.px(context, 30); bottomMargin = Cinema.px(context, -110) })
            art.addView(chip(context, channel.number, look, 22), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(Cinema.px(context, 36), Cinema.px(context, 32), 0, 0) })
            if (onAir) art.addView(onAirBadge(context), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, Cinema.px(context, 32), Cinema.px(context, 32), 0) })
            art.addView(Cinema.text(context, channel.name, 68, ink, serif = true, tracking = -0.02f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Cinema.px(context, 36), 0, Cinema.px(context, 36), Cinema.px(context, 80)) })
            art.addView(Cinema.text(context, channel.resume?.let { "Resumes $it" } ?: channel.detail, 22, Cinema.alpha(ink, 0.88f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Cinema.px(context, 38), 0, Cinema.px(context, 36), Cinema.px(context, 40)) })
            art.addView(band(context, 14), FrameLayout.LayoutParams(-1, Cinema.px(context, 14), Gravity.BOTTOM))
            info.addView(art, LayoutParams(Cinema.px(context, WIDTH), Cinema.px(context, 420)))

            tune.visibility = if (channel.playlist.slots.isEmpty()) GONE else VISIBLE
            if (upcoming.isEmpty()) {
                val note = if (channel.playlist.slots.isEmpty()) "Nothing here yet. Edit the lineup to add films and series."
                           else "Every programme here has been watched. Reset progress in the lineup to start again."
                info.addView(Cinema.text(context, note, 22, Cinema.MUTED).apply { maxLines = 2 }, LayoutParams(Cinema.px(context, WIDTH), -2).apply { topMargin = Cinema.px(context, 30) })
                return
            }
            // Only said when what ran together on classic television really shapes this channel's order.
            info.addView(Cinema.text(context, if (classic) "UP NEXT  ·  CLASSIC TV ORDER" else "UP NEXT", 19, Cinema.SAFFRON, weight = 600, tracking = 0.1f, caps = true), LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 28) })
            val strip = LinearLayout(context).apply { orientation = HORIZONTAL; clipChildren = false }
            upcoming.take(3).forEachIndexed { i, slot ->
                val cell = LinearLayout(context).apply { orientation = VERTICAL }
                val pic = picture(context, 160, 92, 16f, 0xFF2A1F19.toInt())
                (slot.title.backdropUrl ?: slot.title.posterUrl)?.let { url -> loadPoster(url, Cinema.px(context, 160)) { b -> pic.setImageBitmap(b) } }
                cell.addView(pic, LayoutParams(Cinema.px(context, 160), Cinema.px(context, 92)))
                cell.addView(Cinema.text(context, slot.title.name, 20, Cinema.CARD_TITLE, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                    LayoutParams(Cinema.px(context, 160), -2).apply { topMargin = Cinema.px(context, 10) })
                val sub = slot.pending?.let { ChannelGuide.episode(it) } ?: if (slot.title.kind == Kind.MOVIE) "Film" else "Series"
                cell.addView(Cinema.text(context, sub, 17, Cinema.DIM).apply { maxLines = 1 }, LayoutParams(-2, -2))
                strip.addView(cell, LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(context, 14) })
            }
            val more = upcoming.size - 3
            if (more > 0) {
                val tile = FrameLayout(context).apply { background = Cinema.glass(context, 16f, Cinema.GLASS) }
                tile.addView(Cinema.text(context, "+ $more more", 22, Cinema.MUTED, weight = 500).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                strip.addView(tile, LayoutParams(Cinema.px(context, 150), Cinema.px(context, 92)).apply { marginStart = Cinema.px(context, 14) })
            }
            info.addView(strip, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 14) })
        }

        companion object { const val WIDTH = 740 }
    }
}
