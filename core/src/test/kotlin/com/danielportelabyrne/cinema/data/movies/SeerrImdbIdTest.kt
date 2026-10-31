package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class SeerrImdbIdTest {
    @Test fun the_imdb_id_comes_from_the_external_ids() {
        assertEquals("tt0111161", SeerrApi.imdbId("""{"id":278,"externalIds":{"imdbId":"tt0111161","tvdbId":null}}"""))
    }

    @Test fun a_title_with_no_imdb_id_gives_none() {
        assertNull(SeerrApi.imdbId("""{"id":1,"externalIds":{"imdbId":null}}"""))
        assertNull(SeerrApi.imdbId("""{"id":1,"externalIds":{"imdbId":""}}"""))
        assertNull(SeerrApi.imdbId("""{"id":1,"externalIds":{}}"""))
        assertNull(SeerrApi.imdbId("""{"id":1}"""))
    }

    @Test fun something_that_is_not_an_imdb_id_is_refused() {
        assertNull(SeerrApi.imdbId("""{"externalIds":{"imdbId":"12345"}}"""))
        assertNull(SeerrApi.imdbId("""{"externalIds":{"imdbId":"tt"}}"""))
    }

    @Test fun a_broken_answer_gives_none() {
        assertNull(SeerrApi.imdbId("not json"))
        assertNull(SeerrApi.imdbId(""))
    }
}
