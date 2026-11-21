package com.danielportelabyrne.cinema.data.movies

import kotlin.random.Random

/*
 * Film Link (CIN-64 design, CIN-65): start at one film, reach another only by hopping through "More films like this".
 *
 * Everything here is plain Kotlin with no Android and no network, so the rules can be tested exactly. A run is one
 * object holding all of its own state, so one run can never touch another run, the watch history or the app's normal
 * browsing.
 */

/** A film in a Film Link run: just enough to show it and to recognise it again. */
data class LinkFilm(
    val tmdbId: Long, val name: String, val year: Int?,
    val posterPath: String?, val backdropPath: String?, val overview: String?
) {
    companion object {
        fun of(s: Suggestion) = LinkFilm(s.tmdbId, s.name, s.year, s.posterPath, s.backdropPath, s.overview)
    }
}

/** Where the connected films of a film come from. Pairs are checked and runs are played against the same source. */
fun interface FilmGraph {
    /** The films [id] connects to, or null when they could not be fetched (a failure, not an empty list). */
    fun neighbours(id: Long): List<LinkFilm>?
}

/** What counts as a connection: exactly the films the title page's "More films like this" row shows. */
object FilmLinkRules {
    /** The row's length on the title page. */
    const val ROW_SIZE = 16

    /** The films a TMDB recommendation list connects [of] to: films only, never itself, no repeats, the first [ROW_SIZE]. */
    fun connected(of: Long, suggestions: List<Suggestion>): List<LinkFilm> =
        suggestions.filter { it.kind == Kind.MOVIE && it.tmdbId != of }.distinctBy { it.tmdbId }.take(ROW_SIZE).map(LinkFilm::of)

    /**
     * The film in a Seerr search for [name] that the player meant: a film whose title matches (ignoring case) and whose
     * year is within one of [year] when both are known; else the first film whose title matches; else null.
     */
    fun matchFilm(results: List<Suggestion>, name: String, year: Int?): LinkFilm? {
        val films = results.filter { it.kind == Kind.MOVIE }
        val named = films.filter { it.name.equals(name.trim(), ignoreCase = true) }
        val pick = named.firstOrNull { year != null && it.year != null && kotlin.math.abs(it.year - year) <= 1 } ?: named.firstOrNull()
        return pick?.let(LinkFilm::of)
    }
}

/** A start and a target at least [shortest] hops apart by routes found while checking (an upper bound on the true shortest). */
data class LinkPair(val start: LinkFilm, val target: LinkFilm, val shortest: Int)

object FilmLinkPairs {
    /** Never a pair one hop apart (trivial); never beyond [MAX_HOPS] (too long to be fun). */
    const val MIN_HOPS = 2
    const val MAX_HOPS = 4
    /** The most connection lookups spent finding one pair, and the most starting films tried. */
    const val CALL_BUDGET = 30
    const val ATTEMPTS = 6

    /**
     * Picks a random start from [seeds] and a random target reachable from it in [minHops]..[maxHops] hops, found by
     * a breadth-first search over [graph] that makes at most [budget] lookups per start. A pair is therefore solvable
     * by construction, with its route known. Returns null when no seed gives one (no connections, or too few); the
     * caller then says so instead of starting a run that cannot be won. [random] makes the choice repeatable.
     */
    fun generate(
        seeds: List<LinkFilm>, graph: FilmGraph, random: Random,
        minHops: Int = MIN_HOPS, maxHops: Int = MAX_HOPS, budget: Int = CALL_BUDGET, attempts: Int = ATTEMPTS
    ): LinkPair? {
        val pool = seeds.distinctBy { it.tmdbId }.shuffled(random)
        for (start in pool.take(attempts)) {
            val depths = explore(start, graph, maxHops, budget)
            val candidates = depths.filter { (film, depth) -> depth >= minHops && film.tmdbId != start.tmdbId }.keys.toList()
            if (candidates.isEmpty()) continue
            val target = candidates[random.nextInt(candidates.size)]
            return LinkPair(start, target, depths.getValue(target))
        }
        return null
    }

    /**
     * A random target [minHops]..[maxHops] hops from a start the player is already on (the film whose page they turned
     * the game on from), found like [generate]. Null when nothing that far is connected.
     */
    fun pickTarget(start: LinkFilm, graph: FilmGraph, random: Random, minHops: Int = MIN_HOPS, maxHops: Int = MAX_HOPS, budget: Int = CALL_BUDGET): LinkPair? {
        val depths = explore(start, graph, maxHops, budget)
        val candidates = depths.filter { (film, depth) -> depth >= minHops && film.tmdbId != start.tmdbId }.keys.toList()
        if (candidates.isEmpty()) return null
        val target = candidates[random.nextInt(candidates.size)]
        return LinkPair(start, target, depths.getValue(target))
    }

