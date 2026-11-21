package com.danielportelabyrne.cinema.data.movies

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmLinkTest {
    private fun film(id: Long) = LinkFilm(id, "Film $id", 2000 + (id % 20).toInt(), "/p$id.jpg", "/b$id.jpg", "About $id")

    /** A fixed graph: each film points at the films listed. Counts every lookup. */
    private class FakeGraph(val edges: Map<Long, List<Long>>, val missing: Set<Long> = emptySet()) : FilmGraph {
        var calls = 0
        override fun neighbours(id: Long): List<LinkFilm>? {
            calls++
            if (id in missing) return null
            return edges[id].orEmpty().map { LinkFilm(it, "Film $it", 2000, null, null, null) }
        }
    }

    // 1 -> 2,3 ; 2 -> 4 ; 3 -> 4,5 ; 4 -> 6 ; 5 -> 6 ; 6 -> 7
    private val chain = mapOf(1L to listOf(2L, 3L), 2L to listOf(4L), 3L to listOf(4L, 5L), 4L to listOf(6L), 5L to listOf(6L), 6L to listOf(7L))

    // ---- the connection rule

    private fun sug(id: Long, kind: Kind = Kind.MOVIE) = Suggestion(id, kind, "T$id", 2020, null, "/p$id", "/b$id", emptyList(), null, false)

    @Test fun connectedFilmsAreFilmsOnlyNeverItselfWithoutRepeatsAndCappedAtTheRow() {
        val list = listOf(sug(5), sug(1), sug(2, Kind.SHOW), sug(3), sug(3)) + (10L..40L).map { sug(it) }
        val out = FilmLinkRules.connected(5, list)
        assertEquals(1L, out.first().tmdbId)
        assertFalse(out.any { it.tmdbId == 5L })
        assertFalse(out.any { it.tmdbId == 2L })
        assertEquals(out.map { it.tmdbId }.distinct(), out.map { it.tmdbId })
        assertEquals(FilmLinkRules.ROW_SIZE, out.size)
    }

    // ---- pair generation

    @Test fun aPairIsAtLeastTwoHopsApartAndSolvableByConstruction() {
        val graph = FakeGraph(chain)
        val pair = FilmLinkPairs.generate(listOf(film(1)), graph, Random(7))
        assertNotNull(pair)
        assertEquals(1L, pair!!.start.tmdbId)
        assertTrue(pair.shortest in FilmLinkPairs.MIN_HOPS..FilmLinkPairs.MAX_HOPS)
        assertTrue(pair.target.tmdbId in setOf(4L, 5L, 6L, 7L))   // never 2 or 3: they are one hop away
        // A route of exactly that length exists in the graph the game will use.
        val depths = FilmLinkPairs.explore(pair.start, FakeGraph(chain), FilmLinkPairs.MAX_HOPS, 100)
        assertEquals(pair.shortest, depths.entries.first { it.key.tmdbId == pair.target.tmdbId }.value)
    }

    @Test fun theSameSeedGivesTheSamePair() {
        val seeds = (1L..6L).map { film(it) }
        val a = FilmLinkPairs.generate(seeds, FakeGraph(chain), Random(42))
        val b = FilmLinkPairs.generate(seeds, FakeGraph(chain), Random(42))
        assertEquals(a, b)
    }

    @Test fun startAndTargetAreDifferent() {
        repeat(50) { seed ->
            val pair = FilmLinkPairs.generate((1L..6L).map { film(it) }, FakeGraph(chain), Random(seed))
            if (pair != null) assertTrue(pair.start.tmdbId != pair.target.tmdbId)
        }
    }

    @Test fun generationEffortIsBounded() {
        // Every film connects to 16 fresh films: an endless graph.
        val endless = FilmGraph { id -> (1L..16L).map { film(id * 100 + it) } }
        var calls = 0
        val counting = FilmGraph { id -> calls++; endless.neighbours(id) }
        FilmLinkPairs.generate((1L..20L).map { film(it) }, counting, Random(1), budget = 12, attempts = 3)
        assertTrue("calls=$calls", calls <= 12 * 3)
    }

    @Test fun noPairWhenFilmsAreDisconnectedOrOnlyOneHopApart() {
        assertNull(FilmLinkPairs.generate(listOf(film(1)), FakeGraph(emptyMap()), Random(1)))
        assertNull(FilmLinkPairs.generate(listOf(film(1)), FakeGraph(mapOf(1L to listOf(2L, 3L))), Random(1)))
        assertNull(FilmLinkPairs.generate(emptyList(), FakeGraph(chain), Random(1)))
    }

    @Test fun anUnavailableLookupIsSkippedNotCounted() {
        val graph = FakeGraph(chain, missing = setOf(2L))
        val pair = FilmLinkPairs.generate(listOf(film(1)), graph, Random(3))
        assertNotNull(pair)   // still reachable through 3
        assertTrue(pair!!.target.tmdbId != 2L)
    }

    @Test fun anIncompleteSeedListFallsBackToTheNextSeed() {
        val graph = FakeGraph(chain + (50L to emptyList()))
        val pair = FilmLinkPairs.generate(listOf(film(50), film(1)), graph, Random(0))
        assertNotNull(pair)
    }

    // ---- a run

    private var now = 0L
    private fun run(mode: LinkMode = LinkMode.UNTIMED, start: Long = 1, target: Long = 4): FilmLinkRun { now = 1_000; return FilmLinkRun(film(start), film(target), mode) { now } }

    @Test fun aRunStartsAtZeroOnTheStartFilm() {
        val r = run()
        assertEquals(0, r.hops); assertEquals(1L, r.current.tmdbId); assertEquals(listOf(1L), r.route.map { it.tmdbId })
        assertEquals(LinkPhase.PLAYING, r.phase)
    }

    @Test fun twoIdenticalFilmsCannotMakeARun() {
        try { FilmLinkRun(film(1), film(1), LinkMode.UNTIMED) { 0 }; throw AssertionError("expected failure") } catch (_: IllegalArgumentException) { }
    }

    @Test fun aLegalHopCountsExactlyOnce() {
        val r = run()
        r.offer(listOf(film(2), film(3)))
        assertEquals(HopOutcome.COUNTED, r.hop(film(2)))
        assertEquals(1, r.hops); assertEquals(2L, r.current.tmdbId)
        // the same film again is on the route now, and the offer is gone until the next page offers
        assertEquals(HopOutcome.ALREADY_ON_ROUTE, r.hop(film(2)))
        assertEquals(1, r.hops)
    }

    @Test fun aFilmThePageDoesNotConnectToIsNeverAccepted() {
        val r = run()
        r.offer(listOf(film(2)))
        assertEquals(HopOutcome.NOT_CONNECTED, r.hop(film(4)))   // the target itself, not offered: no shortcut
        assertEquals(HopOutcome.NOT_CONNECTED, r.hop(film(99)))
        assertEquals(0, r.hops)
    }

    @Test fun reachingTheTargetCompletesOnceAndCountsTheLastHop() {
        val r = run()
        r.offer(listOf(film(2))); r.hop(film(2))
        r.offer(listOf(film(4)))
        assertEquals(HopOutcome.COMPLETED, r.hop(film(4)))
        assertEquals(2, r.hops); assertEquals(LinkPhase.COMPLETE, r.phase); assertTrue(r.finished)
        assertEquals(listOf(1L, 2L, 4L), r.route.map { it.tmdbId })
        // a second arrival, a back and a give-up after the end change nothing
        r.offer(listOf(film(4)))
        assertEquals(HopOutcome.FINISHED, r.hop(film(4)))
        assertFalse(r.back()); r.giveUp()
        assertEquals(LinkPhase.COMPLETE, r.phase); assertEquals(2, r.hops)
    }

    @Test fun backIsFreeAndDoesNotUndoAHop() {
        val r = run()
        r.offer(listOf(film(2), film(3))); r.hop(film(2))
        assertTrue(r.back())
        assertEquals(1L, r.current.tmdbId); assertEquals(1, r.hops)
        assertFalse(r.back())   // already at the start
    }

    @Test fun everyBackThatLeavesAFilmIsTalliedButNotOnesThatCannotGoBack() {
        val r = run()
        assertEquals(0, r.backs)
        assertFalse(r.back()); assertEquals(0, r.backs)   // at the start there is nothing to go back to
        r.offer(listOf(film(2))); r.hop(film(2)); assertEquals(0, r.backs)
        assertTrue(r.back()); assertEquals(1, r.backs); assertEquals(1, r.hops)
    }

    @Test fun afterBackAFilmLeftTheRouteSoHoppingToItAgainCountsAgain() {
        val r = run()
        r.offer(listOf(film(2), film(3))); r.hop(film(2)); r.back()
        r.offer(listOf(film(2), film(3)))
        assertEquals(HopOutcome.COUNTED, r.hop(film(2)))
        assertEquals(2, r.hops)
    }

    @Test fun backClearsTheOfferSoNothingIsAcceptedUntilThePageOffersAgain() {
        val r = run()
        r.offer(listOf(film(2))); r.hop(film(2)); r.back()
        assertEquals(HopOutcome.NOT_CONNECTED, r.hop(film(2)))
        assertEquals(1, r.hops)   // the earlier hop stays counted: back never refunds
    }

    @Test fun givingUpEndsTheRunWithoutAWinAndKeepsTheRoute() {
        val r = run()
        r.offer(listOf(film(2))); r.hop(film(2)); r.giveUp()
        assertEquals(LinkPhase.GAVE_UP, r.phase); assertEquals(listOf(1L, 2L), r.route.map { it.tmdbId })
        r.offer(listOf(film(4)))
        assertEquals(HopOutcome.FINISHED, r.hop(film(4)))
    }

    // ---- the timer

    @Test fun theClockCountsWhilePlayingAndStopsAtTheEnd() {
        val r = run(LinkMode.TIMED)
        now += 5_000
        assertEquals(5_000L, r.elapsedMs())
        r.offer(listOf(film(2))); r.hop(film(2)); r.offer(listOf(film(4)))
        now += 3_000; r.hop(film(4))
        now += 60_000
        assertEquals(8_000L, r.elapsedMs())   // stopped on arrival
    }

    @Test fun theClockPausesInTheBackgroundAndResumes() {
        val r = run(LinkMode.TIMED)
        now += 2_000; r.pause()
        now += 30_000
        assertEquals(2_000L, r.elapsedMs())
        r.pause()   // pausing twice changes nothing
        r.resume(); now += 1_000
        assertEquals(3_000L, r.elapsedMs())
    }

    @Test fun aFinishedRunNeverResumesItsClock() {
        val r = run(LinkMode.TIMED)
        now += 1_000; r.giveUp(); now += 10_000; r.resume(); now += 10_000
        assertEquals(1_000L, r.elapsedMs())
    }

    @Test fun anUntimedRunIsNotTimedForDisplay() {
        assertFalse(run().timed); assertTrue(run(LinkMode.TIMED).timed)
    }

    // ---- isolation and restart

    @Test fun runsAreIsolatedFromEachOther() {
        val a = run(); val b = run(start = 10, target = 12)
        a.offer(listOf(film(2))); a.hop(film(2))
        assertEquals(0, b.hops); assertEquals(10L, b.current.tmdbId)
        b.offer(listOf(film(11))); b.hop(film(11))
        assertEquals(1, a.hops); assertEquals(1, b.hops)
    }

    @Test fun aRestartIsANewRunFromZero() {
        val first = run()
        first.offer(listOf(film(2))); first.hop(film(2)); first.giveUp()
        val again = FilmLinkRun(first.start, first.target, LinkMode.UNTIMED) { now }
        assertEquals(0, again.hops); assertEquals(LinkPhase.PLAYING, again.phase); assertEquals(1, again.route.size)
        assertEquals(LinkPhase.GAVE_UP, first.phase)
    }
}

