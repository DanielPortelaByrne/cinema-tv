package com.danielportelabyrne.cinema.data.stream

import com.danielportelabyrne.cinema.data.stremio.AddonLink
import com.danielportelabyrne.cinema.data.stremio.AddonStream
import com.danielportelabyrne.cinema.data.stremio.AddonStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectLinksTest {
    private val tv = DeviceCapabilities(1080, setOf(VideoCodec.H264, VideoCodec.HEVC), false, true, false, setOf(AudioFormat.AAC, AudioFormat.AC3, AudioFormat.EAC3))
    private val fast = NetworkEstimate(100_000_000, 0, "test")

    private fun link(title: String, uncached: Boolean = false, url: String = "https://debrid.example/" + title.hashCode()) =
        AddonLink("Torrentio", title, url, emptyMap(), "[RD+] Torrentio\n$title", 2L * 1024 * 1024 * 1024, uncached)

    @Test fun readsQualityFromAReleaseName() {
        val m = ReleaseName.parse("Sharp.Objects.S01E03.Fix.1080p.AMZN.WEB-DL.DDP5.1.H.264-NTb")
        assertEquals(Resolution.P1080, m.resolution)
        assertEquals(SourceType.WEB_DL, m.source)
        assertEquals(VideoCodec.H264, m.codec)
        assertEquals(AudioFormat.EAC3, m.audio)
        val uhd = ReleaseName.parse("Film 2024 2160p UHD BluRay REMUX DV HDR HEVC TrueHD Atmos")
        assertEquals(Resolution.P2160, uhd.resolution)
        assertEquals(SourceType.REMUX, uhd.source)
        assertEquals(DynamicRange.DOLBY_VISION, uhd.range)
        assertTrue(uhd.hdrFallback)
        assertEquals(AudioFormat.ATMOS, uhd.audio)
        assertEquals(Resolution.UNKNOWN, ReleaseName.parse("Sharp Objects").resolution)
    }

    @Test fun readsDirectLinksWithTheirHeadersAndCacheState() {
        val links = AddonStreams.links("""{"streams":[
            {"name":"[RD+] Torrentio\n1080p","description":"Sharp.Objects.S01E03.1080p.WEB-DL.mkv\n💾 1.3 GB","url":"https://rd.example/a.mkv",
             "behaviorHints":{"notWebReady":true,"proxyHeaders":{"request":{"User-Agent":"Stremio"}}}},
            {"name":"[RD download] Torrentio\n4k","title":"Sharp.Objects.S01E03.2160p.mkv","url":"https://rd.example/b"},
            {"infoHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","title":"torrent"},
            {"externalUrl":"https://example.com","title":"web page"}]}""", "Torrentio")
        assertEquals(2, links.size)
        assertEquals("Sharp.Objects.S01E03.1080p.WEB-DL.mkv", links[0].title)
        assertEquals(mapOf("User-Agent" to "Stremio"), links[0].headers)
        assertEquals(false, links[0].uncached)
        assertEquals(true, links[1].uncached)
    }

    @Test fun aDirectLinkAsGoodAsTheBestTorrentIsTriedFirst() {
        val torrents = listOf(AddonStream("Sharp.Objects.S01E03.1080p.WEB-DL", "a".repeat(40), 50, null))
        val chosen = DirectLinks.choose(listOf(link("Sharp.Objects.S01E03.720p.WEB-DL"), link("Sharp.Objects.S01E03.1080p.WEB-DL.H.264")), torrents, tv, fast, 3000)
        assertEquals(listOf("Sharp.Objects.S01E03.1080p.WEB-DL.H.264"), chosen.map { it.candidate.displayName })
    }

    @Test fun aWorseOrUncachedLinkDoesNotReplaceTheTorrent() {
        val torrents = listOf(AddonStream("Film.2024.1080p.BluRay", "a".repeat(40), 50, null))
        assertTrue(DirectLinks.choose(listOf(link("Film.2024.720p.WEB")), torrents, tv, fast, 6000).isEmpty())
        assertTrue(DirectLinks.choose(listOf(link("Film.2024.1080p.WEB", uncached = true)), torrents, tv, fast, 6000).isEmpty())
    }

    @Test fun aFourKTorrentDoesNotOutrankA1080pLinkOnA1080pTv() {
        val torrents = listOf(AddonStream("Film.2024.2160p.WEB-DL", "a".repeat(40), 50, null))
        assertEquals(1, DirectLinks.choose(listOf(link("Film.2024.1080p.WEB-DL")), torrents, tv, fast, 6000).size)
    }
}
