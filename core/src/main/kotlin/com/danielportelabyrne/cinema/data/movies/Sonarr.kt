package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.TimedCache
import com.danielportelabyrne.cinema.platform.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Sonarr"

private val seriesLists = TimedCache<String>(10 * 60_000L)
private val episodeLists = TimedCache<String>(60_000L)

/** Where Sonarr is and how to add a series to it, as Seerr has it configured. */
data class SonarrConfig(val baseUrl: String, val apiKey: String, val profileId: Int, val rootFolder: String, val seasonFolders: Boolean, val seriesType: String)

/** One episode that has been asked for and has no file yet. [id] is Sonarr's id for it; [airDate] ("2026-12-21") when it has not aired. */
data class WantedEpisode(val id: Int, val seriesTmdbId: Long, val seriesTitle: String, val season: Int, val number: Int, val airDate: String? = null)

/** What asking for some episodes does: every one is monitored; only those already out are searched for now. */
data class EpisodeRequestPlan(val monitor: List<Int>, val search: List<Int>)

enum class EpisodeState { NONE, REQUESTED, DOWNLOADED }

enum class EpisodeOutcome { QUEUED, ALREADY, NOT_CONFIGURED, NOT_FOUND, FAILED }

/** The plain-Kotlin half of the Sonarr client: reading its answers and writing its requests. */
object SonarrApi {
    /**
     * Seerr's own Sonarr settings hold the address, key, quality profile and folder it uses, so a request made here lands
     * the same way one made in Seerr does. Seerr sees Sonarr as "localhost"; from the TV that is [seerrHost].
     */
    fun parseConfig(settingsJson: String, seerrHost: String): SonarrConfig? = try {
        val all = JSONArray(settingsJson)
        val servers = (0 until all.length()).map { all.getJSONObject(it) }.filter { !it.optBoolean("is4k") }
        val s = servers.firstOrNull { it.optBoolean("isDefault") } ?: servers.firstOrNull()
        s?.let {
            val host = it.optString("hostname").takeUnless { h -> h.isBlank() || h == "localhost" || h == "127.0.0.1" } ?: seerrHost
            val scheme = if (it.optBoolean("useSsl")) "https" else "http"
            SonarrConfig(
                "$scheme://$host:${it.optInt("port", 8989)}${it.optString("baseUrl")}".trimEnd('/'),
                it.getString("apiKey"), it.optInt("activeProfileId"), it.optString("activeDirectory"),
                it.optBoolean("enableSeasonFolders", true), it.optString("seriesType").ifBlank { "standard" }
            )
        }
    } catch (_: Exception) { null }

    /** Sonarr's own id for the series with this TMDB id, if it already has it. */
    fun findSeries(listJson: String, tmdbId: Long): Int? = try {
        val a = JSONArray(listJson)
        (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optLong("tmdbId") == tmdbId }?.optInt("id")?.takeIf { it > 0 }
    } catch (_: Exception) { null }

    /**
     * What to POST to add a series that Sonarr has not got yet. Nothing is monitored (`monitor: none`) and nothing is
     * searched for, so adding it does not start fetching every season; only the episodes asked for afterwards are.
     */
    fun addBody(lookup: JSONObject, config: SonarrConfig): String = JSONObject(lookup.toString())
        .put("qualityProfileId", config.profileId).put("rootFolderPath", config.rootFolder).put("monitored", true)
        .put("seasonFolder", config.seasonFolders).put("seriesType", config.seriesType)
        .put("addOptions", JSONObject().put("monitor", "none").put("searchForMissingEpisodes", false).put("searchForCutoffUnmetEpisodes", false))
        .toString()

    /** Sonarr's ids for the episodes of [season] numbered [numbers]. */
    fun episodeIds(episodesJson: String, season: Int, numbers: Collection<Int>): List<Int> = try {
        val a = JSONArray(episodesJson)
        (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optInt("seasonNumber") == season && it.optInt("episodeNumber") in numbers }.map { it.getInt("id") }
    } catch (_: Exception) { emptyList() }

