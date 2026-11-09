package com.danielportelabyrne.cinema.data.live

import java.util.Locale

data class LiveFeed(val channel: String, val id: String, val name: String, val main: Boolean, val areas: List<String>)
data class LiveSource(val channel: String, val feed: String?, val url: String, val labels: List<String> = emptyList(),
    val quality: String = "", val authorised: Boolean = false)
data class GuideSource(val channel: String, val feed: String?, val siteId: String, val url: String)
data class LiveProvider(val name: String, val website: String, val packages: List<String> = emptyList(),
    val subscription: Boolean = false, val note: String = "Availability depends on location and programme rights")
data class LiveChannel(val id: String, val name: String, val country: String, val categories: List<String>,
    val aliases: List<String> = emptyList(), val logo: String? = null, val feeds: List<LiveFeed> = emptyList(),
    val sources: List<LiveSource> = emptyList(), val guides: List<GuideSource> = emptyList(),
    val provider: LiveProvider? = null, val alternative: Boolean = false) {
    fun matches(query: String) = (listOf(name, id) + aliases).any { it.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT)) }
}
enum class LiveAvailability(val label: String) {
    PLAY_NOW("Play now"), TRY_STREAM("Try live stream · not yet verified"), OPEN_PROVIDER("Open provider"),
    SUBSCRIPTION("Subscription required"), UNAVAILABLE("Currently unavailable")
}
data class SourceHealth(val successes: Int = 0, val failures: Int = 0, val lastSuccess: Long = 0, val failedAt: Long = 0, val reason: String = "")

/** A tuning session never leaves the requested broadcaster and attempts each URL at most once. */
class LiveResolver(channel: LiveChannel, health: (String) -> SourceHealth, now: Long = System.currentTimeMillis()) {
    private val remaining = channel.sources.filter { it.channel == channel.id && it.authorised }
        .distinctBy { it.url }.sortedByDescending {
            val h = health(it.url)
            (if (now - h.failedAt < 30 * 60_000 && h.failedAt > 0) -100 else 0) +
                h.successes.coerceAtMost(10) * 10 - h.failures.coerceAtMost(10) * 5 +
                (if (it.labels.any { l -> l.contains("Geo", true) }) -30 else 0) +
                (if (it.labels.any { l -> l.contains("24/7", true) }) -10 else 0)
        }.take(4).toMutableList()
    fun next(): LiveSource? = if (remaining.isEmpty()) null else remaining.removeAt(0)
}

fun LiveChannel.availability(health: (String) -> SourceHealth): LiveAvailability = when {
    sources.any { it.authorised && health(it.url).lastSuccess > health(it.url).failedAt &&
        System.currentTimeMillis() - health(it.url).lastSuccess < 24 * 60 * 60_000L } -> LiveAvailability.PLAY_NOW
    sources.any { it.authorised } -> LiveAvailability.TRY_STREAM
    provider?.subscription == true -> LiveAvailability.SUBSCRIPTION
    provider != null -> LiveAvailability.OPEN_PROVIDER
    else -> LiveAvailability.UNAVAILABLE
}
