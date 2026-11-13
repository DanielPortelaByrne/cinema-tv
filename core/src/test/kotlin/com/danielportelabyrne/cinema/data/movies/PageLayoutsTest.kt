package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutsTest {
    @Test fun a_saved_layout_comes_back_as_it_was() {
        val home = listOf(Section(SectionType.MY_LIBRARY), Section(SectionType.TOP10, "svc:nfx"), Section(SectionType.GENRE, "hrr:films"))
        val back = PageLayouts.parse(PageLayouts.encode(mapOf(LayoutPage.HOME to home)))
        assertEquals(home, back[LayoutPage.HOME])
        assertEquals(PageLayouts.defaults(LayoutPage.FILMS), PageLayouts.of(LayoutPage.FILMS, back))
    }

    @Test fun unknown_rows_are_dropped_not_fatal() {
        val back = PageLayouts.parse("""{"home":["NOPE","TOP10|all","TOP10|all"]}""")
        assertEquals(listOf(Section(SectionType.TOP10, "all")), back[LayoutPage.HOME])
        assertTrue(PageLayouts.parse("not json").isEmpty())
    }

    @Test fun search_finds_a_top_10_of_a_service_and_skips_rows_already_there() {
        val found = PageLayouts.search("top 10 netflix", emptyList()).map { it.label }
        assertEquals(listOf("Top 10 on Netflix"), found)
        assertTrue(PageLayouts.search("my library", listOf(Section(SectionType.MY_LIBRARY))).none { it.section.type == SectionType.MY_LIBRARY })
        assertTrue(PageLayouts.search("horror films", emptyList()).any { it.label == "Horror films" })
    }

    @Test fun every_option_has_a_name_and_catalogue_options_ask_for_rows() {
        PageLayouts.options().forEach { o ->
            assertTrue(o.label.isNotBlank())
            if (o.section.type.catalogue && o.section.type != SectionType.DOCS_ON_FOLLOWED) assertTrue(o.label, PageLayouts.specs(o.section).isNotEmpty())
        }
    }
}

class WatchlistKindTest {
    @Test fun a_series_keeps_its_kind_and_older_entries_count_as_films() {
        val list = listOf(WatchlistFilm("tt1", "Show", 2020, null, Kind.SHOW), WatchlistFilm("tt2", "Film", 2019, null))
        val back = Watchlist.parse(Watchlist.toJson(list))
        assertEquals(list, back)
        assertTrue(back[0].isSeries); assertTrue(!back[1].isSeries)
        assertEquals(listOf("Watchlist: films", "Watchlist: series"),
            PageLayouts.search("watchlist", listOf(Section(SectionType.WATCHLIST))).map { it.label })
    }
}

class PageLayoutsPersistTest {
    @Test fun layouts_saved_in_settings_come_back_on_the_next_launch() {
        val platform = com.danielportelabyrne.cinema.platform.MemoryPlatform()
        val home = listOf(Section(SectionType.WATCHLIST, "series"), Section(SectionType.LIBRARY_FILMS), Section(SectionType.TOP10, "svc:nfx"))
        val films = listOf(Section(SectionType.CONTINUE))
        com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform).pageLayouts =
            PageLayouts.encode(mapOf(LayoutPage.HOME to home, LayoutPage.FILMS to films))
        // A fresh settings object over the same store, as after the app restarts or updates.
        val back = PageLayouts.parse(com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform).pageLayouts)
        assertEquals(home, PageLayouts.of(LayoutPage.HOME, back))
        assertEquals(films, PageLayouts.of(LayoutPage.FILMS, back))
        assertEquals(PageLayouts.defaults(LayoutPage.SERIES), PageLayouts.of(LayoutPage.SERIES, back))
    }
}

