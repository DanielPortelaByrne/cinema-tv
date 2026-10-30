package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small samples shaped like Letterboxd's pages (trimmed from real ones, with made-up members). */
class LbParseTest {
    private fun poster(slug: String, name: String) =
        """<li class="posteritem"><div class="react-component" data-component-class="LazyPoster" data-item-name="$name" data-item-slug="$slug" data-item-link="/film/$slug/"><div class="poster film-poster"><img src="x.png"></div></div>"""

    @Test fun poster_grids_give_name_year_rating_and_like() {
        val html = poster("heat-1995", "Heat (1995)") +
            """<p class="poster-viewingdata"><span class="rating -micro rated-9">★★★★½</span><span class="like liked-micro has-icon icon-liked icon-16"></span></p></li>""" +
            poster("alien", "Alien (1979)") + """<p class="poster-viewingdata"></p></li>""" +
            poster("heat-1995", "Heat (1995)")
        val films = Lb.films(html)
        assertEquals(listOf("heat-1995", "alien"), films.map { it.slug })   // a repeat is kept once
        assertEquals("Heat", films[0].name); assertEquals(1995, films[0].year)
        assertEquals(9, films[0].rating); assertEquals("★★★★½", films[0].stars); assertTrue(films[0].liked)
        assertNull(films[1].rating); assertFalse(films[1].liked)
    }

    @Test fun stars_and_half_stars_convert_both_ways() {
        assertEquals(7, Lb.halves("★★★½")); assertEquals(1, Lb.halves("½")); assertNull(Lb.halves("great"))
        assertEquals("★★★½", Lb.stars(7)); assertEquals("½", Lb.stars(1))
    }

    @Test fun a_profile_gives_name_stats_favourites_and_recent() {
        val html = """<section class="profile-header"><div class="profile-avatar"><span class="avatar"><img src="https://a/x.jpg?a=1&amp;b=2"></span></div>
            <h1><span class="displayname tooltip"><span class="label">Sam</span></span></h1>
            <h4 class="profile-statistic statistic"><a><span class="value">1,368</span><span class="definition title-all-caps">Films</span></a></h4>
            <h4 class="profile-statistic statistic"><a><span class="value">24</span><span class="definition title-all-caps">This year</span></a></h4></section>
            <div class="bio js-bio"><div class="js-bio-content"><p>Hello</p></div></div>
            <section id="favourites" class="section">${poster("persona", "Persona (1966)")}</section>
            <section id="recent-activity" class="section">${poster("alien", "Alien (1979)")}<span class="rating rated-8">★★★★</span></section><section class="x"></section>"""
        val p = Lb.profile(html, "sam")!!
        assertEquals("Sam", p.name); assertEquals("https://a/x.jpg?a=1&b=2", p.avatar); assertEquals("Hello", p.bio)
        assertEquals("1,368", p.stat("Films")); assertEquals("24", p.stat("this year"))
        assertEquals(listOf("Persona"), p.favourites.map { it.name }); assertEquals(8, p.recent.single().rating)
    }

    @Test fun diary_rows_give_date_rating_like_and_rewatch() {
        val row = { day: String, rating: Int, like: Boolean, rewatch: Boolean ->
            """<tr class="diary-entry-row"><td class="col-daydate"><a class="daydate" href="/sam/diary/films/for/2026/10/$day/">$day</a></td>
            <td class="col-production">${poster("heat-1995", "Heat (1995)")}</td><td class="col-rating"><span class="rating rated-$rating">x</span></td>
            <td class="col-like">${if (like) "<span class=\"icon-liked\"></span>" else ""}</td>
            <td class="col-rewatch ${if (rewatch) "" else "icon-status-off"}"></td></tr>"""
        }
        val d = Lb.diary(row("16", 5, true, false) + row("14", 8, false, true))
        assertEquals(listOf("2026-10-16", "2026-10-14"), d.map { it.date })
        assertEquals(5, d[0].rating); assertTrue(d[0].liked); assertFalse(d[0].rewatch); assertTrue(d[1].rewatch)
    }

