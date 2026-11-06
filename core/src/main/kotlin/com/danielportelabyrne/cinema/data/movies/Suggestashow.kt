package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A suggestashow server (github.com/ConorHK/suggestashow): it works out Picked for you from the viewer's Jellyfin
 * history and keeps their feedback. It checks the viewer with their own Jellyfin sign-in, so [auth] is the
 * Authorization header Jellyfin takes. Blocking; call off the main thread.
 */
class SuggestashowClient(private val base: String, private val auth: () -> String?, private val transport: Transport = Transport.HTTP) {
    /** One request: method, full address, headers and body in; status and body out. Swapped for a fake in tests. */
    fun interface Transport {
        fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>

        companion object {
            val HTTP = Transport { method, url, headers, body ->
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 15000
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.exchange {
                    if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
                    val code = c.responseCode
                    code to if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else ""
                }
            }
        }
    }

    /** The viewer's picks, best first, or null when the server could not be reached or turned the sign-in down. */
    fun picks(): List<Pick>? = send("GET", "/picks", null)?.let(::parsePicks)

    /** Tells the server these picks were on screen, so ones never opened sink over the next days. */
    fun shown(picks: List<Pick>) {
        if (picks.isEmpty()) return
        val titles = JSONArray().apply { picks.forEach { put(title(it.suggestion.kind, it.suggestion.tmdbId)) } }
        send("POST", "/picks/shown", JSONObject().put("titles", titles).toString())
    }

    /** The viewer opened this pick's page. */
    fun opened(kind: Kind, tmdbId: Long) { send("POST", titlePath(kind, tmdbId, "open"), "") }

    /** The viewer asked for this title, so it leaves the picks without counting against what suggested it. */
    fun accepted(kind: Kind, tmdbId: Long) { send("POST", titlePath(kind, tmdbId, "accept"), "") }

    private fun send(method: String, path: String, body: String?): String? = try {
        val header = auth() ?: return null
        val headers = mapOf("Authorization" to header, "Accept" to "application/json") +
            (if (body != null) mapOf("Content-Type" to "application/json") else emptyMap())
        val (code, text) = transport.call(method, base.trimEnd('/') + path, headers, body)
        text.takeIf { code in 200..299 }
    } catch (_: Exception) { null }

    private fun titlePath(kind: Kind, tmdbId: Long, action: String) = "/titles/${kindName(kind)}/$tmdbId/$action"

    private fun title(kind: Kind, tmdbId: Long) = JSONObject().put("kind", kindName(kind)).put("tmdb", tmdbId)

    companion object {
        private fun kindName(kind: Kind) = if (kind == Kind.SHOW) "series" else "movie"

        /** Reads the server's `/picks` answer. Images come as full addresses. */
        fun parsePicks(json: String): List<Pick>? = try {
            val list = JSONObject(json).getJSONArray("picks")
            (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val kind = when (o.optString("kind")) { "movie" -> Kind.MOVIE; "series" -> Kind.SHOW; else -> return@mapNotNull null }
                val id = o.optLong("tmdb", 0L).takeIf { it > 0 } ?: return@mapNotNull null
                val name = o.optString("title").ifBlank { return@mapNotNull null }
                val genres = o.optJSONArray("genres")
                Pick(
                    Suggestion(
                        tmdbId = id, kind = kind, name = name, year = o.optInt("year", 0).takeIf { it > 0 },
                        overview = o.optString("overview").ifBlank { null }, posterPath = null, backdropPath = null,
                        genres = (0 until (genres?.length() ?: 0)).map { genres!!.getString(it) },
                        vote = o.optDouble("vote", -1.0).takeIf { it > 0 }, owned = false,
                        posterUrl = o.optString("poster").takeIf { it.startsWith("http") },
                        backdropUrl = o.optString("backdrop").takeIf { it.startsWith("http") },
                    ),
                    o.optDouble("score", 0.0),
                    o.optString("becauseTitle"),
                )
            }
        } catch (_: Exception) { null }

        /** True when [base] answers suggestashow's health check. */
        fun reachable(base: String, transport: Transport = Transport.HTTP): Boolean = try {
            val (code, text) = transport.call("GET", base.trimEnd('/') + "/health", mapOf("Accept" to "application/json"), null)
            code == 200 && JSONObject(text).optString("status") == "ok"
        } catch (_: Exception) { false }
    }
}
