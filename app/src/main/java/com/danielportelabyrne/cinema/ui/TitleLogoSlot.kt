package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.danielportelabyrne.cinema.data.movies.TitleLogos
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/**
 * A title heading that shows the title's own logo in place of its name in type, when one arrives and is fit to show.
 * The name in [text] stays until then, and stays for good if there is no logo, it fails to load or it would be drawn
 * too small to read, so the title is never blank and never shown twice. The logo is fitted (not cropped or
 * stretched) inside [boxW] x [boxH] design pixels, left-aligned like the type, and goes through the app's one image
 * loader and cache. Search, focus and navigation keep using the title's text; the logo carries it as its description.
 */
class TitleLogoSlot(context: Context, val text: TextView, boxW: Int, boxH: Int, minHeight: Int = 56) : FrameLayout(context) {
    private val boxWpx = Cinema.px(context, boxW)
    private val boxHpx = Cinema.px(context, boxH)
    private val minHpx = Cinema.px(context, minHeight)
    private val logo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_START; adjustViewBounds = true; visibility = View.GONE
        maxWidth = boxWpx; maxHeight = boxHpx
    }
    private var generation = 0

    init {
        clipChildren = false; clipToPadding = false
        addView(text, LayoutParams(-1, -2))
        addView(logo, LayoutParams(-2, -2, Gravity.START or Gravity.TOP))
    }

    /** Shows the name in type at once, then swaps in the logo for [imdbId] if it can be had. Safe to call again for a new title. */
    fun bind(imdbId: String?, images: RemoteImages, tag: Any?) {
        val mine = ++generation
        logo.animate().cancel(); text.animate().cancel()
        logo.visibility = View.GONE; logo.setImageDrawable(null)
        text.visibility = View.VISIBLE; text.alpha = 1f
        val url = TitleLogos.url(imdbId) ?: return
        var sync = true
        images.loadLogo(url, boxWpx, tag) { b ->
            if (mine != generation || !TitleLogos.usable(b.width, b.height, boxWpx, boxHpx, minHpx)) return@loadLogo
            logo.contentDescription = text.text
            logo.setImageBitmap(b); logo.visibility = View.VISIBLE
            if (!sync && Motion.animationsOn(logo)) {
                logo.alpha = 0f; logo.animate().alpha(1f).setDuration(220).start()
                text.animate().alpha(0f).setDuration(220).withEndAction { if (mine == generation) text.visibility = View.GONE }.start()
            } else text.visibility = View.GONE
        }
        sync = false
    }
}
