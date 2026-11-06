package com.danielportelabyrne.cinema.ui.room

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.AppTheme
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.Type
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.StripeDrawable
import com.danielportelabyrne.cinema.ui.scrollFocusIntoView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PAPER_TOP = 0xFFF3E7C6.toInt()
private val PAPER_BOTTOM = 0xFFE6D5A8.toInt()
private val PAPER_INK = 0xFF2B2117.toInt()
private val PAPER_INK_DIM = 0xFF6B5A46.toInt()
private val TERRACOTTA = 0xFFAF5938.toInt()
private val WALNUT_FRAME = 0xFF5A3F28.toInt()

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Context.text(value: String, sp: Float, color: Int, style: Int = Typeface.NORMAL, family: String = "sans-serif-medium") =
    TextView(this).apply {
        text = value; textSize = sp; setTextColor(color)
        typeface = Typeface.create(family, style)
    }

private fun paper(context: Context, radiusDp: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PAPER_TOP, PAPER_BOTTOM)).apply {
    cornerRadius = context.dp(radiusDp).toFloat()
}

// ---------------------------------------------------------------------------------------------
// Project shelf
// ---------------------------------------------------------------------------------------------

/** The sideboard: projects standing on a walnut ledge. One caption below the ledge follows the focused object. */
class SheetAction(val label: String, val sublabel: String? = null, val primary: Boolean = false, val run: () -> Unit)

/**
 * A calm centred card for details and confirmations, and the app's own replacement for
 * stock Android dialogs. Optional artwork sits on the left; [actions] become pill buttons above Close.
 * Back or OK on Close dismisses it and returns focus to where it was.
 */
class InfoSheet(private val container: FrameLayout) {
    private val context = container.context
    private var previousFocus: View? = null
    private var lastButtons: List<View> = emptyList()
    private var card: View? = null
    private var flight: Flight? = null
    private var surfaceFade: android.animation.ValueAnimator? = null
    val isVisible: Boolean get() = container.visibility == View.VISIBLE
    /** True while the sheet is animating away: it no longer takes keys, and the page is already coming back. */
    var isClosing = false
        private set

    /**
     * A picture that travels from the page into the sheet (and back when it closes). [source] is the
     * picture on the page, [target] its place in the sheet, [reveal] the parts of the sheet that appear
     * around it once it is on its way.
     */
    class Flight(val source: View, val target: View, val reveal: List<View>) {
        internal val from = android.graphics.Rect()
    }

