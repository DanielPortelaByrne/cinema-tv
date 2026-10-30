package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/** Letterboxd's three dots, for headings and the rail (orange, green, blue). */
object LbDots {
    val COLORS = intArrayOf(0xFFFF8000.toInt(), 0xFF00E054.toInt(), 0xFF40BCF4.toInt())

    fun view(context: Context, sizePx: Int = 12): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        COLORS.forEachIndexed { i, c ->
            addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) } },
                LinearLayout.LayoutParams(Cinema.px(context, sizePx), Cinema.px(context, sizePx)).apply { if (i > 0) marginStart = Cinema.px(context, sizePx * 5 / 12) })
        }
    }
}

/** A round picture, or the person's initial on a warm gradient while there is none. */
class AvatarView(context: Context, private val sizeMock: Int) : FrameLayout(context) {
    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val initial = Cinema.text(context, "", sizeMock * 0.44f, Cinema.CREAM, serif = true).apply { gravity = Gravity.CENTER }
    /** Rings of the stripe set around the picture (focused member cards). */
    var ringed = false
        set(v) { field = v; invalidate() }
    /** Whether the picture draws its rings. Off for people's pictures on a film's cast and crew, which only want a clean circle. */
    var drawRing = true
        set(v) { field = v; invalidate() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setWillNotDraw(false); clipChildren = false; clipToPadding = false
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Cinema.STRIPE_RED, Cinema.SAFFRON)).apply { shape = GradientDrawable.OVAL }
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) } }
        addView(initial, LayoutParams(-1, -1)); addView(image, LayoutParams(-1, -1))
        image.clipToOutline = true
        image.outlineProvider = outlineProvider
    }

    fun set(name: String, picture: Bitmap?) {
        initial.text = name.trim().firstOrNull()?.uppercase() ?: "?"
        image.setImageBitmap(picture); image.visibility = if (picture == null) GONE else VISIBLE
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!drawRing) return
        val c = width / 2f
        val gap = Cinema.pxf(context, 4)
        ringPaint.strokeWidth = gap
        ringPaint.color = 0xD90E0A08.toInt(); canvas.drawCircle(c, c, c + gap / 2, ringPaint)
        if (ringed) {
            ringPaint.color = Cinema.STRIPE_SAFFRON; canvas.drawCircle(c, c, c + gap * 1.5f, ringPaint)
            ringPaint.color = Cinema.STRIPE_TERRA; canvas.drawCircle(c, c, c + gap * 2.5f, ringPaint)
        } else { ringPaint.color = Cinema.alpha(Cinema.CREAM, 0.18f); canvas.drawCircle(c, c, c + gap * 1.5f, ringPaint) }
    }
}

/**
 * A followed member's card (`.mc`): a blurred wash of their favourite films, their four favourites fanned on the
 * right, the picture and name, what they watched last and their numbers as tags. Focused, it grows a little, gains a
 * cream edge with a warm bloom, a striped foot and rings round the picture. 600 x 300 mockup pixels.
 */
