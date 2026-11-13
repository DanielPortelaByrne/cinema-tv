package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.AppTheme

/**
 * The light focus treatment for busy screens (poster rows, the programme guide), where the shared one is too costly:
 * that one starts a colour animator for every label and lifts the focused card on a shadow, and a row of twenty
 * posters repeats that at every key press. This one swaps the surface and the label colours and nothing else.
 */
object FastFocus {
    /** A pair of surfaces for one corner radius, cheap to hand out because each view gets a copy of the shared state. */
    class Surfaces(context: Context, radiusPx: Float) {
        private val restState = GradientDrawable().apply { cornerRadius = radiusPx; setColor(0) }.constantState!!
        private val litState = AppTheme.tileBackgroundFocused(context, radiusPx).constantState!!
        fun rest(): Drawable = restState.newDrawable().mutate()
        fun lit(): Drawable = litState.newDrawable().mutate()
    }

    /**
     * Lights [view] ivory when focused, with [labels] turned to ink. [rest] is the resting surface (null for none).
     * Label colours are read once, now, so bind after the labels are styled.
     */
    fun bind(view: View, surfaces: Surfaces, labels: List<TextView>, rest: Drawable? = null, onChange: ((Boolean) -> Unit)? = null) {
        val ink = view.context.getColor(R.color.ink)
        val restColours = labels.map { it.currentTextColor }
        val restSurface = rest ?: surfaces.rest()
        val litSurface = surfaces.lit()
        view.background = restSurface
        view.setOnFocusChangeListener { v, focused ->
            v.background = if (focused) litSurface else restSurface
            labels.forEachIndexed { i, t -> t.setTextColor(if (focused) ink else restColours[i]) }
            onChange?.invoke(focused)
        }
    }

    /** Keeps [view] inside its rows and page by gliding, with the same behaviour for every card: no jumps, no overshoot. */
    fun reveal(view: View, page: ScrollView?, topInset: Int, bottomInset: Int, sideMargin: Int) {
        view.post {
            if (!view.isAttachedToWindow) return@post
            // Title details have their own ScrollView, beside the main page. Resolve actual ancestors:
            // offsetDescendantRectToMyCoords throws if the supplied page does not contain this view.
            var horizontal: HorizontalScrollView? = null
            var vertical: ScrollView? = null
            var p = view.parent
            while (p != null) {
                if (horizontal == null && p is HorizontalScrollView) horizontal = p
                if (vertical == null && p is ScrollView) vertical = p
                p = p.parent
            }
            horizontal?.let { glideSideways(view, it, sideMargin) }
            // The Cinema page moves itself, on one glide; only other scroll views (a title's details) are moved from here.
            if (vertical is CinemaContentScrollView) vertical.reveal(view)
            else vertical?.let { glideDown(view, it, if (it === page) topInset else 0, bottomInset, headingOf(horizontal)) }
        }
    }

    /**
     * The heading above a row's scroller, when there is one: a row's heading is the view just before its scroller in the
     * column. Null for a scroller that is first in its column, or when the view before it is another row.
     */
    private fun headingOf(scroller: View?): View? {
        val parent = scroller?.parent as? ViewGroup ?: return null
        val i = parent.indexOfChild(scroller)
        if (i <= 0) return null
        val before = parent.getChildAt(i - 1)
        return if (before is HorizontalScrollView) null else before
    }

    /**
     * Brings [view] to the middle of its row, so a card or a title is met where the eye rests instead of being dragged
     * along to the edge. The row stops at its ends, so the first and last items sit as near the middle as they can.
     */
    private fun glideSideways(view: View, scroller: HorizontalScrollView, margin: Int) {
        val r = Rect(0, 0, view.width, view.height)
        scroller.offsetDescendantRectToMyCoords(view, r)
        val content = scroller.getChildAt(0)
        val maxScroll = maxOf(0, (content?.width ?: 0) - scroller.width + scroller.paddingLeft + scroller.paddingRight)
        val target = (r.centerX() - scroller.width / 2).coerceIn(0, maxScroll)
        if (Math.abs(target - scroller.scrollX) < 2) return
        scroller.smoothScrollTo(target, 0)
    }

    private fun glideDown(view: View, page: ScrollView, topInset: Int, bottomInset: Int, heading: View?) {
        val r = Rect(0, 0, view.width, view.height)
        page.offsetDescendantRectToMyCoords(view, r)
        // Moving up, the row's heading must stay on screen with its cards: the top is the heading's when it has one.
        val upTop = heading?.let { h ->
            val hr = Rect(0, 0, h.width, h.height)
            page.offsetDescendantRectToMyCoords(h, hr)
            minOf(hr.top, r.top)
        } ?: r.top
        val top = page.scrollY
        val bottom = top + page.height
        val target = when {
            upTop - topInset < top -> upTop - topInset
            r.bottom + bottomInset > bottom -> r.bottom + bottomInset - page.height
            else -> return
        }.coerceAtLeast(0)
        // Moving up to a row near the top, go all the way up: otherwise the page can stop short of its first row and look
        // stuck. A row further down that only needs its bottom shown must not snap to the top, so this is checked on where
        // the item is now, not on the scroll target alone.
        val nearTop = 160 * page.resources.displayMetrics.density
        val snapped = if (target < nearTop && upTop - topInset < nearTop) 0 else target
        page.smoothScrollTo(0, snapped)
    }

    /** Child views of [group] that are directly attached, newest last (used to find rows by position). */
    fun childrenOf(group: ViewGroup): List<View> = (0 until group.childCount).map { group.getChildAt(it) }
}
