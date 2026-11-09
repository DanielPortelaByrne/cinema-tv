package com.danielportelabyrne.cinema.data.live

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import com.danielportelabyrne.cinema.platform.Platform
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

class LiveTvTest {
    @get:Rule val temp = TemporaryFolder()
    private fun data(vararg pairs: Pair<String, String>) = LiveCatalog.endpoints.associateWith { "[]" } + pairs
    private val channel = """[{"id":"TVBrasil.br","name":"TV Brasil","country":"BR","categories":["general"],"alt_names":["Brasil TV"]}]"""
    private val feeds = """[{"channel":"TVBrasil.br","id":"SD","name":"National","is_main":true,"broadcast_area":["c/BR"]}]"""
    private val good = "https://tvbrasil-stream.ebc.com.br/index.m3u8"

    @Test fun joinsByChannelAndFeedAndHandlesLabelsAndNulls() {
        val channels = LiveCatalog.parse(data("channels" to channel, "feeds" to feeds, "streams" to """[
            {"channel":"TVBrasil.br","feed":"SD","url":"$good","labels":["Geo-blocked","Not 24/7"]},
            {"channel":null,"url":"https://unknown.example/live.m3u8"},
            {"channel":"TVBrasil.br","feed":"Wrong","url":"https://wrong.example/live.m3u8"}]
        """))
        val c = channels.single { it.id == "TVBrasil.br" }
        assertEquals(1, c.sources.size); assertTrue(c.sources.single().authorised)
        assertEquals(listOf("Geo-blocked", "Not 24/7"), c.sources.single().labels)
        assertEquals("SD", c.sources.single().feed); assertTrue(c.matches("Brasil TV"))
    }
    @Test fun rejectsUnknownRestreamsAndWrongBroadcasterPaths() {
        assertFalse(OfficialLiveSources.permits("ComedyCentral.us", good))
        assertFalse(OfficialLiveSources.permits("TVBrasilInternacional.br", good))
        assertFalse(OfficialLiveSources.permits("TVBrasil.br", "https://restream.example/tvbrasil.m3u8"))
        assertFalse(OfficialLiveSources.permits("TVBrasil.br", "$good?token=secret"))
        assertFalse(OfficialLiveSources.permits("NHKWorldJapan.jp", "https://media-tyo.hls.nhkworld.jp/hls/wp/live/master.m3u8"))
    }
    @Test fun deduplicatesChannelsFeedsAndStreams() {
        val twice = "[${channel.removeSurrounding("[", "]")},${channel.removeSurrounding("[", "]")}]"
        val streams = """[{"channel":"TVBrasil.br","feed":"SD","url":"$good"},{"channel":"TVBrasil.br","feed":"SD","url":"$good"}]"""
        val c = LiveCatalog.parse(data("channels" to twice, "feeds" to feeds, "streams" to streams)).filter { it.id == "TVBrasil.br" }
        assertEquals(1, c.size); assertEquals(1, c.single().sources.size)
    }
    @Test fun blocklistAndClosedChannelsCannotReturnViaSeed() {
        val result = LiveCatalog.parse(data("channels" to channel, "blocklist" to """[{"channel":"TVBrasil.br","reason":"legal"}]"""))
        // The official provider home may remain, but no blocked streams may ever be restored.
        assertTrue(result.single { it.id == "TVBrasil.br" }.sources.isEmpty())
    }
    @Test fun providerMatchingIsExactAndCanonical() {
        assertEquals("BBC iPlayer", LiveProviders.forChannel("BBCOne.uk")?.name)
        assertEquals("Globoplay", LiveProviders.forChannel("RedeGlobo.br")?.name)
        assertEquals("U", LiveProviders.forChannel("UDave.uk")?.name)
        assertNull(LiveProviders.forChannel("BBCOne.evil"))
        assertTrue(LiveProviders.seeds().single { it.id == "UDave.uk" }.matches("Dave"))
        assertTrue(LiveProviders.seeds().single { it.id == "cinema.pluto" }.alternative)
    }
    @Test fun playlistDeduplicatesPreservesIdentityAndRejectsUnsafeResources() {
        val result = PlaylistParser.parse("""#EXTM3U
            #EXTINF:-1 tvg-id="test" tvg-logo="https://images.example/logo.png",Comedy
            https://tv.example/one.m3u8
            #EXTINF:-1 tvg-id="test",Comedy
            https://tv.example/two.m3u8
            #EXTINF:-1 tvg-id="test",Comedy
            https://tv.example/one.m3u8
            #EXTINF:-1,Private
            http://192.168.1.1/live.m3u8
            #EXTINF:-1,Credentials
            https://username:password@tv.example/live.m3u8
            #EXTINF:-1,Headers
            https://tv.example/live.m3u8|Cookie=secret
        """.trimIndent())
        assertEquals(1, result.size); assertEquals(2, result.single().sources.size)
        assertEquals("playlist:test", result.single().id)
    }
    @Test fun publicUrlRejectsLocalProtocolsAndCredentialQueries() {
        listOf("file:///etc/passwd", "http://tv.example/", "https://localhost/", "https://127.0.0.1/", "https://10.0.0.1/", "https://[::1]/", "https://host.local/", "https://user:pass@tv.example/", "https://tv.example:8096/", "https://tv.example/?token=secret").forEach { assertFalse(it, PublicTvUrl.syntaxSafe(it)) }
        assertTrue(PublicTvUrl.syntaxSafe(good))
    }
    @Test fun dnsAnswersCannotReachPrivateReservedOrMappedPrivateAddresses() {
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "192.0.2.1", "203.0.113.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1", "2001:db8::1", "2002:7f00:1::1").forEach {
            assertFalse(it, PublicTvUrl.publicAddress(java.net.InetAddress.getByName(it)))
        }
        assertTrue(PublicTvUrl.publicAddress(java.net.InetAddress.getByName("8.8.8.8")))
        assertTrue(PublicTvUrl.publicAddress(java.net.InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun rankingReliabilityAndBoundedRetryNeverSwitchBroadcasters() {
        val a = LiveSource("same", "a", "https://a.example/live.m3u8", authorised = true)
        val b = a.copy(url = "https://b.example/live.m3u8")
        val c = a.copy(url = "https://c.example/live.m3u8", labels = listOf("Geo-blocked"))
        val channel = LiveChannel("same", "Same", "GB", emptyList(), sources = listOf(a, b, a, c, a.copy(channel = "other"), a.copy(authorised = false)))
        val resolver = LiveResolver(channel, { if (it == a.url) SourceHealth(failures = 1, failedAt = 1000) else SourceHealth() }, 2000)
        assertEquals(b, resolver.next()); assertEquals(c, resolver.next()); assertEquals(a, resolver.next()); assertNull(resolver.next())
    }
    @Test fun settingsFavouritesOrderingHistoryAndHealthPersist() {
        val platform = MemoryPlatform(); val settings = LivePreferences(platform)
        settings.favourite("a"); settings.favourite("b"); settings.moveFirst("b"); settings.hide("c"); settings.watched("a"); settings.watched("b"); settings.watched("a")
        settings.result(good, true, now = 100); settings.result(good, false, "Access restricted HTTP 403", now = 200)
        val restored = LivePreferences(platform)
        assertEquals(listOf("b", "a"), restored.favourites); assertEquals(listOf("a", "b"), restored.recent); assertEquals(listOf("c"), restored.hidden)
        assertEquals(1, restored.health(good).successes); assertEquals(1, restored.health(good).failures)
        assertEquals("Access restricted HTTP 403", restored.health(good).reason)
        settings.favourite("a"); assertEquals(listOf("b"), settings.favourites)
    }
    @Test fun availabilityDoesNotClaimUnverifiedStreamsArePlayable() {
        val c = LiveChannel("same", "Same", "GB", emptyList())
        assertEquals(LiveAvailability.UNAVAILABLE, c.availability { SourceHealth() })
        assertEquals(LiveAvailability.OPEN_PROVIDER, c.copy(provider = LiveProvider("Official", "https://tv.example/")).availability { SourceHealth() })
        assertEquals(LiveAvailability.SUBSCRIPTION, c.copy(provider = LiveProvider("Pay", "https://tv.example/", subscription = true)).availability { SourceHealth() })
        val direct = c.copy(sources = listOf(LiveSource("same", null, good, authorised = true)))
        assertEquals(LiveAvailability.TRY_STREAM, direct.availability { SourceHealth() })
        assertEquals(LiveAvailability.PLAY_NOW, direct.availability { SourceHealth(lastSuccess = System.currentTimeMillis()) })
        assertEquals(LiveAvailability.TRY_STREAM, direct.availability { SourceHealth(lastSuccess = 100, failedAt = 200) })
    }
    @Test fun xmltvMatchesExactSiteIdAndConvertsTimezonesIncludingDst() {
        val xml = """<tv><programme channel="site.id" start="20261025003000 +0000" stop="20261025013000 +0000"><title>Current &amp; live</title></programme>
            <programme channel="site.id" start="20261025013000 +0000" stop="20261025023000 +0000"><title>Next</title></programme>
            <programme channel="unrelated" start="20261025003000 +0000" stop="20261025013000 +0000"><title>Wrong</title></programme></tv>"""
        val schedule = LiveEpg.schedule(LiveEpg.parse(xml), GuideSource("id", null, "site.id", "https://tv.example/guide.xml"))
        val nn = LiveEpg.nowNext(schedule, LiveEpg.time("20261025010000 +0000")!!)
        assertEquals("Current & live", nn.now?.title); assertEquals("Next", nn.next?.title)
        assertEquals("01:30", nn.now?.localTime(TimeZone.getTimeZone("Europe/London")))
        assertEquals("01:30", nn.next?.localTime(TimeZone.getTimeZone("Europe/London")))
        assertEquals(LiveEpg.time("20261108000000 +0000"), LiveEpg.time("20261107210000 -0300"))
        assertEquals(LiveEpg.time("20261108000000 +0000"), LiveEpg.time("20261108000000"))
        assertNull(LiveEpg.time("20261340000000 +0000")); assertNull(LiveEpg.nowNext(emptyList()).now)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsXmlExternalEntities() {
        LiveEpg.parse("""<!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><tv/>""")
    }
    @Test fun guideFailureHasCooldownAndMissingGuideHasNoInventedProgramme() {
        val requests = AtomicInteger()
        val guides = LiveGuideRepository { requests.incrementAndGet(); error("offline") }
        val c = LiveChannel("id", "Name", "GB", emptyList(), guides = listOf(GuideSource("id", null, "site", "https://tv.example/guide.xml")))
        assertTrue(guides.schedule(c).isEmpty()); assertTrue(guides.schedule(c).isEmpty()); assertEquals(1, requests.get())
    }
    @Test fun xmltvMatchesCompositeFeedIdsWithoutCrossingRegions() {
        val p = listOf(LiveProgramme("BBCOne.uk@London", "London", 1, 2), LiveProgramme("BBCOne.uk@Scotland", "Scotland", 1, 2))
        assertEquals("London", LiveEpg.schedule(p, GuideSource("BBCOne.uk", "London", "bbc", "https://tv.example/guide.xml")).single().title)
        assertTrue(LiveEpg.schedule(p, GuideSource("BBCOne.uk", "Wales", "bbc", "https://tv.example/guide.xml")).isEmpty())
    }
    @Test fun compactRetainsOnlyReviewedIdentitiesAndToleratesNewFields() {
        val raw = """[{"id":"TVBrasil.br","name":"Brazil","new_schema_field":[1,2]},{"id":"unreviewed","name":"Other"}]"""
        val compact = org.json.JSONArray(LiveCatalog.compact("channels", raw))
        assertEquals(1, compact.length()); assertEquals("TVBrasil.br", compact.getJSONObject(0).getString("id"))
        assertEquals(2, compact.getJSONObject(0).getJSONArray("new_schema_field").length())
    }
    @Test fun refreshFailureKeepsLastValidSnapshotAndUserPreferences() {
        val memory = MemoryPlatform()
        val platform = object : Platform by memory { override val cacheDir = temp.newFolder("cache"); override val filesDir = temp.newFolder("files") }
        var fail = false
        val repository = LiveRepository(platform) { url -> if (fail) error("offline") else when (url.substringAfterLast('/')) { "channels.json" -> channel; "feeds.json" -> feeds; else -> "[]" } }
        val initial = repository.load(); repository.preferences.favourite("TVBrasil.br"); fail = true
        val fallback = repository.load(true)
        assertEquals(initial.updatedAt, fallback.updatedAt); assertNotNull(fallback.warning)
        assertEquals(listOf("TVBrasil.br"), LivePreferences(platform).favourites)
        val restored = LiveRepository(platform) { error("offline") }.load()
        assertEquals(initial.channels, restored.channels)
    }
    @Test fun playlistPersistenceDoesNotStoreRejectedCredentialsOrOptions() {
        val memory = MemoryPlatform()
        val folder = temp.newFolder("playlist")
        val platform = object : Platform by memory { override val filesDir = folder; override val cacheDir = folder }
        val repository = LiveRepository(platform) { """#EXTM3U
            #EXTINF:-1 tvg-id="good" tvg-name="Name, with comma",Good name
            https://tv.example/live.m3u8
            #EXTVLCOPT:http-password=secret
            #EXTINF:-1,Secret
            https://user:password@tv.example/hidden.m3u8
        """.trimIndent() }
        assertEquals(1, repository.importPlaylist("https://tv.example/channels.m3u"))
        val saved = folder.listFiles()!!.single().readText()
        assertFalse(saved.contains("password")); assertFalse(saved.contains("secret")); assertFalse(saved.contains("EXTVLCOPT"))
        assertEquals("Good name", repository.imported().single().name)
    }
    @Test fun joinsMainFeedLogoAndXmltvSourcesWithoutCrossingFeeds() {
        val result = LiveCatalog.parse(data("channels" to channel, "feeds" to feeds,
            "logos" to """[{"channel":"TVBrasil.br","feed":"SD","in_use":true,"format":"PNG","url":"https://tv.example/logo.png"}]""",
            "guides" to """[{"channel":"TVBrasil.br","feed":"SD","site_id":"brasil","sources":[{"url":"https://tv.example/guide.xml","format":"XML"}]},
                {"channel":"TVBrasil.br","feed":"wrong","site_id":"other","sources":[{"url":"https://tv.example/wrong.xml","format":"XML"}]}]"""))
        val c = result.single { it.id == "TVBrasil.br" }
        assertEquals("https://tv.example/logo.png", c.logo); assertEquals("brasil", c.guides.single().siteId)
    }
}
