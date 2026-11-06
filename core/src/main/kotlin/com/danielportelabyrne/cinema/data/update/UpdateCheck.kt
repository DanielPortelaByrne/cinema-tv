package com.danielportelabyrne.cinema.data.update

import org.json.JSONObject

/** A newer build of Cinema published on GitHub: its build number, title, the app file to install and its notes. */
data class AvailableUpdate(val build: Int, val title: String, val apkUrl: String, val sizeBytes: Long, val notes: String?)

/**
 * How Cinema finds out about a newer build. Every push to main builds the signed app and publishes it as a GitHub
 * release tagged `build-<n>`, with the app attached as [APK_NAME]; the repository is public, so the latest one can be
 * read without signing in. Plain Kotlin, so it is tested without a TV.
 */
object UpdateCheck {
    const val LATEST_URL = "https://api.github.com/repos/DanielPortelaByrne/cinema-tv/releases/latest"
    const val APK_NAME = "cinema-tv.apk"

    /** The release in GitHub's answer, or null when it is not a Cinema build or has no app file attached. */
    fun parse(json: String, assetName: String = APK_NAME): AvailableUpdate? {
        val o = try { JSONObject(json) } catch (_: Exception) { return null }
        val build = buildOf(o.optString("tag_name")) ?: return null
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.optJSONArray("assets") ?: return null
        val asset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { it.optString("name") == assetName } ?: return null
        val url = asset.optString("browser_download_url").takeIf { it.startsWith("https://") } ?: return null
        return AvailableUpdate(build, o.optString("name").ifBlank { "Cinema build $build" }, url, asset.optLong("size"),
            o.optString("body").trim().ifBlank { null })
    }

    /** The build number in a release tag ("build-312" is 312), or null for any other tag. */
    fun buildOf(tag: String): Int? = Regex("""^build-(\d+)$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Whether [update] is newer than the build running, [current]. */
    fun isNewer(update: AvailableUpdate?, current: Long): Boolean = update != null && update.build > current

    /** The first line of a release's notes, for a one-line summary ("Lighter drawing work for low-end TVs"). */
    fun headline(update: AvailableUpdate): String? = update.notes?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
}