class MemberCard(context: Context) : FrameLayout(context) {
    private val radius = Cinema.pxf(context, 34)
    private val face = FrameLayout(context).apply {
        background = GradientDrawable().apply { cornerRadius = radius; setColor(0xFF17110D.toInt()) }
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
        clipToOutline = true
    }
    private val wash = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.55f; scaleX = 1.3f; scaleY = 1.3f
        if (Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(Cinema.pxf(context, 46), Cinema.pxf(context, 46), Shader.TileMode.CLAMP))
    }
    val favs = List(4) { ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 10); setColor(Cinema.PLACEHOLDER) }
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 10)) } }
        clipToOutline = true; elevation = Cinema.pxf(context, 10)
    } }
    val avatar = AvatarView(context, 96)
    private val name = Cinema.text(context, "", 50, Cinema.CREAM, serif = true, tracking = -0.015f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val handle = Cinema.text(context, "", 18, Cinema.alpha(Cinema.CREAM, 0.55f))
    private val lastLabel = Cinema.text(context, "", 15, Cinema.alpha(Cinema.CREAM, 0.5f), weight = 600, tracking = 0.2f, caps = true)
    private val lastFilm = Cinema.text(context, "", 23, Cinema.CREAM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val tags = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val stripe = View(context).apply { background = StripeDrawable(horizontalBands = false, radius = 0f); alpha = 0f }
    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Cinema.CREAM; strokeWidth = Cinema.pxf(context, 4) }
    private val box = RectF()

    init {
        isFocusable = true; isClickable = true; clipChildren = false; clipToPadding = false; setWillNotDraw(false)
        addView(face, LayoutParams(-1, -1))
        face.addView(wash, LayoutParams(-1, -1))
        face.addView(View(context).apply { background = LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xEB0E0A08.toInt(), 0xC70E0A08.toInt(), 0x400E0A08)),
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0, 0xB30E0A08.toInt())))) }, LayoutParams(-1, -1))
        val fan = FrameLayout(context).apply { clipChildren = false }
        val tops = intArrayOf(22, 8, 0, 12); val turns = floatArrayOf(-9f, -3f, 3f, 9f)
        favs.forEachIndexed { i, v ->
            v.rotation = turns[i]; v.pivotY = Cinema.pxf(context, 147 * 1.2f)
            fan.addView(v, LayoutParams(Cinema.px(context, 98), Cinema.px(context, 147)).apply { leftMargin = Cinema.px(context, i * 30); topMargin = Cinema.px(context, tops[i]) })
        }
        face.addView(fan, LayoutParams(Cinema.px(context, 190), Cinema.px(context, 200), Gravity.END or Gravity.TOP).apply { rightMargin = Cinema.px(context, 26); topMargin = Cinema.px(context, 40) })
        // The avatar paints its rings beyond its bounds. Padding must not crop those rings.
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(context, 34), Cinema.px(context, 30), Cinema.px(context, 34), 0); clipChildren = false; clipToPadding = false }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        top.addView(avatar, LinearLayout.LayoutParams(Cinema.px(context, 96), Cinema.px(context, 96)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(name); names.addView(handle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 6) })
        top.addView(names, LinearLayout.LayoutParams(Cinema.px(context, 260), -2).apply { marginStart = Cinema.px(context, 22) })
        column.addView(top)
        column.addView(lastLabel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 24) })
        column.addView(lastFilm, LinearLayout.LayoutParams(Cinema.px(context, 330), -2).apply { topMargin = Cinema.px(context, 6) })
        face.addView(column, LayoutParams(-1, -1))
        face.addView(tags, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(context, 34); bottomMargin = Cinema.px(context, 26) })
        face.addView(stripe, LayoutParams(-1, Cinema.px(context, 8), Gravity.BOTTOM))
    }

    fun bind(displayName: String, user: String, last: String?, lastStars: String?, lastDate: String?, stats: List<Pair<String, String>>) {
        name.text = displayName; handle.text = "@$user"
        lastLabel.text = listOfNotNull("Last watched", lastDate).joinToString(" · ")
        lastLabel.visibility = if (last == null) INVISIBLE else VISIBLE
        lastFilm.text = android.text.SpannableStringBuilder(last ?: "").apply {
            lastStars?.let { s -> val at = length; append("  $s"); setSpan(android.text.style.ForegroundColorSpan(Cinema.SAFFRON), at, length, 0) }
        }
        tags.removeAllViews()
        stats.take(3).forEachIndexed { i, (value, label) ->
            val tag = Cinema.text(context, android.text.SpannableStringBuilder("$value ").apply {
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0); append(label.lowercase())
            }, 17, 0xFFE6D9C6.toInt()).apply {
                setPadding(Cinema.px(context, 14), Cinema.px(context, 7), Cinema.px(context, 14), Cinema.px(context, 7))
                background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 16); setColor(0x1CF6ECDB) }
            }
            tags.addView(tag, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(context, 10) })
        }
        contentDescription = displayName
    }

    /** The favourite whose colours tint the card. */
    fun washWith(bitmap: Bitmap) { wash.setImageBitmap(bitmap) }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val s = if (gainFocus) 1.04f else 1f
        if (Motion.animationsOn(this)) animate().scaleX(s).scaleY(s).setDuration(if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS).setInterpolator(Motion.SETTLE).start()
        else { scaleX = s; scaleY = s }
        stripe.alpha = if (gainFocus) 1f else 0f
        avatar.ringed = gainFocus
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) {
            val g = Glows.roundRect(width, height, radius, Cinema.pxf(context, 44), Cinema.alpha(Cinema.SAFFRON, 0.32f))
            canvas.drawBitmap(g.bitmap, -g.pad.toFloat(), -g.pad.toFloat(), bloom)
        }
        super.dispatchDraw(canvas)
        if (isFocused) {
            val h = edge.strokeWidth / 2
            box.set(-h, -h, width + h, height + h)
            canvas.drawRoundRect(box, radius + h, radius + h, edge)
        }
    }
}

