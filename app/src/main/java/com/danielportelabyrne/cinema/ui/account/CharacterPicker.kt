package com.danielportelabyrne.cinema.ui.account

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema

/**
 * "Choose your character": every character (and "Your initial") in a grid over [host], then, when [favourites] is
 * given, characters from the person's favourite films and series as the actors' photos, added once they are found.
 * OK picks one ([onPick]); Back closes it. Focus stays in the grid while it is open.
 */
class CharacterPicker(
    private val activity: Activity,
    private val host: ViewGroup,
    private val name: String,
    private val current: String?,
    /** Finds (id, label) pairs off the main thread and hands them back on it; null for none. */
    private val favourites: ((List<Pair<String, String>>) -> Unit) -> Unit = {},
    private val onPick: (String?) -> Unit,
    private val onClose: () -> Unit = {}
) {
    private val initial = name.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
    private fun p(v: Number) = Cinema.px(activity, v)

    private val sheet = object : FrameLayout(activity) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            val found = super.focusSearch(focused, direction)
            var v: Any? = found
            while (v is View) { if (v === this) return found; v = v.parent }
            return focused
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) close(); return true }
            return super.dispatchKeyEvent(event)
        }
    }.apply { isClickable = true; setBackgroundColor(Cinema.alpha(Cinema.BG, 0.97f)); elevation = Cinema.pxf(activity, 120) }

    val isOpen: Boolean get() = sheet.parent != null

    fun close() {
        if (!isOpen) return
        (sheet.parent as? ViewGroup)?.removeView(sheet)
        onClose()
    }

    fun show() {
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        column.addView(Cinema.text(activity, if (name.isBlank()) "Choose your character" else "$name's character", 64, Cinema.CREAM, serif = true))
        val caption = Cinema.text(activity, " ", 26, Cinema.MUTED)
        column.addView(caption, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(14) })
        var first: View? = null
        fun tile(id: String?, label: String, size: Int): View {
            val t = object : View(activity) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val serif = Cinema.text(activity, "", 10, serif = true).typeface
                override fun onDraw(c: android.graphics.Canvas) {
                    val cx = width / 2f; val cy = height / 2f; val r = width / 2f - Cinema.pxf(activity, 22)
                    if (isFocused) { val step = Cinema.pxf(activity, 5); for (i in 4 downTo 1) { paint.color = Cinema.STRIPES[i - 1]; c.drawCircle(cx, cy, r + step * i, paint) } }
                    Avatars.draw(c, id, cx, cy, r, initial, serif = serif, view = this)
                }
            }.apply {
                isFocusable = true; isClickable = true; contentDescription = label
                setOnFocusChangeListener { v, f -> v.invalidate(); if (f) caption.text = label; v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(140).start() }
                setOnClickListener { onPick(id); close() }
            }
            if (id == current || (first == null && id == null)) first = t
            return t
        }
        fun rowOf(items: List<Pair<String?, String>>, size: Int) = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false
            items.forEach { (id, label) -> addView(tile(id, label, size), LinearLayout.LayoutParams(size, size).apply { marginStart = p(12); marginEnd = p(12) }) }
        }
        val choices = listOf<Pair<String?, String>>(null to "Your initial") + Avatars.ALL.map { it.id to it.name }
        choices.chunked(7).forEach { line -> column.addView(rowOf(line, p(170)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(28) }) }
        val favRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        column.addView(favRows, LinearLayout.LayoutParams(-2, -2))
        favourites { found ->
            if (!isOpen || found.isEmpty()) return@favourites
            favRows.addView(Cinema.text(activity, "From your favourites", 30, Cinema.CREAM, serif = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(44) })
            found.chunked(7).forEach { line -> favRows.addView(rowOf(line.map { it.first to it.second }, p(140)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(20) }) }
        }
        val scroller = android.widget.ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false; isFocusable = false; clipToPadding = false
            setPadding(0, p(70), 0, p(120)); addView(column, FrameLayout.LayoutParams(-1, -2))
        }
        sheet.addView(scroller, FrameLayout.LayoutParams(-1, -1))
        host.addView(sheet, FrameLayout.LayoutParams(-1, -1))
        first?.let { v -> v.post { v.requestFocus() } }
    }
}
