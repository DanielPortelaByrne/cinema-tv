package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.account.AccountStore
import com.danielportelabyrne.cinema.data.account.CinemaAccount
import com.danielportelabyrne.cinema.platform.Preferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure, read-only export builder for the Life OS integration (`GET /lifeos/profiles` and `GET /lifeos/history`).
 * Only reads the explicitly requested profile's preferences and never exposes Jellyfin tokens or other profiles.
 */
object LifeOsHistoryExport {

    fun profilesJson(accountsPrefs: Preferences): String {
        val accounts = AccountStore.parse(accountsPrefs.getString("list", null))
        val active = accountsPrefs.getString("active", null)
        val arr = JSONArray()
        if (accounts.isEmpty()) {
            arr.put(JSONObject().put("userId", "default").put("name", "TV Owner"))
        } else {
            for (a in accounts) {
                arr.put(JSONObject().put("userId", a.userId).put("name", a.name))
            }
        }
        return JSONObject()
            .put("version", 1)
            .put("activeProfileId", active ?: accounts.firstOrNull()?.userId ?: "default")
            .put("profiles", arr)
            .toString()
    }

    fun resolveAuthorizedProfile(accountsPrefs: Preferences, requestedProfileId: String?): CinemaAccount? {
        val accounts = AccountStore.parse(accountsPrefs.getString("list", null))
        if (accounts.isEmpty()) {
            val id = requestedProfileId?.takeIf { it.isNotBlank() } ?: "default"
            return if (id == "default") CinemaAccount("default", "TV Owner") else null
        }
        val target = requestedProfileId?.takeIf { it.isNotBlank() } ?: return null
        return accounts.firstOrNull { it.userId == target }
    }

    fun historyJson(
        playHistoryPrefs: Preferences,
        personalPrefs: Preferences,
        favouritesPrefs: Preferences,
        continuePrefs: Preferences,
        profileId: String,
        profileName: String,
        sinceMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
        legacySettingsPrefs: Preferences? = null,
    ): String {
        val allEntries = ArrayList<PlayedEntry>()
        for (k in playHistoryPrefs.all.keys) {
            if (!k.startsWith("hist_")) continue
            val parsed = PlayHistory.parse(playHistoryPrefs.getString(k, null))
            for (e in parsed) {
                if (e.playedAt <= sinceMs) continue
                val existingIdx = allEntries.indexOfFirst { it.sameAs(e) }
                if (existingIdx >= 0) {
                    val existing = allEntries[existingIdx]
                    allEntries[existingIdx] = if (existing.playedAt >= e.playedAt) existing.learn(e) else e.learn(existing)
                } else {
                    allEntries += e
                }
            }
        }
        allEntries.sortByDescending { it.playedAt }

        val entriesArr = JSONArray()
        for (e in allEntries) {
            val dur = e.durationMs.coerceAtLeast(0L)
            val pos = e.positionMs.coerceAtLeast(0L)
            val pct = if (e.completed) 100 else if (dur > 0L) ((pos * 100L) / dur).toInt().coerceIn(0, 100) else 0
            entriesArr.put(
                JSONObject()
                    .put("id", entryStableId(e))
                    .put("kind", if (e.isEpisode) "show" else "film")
                    .put("title", e.title)
                    .put("episodeTitle", e.episodeTitle ?: "")
                    .put("season", e.season ?: JSONObject.NULL)
                    .put("episode", e.episode ?: JSONObject.NULL)
                    .put("playedAt", e.playedAt)
                    .put("positionMs", pos)
                    .put("durationMs", dur)
                    .put("completionPct", pct)
                    .put("completed", e.completed)
                    .put("source", e.source)
            )
        }

        val watchlistArr = JSONArray()
        val watchlistRaw = personalPrefs.getString("watchlist_films", null)
            ?: legacySettingsPrefs?.getString("watchlist_films", null)
        for (w in Watchlist.parse(watchlistRaw)) {
            watchlistArr.put(
                JSONObject()
                    .put("id", w.imdbId)
                    .put("title", w.name)
                    .put("year", w.year ?: JSONObject.NULL)
                    .put("kind", if (w.isSeries) "show" else "film")
            )
        }

        val favArr = JSONArray()
        for (k in favouritesPrefs.all.keys) {
            if (!k.startsWith("favs_")) continue
            val raw = favouritesPrefs.getString(k, null) ?: continue
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                val addedAt = o.optLong("at", 0L).takeIf { it > 0L } ?: o.optLong("addedAt", 0L)
                favArr.put(
                    JSONObject()
                        .put("title", name)
                        .put("kind", if (o.optString("kind") == "SHOW") "show" else "film")
                        .put("addedAt", addedAt)
                )
            }
        }

        val cwArr = JSONArray()
        val cwRaw = continuePrefs.getString("items", null)
        if (!cwRaw.isNullOrBlank()) {
            val arr = runCatching { JSONArray(cwRaw) }.getOrNull()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("title")
                    if (title.isBlank()) continue
                    val dur = o.optLong("dur", 0L)
                    val pos = o.optLong("pos", 0L)
                    val pct = if (dur > 0L) ((pos * 100L) / dur).toInt().coerceIn(0, 100) else 0
                    cwArr.put(
                        JSONObject()
                            .put("title", title)
                            .put("subtitle", o.optString("subtitle", ""))
                            .put("progressPct", pct)
                            .put("lastEngagedMs", o.optLong("t", 0L))
                    )
                }
            }
        }

        return JSONObject()
            .put("version", 1)
            .put("exportedAt", nowMs)
            .put("profileId", profileId)
            .put("profileName", profileName)
            .put("entries", entriesArr)
            .put("watchlist", watchlistArr)
            .put("favourites", favArr)
            .put("continueWatching", cwArr)
            .toString()
    }

    private fun entryStableId(e: PlayedEntry): String {
        val id = e.tmdbId ?: e.imdbId ?: e.jellyfinId ?: e.seriesImdbId ?: e.seriesTmdbId ?: e.seriesJellyfinId ?: e.title
        return if (e.isEpisode) "${e.kind.name}:$id:s${e.season ?: 0}e${e.episode ?: 0}:${e.playedAt}" else "${e.kind.name}:$id:${e.playedAt}"
    }
}
