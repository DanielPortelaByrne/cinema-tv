package com.danielportelabyrne.cinema.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.danielportelabyrne.cinema.design.Motion

/**
 * The Cinema page's focus rules and its one vertical scroll. Left at the edge of a row returns to the active rail item,
 * rather than the nearest unrelated one; Up and Down stay on the page (they never jump sideways into the rail), and at
 * the page's ends the key is simply absorbed.
 *
 * Only this view moves the page up and down: the platform's own "scroll the focused child into view" is switched off, and
 * every move is one glide of [PageScroll.DURATION_MS] that a newer move retargets from where the page is drawn now.
 */
class CinemaContentScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    var navigationTarget: (() -> View?)? = null
    /** Where Up from the page's topmost item goes (the profile in the top right corner), or null to stay put. */
    var topTarget: (() -> View?)? = null

    /** Motion.GLIDE: the curve PageScroll.CURVE names. */
    private val curve = Motion.GLIDE
    private var glide: ValueAnimator? = null
    private var glideTarget: Int? = null
    /** The card last kept in view, its row and its top, so a sideways move along the row leaves the page still. */
    private var lastRow: HorizontalScrollView? = null
    private var lastTop: Int? = null
    /** The one reveal waiting for the next frame: a newer focus replaces it, so stale work never runs. */
    private var pendingFor: View? = null
    private val pendingReveal = Runnable { pendingFor?.let { v -> pendingFor = null; revealNow(v) } }

    /**
     * Some focus moves (along a poster row) never reach this page's own focus callbacks, so the page listens for every
     * focus change in its window and keeps whichever one lands inside it in view.
     */
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now ->
        if (now != null && now.isShown && isInside(now)) reveal(now)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        stopGlide()
        super.onDetachedFromWindow()
    }

    // --- The one owner of the page's vertical position ---------------------------------------------------------

    /** The platform's own moves (a child taking focus, a rectangle asked for, a resize) are all switched off. */
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect?, immediate: Boolean): Boolean = false

    /** Up and Down are never turned into the platform's own arrow scroll or page jump: focus moves, then this page glides. */
    override fun executeKeyEvent(event: KeyEvent): Boolean =
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) false else super.executeKeyEvent(event)

    /** How far the page can scroll, from its laid-out content. */
    private fun maxScroll(): Int {
        val content = getChildAt(0) ?: return 0
        return maxOf(0, content.height - (height - paddingTop - paddingBottom))
    }

    /** Stops any glide where it is (the page rebuilt, or something set the position outright). */
    fun stopGlide() {
        removeCallbacks(pendingReveal); pendingFor = null
        glide?.cancel(); glide = null; glideTarget = null
    }

    /** Forgets the last card kept in view: the next one is placed afresh (a page rebuilt under it). */
    fun forgetFocus() { lastRow = null; lastTop = null }

    /** Glides the page to [y], clamped to what it can scroll. A glide already on its way there carries on untouched. */
    fun glideTo(y: Int) {
        val target = y.coerceIn(0, maxScroll())
        if (!PageScroll.shouldStart(scrollY, glideTarget.takeIf { glide?.isRunning == true }, target)) return
        glide?.cancel()
        if (target == scrollY) { glide = null; glideTarget = null; return }
        if (!Motion.animationsOn(this)) { glide = null; glideTarget = null; scrollTo(scrollX, target); return }
        // From where the page is drawn now, so a quick run of presses turns smoothly instead of jumping back.
        val from = scrollY
        glideTarget = target
        glide = ValueAnimator.ofInt(from, target).apply {
            duration = PageScroll.DURATION_MS
            interpolator = curve
            addUpdateListener { scrollTo(scrollX, minOf(it.animatedValue as Int, maxScroll())) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { if (glide === animation) { glide = null; glideTarget = null } }
            })
            start()
        }
    }

    /** Keeps [view] (a card, a button) in view, once layout has placed it. Any older waiting reveal is dropped. */
    fun reveal(view: View) {
        pendingFor = view
        removeCallbacks(pendingReveal)
        post(pendingReveal)
    }

    private fun revealNow(view: View) {
        if (!view.isAttachedToWindow || !isAttachedToWindow || !isInside(view)) return
        val content = getChildAt(0) ?: return
        val r = Rect(0, 0, view.width, view.height)
        offsetDescendantRectToMyCoords(view, r)   // layout positions: a card's focus scale and lift do not count
        val row = view.ancestorRow()
        val sideways = PageScroll.sideways(row != null && row === lastRow, lastTop, r.top)
        lastRow = row; lastTop = r.top
        if (sideways || content.height == 0) return
        val heading = row?.let { headingOf(it) }?.takeIf { it.visibility == View.VISIBLE }?.let { h ->
            Rect(0, 0, h.width, h.height).also { offsetDescendantRectToMyCoords(h, it) }.top
        }
        val from = glideTarget.takeIf { glide?.isRunning == true } ?: scrollY
        glideTo(PageScroll.target(from, height, maxScroll(), r.top, r.bottom, heading, dp(PageScroll.TOP_CLEAR_DP), dp(PageScroll.BOTTOM_CLEAR_DP)))
    }

    /**
     * The heading above a row's scroller: the view just before it in its column (not another row). Null for a scroller that
     * is first in its column.
     */
    private fun headingOf(scroller: View): View? {
        val parent = scroller.parent as? ViewGroup ?: return null
        val i = parent.indexOfChild(scroller)
        if (i <= 0) return null
        return parent.getChildAt(i - 1).takeIf { it !is HorizontalScrollView }
    }

    private fun isInside(view: View): Boolean {
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
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
            if (next == null && direction == View.FOCUS_UP && scrollY > 0) glideTo(0)
            if (next == null && direction == View.FOCUS_UP) topTarget?.invoke()?.takeIf { it.isShown && it.isFocusable }?.let { return it }
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

    /**
     * The first focusable card of [row], the one at its left edge. A grid row holds its cards in columns, so a column
     * stands for its top card.
     */
    private fun rowStart(row: HorizontalScrollView): View? {
        val cards = row.getChildAt(0) as? ViewGroup ?: return null
        fun first(v: View): View? = when {
            !v.isShown -> null
            v.isFocusable -> v
            v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { first(v.getChildAt(it)) }
            else -> null
        }
        return (0 until cards.childCount).firstNotNullOfOrNull { first(cards.getChildAt(it)) }
    }
}
