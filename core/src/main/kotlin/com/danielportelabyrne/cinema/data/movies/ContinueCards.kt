package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem

/** What OK on a Continue watching card does, once its [ContinueEntry.source] is looked at. */
enum class ContinueOpen {
    /** Play a Jellyfin item. */
    PLAY_LIBRARY,
    /** Reopen the saved stream. */
    PLAY_STREAM,
    /** Open the series page with this episode picked, so a source is found the usual way. */
    EPISODE_PAGE,
    /** Open the film's or series' page only. */
    PAGE
}

/** A card's words and what it does. Pure, so the shelf's labels and routing can be tested without a screen. */
data class ContinueCardModel(
    val key: String,
    val title: String,
    /** "Resume", "Next episode" or "Start over". */
    val label: String,
    /** "S2 E4 · Episode title", or null for a film. */
    val detail: String?,
    /** "23 min left"; null unless resuming with a known length. */
    val remaining: String?,
    /** 0 to 1 for a resuming card with a known length; null otherwise, so no bar is shown. */
    val progress: Float?,
    val open: ContinueOpen,
    /** Play from 0:00: a next episode, or a start over. */
    val fromStart: Boolean,
    val resumeMs: Long?
) {
    /** The lead card's saffron line over its name. */
    val featuredKicker: String get() = listOfNotNull(label, detail).joinToString(" · ")
    val featuredMeta: String get() = remaining.orEmpty()
    /** A small card has one line: the label leads unless it is plain Resume, whose episode and time left say it. */
    val smallMeta: String get() = (if (label == "Resume") listOfNotNull(detail, remaining) else listOfNotNull(label, detail)).joinToString(" · ")
}

/** Continue watching's cards, from the engine's choices. */
object ContinueCards {
    fun model(e: ContinueEntry): ContinueCardModel {
        val label = when (e.action) {
            ContinueAction.RESUME -> "Resume"
            ContinueAction.NEXT_EPISODE -> "Next episode"
            ContinueAction.START_OVER -> "Start over"
        }
        return ContinueCardModel(e.key, title(e), label, detail(e), remaining(e), e.progress.takeIf { e.action == ContinueAction.RESUME },
            route(e), e.action != ContinueAction.RESUME, e.resumeMs.takeIf { e.action == ContinueAction.RESUME })
    }

    private fun title(e: ContinueEntry) = e.title.ifBlank { if (e.kind == Kind.MOVIE) "Film" else "Series" }

    private fun detail(e: ContinueEntry): String? {
        if (e.kind == Kind.MOVIE) return null
        // The next episode is not known yet: say where it follows from, never a guessed number.
        val ep = e.episode ?: return e.finished?.code?.let { "After $it" }
        return listOfNotNull(ep.code, ep.title?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null }
    }

    private fun remaining(e: ContinueEntry): String? {
        val left = e.minutesLeft.takeIf { e.action == ContinueAction.RESUME } ?: return null
        return if (left >= 60) "${left / 60}h ${left % 60}m left" else "$left min left"
    }

    /**
     * Where OK goes. A library item plays; a saved stream reopens (only to resume: a stream cannot be restarted from here);
     * anything else opens a page, on the episode when it is known, and never plays.
     */
    fun route(e: ContinueEntry): ContinueOpen = when (e.source) {
        is ContinueSource.Library -> ContinueOpen.PLAY_LIBRARY
        is ContinueSource.Stream -> if (e.action == ContinueAction.RESUME) ContinueOpen.PLAY_STREAM else pageFor(e)
        ContinueSource.PickSource -> pageFor(e)
        ContinueSource.Unknown -> ContinueOpen.PAGE
    }

    private fun pageFor(e: ContinueEntry): ContinueOpen {
        val ep = e.episode
        return if (e.kind == Kind.SHOW && ep?.season != null && ep.episode != null) ContinueOpen.EPISODE_PAGE else ContinueOpen.PAGE
    }

    /**
     * The library item a [ContinueSource.Library] card plays: the exact film or episode it names, never a series that
     * shares an id with nothing. Null when the cache no longer holds it (or it is a missing placeholder), so the card
     * opens its page instead.
     */
    fun libraryItem(e: ContinueEntry, items: Iterable<JellyfinItem>): JellyfinItem? {
        val id = ContinueEngine.jellyfinKey((e.source as? ContinueSource.Library)?.itemId) ?: return null
        return items.firstOrNull { ContinueEngine.jellyfinKey(it.id) == id && !it.missing && if (e.kind == Kind.SHOW) it.isEpisode else it.isMovie }
    }

    /**
     * The episodes of the series [ids] as the caches hold them: Seerr's listing ([tmdb], null when not held) joined with the
     * library's own episodes. Null when neither knows any, so the engine opens the series page instead of guessing.
     */
    fun catalogue(ids: TitleRef, tmdb: EpisodeCatalogue?, library: Iterable<JellyfinItem>): EpisodeCatalogue? {
        val jf = ContinueEngine.jellyfinKey(ids.jellyfinId)
        val own = if (jf == null) null else EpisodeCatalogue.fromLibrary(library.filter { it.isEpisode && ContinueEngine.jellyfinKey(it.seriesId) == jf })
        return EpisodeCatalogue.merge(tmdb, own?.takeIf { it.episodes.isNotEmpty() })
    }

    /**
     * The episode after S[season]E[episode] in [cat], for the long-press menu's "Next episode": the very next number, or episode 1
     * of the next season. Only in a listing known to be complete is anything further along trusted. Null when there is none.
     */
    fun nextEpisode(cat: EpisodeCatalogue?, season: Int, episode: Int): CatalogueEpisode? {
        if (cat == null) return null
        val list = cat.episodes.filter { it.season > 0 }.distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode }))
        val last = list.firstOrNull { it.season == season && episode in it.episode..it.lastEpisode }?.lastEpisode ?: episode
        return list.firstOrNull { (it.season == season && it.episode == last + 1) || (it.season == season + 1 && it.episode == 1) }
            ?: if (cat.complete) list.firstOrNull { it.season > season || (it.season == season && it.episode > last) } else null
    }

    /**
     * Where focus goes when the shelf changes from [old] to [now] (card keys) with [focused] (an index in [old]) focused:
     * the same film or series wherever it moved to (a Resume that became a Next episode keeps its key), else the nearest
     * card that survived (the one that followed, then those before), else -1.
     */
    fun focusAfter(old: List<String>, focused: Int, now: List<String>): Int {
        if (focused !in old.indices) return -1
        for (i in focused until old.size) now.indexOf(old[i]).takeIf { it >= 0 }?.let { return it }
        for (i in focused - 1 downTo 0) now.indexOf(old[i]).takeIf { it >= 0 }?.let { return it }
        return -1
    }
}
