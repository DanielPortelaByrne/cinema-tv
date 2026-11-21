package com.danielportelabyrne.cinema.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.data.movies.CachedFilmGraph
import com.danielportelabyrne.cinema.data.movies.FilmLinkPairs
import com.danielportelabyrne.cinema.data.movies.FilmLinkRun
import com.danielportelabyrne.cinema.data.movies.FilmLinkSeeds
import com.danielportelabyrne.cinema.data.movies.HopOutcome
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.LinkFilm
import com.danielportelabyrne.cinema.data.movies.LinkMode
import com.danielportelabyrne.cinema.data.movies.LinkPair
import com.danielportelabyrne.cinema.data.movies.LinkPhase
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.PosterCard
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Film Link (CIN-64 design, CIN-65), as an Easter egg on the film info screens.
 *
 * The game runs on the app's own info screens: each film you hop to is the real info page. This class holds the run
 * (the rules live in the tested [FilmLinkRun]), the connections ([CachedFilmGraph]), the HUD and route trail drawn above
 * the page, the clock, and the summary shown at the end. [MoviesPanel] builds the info pages in a game mode (only the row
 * "More films like this" can be used) and asks this class what to do with each hop and each Back.
 */
class FilmLinkGame(
    private val activity: Activity,
    /** The layer the info page lives in; the HUD and the summary are drawn over it. */
    private val host: ViewGroup,
    val pair: LinkPair,
    val mode: LinkMode,
    /** Whether the player turned the game on from this film's own page, so ending it leaves them on that page. */
    val startedHere: Boolean,
    val graph: CachedFilmGraph,
    /** The start film's poster as the page knew it (url and headers), for when the film's own TMDB poster is missing. */
    private val startPoster: Pair<String, Map<String, String>>? = null
) {
    val run = FilmLinkRun(pair.start, pair.target, mode) { SystemClock.elapsedRealtime() }
    private val images = RemoteImages.shared(activity)
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "film-link").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val tag = Any()
    private var epoch = 0

    private var layer: Layer? = null
    private var hudHops: TextView? = null
    private var hudBacks: TextView? = null
    private var hudTime: TextView? = null
    private var trailHolder: FrameLayout? = null
    private var summary: View? = null

    /** The window going to the background pauses the clock; coming back starts it again. */
    private inner class Layer : FrameLayout(activity) {
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (run.finished) return
            if (visibility == VISIBLE) { run.resume(); scheduleTick() } else run.pause()
        }
    }

    val endShowing: Boolean get() = summary != null

    // ---- on and off ----------------------------------------------------------------------------------------

    /** Puts the HUD and the teal edge (the one mark that the game is on) over the page. */
    fun attach() {
        if (layer != null) return
        val l = Layer().apply { isFocusable = false; isClickable = false; setWillNotDraw(true) }
        l.addView(View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Cinema.TEAL, Cinema.alpha(Cinema.TEAL, 0f)))
            isFocusable = false
        }, FrameLayout.LayoutParams(-1, Cinema.px(activity, 14), Gravity.TOP))
        trailHolder = FrameLayout(activity).also { l.addView(it, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 44) }) }
        l.addView(buildHud(), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 20) })
        host.addView(l, FrameLayout.LayoutParams(-1, -1))
        layer = l
        updateHud(); scheduleTick()
    }

    fun detach() {
        epoch++; images.cancel(tag); ui.removeCallbacksAndMessages(null)
        hideEnd()
        layer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        layer = null; hudHops = null; hudBacks = null; hudTime = null; trailHolder = null
    }

    /** The page for [film] is on screen: show the route so far and the numbers. */
    fun showFilm(film: LinkFilm) {
        trailHolder?.let { h -> h.removeAllViews(); h.addView(trail()) }
        updateHud()
    }

    // ---- the row ---------------------------------------------------------------------------------------------

    /** The films [film]'s page connects to, from the session's cache or Seerr; [done] runs on the UI thread, null when it could not be fetched. */
    fun neighbours(film: LinkFilm, done: (List<LinkFilm>?) -> Unit) {
        val my = epoch
        graph.known(film.tmdbId)?.let { run.offer(it); done(it); return }
        pool.execute {
            val list = graph.neighbours(film.tmdbId)
            ui.post { if (my == epoch && layer != null) { list?.let { run.offer(it) }; done(list) } }
        }
    }

    // ---- moves -------------------------------------------------------------------------------------------

    fun hop(f: LinkFilm): HopOutcome {
        val outcome = run.hop(f)
        updateHud()
        if (outcome == HopOutcome.COMPLETED) stopTick()
        return outcome
    }

    /** Back: the previous film (tallied as a back press, no hop refunded), or null at the start. */
    fun stepBack(): LinkFilm? = if (run.back()) run.current.also { updateHud() } else null

    fun giveUp() { run.giveUp(); stopTick(); updateHud() }

    /** The next film on the shortest route from the film on screen to the target (planted links included), or null when none is found. [done] runs on the UI thread. */
    fun hint(done: (LinkFilm?) -> Unit) {
        val from = run.current
        pool.execute {
            val next = FilmLinkPairs.route(from, pair.target, graph)?.firstOrNull()
            ui.post { done(next) }
        }
    }

    // ---- the HUD and its clock --------------------------------------------------------------------------

    private fun buildHud(): View {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = false
            setPadding(Cinema.px(activity, 14), Cinema.px(activity, 12), Cinema.px(activity, 28), Cinema.px(activity, 12))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 26); setColor(0xE6180F0B.toInt()); setStroke(Cinema.px(activity, 2), Cinema.alpha(Cinema.CREAM, 0.14f)) }
        }
        panel.addView(posterCard(run.target, 72, 108), LinearLayout.LayoutParams(-2, -2))
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(Cinema.text(activity, "Target", 18, Cinema.TEAL, weight = 600, tracking = 0.2f, caps = true))
        words.addView(Cinema.text(activity, run.target.name, 36, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 1; maxWidth = Cinema.px(activity, 340); ellipsize = TextUtils.TruncateAt.END })
        run.target.year?.let { words.addView(Cinema.text(activity, it.toString(), 20, Cinema.MUTED)) }
        // So a film you don't know can still be imagined: what it is about.
        run.target.overview?.takeIf { it.isNotBlank() }?.let { o ->
            words.addView(Cinema.text(activity, o, 16, Cinema.READING).apply { maxLines = 3; maxWidth = Cinema.px(activity, 340); ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.15f) },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) })
        }
        panel.addView(words, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 20) })
        fun divider() = panel.addView(View(activity).apply { setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.14f)) },
            LinearLayout.LayoutParams(Cinema.px(activity, 2), Cinema.px(activity, 96)).apply { marginStart = Cinema.px(activity, 26); marginEnd = Cinema.px(activity, 26) })
        divider()
        val hops = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        hops.addView(Cinema.text(activity, "Hops", 18, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
        hudHops = Cinema.text(activity, "0", 64, Cinema.CREAM, serif = true, weight = 600).also { hops.addView(it) }
        panel.addView(hops)
        divider()
        val backs = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        backs.addView(Cinema.text(activity, "Backs", 18, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
        hudBacks = Cinema.text(activity, "0", 64, Cinema.CREAM, serif = true, weight = 600).also { backs.addView(it) }
        panel.addView(backs)
        if (run.timed) {
            divider()
            val time = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            time.addView(Cinema.text(activity, "Time", 18, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
            hudTime = Cinema.text(activity, "00:00", 36, Cinema.CREAM, weight = 600).also { time.addView(it) }
            panel.addView(time)
        }
        return panel
    }

    private fun updateHud() {
        hudHops?.text = run.hops.toString()
        hudBacks?.text = run.backs.toString()
        hudTime?.text = clock(run.elapsedMs())
    }

    private val tick = object : Runnable { override fun run() { updateHud(); scheduleTick() } }
    private fun scheduleTick() {
        ui.removeCallbacks(tick)
        if (run.timed && !run.finished && layer != null) ui.postDelayed(tick, 1000)
    }
    private fun stopTick() { ui.removeCallbacks(tick) }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /** "Film Link · Alien › Blade Runner": the route so far, top left, in the place the page keeps nothing. */
    private fun trail(): TextView {
        val names = run.route.map { it.name }
        val shown = if (names.size > 3) listOf("…") + names.takeLast(3) else names
        val sb = SpannableStringBuilder()
        fun add(text: String, color: Int) { val s = sb.length; sb.append(text); sb.setSpan(ForegroundColorSpan(color), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        add("FILM LINK", Cinema.TEAL)
        shown.forEachIndexed { i, n -> add(if (i == 0) "  ·  " else "  ›  ", Cinema.DIM); add(n.uppercase(), if (i == shown.lastIndex) Cinema.CREAM else Cinema.MUTED) }
        return Cinema.text(activity, sb, 22, Cinema.CREAM, weight = 600, tracking = 0.2f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START; maxWidth = Cinema.px(activity, 1000) }
    }

    // ---- the summary ---------------------------------------------------------------------------------------

    /** The route, hops, time, and Play again, Same pair or Exit: shown over the page when the target is reached or the player gives up. */
    fun showEnd(onAgain: () -> Unit, onSame: () -> Unit, onExit: () -> Unit) {
        hideEnd()
        val won = run.phase == LinkPhase.COMPLETE
        val page = FrameLayout(activity).apply { setBackgroundColor(Cinema.BG); isClickable = true; clipChildren = false; clipToPadding = false }
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        head.addView(Cinema.eyebrow(activity, listOf("Film Link", if (won) "Complete" else "Gave up"), color = Cinema.TEAL))
        head.addView(Cinema.text(activity, if (won) "You got there." else "Not this time.", 120, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
        page.addView(head, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 80) })

        val stats = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun stat(label: String, value: String) {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 40), Cinema.px(activity, 22), Cinema.px(activity, 40), Cinema.px(activity, 22))
                background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 28); setColor(0xD1180F0B.toInt()); setStroke(Cinema.px(activity, 2), Cinema.alpha(Cinema.CREAM, 0.14f)) }
            }
            box.addView(Cinema.text(activity, label, 19, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
            box.addView(Cinema.text(activity, value, 88, Cinema.CREAM, serif = true, weight = 600))
            stats.addView(box, LinearLayout.LayoutParams(-2, -2).apply { if (stats.childCount > 0) marginStart = Cinema.px(activity, 28) })
        }
        stat("Hops", run.hops.toString())
        stat("Backs", run.backs.toString())
        if (run.timed) stat("Time", clock(run.elapsedMs()))
        page.addView(stats, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 90) })

        page.addView(Cinema.text(activity, if (won) "Your route" else "Your route so far", 21, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true),
            FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 338) })
        val strip = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(Cinema.px(activity, 96), 0, Cinema.px(activity, 96), 0) }
        val films = run.route + if (won) emptyList() else listOf(run.target)
        // The cards take focus so Left and Right can walk the whole route: the scroller moves to the focused card itself
        // (CalmHorizontalScrollView does not follow focus on its own).
        val scroller = CalmHorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false }
        films.forEachIndexed { i, f ->
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            val isTarget = f.tmdbId == run.target.tmdbId
            // An explicit size: left to match its column, Android squeezes the card to the width of the caption.
            col.addView(posterCard(f, 300, 450).apply {
                if (isTarget && !won) alpha = 0.6f
                isFocusable = true
                onFocus = { has -> if (has) scroller.smoothScrollTo(maxOf(0, col.left - Cinema.px(activity, 96)), 0) }
            }, LinearLayout.LayoutParams(-2, -2))
            col.addView(Cinema.text(activity, f.name, 32, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 2; maxWidth = Cinema.px(activity, 300); ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
            val sub = when { i == 0 -> "Start"; isTarget -> if (won) "Target" else "Target, not reached"; else -> null }
            val backs = run.backsFrom(f).takeIf { it > 0 }?.let { "Back ×$it" }
            col.addView(Cinema.text(activity, listOfNotNull(sub, f.year?.toString(), backs).joinToString(" · "), 22, if (isTarget) Cinema.TEAL else Cinema.MUTED),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) })
            if (i > 0) strip.addView(Cinema.text(activity, if (i < run.route.size) "→" else "⋯", 64, Cinema.TEAL, serif = true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(Cinema.px(activity, 112), Cinema.px(activity, 450)))
            strip.addView(col, LinearLayout.LayoutParams(-2, -2))
        }
        scroller.addView(strip)
        page.addView(scroller, FrameLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 392) })

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val h = Cinema.px(activity, 76); val gap = Cinema.px(activity, 20)
        val again = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Play again").also { it.setOnClickListener { onAgain() } }
        buttons.addView(again, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        buttons.addView(CinemaButton(activity, CinemaButton.Kind.GLASS, "Same pair, new try").also { it.setOnClickListener { onSame() } }, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        buttons.addView(CinemaButton(activity, CinemaButton.Kind.GLASS, "Exit").also { it.setOnClickListener { onExit() } }, LinearLayout.LayoutParams(-2, h))
        page.addView(buttons, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); bottomMargin = Cinema.px(activity, 44) })
        // Over the HUD and the trail too: the summary is the whole screen.
        host.addView(page, FrameLayout.LayoutParams(-1, -1))
        summary = page
        again.requestFocus()
    }

    fun hideEnd() { summary?.let { (it.parent as? ViewGroup)?.removeView(it) }; summary = null }

    // ---- pieces --------------------------------------------------------------------------------------------

    private fun posterCard(f: LinkFilm, w: Int, h: Int): PosterCard {
        val c = PosterCard(activity, w, h, titled = false)
        c.contentDescription = f.name
        c.isFocusable = false; c.isClickable = false
        // The start film can arrive without a TMDB poster; the page it began on still has one, so use that before initials.
        val own = if (f.posterPath == null && f.tmdbId == pair.start.tmdbId) startPoster else null
        when {
            f.posterPath != null -> images.loadShadedPoster("https://image.tmdb.org/t/p/w342${f.posterPath}", emptyMap(), 332, tag) { b -> c.setShadedPoster(b) }
            own != null -> images.loadShadedPoster(own.first, own.second, 332, tag) { b -> c.setShadedPoster(b) }
            else -> c.placeholder(f.name)
        }
        return c
    }

    companion object {
        /**
         * Works out a pair for the choices the player made, off the UI thread, and calls [done] on it with the pair (null
         * when none could be found) and the session's graph. [start] and [target] are the films they picked, or null
         * for a random one; [unverified] on the result means a start and target they chose have no route found by the
         * quick look, so the caller can ask before starting.
         */
        fun plan(activity: Activity, seerr: SeerrClient, start: LinkFilm?, target: LinkFilm?, done: (pair: LinkPair?, graph: CachedFilmGraph, unverified: Boolean) -> Unit) {
            val graph = CachedFilmGraph { id -> seerr.recommendations(Kind.MOVIE, id) }
            val ui = Handler(Looper.getMainLooper())
            Executors.newSingleThreadExecutor { r -> Thread(r, "film-link-plan").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }.execute {
                val random = Random(System.nanoTime())
                var unverified = false
                val pair: LinkPair? = when {
                    start != null && target != null -> {
                        val hops = FilmLinkPairs.check(start, target, graph)
                        unverified = hops == null
                        // No route in a quick look: plant the finish on every page it is really associated with, for this game only.
                        if (start.tmdbId == target.tmdbId) null else if (hops != null) LinkPair(start, target, hops) else FilmLinkPairs.plant(start, target, graph, random)
                    }
                    start != null -> FilmLinkPairs.pickTarget(start, graph, random)
                    target != null -> FilmLinkPairs.pickStart(target, graph, random)
                    else -> {
                        val seeds = FilmLinkSeeds.collect({ seerr.discoverMovies(it) }, random)
                        if (seeds.isEmpty()) null else FilmLinkPairs.generate(seeds, graph, random)
                    }
                }
                ui.post { done(pair, graph, unverified) }
            }
        }
    }
}
