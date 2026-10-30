package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.QueueItem

/** Where one episode stands, as its title card shows it. */
enum class CardState { LIBRARY, DOWNLOADING, REQUESTED, REQUEST, STREAM_ONLY, UNAIRED, UNAIRED_REQUESTED }

/**
 * What an episode card draws for its [state]: the badge top right ([badgeAccent] is saffron, otherwise muted), whether
 * Play shows, and whether the Download disc shows and can take focus. [progress] is the download's 0..1, or null for
 * "downloading, amount unknown".
 */
data class CardLook(
    val state: CardState,
    val badge: String?,
    val badgeAccent: Boolean,
    val playShown: Boolean,
    val downloadShown: Boolean,
    val downloadFocusable: Boolean,
    val progress: Float? = null,
    /** Not out yet (a future or unknown air date): the still is greyed and the "Airs …" label shows. */
    val unaired: Boolean = false
)

/**
 * Pure so it can be tested. [airDate] and [today] are ISO dates ("2026-12-21"), compared as text; no date at all means
 * "airs TBA", which counts as not out yet. [download] is this episode's entry in the download queue, when it has one.
 * What Sonarr says about the episode itself wins over what Seerr says about its season.
 *
 * An episode that has not aired can still be asked for: Sonarr then watches for it and grabs it once it is out. It
 * keeps its greyed still and "Airs …" label either way; it has no Play (there is nothing to stream yet).
 *
 * [canStream] is whether this TV has a stream service. Without one, an episode that is not in the library has no Play:
 * pressing it could only say to use the download, so it is left off, as Stream now is for films.
 */
fun episodeCardState(
    inLibrary: Boolean,
    sonarrState: EpisodeState?,
    seasonAvailability: Availability,
    airDate: String?,
    today: String,
    canRequest: Boolean,
    canStream: Boolean,
    download: QueueItem? = null
): CardLook {
    val unaired = EpisodeCards.unaired(airDate, today)
    val state = when {
        inLibrary -> CardState.LIBRARY
        sonarrState == EpisodeState.DOWNLOADED -> CardState.LIBRARY
        unaired && sonarrState == EpisodeState.REQUESTED -> CardState.UNAIRED_REQUESTED
        unaired -> CardState.UNAIRED
        download != null -> CardState.DOWNLOADING
        sonarrState == EpisodeState.REQUESTED -> CardState.REQUESTED
        seasonAvailability == Availability.AVAILABLE -> CardState.LIBRARY
        seasonAvailability == Availability.DOWNLOADING -> CardState.DOWNLOADING
        seasonAvailability == Availability.REQUESTED || seasonAvailability == Availability.PROCESSING -> CardState.REQUESTED
        canRequest -> CardState.REQUEST
        else -> CardState.STREAM_ONLY
    }
    val progress = if (state == CardState.DOWNLOADING) download?.progress() else null
    return when (state) {
        CardState.LIBRARY -> CardLook(state, "IN LIBRARY", true, playShown = true, downloadShown = true, downloadFocusable = false)
        CardState.DOWNLOADING -> CardLook(state, if (progress != null) "DOWNLOADING ${Math.round(progress * 100)}%" else "DOWNLOADING", true,
            playShown = canStream, downloadShown = true, downloadFocusable = true, progress = progress)
        CardState.REQUESTED -> CardLook(state, "REQUESTED", false, playShown = canStream, downloadShown = true, downloadFocusable = true)
        CardState.REQUEST -> CardLook(state, null, false, playShown = canStream, downloadShown = true, downloadFocusable = true)
        CardState.STREAM_ONLY -> CardLook(state, null, false, playShown = canStream, downloadShown = false, downloadFocusable = false)
        CardState.UNAIRED -> CardLook(state, null, false, playShown = false, downloadShown = canRequest, downloadFocusable = canRequest, unaired = true)
        CardState.UNAIRED_REQUESTED -> CardLook(state, "REQUESTED", false, playShown = false, downloadShown = true, downloadFocusable = true, unaired = true)
    }
}

