package com.danielportelabyrne.cinema.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import com.danielportelabyrne.cinema.design.cinema.StripeDrawable

/**
 * A genre's card, set like a film poster's title card: a still from one of the genre's popular titles, toned in the
 * genre's two colours (its shadows deep, its lights pale), darkening to the left under the genre's name in large
 * Fraunces, with the row heading's stripe mark above it. Focused, the card grows, glows in the genre's colour and the
 * still warms back to its own colours. [more] makes it a shelf's last card ("More Thriller"). Parents must not clip children.
 */
class GenreCard(context: Context, code: String, label: String, wMock: Int, hMock: Int, more: Boolean = false) : FrameLayout(context) {
    var onFocus: ((Boolean) -> Unit)? = null
    private val w = Cinema.px(context, wMock)
    private val h = Cinema.px(context, hMock)
    private val radius = Cinema.pxf(context, 22)
    private val colours = GenreArt.coloursFor(code)
    private val face = FrameLayout(context)
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val toned = GenreArt.duotone(colours)
    private var tone = 1f
    private var toner: ValueAnimator? = null
    private val bloomSpread = Cinema.pxf(context, 8)
    private val bloom by lazy { Glows.roundRect(w + 2 * bloomSpread.toInt(), h + 2 * bloomSpread.toInt(), radius + bloomSpread, Cinema.pxf(context, 40), Cinema.alpha(Cinema.CREAM, 0.55f)) }
    private val bloomPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.PorterDuffColorFilter(colours.light, android.graphics.PorterDuff.Mode.SRC_IN)
    }
    private val shadow = Glows.shadow(w, h, radius, Cinema.pxf(context, 14))
    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        isFocusable = true; isClickable = true; contentDescription = if (more) "More $label" else label
        clipChildren = false; clipToPadding = false; setWillNotDraw(false)
        face.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colours.base, colours.deep)).apply { cornerRadius = radius }
        face.clipToOutline = true
        face.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
        image.colorFilter = ColorMatrixColorFilter(toned)
        face.addView(image, LayoutParams(-1, -1))
        // The genre's deep colour from the left, under the words, and a little shade along the foot.
        face.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Cinema.alpha(colours.deep, 0.94f), Cinema.alpha(colours.deep, 0.55f), Cinema.alpha(colours.deep, 0f)))
        }, LayoutParams((w * 0.78f).toInt(), -1))
        face.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0, 0x8C000000.toInt()))
        }, LayoutParams(-1, -1))
        val words = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.START
            setPadding(Cinema.px(context, 30), 0, Cinema.px(context, 30), Cinema.px(context, 26))
        }
        val kicker = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        kicker.addView(View(context).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(context, 4)) },
            LinearLayout.LayoutParams(Cinema.px(context, 8), Cinema.px(context, 20)).apply { marginEnd = Cinema.px(context, 12) })
        kicker.addView(Cinema.text(context, if (more) "More" else "Genre", 14, colours.light, weight = 700, tracking = 0.22f, caps = true))
        words.addView(kicker)
        val name = Cinema.text(context, label, 46, Cinema.CREAM, serif = true, weight = 400, tracking = -0.01f).apply {
            maxLines = 1; includeFontPadding = false
        }
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            name, Cinema.px(context, 24), Cinema.px(context, 46), 1, android.util.TypedValue.COMPLEX_UNIT_PX)
        words.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 8) })
        face.addView(words, LayoutParams(-1, -1))
        addView(face, LayoutParams(w, h))
    }

    /** The still behind the name; it fades in. */
    fun setArt(bitmap: Bitmap) {
        image.setImageBitmap(bitmap)
        if (Motion.animationsOn(this)) image.animate().alpha(1f).setDuration(260).start() else image.alpha = 1f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); pivotX = w / 2f; pivotY = h / 2f }

    /** 1 is the genre's tones, 0 the still's own colours. */
    private fun setTone(t: Float) {
        tone = t
        val m = FloatArray(20)
        val own = ColorMatrix().array; val duo = toned.array
        for (i in 0 until 20) m[i] = own[i] + (duo[i] - own[i]) * t
        image.colorFilter = ColorMatrixColorFilter(m)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val scale = if (gainFocus) (if (w > h) 1.06f else 1.09f) else 1f
        val ms = if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
        translationZ = if (gainFocus) Cinema.pxf(context, 20) else 0f
        toner?.cancel()
        val target = if (gainFocus) 0.25f else 1f
        if (Motion.animationsOn(this)) {
            animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(Motion.SETTLE).start()
            image.animate().scaleX(if (gainFocus) 1.05f else 1f).scaleY(if (gainFocus) 1.05f else 1f).setDuration(ms + 200).setInterpolator(Motion.SETTLE).start()
            toner = ValueAnimator.ofFloat(tone, target).apply { duration = ms + 120; addUpdateListener { setTone(it.animatedValue as Float) }; start() }
        } else { scaleX = scale; scaleY = scale; setTone(target) }
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isFocused) { val s = bloomSpread.toInt(); canvas.drawBitmap(bloom.bitmap, (-s - bloom.pad).toFloat(), (-s - bloom.pad).toFloat(), bloomPaint) }
        else canvas.drawBitmap(shadow.bitmap, -shadow.pad.toFloat(), -shadow.pad + Glows.shadowOffset(Cinema.pxf(context, 14)), plain)
        super.dispatchDraw(canvas)
    }
}

