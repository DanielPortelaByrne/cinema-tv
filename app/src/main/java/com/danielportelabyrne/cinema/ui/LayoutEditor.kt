package com.danielportelabyrne.cinema.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.movies.LayoutPage
import com.danielportelabyrne.cinema.data.movies.PageLayouts
import com.danielportelabyrne.cinema.data.movies.Section
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.QwertyKeyboard

/**
 * Lays out one page row by row, over the page. The rows are listed top to bottom: OK on a row picks it up, Up and Down
 * then move it, OK puts it down; Right reaches its Remove. "Add a row" opens a search of every row there is (Top 10s,
 * genres, services, the library, what is part-watched…). Every change is kept at once through [onSave]; [onClose] is
 * told whether anything changed, so the page is rebuilt only then.
 *
 * Cinema moves focus itself (see CinemaActivity), so the panel hands every D-pad move to [move] and Back to [back].
 */
class LayoutEditor(
    private val activity: Activity,
    private val page: LayoutPage,
    start: List<Section>,
    private val onSave: (List<Section>) -> Unit,
    private val onClose: (changed: Boolean) -> Unit
) : FrameLayout(activity) {
    private val sections = start.toMutableList()
    private var changed = false
    private var moving = -1
    private fun p(v: Number) = Cinema.px(activity, v)

    // --- The row list ------------------------------------------------------------------------------------------
    private val listMode = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val addButton = CinemaButton(activity, CinemaButton.Kind.TERRA, "Add a row", R.drawable.ic_cin_plus)
    private val resetButton = CinemaButton(activity, CinemaButton.Kind.GLASS, "Reset to default")
    private val doneButton = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Done", R.drawable.ic_cin_check)
    private val header = listOf<View>(addButton, resetButton, doneButton)
    private val rowsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val rowsScroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, p(20), 0, p(200)); addView(rowsBox) }
    private val bodies = ArrayList<TextView>()
    private val removes = ArrayList<CinemaButton>()

    // --- The search for a row to add ---------------------------------------------------------------------------
    private val pickMode = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; visibility = GONE }
    private var typed = ""
    private val query = Cinema.text(activity, "", 40, Cinema.CREAM, weight = 300).apply { maxLines = 1 }
    private val keyboard = QwertyKeyboard(activity) { key -> typed = Qwerty.type(typed, key); showResults() }
    private val resultsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val resultsScroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, p(200)); addView(resultsBox) }
    private val results = ArrayList<TextView>()

    init {
        isClickable = true; isFocusable = false
        setBackgroundColor(Cinema.BG)
        val side = p(140)

        // List mode: title, the three buttons, then the rows.
        listMode.setPadding(side, p(90), side, 0)
        listMode.addView(Cinema.text(activity, "Customise ${page.label}", 64, Cinema.CREAM, serif = true, tracking = -0.01f))
        listMode.addView(Cinema.keyHints(activity, listOf("OK" to "Pick up a row, then ▲▼ to move it, OK to drop", "▶" to "Remove", "BACK" to "Done")),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(14) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        header.forEachIndexed { i, b -> buttons.addView(b, LinearLayout.LayoutParams(-2, p(76)).apply { if (i > 0) marginStart = p(24) }) }
        listMode.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(36) })
        listMode.addView(rowsScroll, LinearLayout.LayoutParams(-1, -1).apply { topMargin = p(24) })
        addButton.setOnClickListener { openPicker() }
        resetButton.setOnClickListener { sections.clear(); sections += PageLayouts.defaults(page); moving = -1; save(); drawRows(); focusBody(0) }
        doneButton.setOnClickListener { close() }
        addView(listMode, LayoutParams(-1, -1))

        // Pick mode: the keyboard on the left under what is typed, the matching rows on the right.
        pickMode.setPadding(side, p(90), side, 0)
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        left.addView(Cinema.text(activity, "Add a row to ${page.label}", 52, Cinema.CREAM, serif = true))
        left.addView(query, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(30) })
        left.addView(View(activity).apply { setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.25f)) }, LinearLayout.LayoutParams(-1, p(2)).apply { topMargin = p(6) })
        left.addView(keyboard, LinearLayout.LayoutParams(p(980), p(476)).apply { topMargin = p(40) })
        left.addView(Cinema.keyHints(activity, listOf("▲" to "From the top keys: the results", "BACK" to "Back to the rows")),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(30) })
        pickMode.addView(left, LinearLayout.LayoutParams(p(1000), -1))
        pickMode.addView(resultsScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = p(60); topMargin = p(10) })
        keyboard.upTarget = { results.firstOrNull() }
        keyboard.onLeave = { closePicker() }
        addView(pickMode, LayoutParams(-1, -1))

        drawRows()
    }

    private fun lit(view: TextView, focused: Boolean, accent: Boolean = false) {
        view.background = GradientDrawable().apply {
            cornerRadius = Cinema.pxf(activity, 18)
            setColor(when { accent -> Cinema.SAFFRON; focused -> Cinema.CREAM; else -> Cinema.alpha(Cinema.CREAM, 0.06f) })
        }
        view.setTextColor(if (focused || accent) Cinema.INK else Cinema.CREAM)
    }

    private fun rowText(text: String, sub: String?): TextView =
        Cinema.text(activity, if (sub == null) text else "$text   ·   $sub", 30, Cinema.CREAM, weight = 500).apply {
            maxLines = 1; isFocusable = true; isClickable = true
            setPadding(p(30), p(22), p(30), p(22))
        }

    private fun drawRows() {
        rowsBox.removeAllViews(); bodies.clear(); removes.clear()
        if (sections.isEmpty()) rowsBox.addView(Cinema.text(activity, "No rows. Add one, or reset to the default.", 26, Cinema.MUTED))
        sections.forEachIndexed { i, s ->
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
            val body = rowText("${i + 1}.  ${PageLayouts.label(s)}", null)
            val paint = { lit(body, body.isFocused, accent = moving == i) }
            body.setOnFocusChangeListener { _, _ -> paint() }
            body.setOnClickListener { moving = if (moving == i) -1 else i; drawRows(); focusBody(i) }
            paint()
            val remove = CinemaButton(activity, CinemaButton.Kind.DANGER, "Remove", R.drawable.ic_cin_trash, small = true)
            remove.setOnClickListener { sections.removeAt(i); moving = -1; save(); drawRows(); if (sections.isEmpty()) addButton.requestFocus() else focusBody(minOf(i, sections.size - 1)) }
            line.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(remove, LinearLayout.LayoutParams(-2, p(64)).apply { marginStart = p(24) })
            rowsBox.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = p(12) })
            bodies += body; removes += remove
        }
    }

    private fun focusBody(i: Int) { bodies.getOrNull(i)?.let { b -> b.requestFocus(); b.post { b.requestRectangleOnScreen(android.graphics.Rect(0, -p(40), b.width, b.height + p(40))) } } }

    private fun save() { changed = true; onSave(sections.toList()) }

    // --- Picking a row to add ---------------------------------------------------------------------------------

    private fun openPicker() {
        moving = -1; typed = ""
        listMode.visibility = GONE; pickMode.visibility = VISIBLE
        showResults()
        keyboard.post { keyboard.keys.firstOrNull()?.requestFocus() }
    }

    private fun closePicker() {
        pickMode.visibility = GONE; listMode.visibility = VISIBLE
        drawRows(); addButton.requestFocus()
    }

    private fun showResults() {
        query.text = typed.ifEmpty { "Type to search: Top 10, Netflix, horror, library…" }
        query.setTextColor(if (typed.isEmpty()) Cinema.DIM else Cinema.CREAM)
        resultsBox.removeAllViews(); results.clear()
        val found = PageLayouts.search(typed, sections).take(40)
        if (found.isEmpty()) resultsBox.addView(Cinema.text(activity, "Nothing matches \"$typed\"", 26, Cinema.MUTED))
        found.forEach { o ->
            val r = rowText(o.label, o.detail)
            r.setOnFocusChangeListener { v, f -> lit(r, f); if (f) v.post { v.requestRectangleOnScreen(android.graphics.Rect(0, -p(40), v.width, v.height + p(40))) } }
            r.setOnClickListener {
                sections += o.section; save()
                pickMode.visibility = GONE; listMode.visibility = VISIBLE
                drawRows(); focusBody(sections.size - 1)
            }
            lit(r, false)
            resultsBox.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = p(12) })
            results += r
        }
    }

    // --- Keys --------------------------------------------------------------------------------------------------

    /** Back: out of the search, or put a picked-up row down, or close. */
    fun back() {
        when {
            pickMode.visibility == VISIBLE -> closePicker()
            moving >= 0 -> { val i = moving; moving = -1; drawRows(); focusBody(i) }
            else -> close()
        }
    }

    private fun close() { onClose(changed) }

    /** Gives focus to the first thing here when it has gone astray. */
    fun focusFirst() { if (pickMode.visibility == VISIBLE) keyboard.keys.firstOrNull()?.requestFocus() else (bodies.firstOrNull() ?: addButton).requestFocus() }

    /** A D-pad move while the editor is open. Always handled: nothing behind the editor ever takes focus. */
    fun move(focused: View, direction: Int) {
        if (pickMode.visibility == VISIBLE) {
            val r = results.indexOf(focused)
            when {
                r >= 0 -> when (direction) {
                    View.FOCUS_UP -> results.getOrNull(r - 1)?.requestFocus()
                    View.FOCUS_DOWN -> results.getOrNull(r + 1)?.requestFocus()
                    View.FOCUS_LEFT -> keyboard.keys.firstOrNull()?.requestFocus()
                }
                isInside(focused, keyboard) -> focused.focusSearch(direction)?.takeIf { isInside(it, this) }?.requestFocus(direction)
                else -> focusFirst()
            }
            return
        }
        val h = header.indexOf(focused)
        val b = bodies.indexOf(focused)
        val x = removes.indexOf(focused)
        when {
            h >= 0 -> when (direction) {
                View.FOCUS_LEFT -> header.getOrNull(h - 1)?.requestFocus()
                View.FOCUS_RIGHT -> header.getOrNull(h + 1)?.requestFocus()
                View.FOCUS_DOWN -> focusBody(0)
            }
            b >= 0 && moving == b -> when (direction) {
                View.FOCUS_UP, View.FOCUS_DOWN -> {
                    val to = if (direction == View.FOCUS_UP) b - 1 else b + 1
                    if (to in sections.indices) {
                        java.util.Collections.swap(sections, b, to); moving = to; save(); drawRows(); focusBody(to)
                    }
                }
            }
            b >= 0 -> when (direction) {
                View.FOCUS_UP -> if (b == 0) addButton.requestFocus() else focusBody(b - 1)
                View.FOCUS_DOWN -> focusBody(b + 1)
                View.FOCUS_RIGHT -> removes[b].requestFocus()
            }
            x >= 0 -> when (direction) {
                View.FOCUS_LEFT -> bodies[x].requestFocus()
                View.FOCUS_UP -> if (x == 0) doneButton.requestFocus() else removes[x - 1].requestFocus()
                View.FOCUS_DOWN -> removes.getOrNull(x + 1)?.requestFocus()
            }
            else -> focusFirst()
        }
    }

    private fun isInside(v: View, group: View): Boolean {
        var p: Any? = v
        while (p is View) { if (p === group) return true; p = p.parent }
        return false
    }
}