class FilmLinkSourceTest {
    private fun sug(id: Long, kind: Kind = Kind.MOVIE, poster: String? = "/p$id") = Suggestion(id, kind, "T$id", 2020, null, poster, null, emptyList(), null, false)

    @Test fun aFilmsConnectionsAreFetchedOnceAndKept() {
        var calls = 0
        val graph = CachedFilmGraph { calls++; listOf(sug(2), sug(3), sug(1)) }
        val a = graph.neighbours(1); val b = graph.neighbours(1)
        assertEquals(listOf(2L, 3L), a!!.map { it.tmdbId })   // never itself
        assertEquals(a, b); assertEquals(1, calls)
        assertEquals(a, graph.known(1))
    }

    @Test fun aFailedLookupIsNotKeptSoAskingAgainTriesAgain() {
        var calls = 0
        val graph = CachedFilmGraph { calls++; if (calls == 1) null else listOf(sug(2)) }
        assertNull(graph.neighbours(1)); assertNull(graph.known(1))
        assertEquals(listOf(2L), graph.neighbours(1)!!.map { it.tmdbId })
        assertEquals(2, calls)
    }

    @Test fun seedsAreFilmsWithPostersWithoutRepeatsAndFailedPagesAreSkipped() {
        val pages = mapOf(1 to listOf(sug(1), sug(2, Kind.SHOW), sug(3, poster = null)), 2 to listOf(sug(1), sug(4)), 3 to null, 4 to listOf(sug(5)), 5 to listOf(sug(6)))
        val seeds = FilmLinkSeeds.collect({ pages[it] }, kotlin.random.Random(1), pages = 5)
        assertEquals(setOf(1L, 4L, 5L, 6L), seeds.map { it.tmdbId }.toSet())
        assertEquals(seeds.map { it.tmdbId }.distinct(), seeds.map { it.tmdbId })
    }

