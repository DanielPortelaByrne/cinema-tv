package com.danielportelabyrne.cinema.data.movies

/** What the app already knows about a title from elsewhere (the library, favourites), used to name a record that has no title yet. */
data class KnownTitle(
    val kind: Kind,
    val name: String,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val jellyfinId: String? = null,
    val poster: String? = null,
    val backdrop: String? = null
)

/** One card on Home's Recently watched shelf, ready to draw. */
data class RecentCard(
    val entry: PlayedEntry,
    /** The film's name, or the series' name for an episode. Never blank. */
    val title: String,
    /** "S2 E4" for an episode. */
    val code: String?,
    val episodeTitle: String?,
    /** How far in it was, 0 to 1. Null when finished (no resume bar) or when the length is not known. */
    val progress: Float?,
    val watched: Boolean,
    val minutesLeft: Int?,
    /** The best picture it has, or null (the card then shows its placeholder). */
    val artUrl: String?
) {
    /** The line under the name: the episode code, "Watched" or the time left, then the episode's title. */
    val meta: String get() = listOfNotNull(code, if (watched) "Watched" else minutesLeft?.let(::left), episodeTitle).joinToString(" · ")

    private fun left(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m left" else "$m min left"
}

/** Where OK on a card goes: a title page, never the player. */
sealed class RecentTarget {
    data class Film(val entry: PlayedEntry) : RecentTarget()
    /** The series' page with this episode picked. */
    data class Episode(val entry: PlayedEntry, val season: Int, val episode: Int) : RecentTarget()
}

/** Pure selection behind the Recently watched shelf. */
object RecentlyWatched {
    const val LIMIT = 20

    /**
     * [entry] with any title, pictures and series ids that [known] can supply. Playback facts and what the entry already
     * holds are left as they are. Matching is by id only, never by name.
     */
    fun enrich(entry: PlayedEntry, known: List<KnownTitle>): PlayedEntry {
        val k = known.firstOrNull { it.kind == entry.kind && matches(entry, it) } ?: return entry
        return if (entry.isEpisode) entry.copy(
            title = entry.title.ifBlank { k.name }, seriesTmdbId = entry.seriesTmdbId ?: k.tmdbId, seriesImdbId = entry.seriesImdbId ?: k.imdbId,
            seriesJellyfinId = entry.seriesJellyfinId ?: k.jellyfinId, poster = entry.poster ?: k.poster, backdrop = entry.backdrop ?: k.backdrop)
        else entry.copy(
            title = entry.title.ifBlank { k.name }, tmdbId = entry.tmdbId ?: k.tmdbId, imdbId = entry.imdbId ?: k.imdbId,
            jellyfinId = entry.jellyfinId ?: k.jellyfinId, poster = entry.poster ?: k.poster, backdrop = entry.backdrop ?: k.backdrop)
    }

    private fun matches(e: PlayedEntry, k: KnownTitle): Boolean {
        fun eq(a: String?, b: String?) = !a.isNullOrBlank() && a == b
        return if (e.isEpisode) eq(e.seriesTmdbId, k.tmdbId) || eq(e.seriesImdbId, k.imdbId) || eq(e.seriesJellyfinId, k.jellyfinId)
        else eq(e.tmdbId, k.tmdbId) || eq(e.imdbId, k.imdbId) || eq(e.jellyfinId, k.jellyfinId)
    }

    /** Where OK goes, or null when the record cannot yet be opened: an episode needs its numbers and a series page that can list it. */
    fun target(e: PlayedEntry): RecentTarget? {
        if (!e.hasId) return null
        if (!e.isEpisode) return RecentTarget.Film(e)
        val s = e.season ?: return null
        val n = e.episode ?: return null
        // A streamed series' episodes are listed by its TMDB id; a library series' by its own id. An IMDb id alone opens nothing.
        if (e.seriesTmdbId.isNullOrBlank() && e.seriesJellyfinId.isNullOrBlank()) return null
        return RecentTarget.Episode(e, s, n)
    }

    /** The card for [e], or null while it has no name or cannot be opened. Nothing is invented. */
    fun card(e: PlayedEntry): RecentCard? {
        if (e.title.isBlank() || target(e) == null) return null
        val done = e.completed
        val progress = if (!done && e.durationMs > 0) (e.positionMs.toFloat() / e.durationMs).coerceIn(0f, 1f) else null
        val left = if (!done && e.durationMs > 0 && e.positionMs > 0) ((e.durationMs - e.positionMs) / 60_000).toInt().coerceAtLeast(1) else null
        val art = if (e.isEpisode) e.still ?: e.backdrop ?: e.poster else e.backdrop ?: e.poster
        return RecentCard(e, e.title.trim(), if (e.isEpisode) "S${e.season} E${e.episode}" else null,
            if (e.isEpisode) e.episodeTitle?.takeIf { it.isNotBlank() } else null, progress, done, left, art?.takeIf { it.isNotBlank() })
    }

    /**
     * The shelf: [history] enriched from [known], newest playback first, one card per distinct film or episode. Records that
     * cannot be shown yet are left out (but stay in the store). [keep] picks what a page shows (see PersonalFilter); it is
     * asked of the enriched record, so an episode's series ids learned from [known] count, and before [limit].
     */
    fun shelf(history: List<PlayedEntry>, known: List<KnownTitle> = emptyList(), limit: Int = LIMIT, keep: (PlayedEntry) -> Boolean = { true }): List<RecentCard> {
        val out = ArrayList<RecentCard>()
        // A film or episode left out here is not shown later from an older record either.
        val passed = ArrayList<PlayedEntry>()
        for (e in history.map { enrich(it, known) }.sortedByDescending { it.playedAt }) {
            if (out.any { it.entry.sameAs(e) } || passed.any { it.sameAs(e) }) continue
            if (!keep(e)) { passed += e; continue }
            card(e)?.let { out += it }
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * Where focus goes after the shelf changes from [old] to [now] with [focused] (an index in [old]) focused: the same
     * film or episode wherever it moved to, else the nearest card that survived (the one that followed, then the ones
     * before), else -1.
     */
    fun focusAfter(old: List<PlayedEntry>, focused: Int, now: List<PlayedEntry>): Int {
        if (focused !in old.indices) return -1
        fun at(e: PlayedEntry) = now.indexOfFirst { it.sameAs(e) }
        for (i in focused until old.size) at(old[i]).takeIf { it >= 0 }?.let { return it }
        for (i in focused - 1 downTo 0) at(old[i]).takeIf { it >= 0 }?.let { return it }
        return -1
    }
}
