package com.danielportelabyrne.cinema.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danielportelabyrne.cinema.data.movies.CachedFilmGraph
import com.danielportelabyrne.cinema.data.movies.FilmLinkPairs
import com.danielportelabyrne.cinema.data.movies.FilmLinkRun
import com.danielportelabyrne.cinema.data.movies.FilmLinkSeeds
import com.danielportelabyrne.cinema.data.movies.HopOutcome
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.LinkFilm
import com.danielportelabyrne.cinema.data.movies.LinkMode
import com.danielportelabyrne.cinema.data.movies.LinkPair
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.desktop.input.NavAction
import kotlin.random.Random

/** The poster of a film in a game, at row size. */
fun LinkFilm.posterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

/** A game's film as a [Title], so the app's own title page can draw it. */
fun LinkFilm.asTitle(): Title = Title(
    key = "m:$tmdbId", kind = Kind.MOVIE, name = name, year = year, overview = overview, runtimeMin = null, genres = emptyList(),
    age = null, imdbScore = null, posterUrl = posterUrl(), backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
    imdbId = null, tmdbId = tmdbId.toString(),
)

/** What the start menu shows for the page it was opened from. */
class LinkHere(val film: LinkFilm)

/** One playthrough: the run (rules in the tested core), its connections, and how much of the stack it owns. */
class FilmLinkSession(val pair: LinkPair, val mode: LinkMode, val graph: CachedFilmGraph, val floor: Int, val startedHere: Boolean) {
    val run = FilmLinkRun(pair.start, pair.target, mode) { System.nanoTime() / 1_000_000 }
    /** Bumped on every change the HUD and the summary draw. */
    var version by mutableIntStateOf(0)
    /** Whether the run has ended, read through [version] so the screen redraws when it does. */
    val finished: Boolean get() { @Suppress("UNUSED_VARIABLE") val v = version; return run.finished }
}

/**
 * Film Link on the Deck (CIN-65): an Easter egg on a film's page, switched on with Up, Up, Down, Down, Left, Right, Left,
 * Right. It asks where to start, where to finish and whether to race the clock, then runs on the app's own title pages
 * (only "More films like this" can be used), with a HUD and the route so far over them. B walks back along the route and,
 * at the start, ends the game. The rules are core's [FilmLinkRun] and [FilmLinkPairs], the same as on the TV.
 */
class FilmLinkController(private val model: CinemaModel) {
    var menuOpen by mutableStateOf(false)
    var here by mutableStateOf<LinkHere?>(null)
    var game by mutableStateOf<FilmLinkSession?>(null)
    /** Set while the viewer is choosing their finishing film in Search. */
    var pick by mutableStateOf<PickRequest?>(null)
    var planning by mutableStateOf(false)

    class PickRequest(val start: LinkFilm?, val timed: Boolean)

    // ---- the Easter egg ------------------------------------------------------------------------------------

    private val code = listOf(NavAction.UP, NavAction.UP, NavAction.DOWN, NavAction.DOWN, NavAction.LEFT, NavAction.RIGHT, NavAction.LEFT, NavAction.RIGHT)
    private var progress = 0
    private var lastKeyAt = 0L

    /** Watches presses on a title page. True when the press finished the code (and so is not also a move). */
    fun onKey(action: NavAction): Boolean {
        val r = model.route as? Route.Title
        if (r == null || game != null || menuOpen || pick != null || model.player.active || model.streamLoading != null) { progress = 0; return false }
        val now = System.currentTimeMillis()
        if (progress > 0 && now - lastKeyAt > 2500) progress = 0
        lastKeyAt = now
        progress = if (action == code[progress]) progress + 1 else if (action == code[0]) 1 else 0
        if (progress < code.size) return false
        progress = 0
        open(r)
        return true
    }

    private fun open(r: Route.Title) {
        if (!model.seerr.isSignedIn) { model.toast("Sign in to Seerr to play Film Link"); return }
        val t = r.entry.title
        val id = t.tmdbId?.toLongOrNull()
        here = if (t.kind == Kind.MOVIE && id != null && !r.entry.title.key.startsWith("jf:")) LinkHere(LinkFilm(id, t.name, t.year, t.posterUrl?.let(::pathOf), t.backdropUrl?.let(::pathOf), t.overview))
        else if (t.kind == Kind.MOVIE && id != null) LinkHere(LinkFilm(id, t.name, t.year, null, null, t.overview)) else null
        menuOpen = true
    }

    private fun pathOf(url: String): String? = if ("/t/p/" in url) "/" + url.substringAfter("/t/p/").substringAfter('/') else null

    // ---- the questions -------------------------------------------------------------------------------------

    fun cancelMenu() { menuOpen = false }

    /** Popular films with posters, for the shuffle to run through and land on. Empty when Seerr can't be reached. */
    fun loadSeeds(done: (List<LinkFilm>) -> Unit) =
        model.bg({ FilmLinkSeeds.collect({ model.seerr.discoverMovies(it) }, Random(System.nanoTime()), pages = 3) }) { done(it.orEmpty()) }