    @Test fun noSeedsWhenNothingCouldBeFetched() {
        assertTrue(FilmLinkSeeds.collect({ null }, kotlin.random.Random(1)).isEmpty())
    }
}

class FilmLinkChosenPairsTest {
    private fun film(id: Long) = LinkFilm(id, "Film $id", 2000, null, null, null)

    /** A graph where every connection works both ways, like most recommendation graphs: edges as given plus their reverse. */
    private class TwoWay(edges: List<Pair<Long, Long>>) : FilmGraph {
        private val map = HashMap<Long, MutableList<Long>>().also { m -> edges.forEach { (a, b) -> m.getOrPut(a) { mutableListOf() }.add(b); m.getOrPut(b) { mutableListOf() }.add(a) } }
        var calls = 0
        override fun neighbours(id: Long): List<LinkFilm>? { calls++; return map[id].orEmpty().map { LinkFilm(it, "Film $it", 2000, null, null, null) } }
    }

    // 1-2-3-4-5-6-7, a straight line
    private val line = TwoWay((1L..6L).map { it to it + 1 })

    @Test fun aTargetIsPickedTwoToFourHopsFromTheStartTheyAreOn() {
        val pair = FilmLinkPairs.pickTarget(film(4), line, Random(5))
        assertNotNull(pair)
        assertEquals(4L, pair!!.start.tmdbId)
        assertTrue(pair.shortest in 2..4)
        assertTrue(pair.target.tmdbId !in setOf(3L, 5L, 4L))   // never a neighbour, never itself
    }

