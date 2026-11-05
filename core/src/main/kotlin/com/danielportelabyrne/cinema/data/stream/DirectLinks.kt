package com.danielportelabyrne.cinema.data.stream

import com.danielportelabyrne.cinema.data.stremio.AddonLink
import com.danielportelabyrne.cinema.data.stremio.AddonStream

/**
 * Which add-on direct links to try before the stream service's torrent. A direct link starts in seconds, so it wins
 * when it is as good as the best torrent this TV can show; a worse direct link does not replace a better torrent.
 */
object DirectLinks {
    fun candidate(link: AddonLink): StreamCandidate = StreamCandidate(
        id = "link:" + link.url.hashCode(), provider = link.addon, displayName = link.title, url = link.url, infoHash = null,
        meta = ReleaseName.parse(link.title + "\n" + link.text, link.size), headers = link.headers)

    /** The links to try, best first; empty when the torrent route should be used. */
    fun choose(links: List<AddonLink>, torrents: List<AddonStream>, device: DeviceCapabilities, network: NetworkEstimate,
               runtimeSec: Long?, limit: Int = 3): List<RankedStream> {
        val cached = links.filter { !it.uncached }.distinctBy { it.url }
        if (cached.isEmpty()) return emptyList()
        val ranked = StreamRanker.rank(cached.map(::candidate), device, network, emptyMap(), runtimeSec)
            .filter { it.candidate.directPlayable && (it.verdict == Verdict.RECOMMENDED || it.verdict == Verdict.EXCELLENT || it.verdict == Verdict.GOOD) }
        val bestTorrent = torrents.maxOfOrNull { t -> ReleaseName.parse(t.title).resolution.height.coerceAtMost(device.maxHeight) } ?: 0
        return ranked.filter { r ->
            val h = r.candidate.meta.resolution.height
            if (h == 0) bestTorrent == 0 else h >= bestTorrent
        }.take(limit)
    }
}