    /**
     * [start] and [finish] are the films the menu settled on (this film, or one the shuffle landed on); null means a
     * random one is found when the game starts. [choose] sends the viewer to Search to pick their finish instead.
     */
    fun play(start: LinkFilm?, startedHere: Boolean, finish: LinkFilm?, choose: Boolean, timed: Boolean) {
        menuOpen = false
        if (choose) {
            pick = PickRequest(start, timed)
            model.go(com.danielportelabyrne.cinema.desktop.Page.Search, remember = true)
            model.toast("Choose your finishing film. Select it in Search; B cancels")
        } else plan(start, finish, timed, startedHere = startedHere)
    }

    /** The viewer chose [e] as their finish. */
    fun picked(e: Entry) {
        val req = pick ?: return
        val t = e.title
        if (t.kind != Kind.MOVIE) { model.toast("Film Link is films only. Pick a film"); return }
        pick = null
        model.bg({ model.seerr.findFilm(t.name, t.year) }) { found ->
            if (found == null) model.toast("Couldn't find that film to link to. Try another") else plan(req.start, found, req.timed, startedHere = false)
        }
    }

    private fun plan(start: LinkFilm?, target: LinkFilm?, timed: Boolean, startedHere: Boolean) {
        if (planning) return
        planning = true
        model.toast("Finding a route…")
        val mode = if (timed) LinkMode.TIMED else LinkMode.UNTIMED
        val graph = CachedFilmGraph { id -> model.seerr.recommendations(Kind.MOVIE, id) }
        model.bg({
            val random = Random(System.nanoTime())
            var unverified = false
            val pair: LinkPair? = when {
                start != null && target != null -> {
                    val hops = FilmLinkPairs.check(start, target, graph)
                    unverified = hops == null
                    // No route in a quick look: plant the finish at several films it is associated with, for this game only.
                    if (start.tmdbId == target.tmdbId) null else if (hops != null) LinkPair(start, target, hops) else FilmLinkPairs.plant(start, target, graph, random)
                }
                start != null -> FilmLinkPairs.pickTarget(start, graph, random)
                target != null -> FilmLinkPairs.pickStart(target, graph, random)
                else -> FilmLinkSeeds.collect({ model.seerr.discoverMovies(it) }, random).takeIf { it.isNotEmpty() }?.let { FilmLinkPairs.generate(it, graph, random) }
            }
            Triple(pair, unverified, Unit)
        }) { r ->
            planning = false
            val pair = r?.first
            if (pair == null) { model.toast("Couldn't find two films to link right now. Try again"); return@bg }
            if (r.second) model.toast("No natural route found, so ${pair.target.name} is linked to a few related films for this game")
            begin(pair, mode, graph, startedHere)
        }
    }

    private fun begin(pair: LinkPair, mode: LinkMode, graph: CachedFilmGraph, startedHere: Boolean) {
        val floor = model.stack.size
        val onStart = startedHere && (model.route as? Route.Title)?.entry?.title?.tmdbId == pair.start.tmdbId.toString()
        game = FilmLinkSession(pair, mode, graph, floor, onStart)
        if (!onStart) model.stack += route(pair.start)
        else (model.route as Route.Title).linkFilm = pair.start
    }

    private fun route(f: LinkFilm) = Route.Title(Entry(f.asTitle(), null), emptyList()).also { it.linkFilm = f }

    // ---- moves ---------------------------------------------------------------------------------------------

    fun hop(f: LinkFilm) {
        val g = game ?: return
        when (g.run.hop(f)) {
            HopOutcome.COUNTED -> { model.stack += route(f); g.version++ }
            HopOutcome.COMPLETED -> g.version++
            HopOutcome.ALREADY_ON_ROUTE -> model.toast("You've already been to ${f.name}")
            HopOutcome.NOT_CONNECTED -> model.toast("That film isn't connected to this one")
            HopOutcome.FINISHED -> {}
        }
    }

    fun giveUp() { game?.let { it.run.giveUp(); it.version++ } }

    /** Names the next film on the shortest route from here, for when the way on can't be imagined. */
    fun hint() {
        val g = game ?: return
        val from = g.run.current
        model.bg({ FilmLinkPairs.route(from, g.pair.target, g.graph) }) { path ->
            model.toast(path?.firstOrNull()?.let { "Hint: try ${it.name}" } ?: "No route to ${g.pair.target.name} from here. Press B to go back a film")
        }
    }

    /** B: the previous film (free), and at the start the end of the game. */
    fun back() {
        val g = game ?: return
        if (g.run.finished) { exit(); return }
        if (g.run.back()) {
            model.stack.removeAt(model.stack.lastIndex)
            (model.route as? Route.Title)?.linkRow?.let { g.run.offer(it) }
            g.version++
        } else exit()
    }

    fun exit(keepPlants: Boolean = false) {
        val g = game ?: return
        game = null
        if (!keepPlants) g.graph.unplant()
        while (model.stack.size > g.floor) model.stack.removeAt(model.stack.lastIndex)
        (model.route as? Route.Title)?.linkFilm = null
    }

    /** From the summary: the same pair again, or a new random one. */
    fun again(same: Boolean) {
        val g = game ?: return
        val pair = g.pair; val mode = g.mode; val graph = g.graph
        exit(keepPlants = same)
        if (same) begin(pair, mode, graph, startedHere = false) else plan(null, null, mode == LinkMode.TIMED, startedHere = false)
    }
}