    fun show(
        kicker: String, title: String, subtitle: String?, lines: List<String>,
        image: android.graphics.Bitmap? = null, actions: List<SheetAction> = emptyList(),
        refresh: Boolean = false,
        /** Size of [image] in dp; defaults to a portrait poster. */
        imageSizeDp: Pair<Int, Int> = 150 to 225,
        /** The picture on the page that [image] shows: it flies from there into the sheet. */
        from: View? = null
    ) {
        // A refresh redraws the same sheet in place (for results that arrive late): keep the way back and the focused button.
        val focusedIndex = if (refresh) lastButtons.indexOfFirst { it.isFocused }.coerceAtLeast(0) else 0
        if (!refresh) rememberFocus()
        settle()
        container.removeAllViews()
        container.setBackgroundColor(ContextCompat.getColor(context, R.color.sheet_scrim))
        // The Cinema card: dark warm glass, an eyebrow, the title in Fraunces, the words, then the choices and Close.
        val pad = Cinema.px(context, 48)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(pad, Cinema.px(context, 44), pad, Cinema.px(context, 44))
            background = Cinema.glass(context, 32f, 0xF5120D0A.toInt())
            elevation = context.dp(24).toFloat()
        }
        var imageView: View? = null
        if (image != null) {
            card.addView(android.widget.ImageView(context).also { imageView = it }.apply {
                setImageBitmap(image); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 18)) }
                }
            }, LinearLayout.LayoutParams(context.dp(imageSizeDp.first), context.dp(imageSizeDp.second)).apply { marginEnd = Cinema.px(context, 44) })
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val measure = Cinema.px(context, 860)
        card.addView(column, LinearLayout.LayoutParams(0, -2, 1f))

        // A caller's own "Close" becomes the Close button rather than a second one.
        val closeAction = actions.firstOrNull { it.label == "Close" }
        val actions = actions.filter { it.label != "Close" }
        val danger = actions.any { isDestructive(it.label) }
        column.addView(Cinema.eyebrow(context, listOf(kicker.lowercase(Locale.UK).replaceFirstChar { it.uppercase() }), color = if (danger) Cinema.DANGER_TEXT else Cinema.SAFFRON))
        column.addView(Cinema.title(context, title, if (title.length > 28) 48 else 60, maxLines = 3).apply { maxWidth = measure },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 16) })
        subtitle?.let {
            column.addView(Cinema.text(context, it, 20, Cinema.SAFFRON, weight = 600, tracking = 0.06f).apply { maxWidth = measure },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 14) })
        }
        lines.forEach {
            column.addView(Cinema.text(context, it, 24, Cinema.READING, weight = 300).apply { maxWidth = measure; setLineSpacing(0f, 1.22f) },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 18) })
        }

        // Each choice is a glass row: its name and a line on what it does; a destructive one is red until focused, the
        // suggested one carries the stripe mark. Close always comes last, as a plain Cinema button.
        var first: View? = null
        val built = mutableListOf<View>()
        if (actions.isNotEmpty()) {
            val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            actions.forEach { action ->
                val row = choiceRow(action)
                row.setOnClickListener { hide(); action.run() }
                stack.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (stack.childCount > 0) topMargin = Cinema.px(context, 12) })
                built += row
                if (first == null) first = row
            }
            column.addView(stack, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 34) })
        }
        val close = CinemaButton(context, CinemaButton.Kind.GLASS, "Close").apply { setOnClickListener { hide(); closeAction?.run() } }
        column.addView(close, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, if (actions.isEmpty()) 36 else 22) })
        built += close
        if (first == null) first = close

        // Beside a picture the card hugs its words (up to a reading measure) rather than a fixed width.
        // Without a picture it hugs its words too: never narrower than a comfortable card, never wider than reading allows.
        val cardWidth = if (image == null) {
            val words = (0 until column.childCount).map { column.getChildAt(it) }.filterIsInstance<TextView>()
                .maxOfOrNull { it.paint.measureText(it.text.toString()) + it.paddingLeft + it.paddingRight }?.toInt() ?: 0
            val choices = if (actions.isEmpty()) 0 else Cinema.px(context, 620)
            pad * 2 + maxOf(words, choices).coerceIn(Cinema.px(context, 560), Cinema.px(context, if (lines.sumOf { it.length } > 400) 1000 else 860))
        } else {
            val words = (0 until column.childCount).map { column.getChildAt(it) }.filterIsInstance<TextView>()
                .maxOfOrNull { it.paint.measureText(it.text.toString()) + it.paddingLeft + it.paddingRight }?.toInt() ?: 0
            val text = words.coerceIn(Cinema.px(context, 560), measure)
            pad * 2 + context.dp(imageSizeDp.first) + Cinema.px(context, 44) + text
        }
        container.addView(card, FrameLayout.LayoutParams(cardWidth, -2, Gravity.CENTER))
        container.visibility = View.VISIBLE
        this.card = card
        val f = if (from != null && imageView != null) Flight(from, imageView!!, listOf(column)) else null
        val flying = !refresh && f != null && Motion.animationsOn(container) && from!!.isShown && from.getGlobalVisibleRect(f.from)
        if (flying) { flight = f; flyIn(card, f!!) }
        else if (!refresh) Motion.enter(container, card, context.dp(28).toFloat())
        lastButtons = built
        (built.getOrNull(focusedIndex) ?: first)?.post { (built.getOrNull(focusedIndex) ?: first)?.requestFocus() }
    }

    private fun isDestructive(label: String) = listOf("Cancel", "Delete", "Remove", "Stop", "Withdraw").any { label.startsWith(it) }

    /** A choice in the sheet: glass at rest, cream when focused; its line on what it does sits beneath its name. */
    private fun choiceRow(action: SheetAction): LinearLayout {
        val destructive = isDestructive(action.label)
        val restName = if (destructive) Cinema.DANGER_TEXT else Cinema.CREAM
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(Cinema.px(context, 30), Cinema.px(context, 18), Cinema.px(context, 30), Cinema.px(context, 18))
            contentDescription = action.label
        }
        if (action.primary) row.addView(View(context).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(context, 4)) },
            LinearLayout.LayoutParams(Cinema.px(context, 8), Cinema.px(context, 40)).apply { marginEnd = Cinema.px(context, 22) })
        val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val name = Cinema.text(context, action.label, 25, restName, weight = 600).apply { maxLines = 1 }
        words.addView(name)
        val sub = action.sublabel?.let { Cinema.text(context, it, 19, Cinema.MUTED).apply { maxLines = 3 } }
        sub?.let { words.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 4) }) }
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        val rest = Cinema.glass(context, 22f, Cinema.GLASS_STRONG)
        val lit = Cinema.glass(context, 22f, Cinema.CREAM)
        row.background = rest
        row.setOnFocusChangeListener { _, f ->
            row.background = if (f) lit else rest
            name.setTextColor(if (f) Cinema.INK else restName)
            sub?.setTextColor(if (f) 0xFF6A5848.toInt() else Cinema.MUTED)
        }
        return row
    }

    /** Shows any view centred over a scrim, focusing [focus]. Back dismisses it like every other sheet. */
    fun showCustom(view: View, focus: View?, flight: Flight? = null) {
        rememberFocus()
        settle()
        container.removeAllViews()
        container.setBackgroundColor(ContextCompat.getColor(context, R.color.sheet_scrim))
        container.clipChildren = false
        // Measured before anything moves: the page is about to step back behind the sheet.
        val flying = flight != null && Motion.animationsOn(container) &&
            flight.source.isShown && flight.source.getGlobalVisibleRect(flight.from)
        container.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        container.visibility = View.VISIBLE
        card = view
        lastButtons = emptyList()
        if (flying) { this.flight = flight; flyIn(view, flight!!) } else Motion.enter(container, view, context.dp(28).toFloat())
        focus?.post { focus.requestFocus() }
    }

    fun hide() {
        if (!isVisible || isClosing) return
        isClosing = true
        // Focus goes home at once, so the remote answers immediately; the sheet only has to finish leaving.
        container.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        previousFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        container.requestLayout()   // lets the page start coming back while the sheet leaves
        val f = flight
        if (f != null && f.source.isAttachedToWindow && Motion.animationsOn(container)) flyOut(f)
        else Motion.exit(container, card) { settle() }
    }

    private fun rememberFocus() {
        // Reopened while still closing: the way back is the one already remembered.
        if (!isClosing) previousFocus = container.rootView.findFocus()
    }

    /** Ends any entrance or exit in its final state and puts the container back to rest. */
    private fun settle() {
        surfaceFade?.cancel(); surfaceFade = null
        container.animate().cancel()
        card?.animate()?.cancel()
        flight?.let { f ->
            f.target.animate().cancel()
            f.source.alpha = 1f
        }
        flight = null
        if (isClosing) {
            isClosing = false
            container.visibility = View.GONE
            container.removeAllViews()
            card = null
        }
        container.alpha = 1f
        container.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        (container.background as? ColorDrawable)?.alpha = 255
    }

    /** The picture lifts off the page and settles into the sheet; the sheet's surface and words gather around it. */
    private fun flyIn(card: View, f: Flight) {
        f.source.alpha = 0f
        val scrim = container.background as? ColorDrawable
        val surface = card.background
        scrim?.alpha = 0; surface?.alpha = 0
        f.reveal.forEach { it.alpha = 0f; it.translationY = context.dp(10).toFloat() }
        f.target.visibility = View.INVISIBLE
        card.post {
            if (flight !== f || isClosing) return@post
            placeAt(f.target, f.from)
            f.target.visibility = View.VISIBLE
            f.target.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(Motion.FLIGHT_MS).setInterpolator(Motion.SWOOSH).start()
            fadeSurfaces(scrim, surface, 255, Motion.SHEET_IN_MS / 2)
            f.reveal.forEach {
                it.animate().alpha(1f).translationY(0f).setStartDelay(Motion.FLIGHT_MS / 3)
                    .setDuration(Motion.SHEET_IN_MS).setInterpolator(Motion.SETTLE).start()
            }
        }
    }

    /** The reverse: the sheet dissolves from around the picture, which drops back into its place on the page. */
    private fun flyOut(f: Flight) {
        val scrim = container.background as? ColorDrawable
        val surface = card?.background
        f.reveal.forEach { it.animate().alpha(0f).setStartDelay(0).setDuration(Motion.SHEET_OUT_MS / 2).setInterpolator(Motion.SETTLE).start() }
        fadeSurfaces(scrim, surface, 0, Motion.SHEET_OUT_MS)
        // Back to where the picture was taken from: the page comes forward to exactly that position.
        val o = offsetsTo(f.target, f.from)
        f.target.animate().translationX(o[0]).translationY(o[1]).scaleX(o[2]).scaleY(o[3])
            .setDuration(Motion.FLIGHT_MS - 60).setInterpolator(Motion.SWOOSH)
            .withEndAction { if (flight === f) settle() }.start()
    }

    private fun fadeSurfaces(scrim: ColorDrawable?, surface: android.graphics.drawable.Drawable?, to: Int, ms: Long) {
        surfaceFade?.cancel()
        surfaceFade = android.animation.ValueAnimator.ofInt(scrim?.alpha ?: surface?.alpha ?: 255, to).apply {
            duration = ms; interpolator = Motion.SETTLE
            addUpdateListener { val a = it.animatedValue as Int; scrim?.alpha = a; surface?.alpha = a }
            start()
        }
    }

    /** Puts [view] (pivot at its top-left) exactly over [rect] in screen space. */
    private fun placeAt(view: View, rect: android.graphics.Rect) {
        val o = offsetsTo(view, rect)
        view.translationX = o[0]; view.translationY = o[1]; view.scaleX = o[2]; view.scaleY = o[3]
    }

    /** Translation and scale that put [view]'s untransformed layout box over [rect]; sets the pivot to top-left. */
    private fun offsetsTo(view: View, rect: android.graphics.Rect): FloatArray {
        val saved = floatArrayOf(view.translationX, view.translationY, view.scaleX, view.scaleY)
        view.translationX = 0f; view.translationY = 0f; view.scaleX = 1f; view.scaleY = 1f
        val at = IntArray(2); view.getLocationOnScreen(at)
        view.translationX = saved[0]; view.translationY = saved[1]; view.scaleX = saved[2]; view.scaleY = saved[3]
        view.pivotX = 0f; view.pivotY = 0f
        val w = view.width.coerceAtLeast(1); val h = view.height.coerceAtLeast(1)
        return floatArrayOf((rect.left - at[0]).toFloat(), (rect.top - at[1]).toFloat(), rect.width() / w.toFloat(), rect.height() / h.toFloat())
    }
}

// ---------------------------------------------------------------------------------------------
// Tonight card
// ---------------------------------------------------------------------------------------------

/** "Tonight?": a framed paper print with three lines to choose from -- Watch, Make, Eat. */
internal fun trapEdges(row: LinearLayout) {
    val count = row.childCount
    for (i in 0 until count) {
        row.getChildAt(i).setOnKeyListener { _, keyCode, event ->
            event.action == android.view.KeyEvent.ACTION_DOWN &&
                ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && i == 0) || (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && i == count - 1))
        }
    }
}