    @Test fun noTargetWhenTheStartHasNothingTwoHopsAway() {
        assertNull(FilmLinkPairs.pickTarget(film(1), TwoWay(listOf(1L to 2L)), Random(1)))
    }

    @Test fun aStartIsFoundFromWhichTheChosenTargetCanBeReached() {
        val pair = FilmLinkPairs.pickStart(film(4), line, Random(2))
        assertNotNull(pair)
        assertEquals(4L, pair!!.target.tmdbId)
        assertTrue(pair.start.tmdbId != 4L && pair.shortest in 2..4)
        // checked going forward over the same graph
        assertEquals(pair.shortest, FilmLinkPairs.check(pair.start, pair.target, TwoWay((1L..6L).map { it to it + 1 })))
    }

    @Test fun noStartWhenTheTargetIsCutOffFromTheRest() {
        assertNull(FilmLinkPairs.pickStart(film(99), line, Random(1)))
        assertNull(FilmLinkPairs.pickStart(film(1), TwoWay(listOf(1L to 2L)), Random(1)))
    }

    @Test fun aQuickCheckReportsTheHopsToAChosenTargetOrNothing() {
        assertEquals(3, FilmLinkPairs.check(film(1), film(4), line))
        assertNull(FilmLinkPairs.check(film(1), film(99), line))
        assertNull(FilmLinkPairs.check(film(1), film(1), line))   // the same film is not a route
    }

