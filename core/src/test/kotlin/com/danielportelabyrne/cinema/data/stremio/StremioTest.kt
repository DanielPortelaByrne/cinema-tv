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

    @Test fun readsTheInstalledAddonsAndWhichGiveStreams() {
        val addons = StremioApi.addons("""{"result":{"addons":[
            {"transportUrl":"https://torrentio.example/abc/manifest.json","manifest":{"name":"Torrentio","types":["movie","series"],"resources":["stream"]}},
            {"transportUrl":"https://cat.example/manifest.json","manifest":{"name":"Catalogs","types":["movie"],"resources":["catalog",{"name":"stream","types":["movie"]}]}},
            {"transportUrl":"https://meta.example/manifest.json","manifest":{"name":"Only meta","types":["movie"],"resources":["meta"]}},
            {"transportUrl":"not a link","manifest":{"name":"Broken"}},
            {"manifest":{"name":"No address"}}]}}""")
        assertEquals(listOf("Torrentio", "Catalogs", "Only meta"), addons.map { it.name })
        assertEquals(listOf(true, true, false), addons.map { it.streams })
        assertEquals(listOf("movie", "series"), addons[0].types)
        assertEquals("https://torrentio.example/abc/manifest.json", addons[0].transportUrl)
    }

    @Test fun readsTheTorrentsAnAddonOffers() {
        val streams = AddonStreams.parse("""{"streams":[
            {"infoHash":"7E0916BCCB092C678F749671816468379AB978BD","title":"Sharp.Objects.S01E05.Closer.1080p.HEVC\n👤 6 💾 737.2 MB ⚙️ TorrentGalaxy"},
            {"infoHash":"1a817ab6c0000000000000000000000000000000","title":"Sharp Objects S01E05\n👤 12 💾 1.5 GB ⚙️ x"},
            {"infoHash":"nothash","title":"No hash\n👤 9 💾 1 GB"},
            {"infoHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","title":"No seeders\n💾 1 GB"},
            {"url":"https://example.com/file.mkv","title":"Direct link"}]}""")
        assertEquals(listOf("7e0916bccb092c678f749671816468379ab978bd", "1a817ab6c0000000000000000000000000000000"), streams.map { it.infoHash })
        assertEquals(listOf(6, 12), streams.map { it.seeders })
        assertEquals("Sharp.Objects.S01E05.Closer.1080p.HEVC", streams[0].title)
        assertEquals((737.2 * 1024 * 1024).toLong(), streams[0].size)
        assertEquals((1.5 * 1024 * 1024 * 1024).toLong(), streams[1].size)
        assertEquals(emptyList<AddonStream>(), AddonStreams.parse("not json"))
    }

    @Test fun portedAddonsSurviveSavingAndReading() {
        val saved = listOf(StremioAddon("Torrentio", listOf("movie"), "https://torrentio.example/x/manifest.json", true))
        assertEquals(saved, StremioApi.savedAddons(StremioApi.savedAddonsJson(saved)))
        assertEquals(emptyList<StremioAddon>(), StremioApi.savedAddons(null))
        assertEquals(emptyList<StremioAddon>(), StremioApi.savedAddons("not json"))
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
