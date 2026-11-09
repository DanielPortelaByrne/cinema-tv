package com.danielportelabyrne.cinema.data.live

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

data class LiveProgramme(val channel: String, val title: String, val start: Long, val stop: Long) {
    fun localTime(zone: TimeZone = TimeZone.getDefault()): String = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }.format(java.util.Date(start))
}
data class NowNext(val now: LiveProgramme?, val next: LiveProgramme?)

object LiveEpg {
    fun time(raw: String): Long? = runCatching {
        // Missing timezone is UTC per XMLTV. Never interpret upstream wall time as the viewer's timezone.
        val parts = raw.trim().split(Regex("\\s+"))
        require(parts[0].matches(Regex("\\d{14}")))
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
            .parse(parts[0] + " " + parts.getOrElse(1) { "+0000" })!!.time
    }.getOrNull()
    fun parse(xml: String): List<LiveProgramme> {
        require(xml.length <= 20_000_000 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Unsafe or oversized XMLTV" }
        val result = mutableListOf<LiveProgramme>()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach { runCatching { factory.setFeature(it, false) } }
        var channel = ""; var start: Long? = null; var stop: Long? = null; var title = StringBuilder(); var inTitle = false
        factory.newSAXParser().parse(InputSource(StringReader(xml)), object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (qName == "programme") { channel = a.getValue("channel").orEmpty(); start = time(a.getValue("start").orEmpty()); stop = time(a.getValue("stop").orEmpty()); title = StringBuilder() }
                if (qName == "title") inTitle = title.isEmpty()
            }
            override fun characters(ch: CharArray, offset: Int, length: Int) { if (inTitle && title.length < 500) title.append(ch, offset, length) }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (qName == "title") inTitle = false
                if (qName == "programme" && start != null && stop != null && stop!! > start!! && title.isNotBlank() && result.size < 100_000)
                    result.add(LiveProgramme(channel, title.toString().trim(), start!!, stop!!))
            }
        })
        return result.distinctBy { Triple(it.channel, it.start, it.stop) }.sortedBy { it.start }
    }
    fun schedule(programmes: List<LiveProgramme>, guide: GuideSource): List<LiveProgramme> {
        // IPTV-org generated XMLTV uses channel@feed, while broadcaster XMLTV may use its own site ID.
        // Never fall across regional feeds merely because the broadcaster's name is shared.
        val composite = guide.feed?.let { "${guide.channel}@$it" }
        val exact = programmes.filter { it.channel == composite }
        if (exact.isNotEmpty()) return exact
        if (programmes.any { it.channel.startsWith("${guide.channel}@") }) return emptyList()
        val canonical = programmes.filter { it.channel == guide.channel }
        return canonical.ifEmpty { programmes.filter { it.channel == guide.siteId } }
    }
    fun nowNext(programmes: List<LiveProgramme>, now: Long = System.currentTimeMillis()): NowNext = NowNext(
        programmes.firstOrNull { now >= it.start && now < it.stop }, programmes.firstOrNull { it.start > now })
}

/** On-demand guide refresh, bounded cache and a failure cooldown. Catalogue joins supply the XMLTV site ID. */
class LiveGuideRepository(private val fetch: (String) -> String = { PublicTvUrl.read(it) }) {
    private data class Entry(val time: Long, val programmes: List<LiveProgramme>)
    private val cache = linkedMapOf<String, Entry>()
    @Synchronized fun schedule(channel: LiveChannel): List<LiveProgramme> {
        val feed = channel.sources.firstOrNull { it.authorised }?.feed ?: channel.feeds.firstOrNull { it.main }?.id
        val guide = channel.guides.firstOrNull { it.feed == feed } ?: channel.guides.firstOrNull { it.feed == null } ?: return emptyList()
        val now = System.currentTimeMillis()
        var entry = cache[guide.url]
        if (entry == null || now - entry.time > 30 * 60_000) {
            val parsed = runCatching { LiveEpg.parse(fetch(guide.url)) }.getOrElse { entry?.programmes.orEmpty() }
            entry = Entry(now, parsed); cache[guide.url] = entry
            if (cache.size > 6) cache.remove(cache.keys.first())
        }
        return LiveEpg.schedule(entry.programmes, guide).filter { it.stop > now }.take(100)
    }
}
