package com.danielportelabyrne.cinema.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.movies.LinkFilm
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import com.danielportelabyrne.cinema.design.cinema.PosterCard

/**
 * The Film Link start menu: what appears when the Easter egg is turned on from a film's info screen. A game's title
 * screen in the Cinema language: a marquee of lights, film-strip edges, the title in Cinema's serif over its stripe
 * ribbon, a start-and-finish pair of posters that changes with the choices (each with a line about its film, so one you
 * don't know can still be imagined), and option rows with Play at the foot.
 *
 * "Random film" is a button with a shuffle icon: it runs through random posters on that card and lands on a film, and
 * pressing it again reshuffles. Play is focused, so OK alone starts a game with the defaults (start here, random
 * finish, no clock). Back cancels.
 */
class FilmLinkMenu(
    private val activity: Activity,
    private val host: ViewGroup,
    /** The film whose page the game was turned on from; null when that is not a film, so the only start is a random one. */
    private val here: Here?,
    /** Popular films to shuffle through; calls back on the UI thread, with none when Seerr can't be reached. */
    private val loadSeeds: ((List<LinkFilm>) -> Unit) -> Unit,
    /**
     * [start] is the film a shuffle landed on for the start (null: this film when [startedHere], else a random one found
     * when the game starts); [finish] likewise for the finish; [choose] sends the player to Search for their finish.
     */
    private val onPlay: (start: LinkFilm?, startedHere: Boolean, finish: LinkFilm?, choose: Boolean, timed: Boolean) -> Unit,
    private val onCancel: () -> Unit
) {
    class Here(val name: String, val year: Int?, val posterUrl: String?, val posterHeaders: Map<String, String>, val tmdbId: Long? = null, val overview: String? = null)

    private val images = RemoteImages.shared(activity)
    private val tag = Any()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var startHere = here != null
    private var choose = false
    private var timed = false
    private var seeds: List<LinkFilm> = emptyList()
    private var startLanded: LinkFilm? = null
    private var finishLanded: LinkFilm? = null
    private var spinStart = 0
    private var spinFinish = 0
    private var spinningStart = false
    private var spinningFinish = false

    val isShowing: Boolean get() = root != null

    private fun posterOf(f: LinkFilm): String? = f.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

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

        val startCard = Card("Start", Cinema.SAFFRON)
        val finishCard = Card("Finish", Cinema.TEAL)
        val versus = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; clipChildren = false; clipToPadding = false }
        versus.addView(startCard.root)
        versus.addView(Cinema.text(activity, "→", 80, Cinema.TEAL, serif = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 36); marginEnd = Cinema.px(activity, 36); topMargin = Cinema.px(activity, 190) })
        versus.addView(finishCard.root)
        left.addView(versus, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        r.addView(left, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 90) })

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
            if (!spinningStart) {
                val landed = startLanded
                when {
                    startHere && here != null -> startCard.film(here.name, here.year, here.overview, here.posterUrl, here.posterHeaders)
                    landed != null -> startCard.film(landed.name, landed.year, landed.overview, posterOf(landed), emptyMap())
                    else -> startCard.mystery("A random film")
                }
            }
            if (!spinningFinish) {
                val landed = finishLanded
                when {
                    choose -> finishCard.mystery("You’ll pick it in Search")
                    landed != null -> finishCard.film(landed.name, landed.year, landed.overview, posterOf(landed), emptyMap())
                    else -> finishCard.mystery("A random film")
                }
            }
        }
        /** Runs through posters on a card, then lands on a film (never the one on the other card, nor this page's). */
        fun shuffle(forStart: Boolean) {
            if (seeds.isEmpty()) { CinemaToast.show(activity, "Couldn’t fetch films to shuffle. Check Seerr and try again.", Toast.LENGTH_LONG); return }
            val card = if (forStart) startCard else finishCard
            val token: Int
            if (forStart) { startHere = false; startLanded = null; token = ++spinStart; spinningStart = true }
            else { choose = false; finishLanded = null; token = ++spinFinish; spinningFinish = true }
            repaints.forEach { it() }
            fun step(i: Int) {
                if (root == null || (if (forStart) spinStart else spinFinish) != token) return
                if (i < 20) { val shown = seeds.random(); card.spin(shown, posterOf(shown)); ui.postDelayed({ step(i + 1) }, 50L + i * i / 3) }
                else {
                    val avoid = setOfNotNull(here?.tmdbId, if (forStart) finishLanded?.tmdbId else startLanded?.tmdbId)
                    val pick = seeds.filter { it.tmdbId !in avoid }.ifEmpty { seeds }.random()
                    if (forStart) { startLanded = pick; spinningStart = false } else { finishLanded = pick; spinningFinish = false }
                    refresh()
                }
            }
            step(0)
        }
        val shuffleIcon = R.drawable.ic_cin_shuffle
        if (here != null) repaints += row(panel, "Start", listOf("This film" to null, "Random film" to shuffleIcon), { if (startHere) 0 else 1 }) {
            if (it == 0) { spinStart++; spinningStart = false; startHere = true; refresh() } else shuffle(true)
        } else repaints += row(panel, "Start", listOf("Random film" to shuffleIcon), { 0 }) { shuffle(true) }
        repaints += row(panel, "Finish", listOf("A film I choose" to null, "Random film" to shuffleIcon), { if (choose) 0 else 1 }) {
            if (it == 0) { spinFinish++; spinningFinish = false; choose = true; finishLanded = null; refresh() } else shuffle(false)
        }
        repaints += row(panel, "Clock", listOf("No clock" to null, "Timed" to null), { if (timed) 1 else 0 }) { timed = it == 1; refresh() }
        val play = CinemaButton(activity, CinemaButton.Kind.TERRA, "Play", R.drawable.ic_cin_play).also {
            it.setOnClickListener {
                spinStart++; spinFinish++
                onPlay(if (startHere) null else startLanded, startHere && here != null, if (choose) null else finishLanded, choose, timed)
            }
        }
        panel.addView(play, LinearLayout.LayoutParams(-1, Cinema.px(activity, 84)).apply { topMargin = Cinema.px(activity, 44) })
        val cancel = CinemaButton(activity, CinemaButton.Kind.GLASS, "Cancel").also { it.setOnClickListener { onCancel() } }
        panel.addView(cancel, LinearLayout.LayoutParams(-1, Cinema.px(activity, 68)).apply { topMargin = Cinema.px(activity, 16) })
        r.addView(panel, FrameLayout.LayoutParams(Cinema.px(activity, 800), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = Cinema.px(activity, 96) })

        refresh()
        host.addView(r, FrameLayout.LayoutParams(-1, -1))
        root = r
        play.requestFocus()
        // The films to shuffle through, fetched now so the first spin doesn't wait; their posters are warmed too.
        loadSeeds { list ->
            if (root == null) return@loadSeeds
            seeds = list
            list.take(24).forEach { f -> posterOf(f)?.let { images.loadShadedPoster(it, emptyMap(), 332, tag) { } } }
        }
    }

    fun dismiss() {
        spinStart++; spinFinish++
        ui.removeCallbacksAndMessages(null)
        images.cancel(tag)
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        root = null
    }

    // ---- pieces --------------------------------------------------------------------------------------------

    /** One side of the pair: a poster (or a "?"), the film's name and year, and a few lines about it. */
    private inner class Card(label: String, color: Int) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            addView(Cinema.text(activity, label, 21, color, weight = 700, tracking = 0.2f, caps = true))
        }
        private val poster = PosterCard(activity, 220, 330, titled = false).apply { isFocusable = false; isClickable = false }
        private val mysteryView = mysteryCard()
        private val name = Cinema.text(activity, "", 26, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val year = Cinema.text(activity, "", 19, Cinema.MUTED).apply { maxLines = 1 }
        private val about = Cinema.text(activity, "", 17, Cinema.READING).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.2f) }
        private var version = 0

        init {
            val frame = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
            frame.addView(poster, FrameLayout.LayoutParams(-2, -2))
            frame.addView(mysteryView, FrameLayout.LayoutParams(Cinema.px(activity, 220), Cinema.px(activity, 330)))
            root.addView(frame, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
            root.addView(name, LinearLayout.LayoutParams(Cinema.px(activity, 300), -2).apply { topMargin = Cinema.px(activity, 14) })
            root.addView(year, LinearLayout.LayoutParams(Cinema.px(activity, 300), -2).apply { topMargin = Cinema.px(activity, 2) })
            root.addView(about, LinearLayout.LayoutParams(Cinema.px(activity, 300), Cinema.px(activity, 96)).apply { topMargin = Cinema.px(activity, 6) })
        }

        private fun load(url: String?, headers: Map<String, String>, label: String) {
            val my = ++version
            mysteryView.visibility = View.GONE; poster.visibility = View.VISIBLE
            if (url == null) poster.placeholder(label) else images.loadShadedPoster(url, headers, 332, tag) { b -> if (my == version) poster.setShadedPoster(b) }
        }

        fun film(title: String, y: Int?, overview: String?, posterUrl: String?, headers: Map<String, String>) {
            load(posterUrl, headers, title)
            name.text = title; year.text = y?.toString().orEmpty(); about.text = overview.orEmpty()
        }

        /** A poster flicking by: no words until it lands. */
        fun spin(shown: LinkFilm, posterUrl: String?) {
            load(posterUrl, emptyMap(), shown.name)
            name.text = "Shuffling…"; year.text = ""; about.text = ""
        }

        fun mystery(text: String) {
            version++
            poster.visibility = View.GONE; mysteryView.visibility = View.VISIBLE
            name.text = text; year.text = ""; about.text = ""
        }
    }

    /**
     * One labelled row of choices: [selected] says which is on; choosing one calls [pick] (a choice with an icon is a
     * button that can be pressed again, like the shuffle). Returns what repaints the row.
     */
    private fun row(panel: LinearLayout, label: String, options: List<Pair<String, Int?>>, selected: () -> Int, pick: (Int) -> Unit): () -> Unit {
        panel.addView(Cinema.text(activity, label, 20, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val chips = options.mapIndexed { i, (text, icon) ->
            CinemaButton(activity, CinemaButton.Kind.GLASS, text, icon, small = true).also { b ->
                b.setOnClickListener { pick(i) }
                line.addView(b, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { if (i > 0) marginStart = Cinema.px(activity, 16) })
            }
        }
        panel.addView(line, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 12) })
        return {
            chips.forEachIndexed { i, b ->
                val (text, icon) = options[i]
                val on = i == selected()
                b.setText(if (icon != null) text + (if (on) "  ✓" else "") else (if (on) "✓  " else "") + text)
            }
        }
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