    /**
     * A random start from which the player's chosen [target] can be reached in [minHops]..[maxHops] hops. Films near the
     * target (by the same connections) are the candidates, and each is checked going forward, so the pair is solvable by
     * construction. At most [attempts] candidates are checked. Null when none works.
     */
    fun pickStart(target: LinkFilm, graph: FilmGraph, random: Random, minHops: Int = MIN_HOPS, maxHops: Int = MAX_HOPS, budget: Int = CALL_BUDGET, attempts: Int = ATTEMPTS): LinkPair? {
        val near = explore(target, graph, maxHops, budget)
        val candidates = near.filter { (film, depth) -> depth >= minHops && film.tmdbId != target.tmdbId }.keys.shuffled(random).take(attempts)
        for (candidate in candidates) {
            val forward = explore(candidate, graph, maxHops, budget)
            val hops = forward.entries.firstOrNull { it.key.tmdbId == target.tmdbId }?.value ?: continue
            if (hops >= minHops) return LinkPair(candidate, target, hops)
        }
        return null
    }

    /**
     * How many hops a quick look finds from [start] to a [target] the player chose, or null when it finds no route (it
     * may still exist: the look is bounded to [budget] lookups and [maxHops] hops).
     */
    fun check(start: LinkFilm, target: LinkFilm, graph: FilmGraph, maxHops: Int = MAX_HOPS + 1, budget: Int = CALL_BUDGET + 10): Int? =
        explore(start, graph, maxHops, budget).entries.firstOrNull { it.key.tmdbId == target.tmdbId }?.value?.takeIf { it > 0 }

    /** The furthest from the start a target is planted: [PLANT_DEPTH] + 1 hops in all, so a planted game is never long. */
    const val PLANT_DEPTH = 3

    /** The fewest films the target is planted at when none of its real associations are within reach of the start. */
    const val MIN_PLANTS = 3

    /**
     * For a chosen pair with no route found: plants [target] for this game only, on every page where it makes sense, so
     * each player has the same chance to stumble on it however they wander. The rule is a real connection: the target
     * appears in the "more films like this" row of every film in the target's own recommendations (the films TMDB itself
     * associates with it), wherever the player meets one, however far from the start. [CachedFilmGraph] adds it to a page
     * only when that page doesn't already list it. If none of those films is within [PLANT_DEPTH] hops of the start, so the
     * player might never meet one, [MIN_PLANTS] reachable films nearest in release year are planted too. The start page
     * is never planted, so the target is at least 2 hops away, and at most [PLANT_DEPTH] + 1 by the shortest plant. The
     * plants live in [graph] (the session's own copy), never in Seerr or TMDB, so the recommendations are untouched and
     * they are gone with the game ([CachedFilmGraph.unplant] clears them earlier). Null when nothing is plantable.
     */
    fun plant(start: LinkFilm, target: LinkFilm, graph: CachedFilmGraph, random: Random): LinkPair? {
        val depths = explore(start, graph, PLANT_DEPTH, CALL_BUDGET)
        val associated = graph.neighbours(target.tmdbId).orEmpty().map { it.tmdbId }.filter { it != start.tmdbId && it != target.tmdbId }.toSet()
        val candidates = depths.entries.filter { it.value >= 1 && it.key.tmdbId != target.tmdbId }.shuffled(random)
        val reachableAssociated = candidates.filter { it.key.tmdbId in associated }
        val year = target.year
        val fillers = if (reachableAssociated.isNotEmpty()) emptyList() else candidates
            .sortedBy { val y = it.key.year; if (y != null && year != null) kotlin.math.abs(y - year) else 999 }.take(MIN_PLANTS)
        val nearest = (reachableAssociated + fillers).minOfOrNull { it.value } ?: return null
        associated.forEach { graph.plant(it, target) }
        fillers.forEach { graph.plant(it.key.tmdbId, target) }
        return LinkPair(start, target, nearest + 1)
    }

    /**
     * The fewest-hops route from [from] to [to] the connections show (planted ones included), as the films after [from]
     * in order, or null when a bounded look finds none. The first film is the hint for what to open next.
     */
    fun route(from: LinkFilm, to: LinkFilm, graph: FilmGraph, maxDepth: Int = MAX_HOPS + 1, budget: Int = CALL_BUDGET + 10): List<LinkFilm>? {
        val parent = HashMap<Long, LinkFilm?>().apply { put(from.tmdbId, null) }
        val queue = ArrayDeque<Pair<LinkFilm, Int>>().apply { add(from to 0) }
        var calls = 0
        while (queue.isNotEmpty() && calls < budget) {
            val (film, d) = queue.removeFirst()
            if (d >= maxDepth) continue
            calls++
            for (n in graph.neighbours(film.tmdbId).orEmpty()) {
                if (parent.containsKey(n.tmdbId)) continue
                parent[n.tmdbId] = film
                if (n.tmdbId == to.tmdbId) {
                    val path = ArrayList<LinkFilm>()
                    var at: LinkFilm? = n
                    while (at != null && at.tmdbId != from.tmdbId) { path.add(0, at); at = parent[at.tmdbId] }
                    return path
                }
                queue.add(n to d + 1)
            }
        }
        return null
    }

