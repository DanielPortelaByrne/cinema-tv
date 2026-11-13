package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    private val nick = Channel("Q154958", "Nickelodeon", "American pay television channel", "https://commons.wikimedia.org/wiki/Special:FilePath/Nick.svg?width=240", 1382)
    private val citv = Channel("Q1997305", "CITV", "British television channel")

    // --- Following

    @Test fun channelsAreFollowedKeptAndNeverDuplicated() {
        val one = FollowedChannels.follow(emptyList(), nick)
        assertEquals(one, FollowedChannels.follow(one, nick))
        val two = FollowedChannels.follow(one, citv)
        assertEquals(listOf("Q154958", "Q1997305"), two.map { it.id })
        assertTrue(FollowedChannels.isFollowing(two, "Q1997305"))
        assertEquals(listOf(citv), FollowedChannels.unfollow(two, "Q154958"))
        // What is kept is what is needed to show the channel again, not the show count.
        assertEquals(two.map { it.copy(shows = 0) }, FollowedChannels.parse(FollowedChannels.toJson(two)))
        assertEquals(emptyList<Channel>(), FollowedChannels.parse(null))
        assertEquals(emptyList<Channel>(), FollowedChannels.parse("not json"))
    }

    @Test fun theSettingKeepsFollowedChannelsAndNoneIsTheStart() {
        val s = CinemaSettings(MemoryPlatform())
        assertEquals(emptyList<Channel>(), s.followedChannels)
        s.followedChannels = listOf(citv)
        assertEquals(listOf(citv), s.followedChannels)
    }

    // --- Finding channels

    private val search = """{"search":[{"id":"Q154958","label":"Nickelodeon","description":"American pay television channel"},
        {"id":"Q999","label":"Nick Cave"},{"id":"bogus","label":"x"},{"id":"Q7","label":""}]}"""

    @Test fun readsASearchAndIgnoresWhatIsNotAnEntity() {
        assertEquals(listOf(Triple("Q154958", "Nickelodeon", "American pay television channel"), Triple("Q999", "Nick Cave", null)), ChannelData.parseSearch(search))
        assertTrue(ChannelData.parseSearch("nope").isEmpty())
    }

    @Test fun keepsOnlyTheCandidatesThatAreChannelsWithShows() {
        val counts = """{"results":{"bindings":[
            {"ch":{"value":"http://www.wikidata.org/entity/Q154958"},"n":{"value":"1382"},"lg":{"value":"http://commons.wikimedia.org/wiki/Special:FilePath/Nick%20logo.svg"}},
            {"ch":{"value":"http://www.wikidata.org/entity/Q1997305"},"n":{"value":"52"}}]}}"""
        val cands = ChannelData.parseSearch(search) + Triple("Q1997305", "CITV", "British television channel")
        val got = ChannelData.channels(cands, counts)
        assertEquals(listOf("Nickelodeon", "CITV"), got.map { it.name })      // Nick Cave has no shows
        assertEquals(1382, got[0].shows)
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Nick%20logo.svg?width=240", got[0].logo)
        assertNull(got[1].logo)
        assertTrue(ChannelData.channels(cands, "garbage").isEmpty())
    }

    @Test fun onlyRealEntityIdsGoIntoAQuery() {
        assertTrue(ChannelData.countQuery(listOf("Q1", "Q2\" } DROP", "Q3")).contains("wd:Q1 wd:Q3 }"))
        assertFalse(ChannelData.countQuery(listOf("Q1", "x y")).contains("x y"))
        assertTrue(runCatching { ChannelData.showsQuery("Q1 . ?x ?y ?z") }.isFailure)
        assertTrue(ChannelData.showsQuery("Q154958").contains("wd:Q154958"))
    }

    // --- Listing a channel's shows

    private fun row(imdb: String, name: String, year: Int?, links: Int) =
        """{"imdb":{"value":"$imdb"},"sLabel":{"value":"$name"},${if (year != null) """"year":{"value":"$year"},""" else ""}"links":{"value":"$links"}}"""
    private fun shows(vararg r: String) = """{"results":{"bindings":[${r.joinToString(",")}]}}"""

    @Test fun showsAreBestKnownFirstWithDuplicatesAndNonShowsDropped() {
        val got = ChannelData.parseShows(shows(
            row("tt2", "Art Attack", 1990, 16), row("tt1", "SpongeBob", 1999, 162), row("tt3", "Art Attack", 1990, 15),
            row("nm9", "A Person", 1950, 99), row("tt4", "Q12345", 2000, 50), row("tt5", "Rugrats", null, 49)))
        assertEquals(listOf("SpongeBob", "Rugrats", "Art Attack"), got.map { it.name })   // one Art Attack; not the person, not the unnamed
        assertEquals("tt2", got.last().imdbId)
        assertTrue(ChannelData.parseShows("nope").isEmpty())
    }

    private val library = listOf(
        ChannelShow("a", "Old Best", 1985, 90), ChannelShow("b", "Nineties", 1995, 80), ChannelShow("c", "Nineties 2", 1999, 85),
        ChannelShow("d", "New", 2023, 40), ChannelShow("e", "No Year", null, 70), ChannelShow("f", "Newer", 2021, 60))

    @Test fun anEraPicksWhatBeganThenBestKnownFirst() {
        fun names(q: ChannelQuery, n: Int = 10) = ChannelData.select(library, q, n).map { it.name }
        assertEquals(listOf("Old Best", "Nineties 2", "Nineties", "No Year", "Newer", "New"), names(ChannelQuery("Q1")))
        assertEquals(listOf("Nineties 2", "Nineties"), names(ChannelQuery("Q1", 1990, 1999)))
        assertEquals(listOf("Newer", "New"), names(ChannelQuery("Q1", 2020, null)))
        assertEquals(listOf("Old Best"), names(ChannelQuery("Q1", null, 1989)))
        assertEquals(listOf("Old Best", "Nineties 2"), names(ChannelQuery("Q1"), 2))
    }

    // --- Cinemeta

    @Test fun aShowBecomesATitleWithAMediumPoster() {
        val t = ChannelData.parseMeta("""{"meta":{"name":"SpongeBob SquarePants","year":"1999–","description":"Under the sea","runtime":"10 min","imdbRating":"8.2",
            "poster":"https://images.metahub.space/poster/small/tt0206512/img","background":"https://images.metahub.space/background/medium/tt0206512/img",
            "moviedb_id":387,"genres":["Animation","Comedy"]}}""", "tt0206512")!!
        assertEquals("SpongeBob SquarePants", t.name); assertEquals(1999, t.year); assertEquals(10, t.runtimeMin); assertEquals(8.2, t.imdbScore!!, 0.0)
        assertEquals("https://images.metahub.space/poster/medium/tt0206512/img", t.posterUrl)
        assertEquals("tt0206512", t.imdbId); assertEquals("387", t.tmdbId); assertEquals(Kind.SHOW, t.kind)
        assertTrue(t.services.isEmpty())
        assertNull(ChannelData.parseMeta("""{"meta":{"name":"No picture"}}""", "tt1"))   // a grey box helps nobody
        assertNull(ChannelData.parseMeta("nope", "tt1"))
    }

    // --- The channel's page

    @Test fun aChannelPageLeadsWithItsBestAndHasARowForEachEra() {
        val page = Catalog.channelPage(nick, thisYear = 2026)
        assertEquals(listOf("Best of Nickelodeon, all time", "Recent favourites", "The 2010s", "The 2000s", "The 1990s", "The 1980s", "The 1970s", "The 1960s", "The 1950s"), page.map { it.heading })
        assertTrue(page[0].ranked); assertFalse(page[1].ranked)
        assertEquals(ChannelQuery("Q154958", null, null), page[0].channel)
        assertEquals(ChannelQuery("Q154958", 2021, null), page[1].channel)
        assertEquals(ChannelQuery("Q154958", 1990, 1999), page[4].channel)
        assertTrue(page.all { it.packages.isEmpty() && it.kinds == listOf(Kind.SHOW) })
        // The card fans the same shows as the page's first row, so the page costs nothing more.
        assertEquals(page[0].channel, Catalog.cardShelf(nick).channel); assertFalse(Catalog.cardShelf(nick).ranked)
    }

    // --- Ranking channels beside services

    @Test fun channelsAndServicesAreRankedByTheSameScore() {
        assertEquals(100, ServiceDirectory.score("Nickelodeon", "nickelodeon"))
        assertEquals(85, ServiceDirectory.score("Nickelodeon", "nick"))
        assertEquals(70, ServiceDirectory.score("Cartoon Network", "network"))
        assertEquals(0, ServiceDirectory.score("Nickelodeon", "disney"))
        assertEquals(0, ServiceDirectory.score("Tubi TV", "itv"))
        assertTrue(ServiceDirectory.scoreOf(Catalog.CHANNEL_4, "e4") > ServiceDirectory.scoreOf(Catalog.ITVX, "e4"))
    }
}