    /** The episodes of [season] numbered [numbers] that have a file on disk: Sonarr's (episode id, file id) for each. */
    fun episodeFiles(episodesJson: String, season: Int, numbers: Collection<Int>): List<Pair<Int, Int>> = try {
        val a = JSONArray(episodesJson)
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optInt("seasonNumber") == season && it.optInt("episodeNumber") in numbers && it.optInt("episodeFileId") > 0 }
            .map { it.getInt("id") to it.getInt("episodeFileId") }
    } catch (_: Exception) { emptyList() }

    /** Which episodes are on disk and which are wanted, keyed by (season, episode). */
    fun episodeStates(episodesJson: String): Map<Pair<Int, Int>, EpisodeState> = try {
        val a = JSONArray(episodesJson)
        (0 until a.length()).map { a.getJSONObject(it) }.associate {
            (it.optInt("seasonNumber") to it.optInt("episodeNumber")) to when {
                it.optBoolean("hasFile") -> EpisodeState.DOWNLOADED
                it.optBoolean("monitored") -> EpisodeState.REQUESTED
                else -> EpisodeState.NONE
            }
        }
    } catch (_: Exception) { emptyMap() }

    fun monitorBody(ids: List<Int>, on: Boolean = true): String = JSONObject().put("episodeIds", JSONArray(ids)).put("monitored", on).toString()

    /** Monitored episodes with no file: asked for, but nothing has been grabbed for them yet. */
    fun wantedEpisodes(json: String): List<WantedEpisode> = try {
        val records = JSONObject(json).getJSONArray("records")
        (0 until records.length()).map { records.getJSONObject(it) }.map { r ->
            val series = r.optJSONObject("series")
            WantedEpisode(r.optInt("id"), series?.optLong("tmdbId") ?: 0L, series?.optString("title").orEmpty(), r.optInt("seasonNumber"), r.optInt("episodeNumber"))
        }.filter { it.id > 0 && it.seriesTmdbId > 0 }
    } catch (_: Exception) { emptyList() }

    fun searchBody(ids: List<Int>): String = JSONObject().put("name", "EpisodeSearch").put("episodeIds", JSONArray(ids)).toString()

    /**
     * Splits the asked-for episodes of [season] into those to monitor (all of them) and those to search for now. One
     * that has not aired ([nowMs] before its `airDateUtc`, or no date at all) is only monitored: no indexer has it yet,
     * and Sonarr grabs it from its RSS feeds once it is out.
     */
    fun requestPlan(episodesJson: String, season: Int, numbers: Collection<Int>, nowMs: Long): EpisodeRequestPlan = try {
        val a = JSONArray(episodesJson)
        val chosen = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optInt("seasonNumber") == season && it.optInt("episodeNumber") in numbers }
        EpisodeRequestPlan(chosen.map { it.getInt("id") }, chosen.filter { aired(it.optString("airDateUtc"), nowMs) }.map { it.getInt("id") })
    } catch (_: Exception) { EpisodeRequestPlan(emptyList(), emptyList()) }

    /** True once [airDateUtc] ("2026-12-21T02:00:00Z") has passed; an unknown date has not. */
    fun aired(airDateUtc: String?, nowMs: Long): Boolean {
        if (airDateUtc.isNullOrBlank() || airDateUtc.length < 19) return false
        return try {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            (f.parse(airDateUtc.take(19))?.time ?: return false) <= nowMs
        } catch (_: Exception) { false }
    }

    /** Sonarr's calendar: monitored episodes with no file that have not aired yet (what has been asked for ahead). */
    fun upcomingEpisodes(json: String, nowMs: Long): List<WantedEpisode> = try {
        val a = JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optBoolean("monitored") && !it.optBoolean("hasFile") && !aired(it.optString("airDateUtc"), nowMs) }
            .map { r ->
                val series = r.optJSONObject("series")
                WantedEpisode(r.optInt("id"), series?.optLong("tmdbId") ?: 0L, series?.optString("title").orEmpty(), r.optInt("seasonNumber"), r.optInt("episodeNumber"),
                    r.optString("airDate").ifBlank { null })
            }.filter { it.id > 0 && it.seriesTmdbId > 0 }
    } catch (_: Exception) { emptyList() }
}

/**
 * Asks Sonarr for single episodes, which Seerr cannot. It reads Sonarr's address and key from Seerr's settings (so the
 * TV needs only the Seerr sign-in it already has), makes sure the series exists, marks just the chosen episodes as
 * wanted and starts a search for them. Every call blocks and none throw.
 */
class SonarrClient(private val seerr: SeerrClient) {
    private var cached: SonarrConfig? = null

    private fun config(): SonarrConfig? = cached ?: seerr.sonarrConfig()?.also { cached = it }

    /** The state of every episode Sonarr knows for this series; empty when it does not know the series. */
    fun episodeStates(tmdbId: Long, recheck: Boolean = true): Map<Pair<Int, Int>, EpisodeState> {
        val c = config() ?: return emptyMap()
        val id = seriesId(c, tmdbId, recheck) ?: return emptyMap()
        return episodeLists.get("${c.baseUrl}#$id") { call(c, "GET", "/api/v3/episode?seriesId=$id") }?.let { SonarrApi.episodeStates(it) }.orEmpty()
    }

