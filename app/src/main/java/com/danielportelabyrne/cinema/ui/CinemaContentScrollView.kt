package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ScrollView

/**
 * The Cinema page's focus rules. Left at the edge of a row returns to the active rail item, rather than the nearest
 * unrelated one; Up and Down stay on the page (they never jump sideways into the rail), and at the page's ends the
 * key is simply absorbed.
 */
class CinemaContentScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    var navigationTarget: (() -> View?)? = null

    /**
     * Whatever takes focus on this page is glided fully into view, rows included, the same way poster cards are. The
     * default scroll only moves as far as the edge of the card, which left a row half-visible under a hero's button.
     */
    /**
     * Some focus moves (along a poster row) never reach this page's own focus callbacks, so the page listens for every
     * focus change in its window and keeps whichever one lands inside it in view.
     */
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now ->
        if (now != null && now.isShown && isInside(now)) glideRowIntoView(now.ancestorRow() ?: now)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        super.onDetachedFromWindow()
    }

    private fun isInside(view: View): Boolean {
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    /**
     * Moves the page only as far as needed to show [view] whole, vertically. Moving left or right inside a row leaves the
     * page where it is, because the row is already on screen; the row's own scroller handles the sideways move.
     */
    private fun glideRowIntoView(view: View) {
        view.post {
            if (!view.isAttachedToWindow) return@post
            // The whole view has to be on screen, with a margin below it. The platform scrolls the page the least it
            // can to show that rectangle, and a move within a row leaves the rectangle where the page already shows it.
            val margin = dp(56)
            val rect = Rect(0, 0, view.width, view.height + margin)
            view.requestRectangleOnScreen(rect, false)
        }
    }

    private fun dp(v: Int) = Math.round(v * resources.displayMetrics.density)

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction == View.FOCUS_LEFT) {
            val inside = FocusFinder.getInstance().findNextFocus(this, focused, direction)
            if (inside != null && focused != null) {
                val from = Rect().also { focused.getDrawingRect(it); offsetDescendantRectToMyCoords(focused, it) }
                val to = Rect().also { inside.getDrawingRect(it); offsetDescendantRectToMyCoords(inside, it) }
                // A hero button above the row is not a left neighbour of its first poster.
                if (from.top < to.bottom && to.top < from.bottom) return inside
            }
            navigationTarget?.invoke()?.takeIf { it.isShown && it.isFocusable }?.let { return it }
        }
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            val next = FocusFinder.getInstance().findNextFocus(this, focused, direction)
            // Up from the topmost item: nothing above can take focus (a page header, the Downloads counts), so show the
            // top of the page instead of leaving it scrolled out of reach.
            if (next == null && direction == View.FOCUS_UP && scrollY > 0) smoothScrollTo(0, 0)
            if (next == null) return focused
            // Entering another row starts at its first card, not at whichever card sits nearest the one we left.
            val row = next.ancestorRow()
            if (row != null && row !== focused?.ancestorRow()) rowStart(row)?.let { return it }
            return next
        }
        return super.focusSearch(focused, direction)
    }

    /** The horizontal row (a poster shelf) this view sits in, if any, below this page. */
    private fun View.ancestorRow(): HorizontalScrollView? {
        var p: ViewParent? = parent
        while (p != null && p !== this@CinemaContentScrollView) {
            if (p is HorizontalScrollView) return p
            p = p.parent
        }
        return null
    }

    /** The first focusable card of [row], the one at its left edge. */
    private fun rowStart(row: HorizontalScrollView): View? {
        val cards = row.getChildAt(0) as? ViewGroup ?: return null
        return (0 until cards.childCount).map { cards.getChildAt(it) }.firstOrNull { it.isFocusable && it.isShown }
    }
}
