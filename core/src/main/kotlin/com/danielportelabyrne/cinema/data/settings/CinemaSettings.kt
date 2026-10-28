package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.SecretStore
import java.util.Locale

/** Which of the two download managers an address and key belong to. */
enum class Arr(val label: String, val defaultPort: Int) { SONARR("Sonarr", 8989), RADARR("Radarr", 7878) }

/** An address and API key for Sonarr or Radarr, typed in by hand because Seerr would not share its own. */
data class ManualArr(val baseUrl: String, val apiKey: String)

/**
 * Everything this TV has been told about the owner's setup that is not a sign-in: the catalogue region, Sonarr and
 * Radarr entered by hand, and the optional helper services. Jellyfin's and Seerr's addresses live with their clients.
 * API keys go to [SecretStore], never here.
 */
class CinemaSettings(platform: Platform) {
    private val prefs = platform.preferences("cinema_settings")
    private val secrets = platform.secretStore()

    /** First-run setup has been finished (or skipped past Jellyfin) at least once. */
    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) { prefs.edit().putBoolean("setup_done", v).apply() }

    var libraryOrder: LibraryOrder
        get() = LibraryOrder.of(prefs.getString("library_order", null))
        set(v) { prefs.edit().putString("library_order", v.name).apply() }

    /** Two-letter country for what is streaming where: the TV's own country until the owner picks another. */
    var region: String
        get() = prefs.getString("region", null) ?: defaultRegion()
        set(v) { prefs.edit().putString("region", v.uppercase(Locale.ROOT)).apply() }

    /**
     * Optional: the address of a request helper service (it watches requests and retries searches on the server).
     * Blank means there is none, and the app talks to Sonarr and Radarr directly.
     */
    var requestHelperUrl: String?
        get() = prefs.getString("request_helper", null)?.takeIf { it.isNotBlank() }
        set(v) { prefs.edit().putString("request_helper", cleanUrl(v)).apply() }

    /** Optional: the address of a stream service that can play a film before it is downloaded. Blank hides Stream now. */
    var streamServiceUrl: String?
        get() = prefs.getString("stream_service", null)?.takeIf { it.isNotBlank() }
        set(v) { prefs.edit().putString("stream_service", cleanUrl(v)).apply() }

    fun manualArr(arr: Arr): ManualArr? {
        val url = prefs.getString("manual_${arr.name}", null)?.takeIf { it.isNotBlank() } ?: return null
        val key = secrets.get("manual_${arr.name}_key") ?: return null
        return ManualArr(url, key)
    }

    /** Saves Sonarr or Radarr entered by hand, or forgets it when [value] is null. */
    fun setManualArr(arr: Arr, value: ManualArr?) {
        prefs.edit().putString("manual_${arr.name}", value?.baseUrl?.let { cleanUrl(it) }).apply()
        secrets.put("manual_${arr.name}_key", value?.apiKey)
    }

    /** The Subsonic music server for the Music integration, or null when none is set up. */
    var subsonic: SubsonicConfig?
        get() {
            val url = prefs.getString("subsonic_url", null)?.takeIf { it.isNotBlank() } ?: return null
            val user = prefs.getString("subsonic_user", null).orEmpty()
            val password = secrets.get("subsonic_password").orEmpty()
            val key = secrets.get("subsonic_key").orEmpty()
            if (password.isEmpty() && key.isEmpty()) return null
            return SubsonicConfig(url, user, password, key)
        }
        set(v) {
            prefs.edit().putString("subsonic_url", v?.baseUrl?.let { cleanUrl(it) }).putString("subsonic_user", v?.username).apply()
            secrets.put("subsonic_password", v?.password?.takeIf { it.isNotEmpty() })
            secrets.put("subsonic_key", v?.apiKey?.takeIf { it.isNotEmpty() })
        }

    fun isOn(integration: Integration): Boolean = prefs.getBoolean(integration.key, integration.onByDefault)

    fun setOn(integration: Integration, on: Boolean) { prefs.edit().putBoolean(integration.key, on).apply() }

    /** Download-client passwords, per client address. Only used when Sonarr/Radarr hide theirs. */
    fun clientPassword(baseUrl: String): String? = secrets.get("client_pw_$baseUrl")
    fun setClientPassword(baseUrl: String, password: String?) = secrets.put("client_pw_$baseUrl", password)
    fun clientUser(baseUrl: String): String? = prefs.getString("client_user_$baseUrl", null)
    fun setClientUser(baseUrl: String, user: String?) { prefs.edit().putString("client_user_$baseUrl", user).apply() }

    /** Forgets everything here and in the secret store. Jellyfin and Seerr sign-ins are cleared by their own clients. */
    fun reset() { prefs.edit().clear().apply(); secrets.clear() }

    companion object {
        fun defaultRegion(): String = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

        /** "192.168.1.5:8096/" becomes "http://192.168.1.5:8096"; blank stays null. */
        fun cleanUrl(raw: String?): String? {
            val t = raw?.trim()?.trimEnd('/') ?: return null
            if (t.isEmpty()) return null
            return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "http://$t"
        }
    }
}
