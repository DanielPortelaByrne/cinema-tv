package com.danielportelabyrne.cinema.data.music

import com.danielportelabyrne.cinema.data.movies.HttpReply
import com.danielportelabyrne.cinema.data.movies.HttpTransport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubsonicTest {
    private val config = SubsonicConfig("http://192.168.1.20:4533", "someone", "sesame")

    private fun ok(payload: String) = """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true$payload}}"""
    private fun body(payload: String) = (SubsonicApi.reply(ok(payload)) as SubsonicReply.Ok).body

    private class Fake(private val respond: (String) -> HttpReply) : HttpTransport {
        val paths = mutableListOf<String>()
        override fun call(method: String, path: String, body: String?, headers: Map<String, String>): HttpReply {
            paths += path; return respond(path)
        }
    }

    private fun client(c: SubsonicConfig = config, respond: (String) -> HttpReply) = Fake(respond).let { it to SubsonicClient(c, it) { "c19b2d" } }

    @Test fun normalizes_a_typed_address() {
        assertEquals("http://music.lan:4533", SubsonicApi.normalize(" music.lan:4533/ "))
        assertEquals("https://music.example", SubsonicApi.normalize("https://music.example/"))
    }

    @Test fun signs_with_a_salted_token_and_never_the_password() {
        // The worked example from the Subsonic API docs.
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicApi.md5("sesamec19b2d"))
        val auth = SubsonicApi.auth(config, "c19b2d")
        assertEquals("u=someone&t=26719a1196d2a940705a59634eb18eab&s=c19b2d", auth)
        assertFalse(auth.contains("sesame"))
        assertEquals("apiKey=k%20y", SubsonicApi.auth(config.copy(apiKey = "k y"), "x"))
    }

    @Test fun bare_paths_carry_no_credentials_and_signed_ones_do() {
        val (_, c) = client { HttpReply(200, ok("")) }
        val path = c.streamPath("tr 1")
        assertEquals("/rest/stream?id=tr%201&c=Cinema&v=1.16.1&f=json", path)
        assertEquals("http://192.168.1.20:4533$path&u=someone&t=26719a1196d2a940705a59634eb18eab&s=c19b2d", c.signed(path))
    }

    @Test fun reads_failures_and_junk() {
        assertEquals(SubsonicReply.Failed(40, "Wrong username or password"),
            SubsonicApi.reply("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""))
        assertEquals(SubsonicReply.Junk, SubsonicApi.reply("<html>"))
        assertEquals(SubsonicReply.Junk, SubsonicApi.reply("""{"other":1}"""))
    }

    @Test fun sign_in_tells_the_cases_apart() {
        fun signIn(reply: HttpReply) = client { reply }.second.signIn()
        assertEquals(SignIn.OK, signIn(HttpReply(200, ok(""))))
        assertEquals(SignIn.WRONG_CREDENTIALS, signIn(HttpReply(200, """{"subsonic-response":{"status":"failed","error":{"code":40}}}""")))
        assertEquals(SignIn.NEEDS_API_KEY, signIn(HttpReply(200, """{"subsonic-response":{"status":"failed","error":{"code":41}}}""")))
        assertEquals(SignIn.NOT_SUBSONIC, signIn(HttpReply(200, "<html>Jellyfin</html>")))
        assertEquals(SignIn.NOT_SUBSONIC, signIn(HttpReply(404)))
        assertEquals(SignIn.UNREACHABLE, signIn(HttpReply(0)))
    }

    @Test fun reads_artists_across_index_groups() {
        val a = SubsonicApi.artists(body(""","artists":{"index":[
            {"name":"A","artist":[{"id":"ar1","name":"Air","albumCount":3,"coverArt":"ar-ar1"}]},
            {"name":"B","artist":[{"id":"ar2","name":"Bjork"},{"name":"no id"}]}]}"""))
        assertEquals(listOf("Air", "Bjork"), a.map { it.name })
        assertEquals(MusicArtist("ar1", "Air", 3, "ar-ar1"), a.first())
        assertNull(a[1].albumCount)
    }

    @Test fun reads_an_album_with_its_songs_in_disc_and_track_order() {
        val (album, tracks) = SubsonicApi.album(body(""","album":{"id":"al1","name":"Moon Safari","artist":"Air","artistId":"ar1",
            "year":1998,"songCount":3,"duration":2600,"coverArt":"al-al1","song":[
            {"id":"t3","title":"Talisman","track":1,"discNumber":2,"duration":256},
            {"id":"t2","title":"Sexy Boy","track":2,"discNumber":1},
            {"id":"t1","title":"La femme d'argent","track":1,"discNumber":1,"duration":430,"contentType":"audio/flac"}]}"""))!!
        assertEquals(MusicAlbum("al1", "Moon Safari", "Air", "ar1", 1998, 3, 2600, "al-al1"), album)
        assertEquals(listOf("t1", "t2", "t3"), tracks.map { it.id })
        assertEquals("audio/flac", tracks.first().contentType)
        assertNull(SubsonicApi.album(body("")))
    }

    @Test fun reads_album_lists_search_and_random_songs() {
        assertEquals(listOf("al1"), SubsonicApi.albumList(body(""","albumList2":{"album":[{"id":"al1","name":"Moon Safari","year":0}]}""")).map { it.id })
        assertNull(SubsonicApi.albumList(body(""","albumList2":{"album":[{"id":"al1","name":"X","year":0}]}""")).first().year)
        assertTrue(SubsonicApi.albumList(body(""","albumList2":{}""")).isEmpty())
        val s = SubsonicApi.search(body(""","searchResult3":{"artist":[{"id":"ar1","name":"Air"}],"song":[{"id":"t1","title":"Talisman"}]}"""))
        assertEquals(1, s.artists.size); assertTrue(s.albums.isEmpty()); assertEquals("Talisman", s.tracks.single().title)
        assertEquals(listOf("t1"), SubsonicApi.songs(body(""","randomSongs":{"song":[{"id":"t1","title":"Talisman"}]}""")).map { it.id })
    }

    @Test fun client_asks_for_the_right_list_and_caches_it() {
        val (fake, c) = client { HttpReply(200, ok(""","albumList2":{"album":[{"id":"al1","name":"Moon Safari"}]}""")) }
        assertEquals(1, c.albums(AlbumList.NEWEST, size = 20)?.size)
        c.albums(AlbumList.NEWEST, size = 20)
        assertEquals(1, fake.paths.size)
        assertTrue(fake.paths.single().startsWith("/rest/getAlbumList2?type=newest&size=20&offset=0&c=Cinema"))
        c.albums(AlbumList.RANDOM); c.albums(AlbumList.RANDOM)
        assertEquals(3, fake.paths.size)
    }

    @Test fun reads_timed_lyrics_and_finds_the_line_playing() {
        val l = SubsonicApi.lyrics(body(""","lyricsList":{"structuredLyrics":[
            {"lang":"eng","synced":false,"line":[{"value":"plain"}]},
            {"lang":"eng","synced":true,"offset":100,"line":[
                {"start":0,"value":""},{"start":1000,"value":"First "},{"start":3000,"value":""},{"start":3500,"value":""},
                {"start":5000,"value":"Second"},{"start":9000,"value":""}]}]}"""))!!
        assertTrue(l.synced)
        assertEquals(listOf(LyricLine(900, "First"), LyricLine(2900, ""), LyricLine(4900, "Second")), l.lines)
        assertEquals(-1, l.lineAt(500)); assertEquals(0, l.lineAt(900)); assertEquals(1, l.lineAt(4000)); assertEquals(2, l.lineAt(60_000))
        assertNull(SubsonicApi.lyrics(body(""","lyricsList":{"structuredLyrics":[]}""")))
    }

    @Test fun reads_plain_lyrics() {
        val l = SubsonicApi.plainLyrics(body(""","lyrics":{"artist":"Air","title":"Talisman","value":"One\r\n\n\nTwo\n"}"""))!!
        assertFalse(l.synced)
        assertEquals(listOf("One", "", "Two"), l.lines.map { it.text })
        assertEquals(-1, l.lineAt(10_000))
        assertNull(SubsonicApi.plainLyrics(body(""","lyrics":{}""")))
    }

    @Test fun lyrics_fall_back_to_the_classic_call() {
        val track = MusicTrack("t1", "Talisman", "Air", null, null, null, null, null, null, null)
        val (fake, c) = client { path ->
            if (path.startsWith("/rest/getLyricsBySongId")) HttpReply(200, """{"subsonic-response":{"status":"failed","error":{"code":0}}}""")
            else HttpReply(200, ok(""","lyrics":{"value":"La la"}"""))
        }
        assertEquals("La la", c.lyrics(track)?.lines?.single()?.text)
        assertTrue(fake.paths.last().startsWith("/rest/getLyrics?artist=Air&title=Talisman"))
    }

    @Test fun failures_read_as_null() {
        val (_, c) = client { HttpReply(0) }
        assertNull(c.artists()); assertNull(c.album("x")); assertNull(c.search("air")); assertFalse(c.scrobble("t1", true))
        assertTrue(JSONObject(ok("")).has("subsonic-response"))
    }
}