    /** Wants the given episodes of one season and searches for them. */
    fun requestEpisodes(tmdbId: Long, season: Int, numbers: List<Int>): EpisodeOutcome {
        val c = config() ?: return EpisodeOutcome.NOT_CONFIGURED
        var id = seriesId(c, tmdbId)
        if (id == null) {
            val found = call(c, "GET", "/api/v3/series/lookup?term=tmdb:$tmdbId") ?: return EpisodeOutcome.FAILED
            val lookup = try { JSONArray(found).optJSONObject(0) } catch (_: Exception) { null } ?: return EpisodeOutcome.NOT_FOUND
            val added = call(c, "POST", "/api/v3/series", SonarrApi.addBody(lookup, c)) ?: return EpisodeOutcome.FAILED
            id = try { JSONObject(added).optInt("id").takeIf { it > 0 } } catch (_: Exception) { null } ?: return EpisodeOutcome.FAILED
            seriesLists.forget(c.baseUrl)
        }
        episodeLists.forget("${c.baseUrl}#$id")
        // A series that has only just been added fetches its episode list in the background; give it a moment.
        var ids: List<Int> = emptyList()
        var episodesJson: String? = null
        for (attempt in 1..8) {
            episodesJson = call(c, "GET", "/api/v3/episode?seriesId=$id")
            ids = episodesJson?.let { SonarrApi.episodeIds(it, season, numbers) }.orEmpty()
            if (ids.size == numbers.size || (ids.isNotEmpty() && attempt >= 4)) break
            Thread.sleep(1000)
        }
        if (ids.isEmpty()) return EpisodeOutcome.NOT_FOUND
        val plan = episodesJson?.let { SonarrApi.requestPlan(it, season, numbers, System.currentTimeMillis()) } ?: EpisodeRequestPlan(ids, ids)
        if (call(c, "PUT", "/api/v3/episode/monitor", SonarrApi.monitorBody(plan.monitor.ifEmpty { ids })) == null) return EpisodeOutcome.FAILED
        // Episodes not out yet are only monitored: Sonarr picks them up from RSS when they air, so nothing is searched.
        if (plan.search.isEmpty()) return EpisodeOutcome.QUEUED
        return if (call(c, "POST", "/api/v3/command", SonarrApi.searchBody(plan.search)) == null) EpisodeOutcome.FAILED else EpisodeOutcome.QUEUED
    }

    /** Whether Sonarr holds this series, so its episodes can be deleted through Sonarr. */
    fun managesSeries(tmdbId: Long): Boolean = config()?.let { seriesId(it, tmdbId) } != null

    /**
     * Deletes the files of the given episodes of one season and stops Sonarr wanting them, so they are not fetched again.
     * True when every file that was there is gone (an episode with no file counts as gone). Never throws.
     */
    fun deleteEpisodeFiles(tmdbId: Long, season: Int, numbers: List<Int>): Boolean {
        val c = config() ?: return false
        val id = seriesId(c, tmdbId) ?: return false
        val json = call(c, "GET", "/api/v3/episode?seriesId=$id") ?: return false
        val files = SonarrApi.episodeFiles(json, season, numbers)
        episodeLists.forget("${c.baseUrl}#$id")
        if (files.isEmpty()) return true
        // Unmonitor first: a file deleted while still wanted would be searched for and grabbed again.
        if (!unwant(files.map { it.first })) return false
        return files.all { (_, fileId) -> call(c, "DELETE", "/api/v3/episodefile/$fileId") != null }
    }

    /** Stops wanting the given episodes of one season (an episode asked for before it aired). */
    fun unwantEpisodes(tmdbId: Long, season: Int, numbers: List<Int>): Boolean {
        val c = config() ?: return false
        val id = seriesId(c, tmdbId) ?: return false
        val ids = call(c, "GET", "/api/v3/episode?seriesId=$id")?.let { SonarrApi.episodeIds(it, season, numbers) }.orEmpty()
        episodeLists.forget("${c.baseUrl}#$id")
        return ids.isNotEmpty() && unwant(ids)
    }

    /** Episodes asked for ahead of airing, over the next year. Null when Sonarr cannot be reached. */
    fun upcoming(): List<WantedEpisode>? {
        val c = config() ?: return null
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val now = System.currentTimeMillis()
        val start = f.format(java.util.Date(now)); val end = f.format(java.util.Date(now + 366L * 24 * 3600 * 1000))
        return call(c, "GET", "/api/v3/calendar?start=$start&end=$end&unmonitored=false&includeSeries=true")?.let { SonarrApi.upcomingEpisodes(it, now) }
    }

    /** Monitored episodes with no file: what has been asked for but not yet grabbed. Null when Sonarr cannot be reached. */
    fun wanted(): List<WantedEpisode>? {
        val c = config() ?: return null
        return call(c, "GET", "/api/v3/wanted/missing?page=1&pageSize=250&includeSeries=true&monitored=true")?.let { SonarrApi.wantedEpisodes(it) }
    }

    /** Stops wanting these episodes, so nothing more is searched for them. Anything already downloaded is kept. */
    fun unwant(ids: List<Int>): Boolean {
        if (ids.isEmpty()) return true
        val c = config() ?: return false
        episodeLists.forget(c.baseUrl)
        return call(c, "PUT", "/api/v3/episode/monitor", SonarrApi.monitorBody(ids, on = false)) != null
    }

    private fun seriesId(c: SonarrConfig, tmdbId: Long, recheck: Boolean = true): Int? {
        fun find() = seriesLists.get(c.baseUrl) { call(c, "GET", "/api/v3/series") }?.let { SonarrApi.findSeries(it, tmdbId) }
        return find() ?: if (recheck) { seriesLists.forget(c.baseUrl); find() } else null
    }

    fun warm(tmdbId: Long) { episodeStates(tmdbId, recheck = false) }

    private fun call(c: SonarrConfig, method: String, path: String, body: String? = null): String? = arrCall(c, method, path, body)
}
