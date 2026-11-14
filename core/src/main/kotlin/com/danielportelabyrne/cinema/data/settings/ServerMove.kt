package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import java.net.URI

/**
 * The owner's services usually share one home server with Jellyfin (Seerr, the stream service, Sonarr, Radarr...), each
 * saved under its own address. When the router gives that machine a new address and Jellyfin follows it (see
 * JellyfinClient.relocate), every other address that pointed at the old machine is moved with it: same scheme, port and
 * path, new host. Sign-ins and keys stay as they are. Addresses on other machines are left alone.
 */
object ServerMove {
    /** Moves every saved address on [oldBase]'s host to [newBase]'s host. Returns how many were moved. */
    fun follow(platform: Platform, oldBase: String, newBase: String): Int {
        val from = host(oldBase) ?: return 0
        val to = host(newBase) ?: return 0
        if (from.equals(to, ignoreCase = true)) return 0
        var moved = 0
        fun moveIn(prefsName: String, key: String) {
            val prefs = platform.preferences(prefsName)
            val now = prefs.getString(key, null) ?: return
            val next = rehost(now, from, to) ?: return
            prefs.edit().putString(key, next).apply()
            moved++
        }
        moveIn("seerr", "server")
        for (key in listOf("request_helper", "stream_service", "manual_SONARR", "manual_RADARR", "subsonic_url")) moveIn("cinema_settings", key)
        // Download-client logins are kept per address: copy each to its new address.
        val settings = CinemaSettings(platform)
        for (login in settings.clientLogins()) {
            val next = rehost(login.baseUrl, from, to) ?: continue
            settings.setClientUser(next, login.username.ifBlank { null })
            settings.setClientPassword(next, login.password)
            moved++
        }
        if (moved > 0) Log.i("ServerMove", "moved $moved service address(es) from $from to $to with Jellyfin")
        return moved
    }

    /** [url] with its host swapped from [from] to [to], or null when it is on another host (or not an address). */
    fun rehost(url: String, from: String, to: String): String? = try {
        val u = URI(url.trim())
        if (u.host == null || !u.host.equals(from, ignoreCase = true)) null
        else URI(u.scheme, u.userInfo, to, u.port, u.path, u.query, u.fragment).toString()
    } catch (_: Exception) { null }

    private fun host(base: String): String? = try { URI(base.trim()).host } catch (_: Exception) { null }
}
