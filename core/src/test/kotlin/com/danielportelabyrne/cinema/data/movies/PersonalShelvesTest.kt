package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalShelvesTest {
    private fun fav(kind: Kind = Kind.MOVIE, tmdb: String? = null, imdb: String? = null, jf: String? = null, name: String = "Heat") =
        Favourite(kind, tmdb, imdb, jf, name, 1995, null, null, 0)

    private fun facts(kind: Kind = Kind.MOVIE, tmdb: String? = null, imdb: String? = null, genres: List<String>? = null, services: List<String>? = null, at: Long = 1) =
        TitleFacts(kind, tmdb, imdb, null, genres, services, at)

    private fun film(tmdb: String, at: Long, name: String = "Film $tmdb") =
        PlayedEntry(Kind.MOVIE, tmdbId = tmdb, title = name, playedAt = at, positionMs = 600_000, durationMs = 6_000_000)

    private fun episode(seriesTmdb: String?, s: Int, e: Int, at: Long, seriesImdb: String? = null, seriesJf: String? = null) =
        PlayedEntry(Kind.SHOW, seriesTmdbId = seriesTmdb, seriesImdbId = seriesImdb, seriesJellyfinId = seriesJf, title = "Show", season = s, episode = e,
            playedAt = at, positionMs = 600_000, durationMs = 3_000_000)

    private fun lib(id: String, type: String, tmdb: String? = null, imdb: String? = null, genres: List<String> = emptyList(), missing: Boolean = false) =
        JellyfinItem(id, "Name", null, type, null, tmdbId = tmdb, imdbId = imdb, genres = genres, missing = missing)

    // --- Membership by page

    @Test fun homeKeepsEverythingEvenWithNothingKnown() {
        val f = PersonalFilter(PersonalPage.Home, emptyList(), null)
        assertTrue(f.keeps(fav(tmdb = "1")))
        assertTrue(f.keeps(fav(Kind.SHOW, imdb = "tt9")))
        assertTrue(f.unknown.isEmpty())
    }

    @Test fun filmsAndSeriesGoByKindAlone() {
        val films = PersonalFilter(PersonalPage.Films, emptyList(), null)
        assertTrue(films.keeps(fav(Kind.MOVIE, tmdb = "1")))
        assertFalse(films.keeps(fav(Kind.SHOW, tmdb = "2")))
        assertFalse(films.keeps(episode("2", 1, 1, 10)))
        val series = PersonalFilter(PersonalPage.Series, emptyList(), null)
        assertTrue(series.keeps(episode("2", 1, 1, 10)))
        assertTrue(series.keeps(fav(Kind.SHOW, imdb = "tt2")))
        assertFalse(series.keeps(film("1", 10)))
        assertTrue(films.unknown.isEmpty() && series.unknown.isEmpty())
    }

    @Test fun documentariesNeedTheGenreAndUnknownIsLeftOutAndReported() {
        val known = listOf(facts(tmdb = "1", genres = listOf("Documentary", "History")), facts(tmdb = "2", genres = listOf("Drama")))
        val f = PersonalFilter(PersonalPage.Documentaries, known, null)
        assertTrue(f.keeps(fav(tmdb = "1")))
        assertFalse(f.keeps(fav(tmdb = "2")))
        assertFalse(f.keeps(fav(tmdb = "3")))
        assertEquals(listOf(Kind.MOVIE to TitleRef("3", null, null)), f.unknown.toList())
    }

    @Test fun anEmptyGenreListIsNotKnown() {
        val f = PersonalFilter(PersonalPage.InGenre("hrr"), listOf(facts(tmdb = "1", genres = emptyList())), null)
        assertEquals(Membership.UNKNOWN, f.membership(Kind.MOVIE, TitleRef("1")))
    }

    @Test fun genreNamesFromEverySourceMatchTheirCode() {
        assertTrue(PersonalShelves.hasGenre("scf", listOf("Science-Fiction")))
        assertTrue(PersonalShelves.hasGenre("scf", listOf("Science Fiction")))
        assertTrue(PersonalShelves.hasGenre("scf", listOf("Sci-Fi & Fantasy")))
        assertTrue(PersonalShelves.hasGenre("fnt", listOf("Sci-Fi & Fantasy")))
        assertTrue(PersonalShelves.hasGenre("act", listOf("Action & Adventure")))
        assertTrue(PersonalShelves.hasGenre("trl", listOf("Mystery & Thriller")))
        assertTrue(PersonalShelves.hasGenre("fml", listOf("Kids & Family")))
        assertTrue(PersonalShelves.hasGenre("doc", listOf(" documentary ")))
        assertFalse(PersonalShelves.hasGenre("hrr", listOf("Thriller")))
        assertFalse(PersonalShelves.hasGenre("zzz", listOf("Horror")))
    }

    @Test fun genrePageKeepsOnlyThatGenre() {
        val known = listOf(facts(Kind.SHOW, tmdb = "5", genres = listOf("Horror")), facts(Kind.SHOW, tmdb = "6", genres = listOf("Comedy")))
        val f = PersonalFilter(PersonalPage.InGenre("hrr"), known, null)
        assertTrue(f.keeps(episode("5", 2, 3, 10)))
        assertFalse(f.keeps(episode("6", 1, 1, 10)))
    }

    @Test fun servicePageMatchesTheServiceByItsNamesAndLeavesOutUnknown() {
        assertTrue(PersonalShelves.hasService("Prime Video", listOf("Amazon Prime Video")))
        assertTrue(PersonalShelves.hasService("Disney+", listOf("Disney Plus")))
        assertTrue(PersonalShelves.hasService("HBO Max", listOf("Max")))
        assertTrue(PersonalShelves.hasService("Apple TV+", listOf("Apple TV Plus")))
        assertFalse(PersonalShelves.hasService("Netflix", listOf("Prime Video")))
        assertTrue(PersonalShelves.hasService("Channel 5", listOf("My5")))
        assertTrue(PersonalShelves.hasService("Channel 5", listOf("5")))
        assertTrue(PersonalShelves.hasService("RTÉ Player", listOf("RTE Player")))
        assertTrue(PersonalShelves.hasService("RTÉ Player", listOf("RTÉ Player")))
        assertTrue(PersonalShelves.hasService("NOW", listOf("Now TV")))
        assertFalse(PersonalShelves.hasService("Channel 4", listOf("Channel 5")))
        val known = listOf(facts(tmdb = "1", services = listOf("Netflix")), facts(tmdb = "2", services = emptyList()), facts(tmdb = "3", genres = listOf("Drama")))
        val f = PersonalFilter(PersonalPage.OnService("Netflix"), known, null)
        assertTrue(f.keeps(fav(tmdb = "1")))
        assertFalse(f.keeps(fav(tmdb = "2")))
        assertFalse(f.keeps(fav(tmdb = "3")))   // genres known, services not
        assertEquals(setOf(Kind.MOVIE to TitleRef("3")), f.unknown)
    }

    @Test fun servicesComeFromTheNewestRecordThatKnowsThem() {
        val known = listOf(facts(tmdb = "1", services = listOf("Netflix"), at = 1), facts(imdb = "tt1", services = listOf("Prime Video"), at = 5),
            facts(tmdb = "1", imdb = "tt1", genres = listOf("Drama"), at = 9))
        val merged = PersonalShelves.factsFor(Kind.MOVIE, TitleRef("1", "tt1"), known)!!
        assertEquals(listOf("Prime Video"), merged.services)
        assertEquals(listOf("Drama"), merged.genres)
    }

    // --- Identity

    @Test fun matchingIsByIdNeverByNameAndADisagreeingIdIsAnotherTitle() {
        assertTrue(PersonalShelves.matches(Kind.MOVIE, TitleRef("1", null), facts(tmdb = "1", imdb = "tt1")))
        assertFalse(PersonalShelves.matches(Kind.MOVIE, TitleRef("1", "tt2"), facts(tmdb = "1", imdb = "tt1")))
        assertFalse(PersonalShelves.matches(Kind.SHOW, TitleRef("1"), facts(Kind.MOVIE, tmdb = "1")))
        assertFalse(PersonalShelves.matches(Kind.MOVIE, TitleRef(), facts(tmdb = "1")))
    }

    @Test fun aStreamedFavouriteFindsItsDetailsThroughItsImdbAlias() {
        val f = PersonalFilter(PersonalPage.Documentaries, listOf(facts(tmdb = "77", imdb = "tt77", genres = listOf("Documentary"))), null)
        assertTrue(f.keeps(fav(imdb = "tt77")))
    }

    @Test fun anEpisodeGoesWhereverItsSeriesGoes() {
        val known = listOf(facts(Kind.SHOW, imdb = "tt5", genres = listOf("Documentary")))
        val f = PersonalFilter(PersonalPage.Documentaries, known, null)
        assertTrue(f.keeps(episode(null, 3, 2, 10, seriesImdb = "tt5")))
        // The episode's own ids say nothing about its series.
        assertFalse(f.keeps(PlayedEntry(Kind.SHOW, imdbId = "tt5", jellyfinId = "e1", title = "Show", season = 1, episode = 1, playedAt = 5)))
    }

    // --- My library

    @Test fun libraryKeepsOnlyWhatTheLoadedLibraryHolds() {
        val library = listOf(lib("AB-CD", "Movie", tmdb = "1"), lib("s1", "Series", imdb = "tt5"), lib("m9", "Movie", tmdb = "9", missing = true))
        val f = PersonalFilter(PersonalPage.Library, emptyList(), library)
        assertTrue(f.keeps(fav(tmdb = "1")))                 // a streamed favourite of a library film
        assertTrue(f.keeps(fav(jf = "abcd")))                 // the same film by its library id, written another way
        assertTrue(f.keeps(episode(null, 1, 2, 10, seriesImdb = "tt5")))
        assertFalse(f.keeps(fav(tmdb = "2")))
        assertFalse(f.keeps(fav(tmdb = "9")))                 // a missing placeholder is not held
        assertFalse(f.keeps(fav(Kind.SHOW, tmdb = "1")))      // a series is not the film
        assertTrue(f.unknown.isEmpty())
    }

    @Test fun libraryNotLoadedIsUnknownAndLeftOut() {
        val f = PersonalFilter(PersonalPage.Library, listOf(facts(tmdb = "1", genres = listOf("Drama"))), null)
        assertFalse(f.keeps(fav(tmdb = "1")))
        assertEquals(1, f.unknown.size)
    }

    @Test fun libraryGenresDecideAGenrePageForATitleOnlyTheLibraryKnows() {
        val f = PersonalFilter(PersonalPage.InGenre("hrr"), emptyList(), listOf(lib("x1", "Movie", genres = listOf("Horror"))))
        assertTrue(f.keeps(fav(jf = "x1")))
        assertFalse(PersonalFilter(PersonalPage.InGenre("hrr"), emptyList(), null).keeps(fav(jf = "x1")))
    }

    @Test fun aLibraryContinueCardIsALibraryTitle() {
        val e = ContinueEntry("show:jf=ep-e1", Kind.SHOW, ContinueAction.RESUME, "Show", TitleRef(), ContinueEpisode(null, null, jellyfinId = "e1"),
            null, 10, 600_000, 3_000_000, ContinueSource.Library("e1"))
        assertTrue(PersonalFilter(PersonalPage.Library, emptyList(), emptyList()).keeps(e))
        assertFalse(PersonalFilter(PersonalPage.Library, emptyList(), emptyList()).keeps(e.copy(source = ContinueSource.PickSource)))
    }

    // --- Shelves: filtered before their limits

    @Test fun favouritesKeepTheirOrderAndDropOtherPagesTitles() {
        val list = listOf(fav(Kind.SHOW, tmdb = "3"), fav(tmdb = "2"), fav(Kind.SHOW, tmdb = "1"), fav(tmdb = "2"))
        assertEquals(listOf("2"), PersonalFilter(PersonalPage.Films, emptyList(), null).favourites(list).map { it.tmdbId })
        assertEquals(listOf("3", "1"), PersonalFilter(PersonalPage.Series, emptyList(), null).favourites(list).map { it.tmdbId })
    }

    @Test fun favouriteFocusFollowsTheTitleThenTheNearestSurvivor() {
        val a = fav(tmdb = "a"); val b = fav(tmdb = "b"); val c = fav(tmdb = "c")
        assertEquals(0, Favourites.focusAfter(listOf(a, b, c), 1, listOf(b, c)))
        assertEquals(1, Favourites.focusAfter(listOf(a, b, c), 1, listOf(a, c)))   // b went: c, which followed it
        assertEquals(0, Favourites.focusAfter(listOf(a, b, c), 2, listOf(a)))      // nothing after: the one before
        assertEquals(-1, Favourites.focusAfter(listOf(a, b), 0, emptyList()))
        assertEquals(-1, Favourites.focusAfter(listOf(a), 4, listOf(a)))
    }

    @Test fun recentlyWatchedIsFilteredBeforeItsLimit() {
        val history = listOf(episode("50", 1, 1, 100), episode("50", 1, 2, 90), film("1", 80), film("2", 70), film("3", 60))
        val films = PersonalFilter(PersonalPage.Films, emptyList(), null)
        assertEquals(listOf("1", "2"), RecentlyWatched.shelf(history, limit = 2, keep = films::keeps).map { it.entry.tmdbId })
        assertEquals(2, RecentlyWatched.shelf(history, limit = 2).size)
    }

    @Test fun recentlyWatchedNeverFallsBackToAnOlderRecordOfATitleItLeftOut() {
        val newer = PlayedEntry(Kind.MOVIE, tmdbId = "1", imdbId = "tt1", title = "A", playedAt = 100, positionMs = 600_000, durationMs = 6_000_000)
        val older = PlayedEntry(Kind.MOVIE, tmdbId = "1", title = "A", playedAt = 50, positionMs = 60_000, durationMs = 6_000_000)
        val shelf = RecentlyWatched.shelf(listOf(newer, older), keep = { it.imdbId == null })
        assertTrue(shelf.isEmpty())
    }

    @Test fun recentlyWatchedFiltersTheEnrichedRecord() {
        // A stream record knows its series only by IMDb id; the library supplies the TMDB id the facts are kept under.
        val history = listOf(episode(null, 1, 1, 10, seriesImdb = "tt5"))
        val known = listOf(KnownTitle(Kind.SHOW, "Show", tmdbId = "5", imdbId = "tt5"))
        val f = PersonalFilter(PersonalPage.InGenre("crm"), listOf(facts(Kind.SHOW, tmdb = "5", genres = listOf("Crime"))), null)
        assertEquals(1, RecentlyWatched.shelf(history, known, keep = f::keeps).size)
    }

    @Test fun continueWatchingIsFilteredBeforeItsLimit() {
        val history = (1..6).map { film("$it", 100L - it) } + listOf(episode("50", 1, 1, 200))
        val inputs = ContinueInputs("", "2026-11-11", history)
        val all = ContinueEngine.select(inputs, { null }, limit = 3)
        assertEquals(Kind.SHOW, all.first().kind)
        val docs = PersonalFilter(PersonalPage.Documentaries, (4..6).map { facts(tmdb = "$it", genres = listOf("Documentary")) }, null)
        assertEquals(listOf("4", "5", "6"), ContinueEngine.select(inputs, { null }, limit = 3, keep = docs::keeps).map { it.ids.tmdbId })
    }

    // --- The kept details

    @Test fun factsStoreKeepsTmdbAndImdbOnlyAndSurvivesReload() {
        val p = MemoryPlatform()
        val s = TitleFactsStore(p) { 7 }
        assertTrue(s.learn(listOf(TitleFacts(Kind.MOVIE, "1", "tt1", "jf-1", listOf("Drama"), listOf("Netflix"), 0))))
        assertTrue(TitleFactsStore(p).all().isEmpty())   // held in memory until flushed
        s.flush()
        val back = TitleFactsStore(p).all().single()
        assertNull(back.jellyfinId)
        assertEquals(TitleFacts(Kind.MOVIE, "1", "tt1", null, listOf("Drama"), listOf("Netflix"), 7), back)
    }

    @Test fun learningReplacesTheOldRecordButKeepsWhatOnlyItKnew() {
        val old = listOf(facts(tmdb = "1", imdb = "tt1", genres = listOf("Drama"), services = listOf("Netflix"), at = 1))
        val merged = TitleFactsStore.merge(old, listOf(facts(tmdb = "1", genres = listOf("Crime"), at = 5)))
        assertEquals(listOf(facts(tmdb = "1", imdb = "tt1", genres = listOf("Crime"), services = listOf("Netflix"), at = 5)), merged)
        // Another title sharing nothing is kept beside it; one that disagrees on an id is not merged into it.
        assertEquals(3, TitleFactsStore.merge(merged, listOf(facts(tmdb = "2", at = 2), facts(tmdb = "1", imdb = "tt9", genres = listOf("Horror"), at = 3))).size)
    }

    @Test fun servicesLearnedInAnotherCountryJoinTheOnesKnownHere() {
        val old = listOf(facts(tmdb = "1", genres = listOf("Drama"), services = listOf("BBC iPlayer"), at = 1))
        val abroad = listOf(facts(tmdb = "1", services = listOf("RTÉ Player", "BBC iPlayer"), at = 5))
        assertEquals(listOf("BBC iPlayer", "RTÉ Player"), TitleFactsStore.merge(old, abroad, addServices = true).single().services)
        // Learned at home, they replace as before.
        assertEquals(listOf("RTÉ Player", "BBC iPlayer"), TitleFactsStore.merge(old, abroad).single().services)
        // Nothing known before: just the new ones. And the same again changes nothing.
        assertEquals(listOf("RTÉ Player", "BBC iPlayer"), TitleFactsStore.merge(emptyList(), abroad, addServices = true).single().services)
        val s = TitleFactsStore(MemoryPlatform()) { 5 }
        s.learn(old); assertTrue(s.learn(abroad, addServices = true)); assertFalse(s.learn(abroad, addServices = true))
    }

    @Test fun learningTheSameAgainChangesNothing() {
        val p = MemoryPlatform()
        val s = TitleFactsStore(p) { 5 }
        s.learn(listOf(facts(tmdb = "1", genres = listOf("Drama"), at = 5)))
        val before = s.all()
        assertFalse(s.learn(listOf(facts(tmdb = "1", genres = listOf("Drama"), at = 9))))
        assertTrue(before === s.all())
    }

    @Test fun seerrDetailsGiveGenresAndTheCountrysServices() {
        val json = """{"id":5,"genres":[{"id":99,"name":"Documentary"}],"externalIds":{"imdbId":"tt5"},
            "watchProviders":[{"iso_3166_1":"US","flatrate":[{"id":1,"name":"Hulu"}]},
                              {"iso_3166_1":"GB","flatrate":[{"id":8,"name":"Netflix"}],"ads":[{"id":9,"name":"Amazon Prime Video"}],"buy":[{"id":2,"name":"Apple TV"}]}]}"""
        val f = SeerrApi.facts(json, Kind.SHOW, 5, "GB", 3)!!
        assertEquals(TitleFacts(Kind.SHOW, "5", "tt5", null, listOf("Documentary"), listOf("Netflix", "Prime Video"), 3), f)
        assertEquals(emptyList<String>(), SeerrApi.facts(json, Kind.SHOW, 5, "IE", 3)!!.services)
        assertNull(SeerrApi.facts("""{"genres":[]}""", Kind.MOVIE, 1, "GB", 3)!!.services)
        assertNull(SeerrApi.facts("not json", Kind.MOVIE, 1, "GB", 3))
    }
}
