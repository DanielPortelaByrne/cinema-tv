package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestashowTest {
    private class Recorder(private val answer: Pair<Int, String>) : SuggestashowClient.Transport {
        val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
        val bodies = mutableListOf<String?>()
        override fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += Triple(method, url, headers); bodies += body
            return answer
        }
    }

    private val answer = """{"picks":[
        {"kind":"movie","tmdb":949,"title":"Heat","year":1995,"poster":"https://image.tmdb.org/t/p/w500/h.jpg","backdrop":"https://image.tmdb.org/t/p/w1280/b.jpg","genres":["Crime"],"vote":8.3,"score":1.4,"because":{"kind":"movie","tmdb":1},"becauseTitle":"Collateral"},
        {"kind":"series","tmdb":1438,"title":"The Wire","score":1.1,"because":{"kind":"series","tmdb":2},"becauseTitle":"The Sopranos"},
        {"kind":"book","tmdb":3,"title":"Skipped","score":1.0,"because":{"kind":"movie","tmdb":1},"becauseTitle":"x"}]}"""

    @Test fun picks_come_from_the_server_with_the_jellyfin_header() {
        val transport = Recorder(200 to answer)
        val picks = SuggestashowClient("http://192.168.1.20:8099/", { "MediaBrowser Token=\"t\"" }, transport).picks()!!

        assertEquals(listOf("Heat", "The Wire"), picks.map { it.suggestion.name })
        assertEquals(Kind.SHOW, picks[1].suggestion.kind)
        assertEquals("Collateral", picks[0].because)
        assertEquals("https://image.tmdb.org/t/p/w500/h.jpg", picks[0].suggestion.rowPoster)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", picks[0].suggestion.heroBackdrop)
        val (method, url, headers) = transport.calls.single()
        assertEquals("GET http://192.168.1.20:8099/picks", "$method $url")
        assertEquals("MediaBrowser Token=\"t\"", headers["Authorization"])
    }

    @Test fun no_sign_in_or_a_refusal_means_no_picks() {
        val transport = Recorder(401 to "")
        assertNull(SuggestashowClient("http://nas:8099", { null }, transport).picks())
        assertTrue(transport.calls.isEmpty())
        assertNull(SuggestashowClient("http://nas:8099", { "h" }, transport).picks())
    }

    @Test fun shown_picks_are_reported_by_kind_and_id() {
        val transport = Recorder(204 to "")
        val client = SuggestashowClient("http://nas:8099", { "h" }, transport)
        val picks = SuggestashowClient.parsePicks(answer)!!

        client.shown(picks)
        client.accepted(Kind.SHOW, 1438)

        assertEquals("POST http://nas:8099/picks/shown", transport.calls[0].let { "${it.first} ${it.second}" })
        val titles = org.json.JSONObject(transport.bodies[0]!!).getJSONArray("titles")
        assertEquals(listOf("movie:949", "series:1438"), (0 until titles.length()).map { titles.getJSONObject(it).let { t -> "${t.getString("kind")}:${t.getLong("tmdb")}" } })
        assertEquals("POST http://nas:8099/titles/series/1438/accept", transport.calls[1].let { "${it.first} ${it.second}" })
    }

    @Test fun health_check_needs_the_ok_answer() {
        assertTrue(SuggestashowClient.reachable("http://nas:8099", Recorder(200 to """{"status":"ok"}""")))
        assertFalse(SuggestashowClient.reachable("http://nas:8099", Recorder(200 to "<html>")))
        assertFalse(SuggestashowClient.reachable("http://nas:8099", Recorder(404 to "")))
    }

    @Test fun server_images_survive_a_save() {
        val picks = SuggestashowClient.parsePicks(answer)!!
        val back = Picks.fromJson(Picks.toJson(picks))
        assertEquals(picks.map { it.suggestion.rowPoster }, back.map { it.suggestion.rowPoster })
        assertEquals(picks.map { it.suggestion.heroBackdrop }, back.map { it.suggestion.heroBackdrop })
    }
}
