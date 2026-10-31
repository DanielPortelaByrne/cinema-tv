package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LbFilmInfoTest {
    private val head = """<meta property="og:url" content="https://letterboxd.com/film/persona/">"""

    @Test fun `reads the IMDb id from the film's links`() {
        val html = head + """<a href="https://www.imdb.com/title/tt0060827/" data-track-action="IMDb">IMDb</a>"""
        assertEquals("tt0060827", Lb.film(html)?.imdb)
    }

    @Test fun `no IMDb link gives no id`() {
        assertNull(Lb.film(head)?.imdb)
    }
}