    @Test fun checkingIsBounded() {
        val endless = object : FilmGraph { var calls = 0; override fun neighbours(id: Long): List<LinkFilm>? { calls++; return (1L..16L).map { film(id * 100 + it) } } }
        FilmLinkPairs.check(film(1), film(-1), endless, budget = 12)
        assertTrue("calls=${endless.calls}", endless.calls <= 12)
    }

    private fun sug(id: Long, name: String, year: Int?, kind: Kind = Kind.MOVIE) = Suggestion(id, kind, name, year, null, "/p$id", null, emptyList(), null, false)

    @Test fun aSearchResultIsMatchedByTitleAndYear() {
        val results = listOf(sug(1, "Heat", 1986), sug(2, "Heat", 1995), sug(3, "Heat", 2022, Kind.SHOW))
        assertEquals(2L, FilmLinkRules.matchFilm(results, "heat", 1995)!!.tmdbId)
        assertEquals(1L, FilmLinkRules.matchFilm(results, "Heat", null)!!.tmdbId)   // no year: the first film of that name
        assertEquals(2L, FilmLinkRules.matchFilm(results, " Heat ", 1996)!!.tmdbId)   // a year out by one still matches
        assertNull(FilmLinkRules.matchFilm(results, "Something else", 1995))
        assertNull(FilmLinkRules.matchFilm(listOf(sug(3, "Heat", 2022, Kind.SHOW)), "Heat", 2022))   // series are not films
    }
}

class FilmLinkPlantTest {
    private fun sug(id: Long) = Suggestion(id, Kind.MOVIE, "T$id", 2000 + (id % 20).toInt(), null, "/p$id", null, emptyList(), null, false)
    private fun film(id: Long) = LinkFilm(id, "T$id", 2000 + (id % 20).toInt(), "/p$id", null, null)

    /** A line 1-2-3-4-5-6 (both ways) and a target 99 that nothing recommends, though it recommends 3, 4 and 5 itself. */
    private fun graph(): CachedFilmGraph {
        val edges = HashMap<Long, MutableList<Long>>()
        (1L..5L).forEach { a -> edges.getOrPut(a) { mutableListOf() }.add(a + 1); edges.getOrPut(a + 1) { mutableListOf() }.add(a) }
        edges[99] = mutableListOf(3, 4, 5)
        return CachedFilmGraph { id -> edges[id]?.map(::sug) }
    }

    @Test fun aTargetWithNoRouteIsPlantedAtFilmsItIsAssociatedWith() {
        val g = graph()
        assertNull(FilmLinkPairs.check(film(1), film(99), g))
        val pair = FilmLinkPairs.plant(film(1), film(99), g, Random(3))
        assertNotNull(pair)
        val sites = g.plants.keys
        assertTrue(sites.size >= FilmLinkPairs.MIN_PLANTS - 0 && sites.all { it in setOf(3L, 4L, 5L, 2L, 6L) })
        assertTrue(sites.containsAll(setOf(3L)))   // reachable and associated
        assertTrue(pair!!.shortest in 2..FilmLinkPairs.PLANT_DEPTH + 1)
        // and the planted game now has a route, of exactly the hops reported
        assertEquals(pair.shortest, FilmLinkPairs.check(film(1), film(99), g))
    }

    @Test fun manyFilmsLeadToThePlantSoOneWrongTurnIsNotTheEnd() {
        val g = graph()
        FilmLinkPairs.plant(film(1), film(99), g, Random(1))
        // from any film at the planted depth the target is within a hop or two
        assertTrue(g.plants.size >= 3)
        assertTrue(FilmLinkPairs.route(film(1), film(99), g)!!.last().tmdbId == 99L)
    }

