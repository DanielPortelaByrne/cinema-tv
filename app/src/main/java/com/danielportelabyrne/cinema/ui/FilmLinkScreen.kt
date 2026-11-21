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
import android.widget.ScrollView
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
import com.danielportelabyrne.cinema.design.cinema.HeroBackdrop
import com.danielportelabyrne.cinema.design.cinema.PosterCard
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Film Link (CIN-64 design, CIN-65): a game for the TV. Start at one film, reach another only by hopping through
 * "More films like this" (TMDB's recommendations, through Seerr). It is a screen of its own over the app, so nothing
 * here touches the title pages, the watch history, playback or normal browsing, and the only way forward is the row:
 * there is no search, no streaming and no shortcut to the target while a run is on.
 *
 * The rules live in [FilmLinkRun] (core, tested); this class only draws them. It is entered from the rail's Film Link
 * item, and Back always goes somewhere: the previous film, then out of the game.
 */
class FilmLinkScreen(
    private val activity: Activity,
    /** The layer this screen is drawn into, over everything. */
    private val host: ViewGroup,
    private val seerr: SeerrClient,
    /** Called with true when the game covers the app and false when it has gone, so the app can hide and restore its page. */
    private val onOpenChanged: (Boolean) -> Unit
) {
    private enum class Stage { LOADING, MESSAGE, REVEAL, FILM, END }

    private val images = RemoteImages.shared(activity)
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "film-link").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    private var root: Root? = null
    private var stageView: View? = null
    private var stage = Stage.LOADING
    private var epoch = 0
    private var tag = Any()
    private var graph: CachedFilmGraph? = null
    private var pair: LinkPair? = null
    private var mode = LinkMode.UNTIMED
    private var run: FilmLinkRun? = null
    private var focusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener? = null

    // The persistent HUD (target, hops, optional timer) and the teal edge that marks the mode.
    private var hud: View? = null
    private var hudHops: TextView? = null
    private var hudTime: TextView? = null
    private var noticeView: View? = null

    val isOpen: Boolean get() = root != null

    /** The window going to the background pauses the clock; coming back starts it again. */
    private inner class Root : FrameLayout(activity) {
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            val r = run ?: return
            if (visibility == VISIBLE) { r.resume(); scheduleTick() } else r.pause()
        }
    }

    // ---- opening and closing -------------------------------------------------------------------------------

    fun open() {
        if (isOpen) return
        val r = Root().apply { setBackgroundColor(Cinema.BG); isClickable = true; isFocusable = false }
        host.addView(r, FrameLayout.LayoutParams(-1, -1))
        root = r
        // The teal edge: the one mark that the game is on, on every screen of it.
        r.addView(View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Cinema.TEAL, Cinema.alpha(Cinema.TEAL, 0f)))
            isFocusable = false
        }, FrameLayout.LayoutParams(-1, Cinema.px(activity, 14), Gravity.TOP))
        tag = Any()
        onOpenChanged(true)
        newGame()
    }

    fun close() {
        val r = root ?: return
        epoch++; images.cancel(tag); ui.removeCallbacksAndMessages(null)
        detachFocusListener()
        (r.parent as? ViewGroup)?.removeView(r)
        root = null; stageView = null; hud = null; hudHops = null; hudTime = null; noticeView = null
        run = null; pair = null; graph = null
        onOpenChanged(false)
    }

    /** Back: the previous film while playing, otherwise out of the game. */
    fun back(): Boolean {
        if (!isOpen) return false
        val r = run
        if (stage == Stage.FILM && r != null && r.back()) { showFilm(r.current); return true }
        close()
        return true
    }

    // ---- setting up a game ---------------------------------------------------------------------------------

    private fun newGame() {
        stopTick(); hideHud(); run = null
        if (!seerr.isSignedIn) {
            showMessage("Film Link needs Seerr", "Film Link finds its films through Seerr. Sign in to Seerr in Settings, then come back.", null)
            return
        }
        showLoading("Finding two films…")
        val my = ++epoch
        val g = CachedFilmGraph { id -> seerr.recommendations(Kind.MOVIE, id) }
        graph = g
        pool.execute {
            val seeds = FilmLinkSeeds.collect({ seerr.discoverMovies(it) }, Random(System.nanoTime()))
            val found = if (seeds.isEmpty()) null else FilmLinkPairs.generate(seeds, g, Random(System.nanoTime()))
            ui.post {
                if (my != epoch || !isOpen) return@post
                if (found == null) showMessage("Couldn’t find two films", "Seerr didn’t return enough connected films just now. Try again in a moment.", ::newGame)
                else { pair = found; mode = LinkMode.UNTIMED; showReveal(found) }
            }
        }
    }

    private fun startRun() {
        val p = pair ?: return
        epoch++; images.cancel(tag)
        val r = FilmLinkRun(p.start, p.target, mode) { SystemClock.elapsedRealtime() }
        run = r
        buildHud(r)
        showFilm(r.start)
        scheduleTick()
    }

    // ---- screens -------------------------------------------------------------------------------------------

    private fun setStage(s: Stage, view: View) {
        val r = root ?: return
        detachFocusListener()
        stageView?.let { r.removeView(it) }
        stage = s; stageView = view
        r.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showLoading(text: String) {
        val page = FrameLayout(activity)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(Cinema.eyebrow(activity, listOf("Film Link"), color = Cinema.TEAL))
        box.addView(Cinema.text(activity, text, 56, Cinema.CREAM, serif = true, weight = 500), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 24) })
        page.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setStage(Stage.LOADING, page)
    }

    private fun showMessage(title: String, body: String, retry: (() -> Unit)?) {
        val page = FrameLayout(activity)
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 96), Cinema.px(activity, 300), Cinema.px(activity, 96), 0) }
        col.addView(Cinema.eyebrow(activity, listOf("Film Link"), color = Cinema.TEAL))
        col.addView(Cinema.text(activity, title, 84, Cinema.CREAM, serif = true, weight = 600), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
        col.addView(Cinema.text(activity, body, 30, Cinema.READING).apply { maxWidth = Cinema.px(activity, 1000); setLineSpacing(0f, 1.25f) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 26) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val h = Cinema.px(activity, 76); val gap = Cinema.px(activity, 20)
        var first: View? = null
        retry?.let { go -> first = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Try again").also { b -> b.setOnClickListener { go() }; buttons.addView(b, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap }) } }
        val leave = CinemaButton(activity, CinemaButton.Kind.GLASS, "Leave Film Link").also { it.setOnClickListener { close() }; buttons.addView(it, LinearLayout.LayoutParams(-2, h)) }
        col.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 44) })
        page.addView(col, FrameLayout.LayoutParams(-1, -1))
        setStage(Stage.MESSAGE, page)
        (first ?: leave).requestFocus()
    }

    /** Start and target, the rules in a line, untimed or timed, and Begin (`Game-Reveal` in the CIN-64 mockup). */
    private fun showReveal(p: LinkPair) {
        val page = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        fun poster(f: LinkFilm, label: String, labelColor: Int): View {
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            col.addView(Cinema.text(activity, label, 21, labelColor, weight = 600, tracking = 0.2f, caps = true))
            val card = posterCard(f, 300, 450)
            col.addView(card, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
            col.addView(Cinema.text(activity, f.name, 36, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 2; maxWidth = Cinema.px(activity, 300); ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 20) })
            f.year?.let { col.addView(Cinema.text(activity, it.toString(), 24, Cinema.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) }) }
            return col
        }
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        head.addView(Cinema.eyebrow(activity, listOf("Film Link"), color = Cinema.TEAL))
        head.addView(Cinema.title(activity, "Get from one film to another.", 88, maxLines = 2), LinearLayout.LayoutParams(Cinema.px(activity, 1500), -2).apply { topMargin = Cinema.px(activity, 22) })
        head.addView(Cinema.text(activity, "Move only through “More films like this”. Every hop counts once, including the last. Back is free.", 28, Cinema.READING).apply { setLineSpacing(0f, 1.25f); maxWidth = Cinema.px(activity, 1100) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
        page.addView(head, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 80) })

        val posters = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        posters.addView(poster(p.start, "Start", Cinema.SAFFRON))
        posters.addView(Cinema.text(activity, "→", 80, Cinema.MUTED, serif = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 48); marginEnd = Cinema.px(activity, 48) })
        posters.addView(poster(p.target, "Target", Cinema.TEAL))
        page.addView(posters, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 350) })

        val side = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        side.addView(Cinema.text(activity, "Mode", 21, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
        val h = Cinema.px(activity, 76)
        val modes = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val untimed = CinemaButton(activity, CinemaButton.Kind.GLASS, "")
        val timed = CinemaButton(activity, CinemaButton.Kind.GLASS, "")
        fun paint() { untimed.setText(if (mode == LinkMode.UNTIMED) "✓ Untimed" else "Untimed"); timed.setText(if (mode == LinkMode.TIMED) "✓ Timed" else "Timed") }
        untimed.setOnClickListener { mode = LinkMode.UNTIMED; paint() }
        timed.setOnClickListener { mode = LinkMode.TIMED; paint() }
        paint()
        modes.addView(untimed, LinearLayout.LayoutParams(-2, h).apply { marginEnd = Cinema.px(activity, 20) })
        modes.addView(timed, LinearLayout.LayoutParams(-2, h))
        side.addView(modes, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 16) })
        side.addView(Cinema.text(activity, "Timed shows a clock in the corner and pauses if you leave Cinema.", 22, Cinema.DIM).apply { maxWidth = Cinema.px(activity, 640) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
        val begin = CinemaButton(activity, CinemaButton.Kind.TERRA, "Begin").also { it.setOnClickListener { startRun() } }
        side.addView(begin, LinearLayout.LayoutParams(-2, h).apply { topMargin = Cinema.px(activity, 44) })
        val another = CinemaButton(activity, CinemaButton.Kind.GLASS, "New pair").also { it.setOnClickListener { newGame() } }
        side.addView(another, LinearLayout.LayoutParams(-2, h).apply { topMargin = Cinema.px(activity, 20) })
        page.addView(side, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 1150); topMargin = Cinema.px(activity, 430) })
        setStage(Stage.REVEAL, page)
        begin.requestFocus()
    }

    /** One film in a run: the title page's look, with the row as the only way on (`Game-Details` and `Game-Row` in the mockup). */
    private fun showFilm(film: LinkFilm) {
        val r = run ?: return
        val my = ++epoch
        val page = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val backdrop = HeroBackdrop(activity, focusX = 0.5f, fadeTo = 0.38f)
        page.addView(backdrop, FrameLayout.LayoutParams(Cinema.px(activity, 2200), Cinema.px(activity, 1080), Gravity.START or Gravity.TOP))
        film.backdropPath?.let { images.loadBackdrop("https://image.tmdb.org/t/p/w1280$it", maxWidth = 1920) { b -> if (my == epoch) backdrop.show(b) } }
        page.addView(View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Cinema.alpha(Cinema.BG, 0.97f), Cinema.alpha(Cinema.BG, 0.86f), Cinema.alpha(Cinema.BG, 0.15f)))
        }, FrameLayout.LayoutParams(-1, -1))
        val dimmer = View(activity).apply { setBackgroundColor(Cinema.BG); alpha = 0f }
        page.addView(dimmer, FrameLayout.LayoutParams(-1, -1))

        val side = Cinema.px(activity, 96)
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(side, Cinema.px(activity, 230), side, Cinema.px(activity, 520)) }
        val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        details.addView(Cinema.eyebrow(activity, listOfNotNull("Film", film.year?.toString())))
        details.addView(Cinema.title(activity, film.name, 104, maxLines = 2).apply { setShadowLayer(Cinema.pxf(activity, 22), 0f, Cinema.pxf(activity, 3), 0xAA000000.toInt()) },
            LinearLayout.LayoutParams(Cinema.px(activity, 960), -2).apply { topMargin = Cinema.px(activity, 22) })
        details.addView(Cinema.synopsis(activity, film.overview ?: "No description yet.", 26, maxLines = 3),
            LinearLayout.LayoutParams(Cinema.px(activity, 760), -2).apply { topMargin = Cinema.px(activity, 28) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val h = Cinema.px(activity, 76); val gap = Cinema.px(activity, 20)
        val findLink = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Find a link")
        buttons.addView(findLink, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        if (r.route.size > 1) {
            val previous = r.route[r.route.size - 2]
            val back = CinemaButton(activity, CinemaButton.Kind.GLASS, "Back to ${previous.name}".let { if (it.length > 28) it.take(27) + "…" else it })
            back.setOnClickListener { this.back() }
            buttons.addView(back, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        }
        val giveUp = CinemaButton(activity, CinemaButton.Kind.GLASS, "Give up").also { it.label.setTextColor(Cinema.DANGER_TEXT) }
        giveUp.setOnClickListener { r.giveUp(); stopTick(); showEnd() }
        buttons.addView(giveUp, LinearLayout.LayoutParams(-2, h))
        details.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 40) })
        details.addView(Cinema.text(activity, "Streaming and search are paused during Film Link.", 22, Cinema.DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
        column.addView(details)

        // The row: the same "More films like this" the title page shows; the only way on.
        val heading = Cinema.rowHeading(activity, "More films like this").apply { setPadding(0, Cinema.px(activity, 70), 0, Cinema.px(activity, 14)) }
        column.addView(heading)
        val inner = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
            setPadding(0, Cinema.px(activity, 16), Cinema.px(activity, 820), Cinema.px(activity, 16)) }
        val scroller = CalmHorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false; addView(inner) }
        column.addView(scroller, LinearLayout.LayoutParams(-1, -2))
        inner.addView(Cinema.text(activity, "Finding connected films…", 28, Cinema.MUTED))

        val scroll = ScrollView(activity).apply { isFocusable = false; isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false
            addView(column, FrameLayout.LayoutParams(-1, -2)) }
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val hints = Cinema.keyHints(activity, listOf("◀ ▶" to "Browse", "OK" to "Hop (+1)", "▲" to "Back to details", "Back" to "Previous film")).apply { alpha = 0f }
        page.addView(hints, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = side; bottomMargin = Cinema.px(activity, 48) })
        page.addView(trail(r), FrameLayout.LayoutParams(-2, -2).apply { leftMargin = side; topMargin = Cinema.px(activity, 44) })
        setStage(Stage.FILM, page)   // pages go in beneath the HUD and the edge, which stay on top

        fun fill(list: List<LinkFilm>?) {
            inner.removeAllViews()
            when {
                list == null -> {
                    inner.addView(Cinema.text(activity, "Couldn’t load the connected films.", 28, Cinema.MUTED), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Cinema.px(activity, 32) })
                    inner.addView(CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Try again", small = true).also { b -> b.setOnClickListener { loadRow(film, my, ::fill) } }, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)))
                }
                list.isEmpty() -> inner.addView(Cinema.text(activity, "Dead end: no films connect to this one. Go back, or give up.", 28, Cinema.MUTED))
                else -> {
                    r.offer(list)
                    list.forEachIndexed { i, f ->
                        inner.addView(rowCard(f, r) { picked -> hop(picked) }, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(activity, 44) })
                    }
                }
            }
        }
        findLink.setOnClickListener { (inner.getChildAt(0) as? PosterCard)?.requestFocus() }
        loadRow(film, my, ::fill)

        // Down into the row slides the page up and quietens the details; Up returns, as on the title page.
        val listener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            if (my != epoch || focused == null) return@OnGlobalFocusChangeListener
            val inRow = isWithin(focused, scroller)
            if (inRow) scroll.post { scroll.smoothScrollTo(0, (heading.top - Cinema.px(activity, 200)).coerceAtLeast(0)) }
            else if (isWithin(focused, details)) scroll.post { scroll.smoothScrollTo(0, 0) }
            details.animate().alpha(if (inRow) 0f else 1f).setDuration(220).start()
            hints.animate().alpha(if (inRow) 1f else 0f).setDuration(220).start()
        }
        focusListener = listener
        page.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        findLink.requestFocus()
    }

    private fun loadRow(film: LinkFilm, my: Int, show: (List<LinkFilm>?) -> Unit) {
        graph?.known(film.tmdbId)?.let { show(it); return }
        pool.execute {
            val list = graph?.neighbours(film.tmdbId)
            ui.post { if (my == epoch && isOpen) show(list) }
        }
    }

    private fun rowCard(f: LinkFilm, r: FilmLinkRun, onPick: (LinkFilm) -> Unit): PosterCard {
        val c = posterCard(f, 200, 300, focusable = true)
        val visited = r.isOnRoute(f)
        c.title?.text = f.name
        c.subline?.text = listOfNotNull(f.year?.toString(), if (visited) "On your route" else "+1 hop").joinToString(" · ")
        if (visited) { c.chip("Visited"); c.alpha = 0.55f }
        c.setOnClickListener { onPick(f) }
        return c
    }

    private fun posterCard(f: LinkFilm, w: Int, h: Int, focusable: Boolean = false): PosterCard {
        val c = PosterCard(activity, w, h, titled = focusable)
        c.contentDescription = f.name
        if (!focusable) { c.isFocusable = false; c.isClickable = false }
        if (f.posterPath == null) c.placeholder(f.name)
        else images.loadShadedPoster("https://image.tmdb.org/t/p/w342${f.posterPath}", emptyMap(), 332, tag) { b -> c.setShadedPoster(b) }
        return c
    }

    private fun hop(f: LinkFilm) {
        val r = run ?: return
        when (r.hop(f)) {
            HopOutcome.COUNTED -> { updateHud(); showFilm(f) }
            HopOutcome.COMPLETED -> { updateHud(); stopTick(); showEnd() }
            HopOutcome.ALREADY_ON_ROUTE -> notice("You’ve already been to ${f.name}")
            HopOutcome.NOT_CONNECTED -> notice("${f.name} isn’t connected from here")
            HopOutcome.FINISHED -> Unit
        }
    }

    /** The route, the hops, the time and Play again or Exit (`Game-Complete` in the mockup); also shown on giving up. */
    private fun showEnd() {
        val r = run ?: return
        hideHud()
        val won = r.phase == LinkPhase.COMPLETE
        val page = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        head.addView(Cinema.eyebrow(activity, listOf("Film Link", if (won) "Complete" else "Gave up"), color = Cinema.TEAL))
        head.addView(Cinema.text(activity, if (won) "You got there." else "Not this time.", 120, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
        page.addView(head, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 80) })

        val stats = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun stat(label: String, value: String) {
            val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 40), Cinema.px(activity, 22), Cinema.px(activity, 40), Cinema.px(activity, 22))
                background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 28); setColor(0xD1180F0B.toInt()); setStroke(Cinema.px(activity, 2), Cinema.alpha(Cinema.CREAM, 0.14f)) } }
            box.addView(Cinema.text(activity, label, 19, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
            box.addView(Cinema.text(activity, value, 88, Cinema.CREAM, serif = true, weight = 600))
            stats.addView(box, LinearLayout.LayoutParams(-2, -2).apply { if (stats.childCount > 0) marginStart = Cinema.px(activity, 28) })
        }
        stat("Hops", r.hops.toString())
        if (r.timed) stat("Time", clock(r.elapsedMs()))
        page.addView(stats, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 90) })

        page.addView(Cinema.text(activity, if (won) "Your route" else "Your route so far", 21, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true),
            FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 338) })
        val strip = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(Cinema.px(activity, 96), 0, Cinema.px(activity, 96), 0) }
        val films = r.route + if (won) emptyList() else listOf(r.target)
        films.forEachIndexed { i, f ->
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            val isTarget = f.tmdbId == r.target.tmdbId
            col.addView(posterCard(f, 300, 450).apply { if (isTarget && !won) alpha = 0.6f })
            col.addView(Cinema.text(activity, f.name, 32, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 2; maxWidth = Cinema.px(activity, 300); ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
            val sub = when { i == 0 -> "Start"; isTarget -> if (won) "Target" else "Target, not reached"; else -> null }
            col.addView(Cinema.text(activity, listOfNotNull(sub, f.year?.toString()).joinToString(" · "), 22, if (isTarget) Cinema.TEAL else Cinema.MUTED),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) })
            if (i > 0) strip.addView(Cinema.text(activity, if (i < r.route.size) "→" else "⋯", 64, Cinema.TEAL, serif = true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(Cinema.px(activity, 112), Cinema.px(activity, 450)))
            strip.addView(col, LinearLayout.LayoutParams(-2, -2))
        }
        val scroller = CalmHorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false; addView(strip) }
        page.addView(scroller, FrameLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 392) })

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val h = Cinema.px(activity, 76); val gap = Cinema.px(activity, 20)
        val again = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Play again").also { it.setOnClickListener { newGame() } }
        buttons.addView(again, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        buttons.addView(CinemaButton(activity, CinemaButton.Kind.GLASS, "Same pair, new try").also { it.setOnClickListener { startRun() } }, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        buttons.addView(CinemaButton(activity, CinemaButton.Kind.GLASS, "Exit").also { it.setOnClickListener { close() } }, LinearLayout.LayoutParams(-2, h))
        page.addView(buttons, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); bottomMargin = Cinema.px(activity, 44) })
        setStage(Stage.END, page)
        again.requestFocus()
    }

    // ---- the HUD and its clock ----------------------------------------------------------------------------

    /** Target poster and title, hops, and the time when the run is timed: top right, on every film page. */
    private fun buildHud(r: FilmLinkRun) {
        hideHud()
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = false
            setPadding(Cinema.px(activity, 18), Cinema.px(activity, 16), Cinema.px(activity, 34), Cinema.px(activity, 16))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 28); setColor(0xD1180F0B.toInt()); setStroke(Cinema.px(activity, 2), Cinema.alpha(Cinema.CREAM, 0.14f)) }
        }
        panel.addView(posterCard(r.target, 84, 126).apply { (this as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS }, LinearLayout.LayoutParams(-2, -2))
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(Cinema.text(activity, "Target", 19, Cinema.TEAL, weight = 600, tracking = 0.2f, caps = true))
        words.addView(Cinema.text(activity, r.target.name, 40, Cinema.CREAM, serif = true, weight = 600).apply { maxLines = 1; maxWidth = Cinema.px(activity, 360); ellipsize = TextUtils.TruncateAt.END })
        r.target.year?.let { words.addView(Cinema.text(activity, it.toString(), 22, Cinema.MUTED)) }
        panel.addView(words, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 22) })
        fun divider() = panel.addView(View(activity).apply { setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.14f)) }, LinearLayout.LayoutParams(Cinema.px(activity, 2), Cinema.px(activity, 112)).apply { marginStart = Cinema.px(activity, 30); marginEnd = Cinema.px(activity, 30) })
        divider()
        val hops = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        hops.addView(Cinema.text(activity, "Hops", 19, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
        hudHops = Cinema.text(activity, "0", 72, Cinema.CREAM, serif = true, weight = 600).also { hops.addView(it) }
        panel.addView(hops)
        if (r.timed) {
            divider()
            val time = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            time.addView(Cinema.text(activity, "Time", 19, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true))
            hudTime = Cinema.text(activity, "00:00", 40, Cinema.CREAM, weight = 600).also { time.addView(it) }
            panel.addView(time)
        }
        hud = panel
        root?.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 36) })
        updateHud()
    }

    private fun hideHud() { hud?.let { (it.parent as? ViewGroup)?.removeView(it) }; hud = null; hudHops = null; hudTime = null }

    private fun updateHud() {
        val r = run ?: return
        hudHops?.text = r.hops.toString()
        hudTime?.text = clock(r.elapsedMs())
        hud?.contentDescription = "Target: ${r.target.name}. ${r.hops} hops" + if (r.timed) ". ${clock(r.elapsedMs())}" else ""
    }

    private val tick = object : Runnable {
        override fun run() { updateHud(); scheduleTick() }
    }
    private fun scheduleTick() {
        ui.removeCallbacks(tick)
        val r = run ?: return
        if (r.timed && !r.finished && isOpen && stage == Stage.FILM) ui.postDelayed(tick, 1000)
    }
    private fun stopTick() { ui.removeCallbacks(tick) }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /** "Film Link · Alien › Blade Runner": the route so far, top left, in the place the title page keeps its reminder. */
    private fun trail(r: FilmLinkRun): TextView {
        val names = r.route.map { it.name }
        val shown = if (names.size > 3) listOf("…") + names.takeLast(3) else names
        val sb = SpannableStringBuilder()
        fun add(text: String, color: Int) { val s = sb.length; sb.append(text); sb.setSpan(ForegroundColorSpan(color), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        add("FILM LINK", Cinema.TEAL)
        shown.forEachIndexed { i, n -> add("  ·  ".takeIf { i == 0 } ?: "  ›  ", Cinema.DIM); add(n.uppercase(), if (i == shown.lastIndex) Cinema.CREAM else Cinema.MUTED) }
        return Cinema.text(activity, sb, 22, Cinema.CREAM, weight = 600, tracking = 0.2f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START; maxWidth = Cinema.px(activity, 1000) }
    }

    private fun notice(text: String) {
        val r = root ?: return
        noticeView?.let { r.removeView(it) }
        val v = Cinema.text(activity, text, 24, Cinema.CREAM, weight = 500).apply {
            setPadding(Cinema.px(activity, 28), Cinema.px(activity, 14), Cinema.px(activity, 28), Cinema.px(activity, 14))
            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 30); setColor(0xE6180F0B.toInt()) }
        }
        noticeView = v
        r.addView(v, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Cinema.px(activity, 120) })
        ui.postDelayed({ if (noticeView === v) { (v.parent as? ViewGroup)?.removeView(v); noticeView = null } }, 2200)
    }

    // ---- small helpers -------------------------------------------------------------------------------------

    private fun detachFocusListener() {
        focusListener?.let { l -> stageView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(l) }
        focusListener = null
    }

    private fun isWithin(view: View, container: View): Boolean {
        var v: View? = view
        while (v != null) { if (v === container) return true; v = v.parent as? View }
        return false
    }
}