    @Test fun reviews_give_author_rating_text_and_likes() {
        val html = """<div class="listitem"><article class="production-viewing -viewing"><a class="avatar -a40" href="/eddy/"><img src="https://a/e.jpg"></a>
            <div class="body"><div class="content-reactions-strip -viewing"><span class="inline-rating"><svg aria-label="★★★★★"></svg></span><svg aria-label="Liked"></svg>
            <a href="/eddy/film/heat-1995/" class="context">Watched by <strong class="displayname">Eddy B</strong></a><time datetime="2025-09-25">25 Sep</time></div>
            <div class="js-review"><div class="body-text js-review-body"><p>A great walk.</p><p>Truly.</p></div>
            <p class="like-link-target" data-count="77152"></p></div></div></article></div>
            <article class="production-viewing"><a href="/no/film/x/"></a><div class="js-review-body"></div></article>"""
        val r = Lb.reviews(html).single()   // one with no text is left out
        assertEquals("eddy", r.author); assertEquals("Eddy B", r.authorName); assertEquals(10, r.rating); assertTrue(r.liked)
        assertEquals("A great walk.\n\nTruly.", r.text); assertEquals("2025-09-25", r.date); assertEquals(77152, r.likes)
    }

    @Test fun list_summaries_give_name_owner_count_likes_and_posters() {
        val html = """<article class="list-summary js-list" data-person="Herman2181"><a href="/herman2181/list/a24/" class="poster-list-link">${poster("x", "X (2020)")}</a>
            <div class="body"><h2 class="name prettify"><a href="/herman2181/list/a24/">A24</a></h2><strong class="displayname">Keith</strong>
            <span class="value">117&nbsp;films</span><a href="/herman2181/list/a24/likes/" class="metadata"><span class="inline-symbol"></span><span class="label">14K</span></a></div></article>"""
        val l = Lb.lists(html).single()
        assertEquals("/herman2181/list/a24/", l.path); assertEquals("A24", l.name); assertEquals("herman2181", l.owner)
        assertEquals("Keith", l.ownerName); assertEquals(117, l.count); assertEquals("14K", l.likes); assertEquals(1, l.previews.size)
    }

    @Test fun a_film_page_gives_average_counts_and_tmdb() {
        val html = """<meta property="og:url" content="https://letterboxd.com/film/heat-1995/" />
            <script type="application/ld+json">{"aggregateRating":{"ratingValue":4.12,"ratingCount":2906266,"reviewCount":1017843}}</script>
            <a href="https://www.themoviedb.org/movie/949/">TMDB</a>"""
        val f = Lb.film(html)!!
        assertEquals("heat-1995", f.slug); assertEquals(4.12, f.average!!, 0.001); assertEquals(2906266L, f.ratings)
        assertEquals(949L, f.tmdbId); assertFalse(f.isTv)
    }

    @Test fun saving_a_page_keeps_the_film_data_and_the_stars_and_likes() {
        val html = """<script>big()</script><script type="application/ld+json">{"ratingValue":4.1}</script><style>x{}</style>
            <svg class="glyph"><path d="M0"/></svg><svg aria-label="★★★½"><title>★★★½</title></svg><svg aria-label="Liked"></svg>"""
        val slim = LetterboxdPages.slim(html)
        assertFalse(slim.contains("big()")); assertFalse(slim.contains("<style")); assertFalse(slim.contains("M0"))
        assertTrue(slim.contains("\"ratingValue\":4.1")); assertTrue(slim.contains("aria-label=\"★★★½\"")); assertTrue(slim.contains("aria-label=\"Liked\""))
    }