/** Plain helpers for the season bar and the cards, kept apart from the views so they can be tested. */
object EpisodeCards {
    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "2026-12-21" as "21 Dec 2026"; anything unreadable gives null. */
    fun formatDate(iso: String?): String? {
        if (iso == null || iso.length < 10) return null
        val y = iso.substring(0, 4).toIntOrNull() ?: return null
        val m = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val d = iso.substring(8, 10).toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        return "$d ${MONTHS[m - 1]} $y"
    }

    /** Not out yet: aired after [today], or no air date known at all ("Airs TBA"). */
    fun unaired(airDate: String?, today: String): Boolean = airDate == null || (airDate.length >= 10 && airDate.take(10) > today.take(10))

    /** The label on an unaired episode's still: "Airs 21 Dec 2026", or "Airs TBA" with no date. */
    fun airsLabel(airDate: String?): String = "Airs " + (formatDate(airDate) ?: "TBA")

    /** The tip over an unaired episode's Download disc. */
    fun preRequestTip(season: Int, episode: Int) = "Get S$season E$episode when it airs"

    /** What the toast says once an unaired episode is asked for. */
    fun preRequestDone(season: Int, episode: Int, airDate: String?) =
        "S$season E$episode will download when it airs" + (formatDate(airDate)?.let { " on ${it.substringBeforeLast(' ')}" } ?: "")

    /** Today in the TV's own time zone, as an ISO date. */
    fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** How far into the episode the viewer got (0..1) and the whole minutes left, or null when it has not been started. */
    fun resume(resumeMs: Long, runtimeMin: Int?): Pair<Float, Int>? {
        val total = (runtimeMin ?: return null) * 60_000L
        if (resumeMs <= 0 || total <= 0 || resumeMs >= total) return null
        return (resumeMs.toFloat() / total) to Math.round((total - resumeMs) / 60_000.0).toInt().coerceAtLeast(1)
    }

    /** "52 min · 32 min left", "6 Apr 2025": the meta line under a card's title, leaving out what is not known. */
    fun metaParts(runtimeMin: Int?, minutesLeft: Int?, airDate: String?): List<String> = listOfNotNull(
        runtimeMin?.let { r -> "$r min" + (minutesLeft?.let { " · $it min left" } ?: "") },
        formatDate(airDate)
    )

    /** The queue entry for one episode, from its "S2 E4 · Title" detail. A whole-season pack does not match. */
    fun download(items: List<QueueItem>, tmdbId: Long?, season: Int, episode: Int): QueueItem? {
        if (tmdbId == null) return null
        val label = Regex("^S(\\d+) E(\\d+)\\b")
        return items.firstOrNull { item ->
            item.tmdbId == tmdbId && item.detail?.let { d -> label.find(d)?.destructured?.let { (s, e) -> s.toInt() == season && e.toInt() == episode } } == true
        }
    }

    /**
     * The season bar's info, as (text, emphasised) pieces: "10 episodes", then "1 in library" and "1 downloading", or
     * "All in your library" when every episode is there.
     */
    fun seasonInfo(episodes: Int, inLibrary: Int, downloading: Int): List<Pair<String, Boolean>> {
        val parts = arrayListOf("$episodes episode${if (episodes == 1) "" else "s"}" to true)
        if (episodes > 0 && inLibrary >= episodes) parts += "All in your library" to false
        else {
            if (inLibrary > 0) parts += "$inLibrary in library" to false
            if (downloading > 0) parts += "$downloading downloading" to false
        }
        return parts
    }

    /** The season action's label: a request for a season nobody has asked for, otherwise where it has got to. Null hides it. */
    fun seasonAction(availability: Availability, seasonName: String, allInLibrary: Boolean): String? = when {
        allInLibrary -> null
        availability == Availability.AVAILABLE -> "In your library"
        availability == Availability.NONE -> "Download $seasonName"
        availability == Availability.DOWNLOADING -> "Downloading…"
        availability == Availability.PARTIAL -> "Partly downloaded"
        else -> "Requested ✓"
    }
}
