package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.account.AccountStore
import com.danielportelabyrne.cinema.data.account.CinemaAccount
import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LifeOsHistoryExportTest {
    @Test
    fun exportsProfilesWithoutExposingTokensAndIsolatesRequestedProfileHistory() {
        val platform = MemoryPlatform()
        val accounts = AccountStore(platform)
        accounts.upsert(CinemaAccount("u_dan", "Daniel"), "secret-jellyfin-token")
        accounts.upsert(CinemaAccount("u_eva", "Eva"), "eva-secret-token")

        val profilesObj = JSONObject(LifeOsHistoryExport.profilesJson(platform.preferences("accounts")))
        assertEquals(2, profilesObj.getJSONArray("profiles").length())
        assertFalse(profilesObj.toString().contains("secret-jellyfin-token"))

        assertNull(LifeOsHistoryExport.resolveAuthorizedProfile(platform.preferences("accounts"), "unknown"))
        assertEquals("Daniel", LifeOsHistoryExport.resolveAuthorizedProfile(platform.preferences("accounts"), "u_dan")?.name)

        val playPrefs = platform.preferences("play_history~u_dan")
        val entry = PlayedEntry(
            kind = Kind.SHOW,
            seriesImdbId = "tt1234",
            title = "Abstract: The Art of Design",
            episodeTitle = "Paula Scher",
            season = 1,
            episode = 6,
            playedAt = 1_780_000_000_000L,
            positionMs = 2_400_000L,
            durationMs = 2_500_000L,
            completed = true,
        )
        playPrefs.edit().putString("hist_local", PlayHistory.toJson(listOf(entry))).commit()

        val personalPrefs = platform.preferences("cinema_personal~u_dan")
        personalPrefs.edit().putString(
            "watchlist_films",
            Watchlist.toJson(listOf(WatchlistFilm("tt0050083", "12 Angry Men", 1957, null, Kind.MOVIE)))
        ).commit()

        val favPrefs = platform.preferences("favourites~u_dan")
        val cwPrefs = platform.preferences("continue_watching~u_dan")
        cwPrefs.edit().putString("items", JSONArray().put(
            JSONObject().put("title", "Andor").put("subtitle", "S2E1").put("pos", 1200000L).put("dur", 3000000L).put("t", 1_780_000_000_000L)
        ).toString()).commit()

        val out = JSONObject(
            LifeOsHistoryExport.historyJson(playPrefs, personalPrefs, favPrefs, cwPrefs, "u_dan", "Daniel", sinceMs = 0L, nowMs = 1_780_001_000_000L)
        )
        assertEquals("u_dan", out.getString("profileId"))
        assertEquals(1, out.getJSONArray("entries").length())
        assertEquals("Abstract: The Art of Design", out.getJSONArray("entries").getJSONObject(0).getString("title"))
        assertEquals(1, out.getJSONArray("watchlist").length())
        assertEquals("12 Angry Men", out.getJSONArray("watchlist").getJSONObject(0).getString("title"))
        assertEquals(1, out.getJSONArray("continueWatching").length())
        assertEquals(40, out.getJSONArray("continueWatching").getJSONObject(0).getInt("progressPct"))

        // Incremental sync respects `sinceMs`
        val incremental = JSONObject(
            LifeOsHistoryExport.historyJson(playPrefs, personalPrefs, favPrefs, cwPrefs, "u_dan", "Daniel", sinceMs = 1_780_000_000_000L)
        )
        assertEquals(0, incremental.getJSONArray("entries").length())
    }

    @Test
    fun exportsFavouritesAtTimestampAndLegacyWatchlistFallbackOnlyWhenProvided() {
        val platform = MemoryPlatform()
        val favPrefs = platform.preferences("favourites~u_dan")
        favPrefs.edit().putString(
            "favs_local",
            Favourites.toJson(
                listOf(Favourite(Kind.MOVIE, "123", "tt0123456", null, "Spirited Away", 2001, null, null, 1_780_000_123_000L))
            )
        ).commit()

        val legacyPrefs = platform.preferences("cinema_settings")
        legacyPrefs.edit().putString(
            "watchlist_films",
            Watchlist.toJson(listOf(WatchlistFilm("tt0245429", "Spirited Away", 2001, null, Kind.MOVIE)))
        ).commit()

        val originalOut = JSONObject(
            LifeOsHistoryExport.historyJson(
                platform.preferences("play_history~u_dan"),
                platform.preferences("cinema_personal~u_dan"),
                favPrefs,
                platform.preferences("continue_watching~u_dan"),
                "u_dan",
                "Daniel",
                legacySettingsPrefs = legacyPrefs,
            )
        )
        assertEquals(1, originalOut.getJSONArray("favourites").length())
        assertEquals(1_780_000_123_000L, originalOut.getJSONArray("favourites").getJSONObject(0).getLong("addedAt"))
        assertEquals(1, originalOut.getJSONArray("watchlist").length())
        assertEquals("Spirited Away", originalOut.getJSONArray("watchlist").getJSONObject(0).getString("title"))

        val nonOriginalOut = JSONObject(
            LifeOsHistoryExport.historyJson(
                platform.preferences("play_history~u_eva"),
                platform.preferences("cinema_personal~u_eva"),
                platform.preferences("favourites~u_eva"),
                platform.preferences("continue_watching~u_eva"),
                "u_eva",
                "Eva",
                legacySettingsPrefs = null,
            )
        )
        assertEquals(0, nonOriginalOut.getJSONArray("watchlist").length())
    }
}