    /** Every film reached from [start] within [maxDepth] hops, with the fewest hops found. At most [budget] lookups. */
    internal fun explore(start: LinkFilm, graph: FilmGraph, maxDepth: Int, budget: Int): Map<LinkFilm, Int> {
        val depth = LinkedHashMap<Long, Int>().apply { put(start.tmdbId, 0) }
        val films = LinkedHashMap<Long, LinkFilm>().apply { put(start.tmdbId, start) }
        val queue = ArrayDeque<LinkFilm>().apply { add(start) }
        var calls = 0
        while (queue.isNotEmpty() && calls < budget) {
            val film = queue.removeFirst()
            val d = depth.getValue(film.tmdbId)
            if (d >= maxDepth) continue
            calls++
            val next = graph.neighbours(film.tmdbId) ?: continue
            for (n in next) {
                if (depth.containsKey(n.tmdbId)) continue
                depth[n.tmdbId] = d + 1; films[n.tmdbId] = n; queue.add(n)
            }
        }
        return films.values.associateWith { depth.getValue(it.tmdbId) }
    }
}

enum class LinkMode { UNTIMED, TIMED }
enum class LinkPhase { PLAYING, COMPLETE, GAVE_UP }

/** What a hop did. Only [COUNTED] and [COMPLETED] add to the hop count. */
enum class HopOutcome { COUNTED, COMPLETED, NOT_CONNECTED, ALREADY_ON_ROUTE, FINISHED }

/**
 * One playthrough. The route starts at the start film with zero hops. [offer] says which films the current page
 * connects to; [hop] only accepts one of those, counts it exactly once, and completes the run exactly once when the
 * target is reached. [back] returns to the previous film, does not undo a hop, and is tallied in [backs]. The clock only runs while
 * the run is playing and not paused, and is only shown when [mode] is TIMED.
 */
class FilmLinkRun(val start: LinkFilm, val target: LinkFilm, val mode: LinkMode, private val clock: () -> Long) {
    init { require(start.tmdbId != target.tmdbId) { "A run needs two different films" } }

    private val path = ArrayList<LinkFilm>().apply { add(start) }
    private var offered: Set<Long> = emptySet()
    private var elapsed = 0L
    private var runningSince: Long? = clock()

    /** Every film landed on at any point, so a film backed out of (a dead end) still counts as visited. */
    private val seen = HashSet<Long>().apply { add(start.tmdbId) }
    /** How many times Back was pressed to leave each film (film id -> presses), so a dead end stands out. */
    private val backCounts = HashMap<Long, Int>()

    var hops = 0; private set
    /** How many times Back was pressed to leave a film along the route. The aim is a win with none, and as few hops as possible. */
    var backs = 0; private set
    var phase = LinkPhase.PLAYING; private set

    /** The films visited so far, in order, ending at the one on screen. */
    val route: List<LinkFilm> get() = path.toList()
    val current: LinkFilm get() = path.last()
    val timed: Boolean get() = mode == LinkMode.TIMED
    val finished: Boolean get() = phase != LinkPhase.PLAYING

    /** The films the page now on screen connects to. Replaces any earlier offer. */
    fun offer(films: List<LinkFilm>) { offered = films.map { it.tmdbId }.toSet() }

    fun isOnRoute(film: LinkFilm) = path.any { it.tmdbId == film.tmdbId }

    /** Whether the player has landed on [film] at any point, on the route now or backed out of since. */
    fun hasVisited(film: LinkFilm) = film.tmdbId in seen

    /** How many times Back was pressed to leave [film] after landing on it. */
    fun backsFrom(film: LinkFilm): Int = backCounts[film.tmdbId] ?: 0

    fun hop(to: LinkFilm): HopOutcome {
        if (finished) return HopOutcome.FINISHED
        if (isOnRoute(to)) return HopOutcome.ALREADY_ON_ROUTE
        if (to.tmdbId !in offered) return HopOutcome.NOT_CONNECTED
        path.add(to); seen.add(to.tmdbId); hops++; offered = emptySet()
        if (to.tmdbId == target.tmdbId) {
            phase = LinkPhase.COMPLETE; stopClock()
            return HopOutcome.COMPLETED
        }
        return HopOutcome.COUNTED
    }

    /** Back to the previous film. Free: the hop count stays. False at the start, or once the run has ended. */
    fun back(): Boolean {
        if (finished || path.size < 2) return false
        val left = path.removeAt(path.size - 1); offered = emptySet(); backs++
        backCounts[left.tmdbId] = (backCounts[left.tmdbId] ?: 0) + 1
        return true
    }

    /** Ends the run without a win; the route so far stays for the summary. */
    fun giveUp() { if (!finished) { phase = LinkPhase.GAVE_UP; stopClock() } }

    /** Stops the clock while the app is in the background; [resume] starts it again. */
    fun pause() { runningSince?.let { elapsed += clock() - it; runningSince = null } }
    fun resume() { if (!finished && runningSince == null) runningSince = clock() }

    /** Milliseconds played so far, whether or not they are shown. */
    fun elapsedMs(): Long = elapsed + (runningSince?.let { clock() - it } ?: 0L)

    private fun stopClock() { pause() }
}
