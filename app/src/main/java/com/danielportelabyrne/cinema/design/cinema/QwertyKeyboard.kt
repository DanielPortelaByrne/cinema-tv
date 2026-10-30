package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/**
 * The D-pad keyboard every search uses (`22-search`): letters, with Shift for capitals and "123#" for symbols, and
 * Shift / Symbols / Space / Clear / Delete along the bottom. Each row wraps: left from its first key lands on its last,
 * and right from its last lands on its first. Back leaves the keyboard for [onLeave] instead of closing the page.
 * Keys are glass caps; the focused one turns cream, grows to 1.12 and blooms. [onKey] gets every press; the text itself
 * is kept by the caller.
 */
class QwertyKeyboard(context: Context, private val onKey: (Qwerty.Key) -> Unit) : FrameLayout(context) {
    /** Every key, for focusing the first one. */
    val keys = ArrayList<KeyCap>()

    /** Called when Back is pressed on the keyboard; the caller moves focus out of it. Null lets Back act as usual. */
    var onLeave: (() -> Unit)? = null

    private var symbols = false
    private var shifted = false
    private val letterKeys = ArrayList<KeyCap>()
    private lateinit var shiftKey: KeyCap
    private lateinit var symbolKey: KeyCap

    init {
        clipChildren = false; clipToPadding = false
        build()
    }

    private fun build() {
        removeAllViews(); keys.clear(); letterKeys.clear()
        val rows = if (symbols) Qwerty.SYMBOL_ROWS else Qwerty.ROWS
        val offsets = if (symbols) Qwerty.SYMBOL_OFFSETS else Qwerty.ROW_OFFSETS
        val rowKeys = ArrayList<List<KeyCap>>()
        rows.forEachIndexed { r, row ->
            val line = row.map { ch ->
                KeyCap(context, "", null, wide = false).apply {
                    setValue(ch)
                    setOnClickListener { press(Qwerty.Key.Char(value)) }
                    if (!symbols) letterKeys += this
                }
            }
            line.forEachIndexed { c, key -> add(key, Cinema.px(context, c * 98 + offsets[r] * 98), Cinema.px(context, r * 98), Cinema.px(context, 90)) }
            rowKeys += line
        }
        val y = Cinema.px(context, 4 * 98)
        shiftKey = KeyCap(context, "Shift", null, wide = true).apply { setOnClickListener { toggleShift() } }
        symbolKey = KeyCap(context, if (symbols) "ABC" else "123#", null, wide = true).apply { setOnClickListener { toggleSymbols() } }
        val bottom = listOf(
            shiftKey to (0 to 140),
            symbolKey to (150 to 140),
            KeyCap(context, "Space", null, wide = true).apply { setOnClickListener { press(Qwerty.Key.Space) } } to (300 to 320),
            KeyCap(context, "Clear", R.drawable.ic_cin_x, wide = true).apply { setOnClickListener { press(Qwerty.Key.Clear) } } to (630 to 140),
            KeyCap(context, "Delete", R.drawable.ic_cin_bksp, wide = true).apply { setOnClickListener { press(Qwerty.Key.Delete) } } to (780 to 200),
        )
        bottom.forEach { (key, place) -> add(key, Cinema.px(context, place.first), y, Cinema.px(context, place.second)) }
        rowKeys.forEach { wrap(it) }
        wrap(bottom.map { it.first })
        shiftKey.latch(shifted)
        keys.firstOrNull()?.requestFocus()
    }

    private fun press(key: Qwerty.Key) {
        onKey(key)
        // A capital is for one letter: Shift goes back to small letters after it.
        if (shifted && key is Qwerty.Key.Char) { shifted = false; relabel(); shiftKey.latch(false) }
    }

    private fun toggleShift() {
        shifted = !shifted
        relabel()
        shiftKey.latch(shifted)
    }

    private fun toggleSymbols() {
        symbols = !symbols
        shifted = false
        build()
    }

    /** Capitals on the letter keys while Shift is on, small letters otherwise. */
    private fun relabel() {
        letterKeys.forEach { it.setValue(if (shifted) it.value.uppercaseChar() else it.value.lowercaseChar()) }
    }

    private fun add(key: KeyCap, x: Int, y: Int, w: Int) {
        keys += key
        addView(key, LayoutParams(w, Cinema.px(context, 84)).apply { leftMargin = x; topMargin = y })
    }

    /** Left from the first key of a row lands on its last, and right from the last lands on its first. */
    private fun wrap(row: List<KeyCap>) {
        if (row.size < 2) return
        row.forEach { if (it.id == View.NO_ID) it.id = View.generateViewId() }
        row.first().nextFocusLeftId = row.last().id
        row.last().nextFocusRightId = row.first().id
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val leave = onLeave
        if (leave != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) leave()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** One key cap. Its [value] is the character it types; the wide ones carry a word or an icon instead. */
    class KeyCap(context: Context, label: String, iconRes: Int?, private val wide: Boolean) : LinearLayout(context) {
        var value: Char = ' '
            private set
        private val text: TextView = if (wide) Cinema.text(context, label, 20, Cinema.MUTED, tracking = 0.05f) else Cinema.text(context, label, 30, 0xFFE6DAC8.toInt())
        private val icon = iconRes?.let { Cinema.icon(context, it, Cinema.MUTED) }
        private val radius = Cinema.pxf(context, 18)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Cinema.alpha(Cinema.CREAM, 0.22f)
            if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 18), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private val box = RectF()
        private var latched = false

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; contentDescription = label
            clipChildren = false; setWillNotDraw(false)
            icon?.let { addView(it, LayoutParams(Cinema.px(context, 26), Cinema.px(context, 26)).apply { marginEnd = Cinema.px(context, 8) }) }
            addView(text)
            restyle(false)
        }

        /** Sets the character this key types and shows it. Wide keys keep their word. */
        fun setValue(c: Char) {
            value = c
            if (!wide) { text.text = c.toString(); contentDescription = c.toString() }
        }

        /** A key that stays lit while its mode is on (Shift while capitals are on). */
        fun latch(on: Boolean) {
            latched = on
            restyle(isFocused)
        }

        private fun restyle(lit: Boolean) {
            background = Cinema.glass(context, 18f, if (lit) Cinema.CREAM else if (latched) 0xFF7A5C42.toInt() else Cinema.GLASS)
            val c = if (lit) Cinema.INK else if (wide) Cinema.MUTED else 0xFFE6DAC8.toInt()
            text.setTextColor(c); icon?.setColorFilter(c)
            Cinema.weight(text, if (wide) 20 else 30, serif = false, weight = if (lit) 600 else 400)
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            restyle(gainFocus)
            val s = if (gainFocus) 1.12f else 1f
            if (Motion.animationsOn(this)) animate().scaleX(s).scaleY(s).translationZ(if (gainFocus) Cinema.pxf(context, 12) else 0f).setDuration(if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS).setInterpolator(Motion.SETTLE).start()
            else { scaleX = s; scaleY = s }
            invalidate()
        }

        override fun draw(canvas: Canvas) {
            if (isFocused && android.os.Build.VERSION.SDK_INT >= 28) { box.set(0f, 0f, width.toFloat(), height.toFloat()); canvas.drawRoundRect(box, radius, radius, glow) }
            super.draw(canvas)
        }
    }
}