/** "Follow someone": a dashed glass card with a saffron plus. Cream when focused. */
class FollowCard(context: Context) : LinearLayout(context) {
    private val radius = Cinema.pxf(context, 34)
    private val rest = GradientDrawable().apply { cornerRadius = radius; setStroke(Cinema.px(context, 2), 0x24F6ECDB, Cinema.pxf(context, 10), Cinema.pxf(context, 8)) }
    private val lit = GradientDrawable().apply { cornerRadius = radius; setColor(Cinema.CREAM) }
    private val label = Cinema.text(context, "Follow someone", 32, Cinema.CREAM, serif = true)
    private val sub = Cinema.text(context, "Any Letterboxd member", 18, Cinema.DIM)
    // A vector plus centred in the round button: a text "+" sits low in its line, so it would not be centred.
    private val plus = FrameLayout(context).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x17FFF2DE); setStroke(Cinema.px(context, 2), 0x59EDBE56) }
        addView(ImageView(context).apply { setImageResource(com.danielportelabyrne.cinema.R.drawable.ic_cin_plus); setColorFilter(Cinema.SAFFRON); scaleType = ImageView.ScaleType.FIT_CENTER },
            FrameLayout.LayoutParams(Cinema.px(context, 44), Cinema.px(context, 44), Gravity.CENTER))
    }

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true; background = rest
        addView(plus, LayoutParams(Cinema.px(context, 92), Cinema.px(context, 92)))
        addView(label, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 20) })
        addView(sub, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 4) })
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        background = if (gainFocus) lit else rest
        label.setTextColor(if (gainFocus) Cinema.INK else Cinema.CREAM); sub.setTextColor(if (gainFocus) 0xFF6A5848.toInt() else Cinema.DIM)
    }
}

/**
 * A list (`.lc`): its first five posters overlapped like a hand of cards, then its name, owner, size and likes. Glows
 * cream behind the posters when focused. [posterW] is one poster's width in mockup pixels.
 */
class ListCard(context: Context, private val posterW: Int = 120, private val step: Int = 76, private val widthMock: Int = 520) : LinearLayout(context) {
    val posters = List(5) { ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 12); setColor(Cinema.PLACEHOLDER) }
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 12)) } }
        clipToOutline = true; elevation = Cinema.pxf(context, 8 - it.toFloat())
    } }
    private val fan = FrameLayout(context).apply { clipChildren = false }
    val name = Cinema.text(context, "", 30, Cinema.CREAM, serif = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val by = Cinema.text(context, "", 19, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val box = RectF()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Cinema.CREAM; strokeWidth = Cinema.pxf(context, 4) }

    init {
        orientation = VERTICAL; isFocusable = true; isClickable = true; clipChildren = false; clipToPadding = false; setWillNotDraw(false)
        posters.forEachIndexed { i, v -> fan.addView(v, FrameLayout.LayoutParams(Cinema.px(context, posterW), Cinema.px(context, posterW * 3 / 2)).apply { leftMargin = Cinema.px(context, i * step) }) }
        posters.reversed().forEach { it.bringToFront() }
        addView(fan, LayoutParams(Cinema.px(context, widthMock), Cinema.px(context, posterW * 3 / 2)))
        addView(name, LayoutParams(Cinema.px(context, widthMock), -2).apply { topMargin = Cinema.px(context, 18) })
        addView(by, LayoutParams(Cinema.px(context, widthMock), -2).apply { topMargin = Cinema.px(context, 6) })
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        name.setTextColor(if (gainFocus) Cinema.CREAM else 0xFFE6D9C6.toInt())
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) {
            val pad = Cinema.pxf(context, 14)
            val w = Cinema.pxf(context, posterW + 4 * step); val h = Cinema.pxf(context, posterW * 1.5f)
            val g = Glows.roundRect((w + 2 * pad).toInt(), (h + 2 * pad).toInt(), Cinema.pxf(context, 24), Cinema.pxf(context, 40), Cinema.alpha(Cinema.SAFFRON, 0.26f))
            canvas.drawBitmap(g.bitmap, -pad - g.pad, -pad - g.pad, null)
            super.dispatchDraw(canvas)
            box.set(-pad, -pad, w + pad, h + pad); canvas.drawRoundRect(box, Cinema.pxf(context, 24), Cinema.pxf(context, 24), edge)
        } else super.dispatchDraw(canvas)
    }
}