    @Test fun plantsLeaveTheRecommendationsAloneAndCanBeTakenAway() {
        val g = graph()
        val before = g.neighbours(3)!!.map { it.tmdbId }
        FilmLinkPairs.plant(film(1), film(99), g, Random(2))
        assertTrue(g.neighbours(3)!!.any { it.tmdbId == 99L })
        g.unplant()
        assertEquals(before, g.neighbours(3)!!.map { it.tmdbId })
        assertNull(FilmLinkPairs.check(film(1), film(99), g))
    }

    @Test fun nothingIsPlantedWhenTheStartIsCutOff() {
        val g = CachedFilmGraph { _ -> emptyList() }
        assertNull(FilmLinkPairs.plant(film(1), film(99), g, Random(1)))
    }

    @Test fun aHintNamesTheNextFilmOnTheShortestRoute() {
        val g = graph(); g.plant(3, film(99))
        val route = FilmLinkPairs.route(film(1), film(99), g)!!
        assertEquals(listOf(2L, 3L, 99L), route.map { it.tmdbId })
        assertNull(FilmLinkPairs.route(film(1), film(50), g))
    }
}

class FilmLinkPlantEverywhereTest {
    private fun sug(id: Long) = Suggestion(id, Kind.MOVIE, "T$id", 2000, null, "/p$id", null, emptyList(), null, false)
    private fun film(id: Long) = LinkFilm(id, "T$id", 2000, "/p$id", null, null)

    /** A line 1-2-...-8 both ways; target 99 recommends 3 and 7 (7 is five hops out, beyond the planting depth). */
    private fun graph(extra: Map<Long, List<Long>> = emptyMap()): CachedFilmGraph {
        val edges = HashMap<Long, MutableList<Long>>()
        (1L..7L).forEach { a -> edges.getOrPut(a) { mutableListOf() }.add(a + 1); edges.getOrPut(a + 1) { mutableListOf() }.add(a) }
        edges[99] = mutableListOf(3, 7)
        extra.forEach { (k, v) -> edges.getOrPut(k) { mutableListOf() }.addAll(v) }
        return CachedFilmGraph { id -> edges[id]?.map(::sug) }
    }

    @Test fun theTargetIsOfferedOnEveryPageItIsReallyAssociatedWithNotJustOne() {
        val g = graph()
        FilmLinkPairs.plant(film(1), film(99), g, Random(1))
        assertEquals(setOf(3L, 7L), g.plants.keys)
        assertTrue(g.neighbours(3)!!.any { it.tmdbId == 99L })
        assertTrue(g.neighbours(7)!!.any { it.tmdbId == 99L })   // however far the player has wandered
        assertFalse(g.neighbours(4)!!.any { it.tmdbId == 99L })  // never on a page it has no real connection to
    }

    @Test fun aPageThatAlreadyListsTheTargetIsNotPlantedAgain() {
        val g = graph(mapOf(3L to listOf(99L)))
        FilmLinkPairs.plant(film(1), film(99), g, Random(1))
        assertEquals(1, g.neighbours(3)!!.count { it.tmdbId == 99L })
    }

    @Test fun theStartPageIsNeverPlantedSoTheGameIsAtLeastTwoHops() {
        val g = graph(mapOf(99L to listOf(1L)))   // 99 also recommends the start itself
        val pair = FilmLinkPairs.plant(film(1), film(99), g, Random(1))!!
        assertFalse(1L in g.plants.keys)
        assertTrue(pair.shortest >= 2)
    }

    @Test fun aNearbyAssociatedFilmIsPlantedAlongsideFarOnes() {
        val g = graph()   // 99 is associated with 3 and 7; start at 8, next to 7
        val pair = FilmLinkPairs.plant(film(8), film(99), g, Random(1))
        assertNotNull(pair)
        assertTrue(g.plants.keys.containsAll(setOf(3L, 7L)))
        assertTrue(pair!!.shortest in 2..FilmLinkPairs.PLANT_DEPTH + 1)
    }
}
