package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.data.stream.LoadingStep
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The stream loading screen (`30-stream-loading-overlay`): the film's backdrop on the right, a stepped ribbon
 * falling from the top left, "Now loading", the title, a status line, a striped bar, the three-step trail (Finding
 * peers, Connecting, Buffering), Seeds / Peers / Speed chips and "Back to cancel".
 *
 * The Movies page shows it while the home server looks for a copy; the player shows the same screen until the first
 * frame, so the hand-over between them is seamless. Only figures the server reports are shown; the rest read "—".
 */
class StreamLoadingView(context: Context, title: String) : FrameLayout(context) {
    private val backdrop = HeroBackdrop(context, focusX = 1f, fadeTo = 0.38f)
    private val status: TextView
    // While nothing is known, the wavy loader; once some of the film has arrived, the wavy bar shows how much.
    private val loader = WaveLoader(context, amplitudePx = 14f, wavelengthPx = 140f, thicknessPx = 20f)
    private val bar = WaveBar(context, amplitudePx = 10f, wavelengthPx = 120f, thicknessPx = 18f).apply { visibility = GONE; lit = true }
    private val steps = LinkedHashMap<LoadingStep, Pair<View, TextView>>()
    private val seeds: TextView
    private val peers: TextView
    private val speed: TextView

    init {
        setBackgroundColor(Cinema.BG)
        isClickable = true
        addView(backdrop, LayoutParams(Cinema.px(context, 1060), -1, Gravity.END))
        addView(View(context).apply {
            background = Scrim(listOf(0f to 1f, 0.2f to 1f, 0.42f to 0.7f, 1f to 0.2f), listOf(0f to 0.3f, 0.3f to 0f, 1f to 0.9f))
        }, LayoutParams(-1, -1))
        addView(RibbonView.loading(context), LayoutParams(-1, -1))

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val eyebrow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        eyebrow.addView(PulseDot(context), LinearLayout.LayoutParams(Cinema.px(context, 26), Cinema.px(context, 26)).apply { marginEnd = Cinema.px(context, 8) })
        eyebrow.addView(Cinema.text(context, "Now loading", 17, Cinema.SAFFRON, weight = 600, tracking = 0.24f, caps = true))
        column.addView(eyebrow)
        column.addView(Cinema.title(context, title, if (title.length > 22) 96 else 132), LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 26) })
        status = Cinema.text(context, "Finding a copy of $title. This can take a minute.", 28, Cinema.READING, weight = 300).apply { maxLines = 2 }
        column.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 28) })
        val progress = FrameLayout(context)
        progress.addView(loader, LayoutParams(-1, -1))
        progress.addView(bar, LayoutParams(-1, -1))
        column.addView(progress, LinearLayout.LayoutParams(Cinema.px(context, 780), Cinema.px(context, 120)).apply { topMargin = Cinema.px(context, 4) })

        val trail = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        LoadingStep.values().forEachIndexed { i, step ->
            val dot = Cinema.dot(context, Cinema.DIM, 10)
            val label = Cinema.text(context, step.label, 21, Cinema.DIM)
            trail.addView(dot, LinearLayout.LayoutParams(Cinema.px(context, 10), Cinema.px(context, 10)).apply { if (i > 0) marginStart = Cinema.px(context, 44) })
            trail.addView(label, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 12) })
            steps[step] = dot to label
        }
        column.addView(trail, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 34) })

        val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(name: String): TextView {
            val value = Cinema.text(context, "—", 20, 0xFFDDD0BD.toInt())
            chips.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(Cinema.px(context, 26), 0, Cinema.px(context, 26), 0)
                background = Cinema.glass(context, 25f)
                addView(Cinema.text(context, name, 20, Cinema.DIM))
                addView(value, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 12) })
            }, LinearLayout.LayoutParams(-2, Cinema.px(context, 50)).apply { if (chips.childCount > 0) marginStart = Cinema.px(context, 16) })
            return value
        }
        seeds = chip("Seeds"); peers = chip("Peers"); speed = chip("Speed")
        column.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 36) })
        addView(column, LayoutParams(Cinema.px(context, 900), -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(context, 112); bottomMargin = Cinema.px(context, 120) })

        addView(Cinema.keyHints(context, listOf("Back" to "to cancel"), 20), LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Cinema.px(context, 96); bottomMargin = Cinema.px(context, 110) })
        step(LoadingStep.FINDING_PEERS)
    }

    fun backdrop(bitmap: Bitmap) = backdrop.show(bitmap)

    fun message(text: String) { status.text = text }

    /** Lights the trail up to [current]: done and current steps saffron, the rest dim. */
    fun step(current: LoadingStep) {
        steps.forEach { (s, views) ->
            val lit = s.ordinal <= current.ordinal
            (views.first.background as? android.graphics.drawable.GradientDrawable)?.setColor(if (lit) Cinema.SAFFRON else Cinema.DIM)
            views.first.alpha = if (lit) 1f else 0.5f
            views.second.setTextColor(if (s == current) Cinema.SAFFRON else if (lit) Cinema.MUTED else Cinema.DIM)
        }
    }

    /** Live figures from the server; null leaves a dash. [progress] fills the bar once some of the film has arrived. */
    fun stats(seedCount: Int?, peerCount: Int?, bytesPerSecond: Long?, progress: Float?) {
        seeds.text = seedCount?.toString() ?: "—"
        peers.text = peerCount?.toString() ?: "—"
        speed.text = LoadingStep.speed(bytesPerSecond) ?: "—"
        val known = progress != null && progress > 0f
        bar.visibility = if (known) VISIBLE else GONE
        loader.visibility = if (known) GONE else VISIBLE
        if (known) bar.progress = progress!!
    }

    /** The saffron dot before "Now loading", with a soft halo that breathes. */
    private class PulseDot(context: Context) : View(context) {
        private val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.SAFFRON }
        private val halo = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            val c = width / 2f
            val t = if (com.danielportelabyrne.cinema.design.Motion.animationsOn(this)) (android.os.SystemClock.uptimeMillis() % 1800L) / 1800f else 0.5f
            val breathe = 0.5f + 0.5f * kotlin.math.sin(t * 2 * Math.PI).toFloat()
            halo.color = Cinema.alpha(Cinema.SAFFRON, 0.12f + 0.14f * breathe)
            canvas.drawCircle(c, c, c, halo)
            canvas.drawCircle(c, c, Cinema.pxf(context, 6), dot)
            if (isAttachedToWindow && isShown) postInvalidateOnAnimation()
        }
    }
}
