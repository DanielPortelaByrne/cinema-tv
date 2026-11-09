package com.danielportelabyrne.cinema.data.intro

import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks an episode's credits up on TheIntroDB (community-submitted intro and credits timestamps, keyed by IMDb id). Each
 * answer is kept in [CreditsStore], so an episode is asked for once and then again only when the saved answer goes stale.
 * [get] fetches an address and gives its status code and body, or null when no answer came at all.
 */
class IntroDb(private val get: (String) -> Pair<Int, String>? = ::httpGet) {
    /** [startMs] is the credits' start (null when TheIntroDB has none, or could not be asked); [storeJson] the saved state to write back, if it changed. */
    data class Result(val startMs: Long?, val storeJson: String?)

    fun creditsStart(imdbId: String, season: Int, episode: Int, storeJson: String?, now: Long): Result {
        val key = "$imdbId|$season|$episode"
        CreditsStore.cachedStart(storeJson, key, now)?.let { return Result(it.startMs, null) }
        val (code, body) = get(CreditsTimes.introDbUrl(imdbId, season, episode)) ?: return Result(null, null)
        return when (code) {
            // Answered: nothing for this episode. Kept, so it is not asked again soon.
            404 -> Result(null, CreditsStore.putStart(storeJson, key, null, now))
            in 200..299 -> {
                val start = CreditsTimes.fromIntroDb(body)
                Result(start, CreditsStore.putStart(storeJson, key, start, now))
            }
            // Rate limited or failing: nothing is saved, so it is asked again another time.
            else -> Result(null, null)
        }
    }
}

/** A plain GET: the status code and the body (the error body for a failure), or null when the request never got an answer. */
private fun httpGet(url: String): Pair<Int, String>? = try {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"; connection.connectTimeout = 8_000; connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Cinema TV")
        val code = connection.responseCode
        code to ((if (code < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
    } finally { connection.disconnect() }
} catch (_: Exception) { null }
