package com.danielportelabyrne.cinema.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.PosterCard

/**
 * The Film Link start menu: what appears when the Easter egg is turned on from a film's info screen. A game's title
 * screen in the Cinema language: a marquee of lights, film-strip edges, the title in Cinema's serif over its stripe
 * ribbon, a start-and-finish pair of posters that changes with the choices, and option rows with Play at the foot.
 * Play is focused, so OK alone starts a game with the defaults (start here, random finish, no clock). Back cancels.
 */
class FilmLinkMenu(
    private val activity: Activity,
    private val host: ViewGroup,
    /** The film whose page the game was turned on from; null when that is not a film, so the only start is a random one. */
    private val here: Here?,
    private val onPlay: (startHere: Boolean, choose: Boolean, timed: Boolean) -> Unit,
    private val onCancel: () -> Unit
) {
    class Here(val name: String, val year: Int?, val posterUrl: String?, val posterHeaders: Map<String, String>)

    private val images = RemoteImages.shared(activity)
    private val tag = Any()
    private var root: FrameLayout? = null
    private var startHere = here != null
    private var choose = false
    private var timed = false

    val isShowing: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val r = FrameLayout(activity).apply { setBackgroundColor(Cinema.BG); isClickable = true; clipChildren = false; clipToPadding = false }
        // The house lights: a warm glow behind the title and a cool one behind the menu.
        r.addView(glow(0.16f, 0.22f, Cinema.alpha(Cinema.TERRA, 0.30f)), FrameLayout.LayoutParams(-1, -1))
        r.addView(glow(0.88f, 0.80f, Cinema.alpha(Cinema.TEAL, 0.26f)), FrameLayout.LayoutParams(-1, -1))
        // Film-strip perforations down both edges, and a marquee of lights along the top and the foot.
        r.addView(sprockets(), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply { leftMargin = Cinema.px(activity, 28); topMargin = Cinema.px(activity, 84) })
        r.addView(sprockets(), FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { rightMargin = Cinema.px(activity, 28); topMargin = Cinema.px(activity, 84) })
        val lights = ArrayList<View>()
        r.addView(marquee(lights), FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = Cinema.px(activity, 26) })
        r.addView(marquee(lights), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = Cinema.px(activity, 26) })
        startChase(lights)

        // ---- the title, and the pair of posters under it
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        left.addView(Cinema.eyebrow(activity, listOf("Now showing"), mark = true))
        left.addView(Cinema.text(activity, "Film Link", 150, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 1; setShadowLayer(Cinema.pxf(activity, 28), 0f, Cinema.pxf(activity, 4), 0xAA000000.toInt()) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 8) })
        left.addView(ribbon(), LinearLayout.LayoutParams(Cinema.px(activity, 330), -2).apply { topMargin = Cinema.px(activity, 10) })
        left.addView(Cinema.text(activity, "Get from one film to another, one connection at a time.", 28, Cinema.READING).apply { maxWidth = Cinema.px(activity, 820); setLineSpacing(0f, 1.2f) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })

        val start = side("Start", Cinema.SAFFRON)
        val finish = side("Finish", Cinema.TEAL)
        val startPoster = posterOrMystery()
        val finishPoster = mysteryCard()
        start.addView(startPoster, 1, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
        finish.addView(finishPoster, 1, LinearLayout.LayoutParams(Cinema.px(activity, 220), Cinema.px(activity, 330)).apply { topMargin = Cinema.px(activity, 14) })
        val startCaption = caption(); val finishCaption = caption()
        start.addView(startCaption, LinearLayout.LayoutParams(Cinema.px(activity, 300), -2).apply { topMargin = Cinema.px(activity, 14) })
        finish.addView(finishCaption, LinearLayout.LayoutParams(Cinema.px(activity, 300), -2).apply { topMargin = Cinema.px(activity, 14) })
        val versus = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; clipChildren = false; clipToPadding = false }
        versus.addView(start)
        versus.addView(Cinema.text(activity, "→", 80, Cinema.TEAL, serif = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 36); marginEnd = Cinema.px(activity, 36); topMargin = Cinema.px(activity, 190) })
        versus.addView(finish)
        left.addView(versus, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 34) })
        r.addView(left, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 96) })

        // ---- the menu
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            background = Cinema.glass(activity, 30f, 0xE6140E0B.toInt())
            setPadding(Cinema.px(activity, 48), Cinema.px(activity, 40), Cinema.px(activity, 48), Cinema.px(activity, 44))
        }
        panel.addView(Cinema.text(activity, "Set up your run", 22, Cinema.SAFFRON, weight = 700, tracking = 0.2f, caps = true))
        val repaints = ArrayList<() -> Unit>()
        fun refresh() {
            repaints.forEach { it() }
            startPoster.getChildAt(0).visibility = if (startHere) View.VISIBLE else View.GONE
            startPoster.getChildAt(1).visibility = if (startHere) View.GONE else View.VISIBLE
            startCaption.text = if (startHere && here != null) listOfNotNull(here.name, here.year?.toString()).joinToString(" · ") else "A random film"
            finishCaption.text = if (choose) "You’ll pick it in Search" else "A random film"
        }
        if (here != null) repaints += row(panel, "Start", listOf("This film", "A random film"), { if (startHere) 0 else 1 }) { startHere = it == 0; refresh() }
        repaints += row(panel, "Finish", listOf("A random film", "A film I choose"), { if (choose) 1 else 0 }) { choose = it == 1; refresh() }
        repaints += row(panel, "Clock", listOf("No clock", "Timed"), { if (timed) 1 else 0 }) { timed = it == 1; refresh() }
        val play = CinemaButton(activity, CinemaButton.Kind.TERRA, "Play", R.drawable.ic_cin_play).also { it.setOnClickListener { onPlay(startHere, choose, timed) } }
        panel.addView(play, LinearLayout.LayoutParams(-1, Cinema.px(activity, 84)).apply { topMargin = Cinema.px(activity, 44) })
        val cancel = CinemaButton(activity, CinemaButton.Kind.GLASS, "Cancel").also { it.setOnClickListener { onCancel() } }
        panel.addView(cancel, LinearLayout.LayoutParams(-1, Cinema.px(activity, 68)).apply { topMargin = Cinema.px(activity, 16) })
        r.addView(panel, FrameLayout.LayoutParams(Cinema.px(activity, 800), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = Cinema.px(activity, 96) })

        refresh()
        host.addView(r, FrameLayout.LayoutParams(-1, -1))
        root = r
        play.requestFocus()
    }

    fun dismiss() {
        ui.removeCallbacks(chaseTick)
        images.cancel(tag)
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        root = null
    }

    // ---- pieces --------------------------------------------------------------------------------------------

    /** One labelled row of choices: [selected] says which is on; choosing one calls [pick]. Returns what repaints the row. */
    private fun row(panel: LinearLayout, label: String, options: List<String>, selected: () -> Int, pick: (Int) -> Unit): () -> Unit {
        panel.addView(Cinema.text(activity, label, 20, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val chips = options.mapIndexed { i, text ->
            CinemaButton(activity, CinemaButton.Kind.GLASS, text, small = true).also { b ->
                b.setOnClickListener { pick(i) }
                line.addView(b, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { if (i > 0) marginStart = Cinema.px(activity, 16) })
            }
        }
        panel.addView(line, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 12) })
        return { chips.forEachIndexed { i, b -> b.setText((if (i == selected()) "✓  " else "") + options[i]) } }
    }

    private fun side(label: String, color: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
        addView(Cinema.text(activity, label, 21, color, weight = 700, tracking = 0.2f, caps = true))
    }

    private fun caption(): TextView = Cinema.text(activity, "", 26, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }

    /** The start poster (the film itself) over its "?" stand-in; the first is shown when starting here. */
    private fun posterOrMystery(): FrameLayout {
        val box = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val card = PosterCard(activity, 220, 330, titled = false).apply { isFocusable = false; isClickable = false }
        if (here?.posterUrl == null) card.placeholder(here?.name ?: "") else images.loadShadedPoster(here.posterUrl, here.posterHeaders, 332, tag) { b -> card.setShadedPoster(b) }
        box.addView(card, 0, FrameLayout.LayoutParams(-2, -2))
        box.addView(mysteryCard(), 1, FrameLayout.LayoutParams(Cinema.px(activity, 220), Cinema.px(activity, 330)))
        return box
    }

    /** A poster-shaped "?": a film to be drawn at random, or chosen later. */
    private fun mysteryCard(): FrameLayout = FrameLayout(activity).apply {
        background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 16); setColor(Cinema.PLACEHOLDER) }
        addView(Cinema.text(activity, "?", 150, Cinema.alpha(Cinema.CREAM, 0.55f), serif = true, weight = 600).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        addView(ribbon(thick = 10), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(Cinema.px(activity, 20), 0, Cinema.px(activity, 20), Cinema.px(activity, 22)) })
    }

    /** Cinema's stripe motif as a short ribbon of five bars. */
    private fun ribbon(thick: Int = 9): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        intArrayOf(Cinema.STRIPE_SAFFRON, Cinema.STRIPE_ORANGE, Cinema.STRIPE_TERRA, Cinema.STRIPE_RED, Cinema.STRIPE_MAROON).forEachIndexed { i, c ->
            addView(View(activity).apply { background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 5); setColor(c) } },
                LinearLayout.LayoutParams(0, Cinema.px(activity, thick), 1f).apply { if (i > 0) marginStart = Cinema.px(activity, 5) })
        }
    }

    private fun glow(cx: Float, cy: Float, color: Int): View = View(activity).apply {
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = Cinema.pxf(activity, 1000)
            setGradientCenter(cx, cy); colors = intArrayOf(color, 0)
        }
        isFocusable = false
    }

    /** The film's perforations: a column of small rounded holes. */
    private fun sprockets(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; isFocusable = false
        repeat(20) { i ->
            addView(View(activity).apply { background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 6); setColor(Cinema.alpha(Cinema.CREAM, 0.09f)) } },
                LinearLayout.LayoutParams(Cinema.px(activity, 18), Cinema.px(activity, 28)).apply { if (i > 0) topMargin = Cinema.px(activity, 22) })
        }
    }

    /** A row of theatre-marquee bulbs; [into] collects them so they can chase. */
    private fun marquee(into: MutableList<View>): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; isFocusable = false
        repeat(34) {
            val bulb = View(activity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.SAFFRON) } }
            into += bulb
            addView(bulb, LinearLayout.LayoutParams(Cinema.px(activity, 14), Cinema.px(activity, 14)).apply { marginStart = Cinema.px(activity, 20); marginEnd = Cinema.px(activity, 20) })
        }
    }

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var bulbs: List<View> = emptyList()
    private var phase = 0
    private val chaseTick = object : Runnable {
        override fun run() { phase = 1 - phase; paintBulbs(); ui.postDelayed(this, 650) }
    }
    private fun paintBulbs() = bulbs.forEachIndexed { i, b -> b.alpha = if ((i + phase) % 2 == 0) 0.95f else 0.28f }

    /** Alternate bulbs light in turn, as on a marquee; still when the device asks for less motion. */
    private fun startChase(lights: List<View>) {
        bulbs = lights; phase = 0; paintBulbs()
        val anchor = lights.firstOrNull() ?: return
        if (Motion.animationsOn(anchor)) ui.postDelayed(chaseTick, 650)
    }
}