    @Test fun the_ratings_chart_gives_ten_counts() {
        val labels = listOf("half-★", "★", "★½", "★★", "★★½", "★★★", "★★★½", "★★★★", "★★★★½", "★★★★★")
        val html = labels.mapIndexed { i, l -> """<tr class="column"><td><a href="/x/" class="barcolumn tooltip" title="${(i + 1) * 1000 + 773} $l ratings (1%)"></a></td></tr>""" }.joinToString("")
        assertEquals((1..10).map { it * 1000L + 773 }, Lb.histogram(html))
        assertEquals(emptyList<Long>(), Lb.histogram("<p>nothing</p>"))
    }

    @Test fun a_members_page_for_a_film_gives_their_own_rating_or_nothing() {
        val html = """<div class="content-reactions-strip -viewing"><svg aria-label="★★★★½"></svg><svg aria-label="Liked"></svg></div>
            <div class="js-review-body"><p>Tunnels.</p></div><li><span class="rating -nano rated-2">★</span></li>"""
        assertEquals(LbMemberFilm("sam", 9, true, "Tunnels."), Lb.memberFilm(html, "sam"))
        assertNull(Lb.memberFilm("<title>Letterboxd - Not Found</title>", "sam"))
    }

    @Test fun a_members_film_says_whether_they_watched_it() {
        assertEquals(LbMemberFilm("sam", null, false, null, watched = false), Lb.member("<title>Letterboxd - Not Found</title>", "sam"))
        // A page that is neither their film nor Not Found (a browser check, say) is no answer at all.
        assertNull(Lb.member("<title>Just a moment...</title>", "sam"))
        val watchedNoRating = """<meta property="og:url" content="https://letterboxd.com/film/heat-1995/"><p>Logged, unrated.</p>"""
        assertEquals(LbMemberFilm("sam", null, false, null, watched = true), Lb.member(watchedNoRating, "sam"))
    }

    @Test fun a_films_average_is_worked_out_from_its_rating_chart() {
        // One ½ and one ★★★★★ rating: (0.5 + 5) / 2.
        assertEquals(2.75, Lb.average(listOf(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L))!!, 0.0001)
        assertNull(Lb.average(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)))
        assertNull(Lb.average(emptyList()))
    }

    @Test fun a_films_cast_and_crew_come_with_their_page_paths() {
        val html = """<div id="tab-panel-cast" role="tabpanel"> <div class="cast-list text-sluglist">
            <a title="Lt. Vincent Hanna" href="/actor/al-pacino/" class="text-slug tooltip">Al Pacino</a>
            <a title="Neil McCauley" href="/actor/robert-de-niro/" class="text-slug tooltip">Robert De Niro</a> </div></div>
            <div id="tab-panel-crew" class="column-block"> <h3> <span class="crewrole -full">Director</span> </h3>
            <div class="text-sluglist"> <a href="/director/michael-mann/" class="text-slug">Michael Mann</a> </div>
            <h3> <span class="crewrole -full">Writer</span> </h3> <div class="text-sluglist"> <a href="/writer/michael-mann/" class="text-slug">Michael Mann</a> </div></div>
            <div id="tab-panel-details">Studios</div>"""
        val credits = Lb.credits(html)
        assertEquals(listOf(LbPerson("/actor/al-pacino/", "Al Pacino", "Lt. Vincent Hanna"), LbPerson("/actor/robert-de-niro/", "Robert De Niro", "Neil McCauley")), credits.cast)
        assertEquals(listOf("Director", "Writer"), credits.crew.map { it.role })
        assertEquals(LbPerson("/director/michael-mann/", "Michael Mann", null), credits.crew[0].people[0])
        assertEquals(LbCredits(emptyList(), emptyList()), Lb.credits("<p>nothing</p>"))
    }

    @Test fun a_persons_picture_is_their_tmdb_headshot() {
        val html = """<img src="https://s.ltrbxd.com/static/img/empty.png" class="js-tmdb-person" data-image="https://image.tmdb.org/t/p/w500/m8HAAjq1T75.jpg" data-tmdb-id="1158" />"""
        assertEquals("https://image.tmdb.org/t/p/w500/m8HAAjq1T75.jpg", Lb.headshot(html))
        assertNull(Lb.headshot("<p>no picture</p>"))
    }
}
