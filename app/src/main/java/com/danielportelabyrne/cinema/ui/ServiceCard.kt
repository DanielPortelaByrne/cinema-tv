package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.Glows

/**
 * A "Browse by service" card: the service's own colours as a deep gradient, its app icon top left, its name in Fraunces
 * at the foot, and three of its popular posters fanned out on the right, tilted like cards in a hand. Focused, the
 * card grows, glows in the service's colour and the fan opens a little. Parents must not clip children.
 */
class ServiceCard(context: Context, label: String, private val colours: Pair<Int, Int>) : FrameLayout(context) {
    val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = label }
    var onFocus: ((Boolean) -> Unit)? = null

    private val w = Cinema.px(context, W)
    private val h = Cinema.px(context, H)
    private val radius = Cinema.pxf(context, 30)
    private val face = FrameLayout(context)
    private val posters = List(3) { ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }
    private val fan = List(3) { FrameLayout(context) }
    private val bloomSpread = Cinema.pxf(context, 8)
    // One white bloom for every service card, tinted with this service's colour as it is drawn; made on first focus.
    private val bloom by lazy { Glows.roundRect(w + 2 * bloomSpread.toInt(), h + 2 * bloomSpread.toInt(), radius + bloomSpread, Cinema.pxf(context, 44), Cinema.alpha(Cinema.CREAM, 0.62f)) }
    private val bloomPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.PorterDuffColorFilter(lighten(colours.first), android.graphics.PorterDuff.Mode.SRC_IN)
    }
    private val restElevation = Cinema.pxf(context, 16)
    private val shadow = Glows.shadow(w, h, radius, restElevation)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        isFocusable = true; isClickable = true; contentDescription = label
        clipChildren = false; clipToPadding = false
        setWillNotDraw(false)
        face.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colours.first, mix(colours.first, colours.second, 0.55f), colours.second)).apply { cornerRadius = radius }
        face.clipToOutline = true
        face.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
        // A pale sun low on the right, behind the fan, in the Cinema tiles' manner.
        face.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.alpha(Cinema.CREAM, 0.07f)) }
        }, LayoutParams(Cinema.px(context, 300), Cinema.px(context, 300), Gravity.END or Gravity.BOTTOM).apply { rightMargin = Cinema.px(context, -40); bottomMargin = Cinema.px(context, -120) })
        // The fan: three posters, the middle one in front, tipping out from a point below the card.
        val pw = Cinema.px(context, POSTER_W); val ph = Cinema.px(context, POSTER_H)
        listOf(0, 2, 1).forEach { i ->
            val holder = fan[i].apply {
                background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 12); setColor(Cinema.alpha(Cinema.INK, 0.6f)) }
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 12)) } }
                addView(posters[i], LayoutParams(-1, -1))
                alpha = 0f
            }
            face.addView(holder, LayoutParams(pw, ph, Gravity.END or Gravity.TOP).apply {
                rightMargin = Cinema.px(context, 34 + (2 - i) * 50); topMargin = Cinema.px(context, 50)
            })
            holder.pivotX = pw / 2f; holder.pivotY = ph * 1.6f
        }
        layFan(false)
        // Darkens the left, where the icon and name sit, so they read over the fan.
        face.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Cinema.alpha(colours.second, 0.75f), Cinema.alpha(colours.second, 0.3f), 0))
        }, LayoutParams(Cinema.px(context, 300), -1, Gravity.START))
        val icon = FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(context, 22); setColor(Cinema.alpha(Cinema.INK, 0.5f)) }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 22)) } }
            addView(logo, LayoutParams(-1, -1))
        }
        face.addView(icon, LayoutParams(Cinema.px(context, 92), Cinema.px(context, 92)).apply { leftMargin = Cinema.px(context, 30); topMargin = Cinema.px(context, 30) })
        val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        // Its own width, not the column's: a name longer than "FILMS & SERIES" was cut to it ("Paramo").
        words.addView(Cinema.text(context, label, 36, Cinema.CREAM, serif = true, weight = 500).apply {
            maxLines = 1; setShadowLayer(Cinema.pxf(context, 14), 0f, Cinema.pxf(context, 2), 0xCC000000.toInt())
            // Clear of the fan: a long name ("Paramount+") steps down in size rather than running into the posters.
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, Cinema.px(context, 26), Cinema.px(context, 36), 1, android.util.TypedValue.COMPLEX_UNIT_PX)
        }, LinearLayout.LayoutParams(Cinema.px(context, 186), Cinema.px(context, 50)))
        words.addView(Cinema.text(context, "Films & series", 15, Cinema.alpha(Cinema.CREAM, 0.7f), weight = 600, tracking = 0.18f, caps = true),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 4) })
        // Added after the fan, so a long name is drawn over the posters, never hidden behind one.
        face.addView(words, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(context, 32); bottomMargin = Cinema.px(context, 28) })
        addView(face, LayoutParams(w, h))
    }

    /** One of the service's popular posters, into the fan (0 left, 1 middle, 2 right). */
    fun setPoster(index: Int, bitmap: Bitmap) {
        val holder = fan.getOrNull(index) ?: return
        posters[index].setImageBitmap(bitmap)
        if (Motion.animationsOn(this)) holder.animate().alpha(1f).setDuration(260).start() else holder.alpha = 1f
    }

    private fun layFan(open: Boolean) {
        val spread = if (open) 1.35f else 1f
        listOf(-11f, 0f, 11f).forEachIndexed { i, deg ->
            val r = deg * spread
            val lift = if (open && i == 1) -Cinema.pxf(context, 10) else 0f
            if (Motion.animationsOn(this)) fan[i].animate().rotation(r).translationY(lift).setDuration(if (open) Motion.FOCUS_IN_MS + 80 else Motion.FOCUS_OUT_MS).setInterpolator(Motion.SETTLE).start()
            else { fan[i].rotation = r; fan[i].translationY = lift }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); pivotX = w / 2f; pivotY = h / 2f }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val scale = if (gainFocus) 1.07f else 1f
        translationZ = if (gainFocus) Cinema.pxf(context, 20) else 0f
        if (Motion.animationsOn(this)) animate().scaleX(scale).scaleY(scale).setDuration(if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS).setInterpolator(Motion.SETTLE).start()
        else { scaleX = scale; scaleY = scale }
        layFan(gainFocus)
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) {
            val s = bloomSpread.toInt()
            canvas.drawBitmap(bloom.bitmap, (face.left - s - bloom.pad).toFloat(), (face.top - s - bloom.pad).toFloat(), bloomPaint)
        } else canvas.drawBitmap(shadow.bitmap, (face.left - shadow.pad).toFloat(), face.top - shadow.pad + Glows.shadowOffset(restElevation), paint)
        super.dispatchDraw(canvas)
    }

    companion object {
        /** Mockup sizes: the card, and each poster in its fan. */
        const val W = 520; const val H = 292
        const val POSTER_W = 128; const val POSTER_H = 192

        /**
         * Each service's colours, from its own branding but deepened and warmed a little toward Cinema's dark brown, so the
         * row sits in the page rather than shouting: (top left, bottom right). Unknown services get Cinema's own maroon.
         */
        fun coloursFor(code: String): Pair<Int, Int> = when (code) {
            "nfx" -> 0xFFA3121A.toInt() to 0xFF180607.toInt()
            "mxx" -> 0xFF3A3A3D.toInt() to 0xFF0A0A0B.toInt()
            "pmp" -> 0xFF2262D9.toInt() to 0xFF0A1532.toInt()
            "amp" -> 0xFF1689BA.toInt() to 0xFF071B26.toInt()
            "dnp" -> 0xFF137A86.toInt() to 0xFF081A2E.toInt()
            "atp" -> 0xFF5E5A56.toInt() to 0xFF141210.toInt()
            else -> Cinema.STRIPE_TERRA to Cinema.MAROON
        }

        private fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() shl shift
            return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
        }

        private fun lighten(c: Int) = mix(c, 0xFFFFFFFF.toInt(), 0.25f)
    }
}