/** Each genre's colours, and the duotone that sets a still in them. */
object GenreArt {
    /** [base] and [deep] for the card's ground and shadows, [light] for its highlights, kicker and focus glow. */
    class Colours(val base: Int, val deep: Int, val light: Int)

    private fun c(v: Long) = v.toInt()

    /** Deep, film-poster colours: warm and muted, never toy-bright. */
    fun coloursFor(code: String): Colours = when (code) {
        "act" -> Colours(c(0xFF7A1F16), c(0xFF1A0705), c(0xFFF2A46A))
        "cmy" -> Colours(c(0xFF8A6418), c(0xFF1E1405), c(0xFFF6D58A))
        "crm" -> Colours(c(0xFF233847), c(0xFF080D12), c(0xFFB9CFDD))
        "drm" -> Colours(c(0xFF5E1A2E), c(0xFF14060B), c(0xFFEBB9C2))
        "hrr" -> Colours(c(0xFF4A0D0D), c(0xFF070202), c(0xFFE07A62))
        "scf" -> Colours(c(0xFF1F2E70), c(0xFF06081C), c(0xFFAFC2F2))
        "trl" -> Colours(c(0xFF2C3A33), c(0xFF080C0A), c(0xFFCFE0B4))
        "ani" -> Colours(c(0xFF8A3A1C), c(0xFF220C05), c(0xFFFFD2A0))
        "rma" -> Colours(c(0xFF7A2D45), c(0xFF1C0910), c(0xFFF6C2CF))
        "fnt" -> Colours(c(0xFF3D2C70), c(0xFF0D0820), c(0xFFDACBFA))
        "fml" -> Colours(c(0xFF1F5C55), c(0xFF061714), c(0xFFF3DC9A))
        "hst" -> Colours(c(0xFF5E4826), c(0xFF161007), c(0xFFF0D7A8))
        "doc" -> Colours(c(0xFF4A5524), c(0xFF0F1306), c(0xFFE6EBB8))
        else -> Colours(Cinema.STRIPE_TERRA, Cinema.MAROON, Cinema.SAFFRON)
    }

    /** A matrix that maps a picture's brightness from [Colours.deep] (black) to [Colours.light] (white). */
    fun duotone(col: Colours): ColorMatrix {
        fun ch(v: Int, shift: Int) = ((v shr shift) and 0xFF).toFloat()
        val m = FloatArray(20)
        listOf(16, 8, 0).forEachIndexed { row, shift ->
            val d = ch(col.deep, shift); val span = (ch(col.light, shift) - d) / 255f
            m[row * 5] = 0.299f * span; m[row * 5 + 1] = 0.587f * span; m[row * 5 + 2] = 0.114f * span; m[row * 5 + 4] = d
        }
        m[18] = 1f
        return ColorMatrix(m)
    }
}