class MixesTest {
    @Test fun a_documentary_row_can_take_any_subcategory() {
        val m = Mix("top10", "all", "doc", "crm", service = "nfx")
        assertEquals("Top 10 crime documentaries on Netflix", Mixes.label(m))
        val spec = Mixes.spec(m)!!
        assertEquals("doc", spec.genre); assertEquals(listOf("crm"), spec.alsoGenres); assertTrue(spec.ranked)
        assertEquals(listOf("nfx"), spec.packages)
        assertEquals(m, Mix.decode(m.encode()))
        assertEquals("Popular British & Irish comedy series", Mixes.label(Mix("popular", "series", "cmy", made = "gb")))
        assertEquals(listOf("GB", "IE"), Mixes.spec(Mix("popular", "series", "cmy", made = "gb"))!!.madeIn)
        assertEquals("Trending music films", Mixes.label(Mix("trending", "films", "msc")))
    }

    @Test fun search_finds_subcategory_mixes_with_plurals() {
        val found = PageLayouts.search("sports documentaries", emptyList()).map { it.label }
        assertTrue(found.toString(), "Trending sport documentaries" in found)
        assertTrue(PageLayouts.search("top 10 crime documentary series", emptyList()).any { it.label == "Top 10 crime documentary series" })
        assertTrue(PageLayouts.search("music", emptyList()).any { it.label == "Popular music films" })
        // An old documentary row opens in the builder as a documentary mix.
        assertEquals(Mix("popular", "films", "doc"), PageLayouts.asMix(Section(SectionType.DOCS, "films")))
    }
}

class GenrePagesTest {
    @Test fun a_narrowed_genre_page_keeps_its_kind_and_subcategory() {
        assertEquals("Comedy series", Mixes.title("cmy", null, "series"))
        assertEquals("Crime documentaries", Mixes.title("doc", "crm", "all"))
        assertEquals("Crime documentaries", Mixes.title("crm", "doc", "all"))
        val series = Mixes.page("cmy", null, "series")
        assertTrue(series.all { it.kinds == listOf(Kind.SHOW) && it.genre == "cmy" })
        assertTrue(series.first().ranked)
        // Crime documentaries are asked as documentaries and kept to crime, whichever way round they were chosen.
        val docs = Mixes.page("crm", "doc", "all")
        assertTrue(docs.all { it.genre == "doc" && it.alsoGenres == listOf("crm") })
        assertTrue(docs.any { it.kinds == listOf(Kind.MOVIE) } && docs.any { it.kinds == listOf(Kind.SHOW) })
    }
}

class CastMemberTest {
    @Test fun reads_billed_characters_with_photos() {
        val cast = CastMember.parse("""{"credits":{"cast":[
            {"character":"Walter White / Heisenberg","name":"Bryan Cranston","profilePath":"/a.jpg"},
            {"character":"Skyler White","name":"Anna Gunn","profilePath":null},
            {"character":"Narrator (voice)","name":"Someone","profilePath":"/c.jpg"},
            {"character":"","name":"Extra","profilePath":"/d.jpg"}]}}""")
        assertEquals(listOf("Walter White", "Narrator"), cast.map { it.character })
        assertEquals("/a.jpg", cast[0].profilePath)
    }
}

class CategoryPagesTest {
    @Test fun a_category_page_asks_for_a_hundred_at_a_time_and_follows_the_cursor() {
        val spec = Mixes.spec(Mix("popular", "series", "cmy"))!!
        val first = org.json.JSONObject(Catalog.pageBody(spec, null))
        assertEquals(100, first.getJSONObject("variables").getInt("n"))
        assertTrue(first.getString("query").contains("after: \$a") && first.getString("query").contains("pageInfo"))
        assertEquals("abc", org.json.JSONObject(Catalog.pageBody(spec, "abc")).getJSONObject("variables").getString("a"))
        val (titles, next) = Catalog.parsePage("""{"data":{"popularTitles":{"pageInfo":{"endCursor":"c2","hasNextPage":true},"edges":[
            {"node":{"id":"ts1","objectType":"SHOW","content":{"title":"A","genres":[{"shortName":"cmy","translation":"Comedy"}]}}}]}}}""")
        assertEquals(listOf("A"), titles.map { it.name }); assertEquals("c2", next)
        assertEquals(null, Catalog.parsePage("""{"data":{"popularTitles":{"pageInfo":{"endCursor":"c3","hasNextPage":false},"edges":[]}}}""").second)
    }
}
