package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.ui.Perf
import org.json.JSONArray

/** What the TV's and the desktop's Letterboxd page caches have in common. */
object LetterboxdPages {
    /** Nobody to begin with: the owner follows whoever they like from the Letterboxd page. */
    val DEFAULT = emptyList<String>()
    const val PREFS = "letterboxd_follow"
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE

    /** How many people are followed, without opening the page cache (for Settings). */
    fun followingCount(platform: Platform): Int = try {
        platform.preferences(PREFS).getString("users", null)?.let { JSONArray(it).length() } ?: DEFAULT.size
    } catch (_: Exception) { 0 }

    /** Unfollows everyone (Settings, Reset everything). */
    fun forgetFollowing(platform: Platform) { platform.preferences(PREFS).edit().remove("users").apply() }

    /** Hosts a page may load from: Letterboxd, its picture and script servers, and the browser check's. */
    private val NEEDED_HOSTS = listOf("letterboxd.com", "ltrbxd.com", "challenges.cloudflare.com")

    /** Files a scraped page draws with and never reads: styles, fonts and pictures. */
    private val UNNEEDED_FILES = listOf(".css", ".woff", ".woff2", ".ttf", ".otf", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico")

    /** True for a browser request the page does not need: from another host, or a style, font or picture. */
    fun notNeededByBrowser(host: String?, path: String): Boolean {
        if (host == null || NEEDED_HOSTS.none { host == it || host.endsWith(".$it") }) return true
        return UNNEEDED_FILES.any { path.lowercase().substringBefore('?').endsWith(it) }
    }

    /** Drops scripts, styles and inline drawings: a review page goes from 1.4 MB to a fraction of that. */
    fun slim(html: String): String {
        val started = Perf.now()
        val out = html
            // Every script but the film's data block (its rating, and how many have rated and reviewed it).
            .replace(Regex("<script\\b(?![^>]*application/ld\\+json).*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style\\b.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<svg\\b(?:(?!aria-label=\"(?:[★½]+|Liked)\").)*?</svg>", RegexOption.DOT_MATCHES_ALL), "")
        Perf.took("lb slim ${html.length}->${out.length} chars", Perf.now() - started)
        return out
    }
}
