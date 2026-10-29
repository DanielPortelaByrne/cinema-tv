package com.danielportelabyrne.cinema.data.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioTest {
    @Test fun readsALinkAndItsKey() {
        val l = StremioApi.link("""{"result":{"success":true,"code":"O9XW","link":"https://link.stremio.com/O9XW","qrcode":"https://link.stremio.com/qr?data=x"}}""")
        assertEquals("O9XW", l?.code)
        assertNull(StremioApi.link("""{"error":{"code":1}}"""))
        assertEquals("abc", StremioApi.authKey("""{"result":{"authKey":"abc"}}"""))
        assertNull(StremioApi.authKey("""{"result":{"success":true}}"""))
    }

    @Test fun keepsOnlySavedFilmsNewestFirst() {
        val films = StremioApi.films("""{"result":[
            {"_id":"tt1","type":"movie","name":"Old","year":"2001","poster":"https://p/1","_mtime":"2024-01-01T00:00:00Z","state":{"timesWatched":2}},
            {"_id":"tt2","type":"movie","name":"New","year":"2020-","poster":"","_mtime":"2025-01-01T00:00:00Z","state":{"timesWatched":0}},
            {"_id":"tt3","type":"series","name":"Show","_mtime":"2025-02-01T00:00:00Z"},
            {"_id":"tt4","type":"movie","name":"Gone","removed":true,"_mtime":"2025-03-01T00:00:00Z"},
            {"_id":"tt5","type":"movie","name":"Peeked","temp":true,"_mtime":"2025-03-01T00:00:00Z"},
            {"_id":"kitsu:9","type":"movie","name":"Anime","_mtime":"2025-03-01T00:00:00Z"}]}""")
        assertEquals(listOf("tt2", "tt1"), films.map { it.imdbId })
        assertEquals(2020, films[0].year)
        assertNull(films[0].poster)
        assertTrue(films[1].watched)
    }

    @Test fun filmPageUsesCinemetaOrTheLibraryEntry() {
        val film = StremioFilm("tt1", "Old", null, 2001, false, "")
        assertNull(StremioApi.title("""{"nope":1}""", film))
        assertEquals("Old", StremioApi.bare(film).name)
        val t = StremioApi.title("""{"meta":{"name":"Old","runtime":"101 min","genres":["Drama"],"moviedb_id":603,"imdbRating":"8.1"}}""", film)
        assertEquals(101, t?.runtimeMin)
        assertEquals("603", t?.tmdbId)
    }
}