/** A review (`.rv`): reviewer, their stars and like, the text in Fraunces and when it was written. Cream when focused. */
class ReviewCard(context: Context, widthMock: Int = 540, heightMock: Int = 300) : FrameLayout(context) {
    private val radius = Cinema.pxf(context, 26)
    private val rest = Cinema.glass(context, 26f, Cinema.GLASS)
    private val lit = GradientDrawable().apply { cornerRadius = radius; setColor(Cinema.CREAM) }
    val avatar = AvatarView(context, 52)
    private val who = Cinema.text(context, "", 22, Cinema.CREAM, weight = 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val stars = Cinema.text(context, "", 19, Cinema.SAFFRON, tracking = 0.06f)
    val body = Cinema.text(context, "", 26, 0xFFE5D8C4.toInt(), serif = true, weight = 300).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.18f) }
    private val foot = Cinema.text(context, "", 17, Cinema.DIM)

    init {
        isFocusable = true; isClickable = true; background = rest
        clipChildren = false; clipToPadding = false
        setPadding(Cinema.px(context, 30), Cinema.px(context, 28), Cinema.px(context, 30), Cinema.px(context, 24))
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        head.addView(avatar, LinearLayout.LayoutParams(Cinema.px(context, 52), Cinema.px(context, 52)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(who); names.addView(stars)
        head.addView(names, LinearLayout.LayoutParams(-1, -2).apply { marginStart = Cinema.px(context, 16) })
        addView(head, LayoutParams(-1, -2))
        addView(body, LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 74) })
        addView(foot, LayoutParams(-2, -2, Gravity.BOTTOM))
        layoutParams = LayoutParams(Cinema.px(context, widthMock), Cinema.px(context, heightMock))
    }

    fun bind(name: String, starsText: String?, liked: Boolean, text: String, footer: String?) {
        who.text = name
        stars.text = android.text.SpannableStringBuilder(starsText ?: "").apply {
            if (liked) { val at = length; append(if (isEmpty()) "♥" else "  ♥"); setSpan(android.text.style.ForegroundColorSpan(0xFFFF6F61.toInt()), at, length, 0) }
        }
        body.text = text; foot.text = footer.orEmpty(); contentDescription = "$name: $text"
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        background = if (gainFocus) lit else rest
        who.setTextColor(if (gainFocus) Cinema.INK else Cinema.CREAM); body.setTextColor(if (gainFocus) 0xFF2A1F17.toInt() else 0xFFE5D8C4.toInt())
        foot.setTextColor(if (gainFocus) 0xFF7A6858.toInt() else Cinema.DIM); stars.setTextColor(if (gainFocus) 0xFFB07C22.toInt() else Cinema.SAFFRON)
    }
}

/** The ratings chart: ten bars from ½ to ★★★★★, the top three in the stripe colours. */
class LbHistogram(context: Context) : View(context) {
    var counts: List<Long> = emptyList()
        set(v) { field = v; invalidate() }
    private val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.alpha(Cinema.CREAM, 0.22f) }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lit.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Cinema.STRIPE_SAFFRON, Cinema.STRIPE_ORANGE, Cinema.STRIPE_TERRA, Cinema.STRIPE_RED), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (counts.size != 10) return
        val max = counts.maxOrNull()?.takeIf { it > 0 } ?: return
        val gap = Cinema.pxf(context, 5); val w = (width - gap * 9) / 10f; val r = Cinema.pxf(context, 4)
        counts.forEachIndexed { i, n ->
            val h = (height * (n.toFloat() / max)).coerceAtLeast(Cinema.pxf(context, 4))
            box.set(i * (w + gap), height - h, i * (w + gap) + w, height.toFloat())
            canvas.drawRoundRect(box, r, r, if (i >= 7) lit else rest)
        }
    }
}
