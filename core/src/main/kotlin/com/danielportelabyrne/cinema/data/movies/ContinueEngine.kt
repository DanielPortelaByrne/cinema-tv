package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.ui.EpisodeCards
import org.json.JSONObject

// --- What goes in ---------------------------------------------------------------------------------

/** A stream the player saved for Continue watching: the payload that reopens it, where it stopped, and when. */
data class SavedStream(val payload: String, val positionMs: Long, val durationMs: Long, val atMs: Long)

/** The ids a film or series is known by. A Jellyfin id is kept in [ContinueEngine.jellyfinKey] form. */
data class TitleRef(val tmdbId: String? = null, val imdbId: String? = null, val jellyfinId: String? = null)

/** One episode a series is known to have, from TMDB and/or the library. */
data class CatalogueEpisode(
    val season: Int,
    val episode: Int,
    val title: String? = null,
    /** ISO date ("2026-12-21"); null when not known. */
    val airDate: String? = null,
    val still: String? = null,
    /** The library's copy, when the server holds the file: it can then play at once, whatever its air date. */
    val jellyfinId: String? = null,
    /** The last episode one file covers ("E1-E2"); the same as [episode] otherwise. */
    val lastEpisode: Int = episode
)

/**
 * The episodes a series is known to have, as already cached. [complete] is true only when every season's whole listing
 * is known; otherwise a gap may hide an episode, so nothing is guessed across one.
 */
data class EpisodeCatalogue(val episodes: List<CatalogueEpisode>, val complete: Boolean) {
    companion object {
        /** From Seerr's seasons and the episode listings loaded so far. Complete once each season lists all its episodes. */
        fun fromTmdb(seasons: List<SeasonInfo>, episodes: List<EpisodeInfo>): EpisodeCatalogue {
            val eps = episodes.filter { it.season > 0 }.distinctBy { it.season to it.number }
                .map { CatalogueEpisode(it.season, it.number, it.name, it.airDate, it.stillUrl) }
            val regular = seasons.filter { it.number > 0 }
            val complete = regular.isNotEmpty() && regular.all { s -> eps.count { it.season == s.number } >= s.episodeCount }
            return EpisodeCatalogue(eps, complete)
        }

        /** From the library's episodes. Never complete: the library may lack episodes it never downloaded. */
        fun fromLibrary(items: List<JellyfinItem>): EpisodeCatalogue = EpisodeCatalogue(items.filter { it.isEpisode }.mapNotNull { i ->
            val s = i.season ?: return@mapNotNull null
            val n = i.episode ?: return@mapNotNull null
            CatalogueEpisode(s, n, i.name.ifBlank { null }, jellyfinId = i.id.takeUnless { i.missing }, lastEpisode = maxOf(n, i.episodeEnd ?: n))
        }, complete = false)

        /** Both listings as one: an episode keeps what either knows. */
        fun merge(a: EpisodeCatalogue?, b: EpisodeCatalogue?): EpisodeCatalogue? {
            if (a == null || b == null) return a ?: b
            val all = LinkedHashMap<Pair<Int, Int>, CatalogueEpisode>()
            for (e in a.episodes + b.episodes) {
                val k = e.season to e.episode
                val o = all[k]
                all[k] = if (o == null) e else o.copy(title = o.title ?: e.title, airDate = o.airDate ?: e.airDate, still = o.still ?: e.still,
                    jellyfinId = o.jellyfinId ?: e.jellyfinId, lastEpisode = maxOf(o.lastEpisode, e.lastEpisode))
            }
            return EpisodeCatalogue(all.values.toList(), a.complete || b.complete)
        }
    }
}

/**
 * Everything the engine reads, all already on this TV: nothing here is fetched. [scope] is this server and user
 * ([FavouritesStore.scope]); [history] is that scope's play history. [streamProgress] is the saved JSON as stored.
 * [server] is Jellyfin items with the user's own data (the resume list, a series' episodes); items without any
 * played state are ignored. [known] names titles and joins a stream's ids to the library's. [today] is an ISO date.
 */
data class ContinueInputs(
    val scope: String,
    val today: String,
    val history: List<PlayedEntry> = emptyList(),
    val streamProgress: String? = null,
    val savedStreams: List<SavedStream> = emptyList(),
    val server: List<JellyfinItem> = emptyList(),
    val known: List<KnownTitle> = emptyList()
)

// --- What comes out -------------------------------------------------------------------------------

/** What OK on a Continue watching card does. */
enum class ContinueAction {
    /** Carry on from [ContinueEntry.resumeMs]. */
    RESUME,
    /** The episode after the one finished. It starts at the beginning, so there is no progress to show. */
    NEXT_EPISODE,
    /** An episode watched without a usable resume point (under a minute in, or sought back to the start): play it from the start. */
    START_OVER
}

/** How a card's film or episode can be played. */
sealed class ContinueSource {
    /** The library holds it: play this Jellyfin item. */
    data class Library(val itemId: String) : ContinueSource()

    /** Reopen the stream the player saved for it (PlayerActivity.resume). */
    data class Stream(val payload: String) : ContinueSource()

    /** The film or episode is known but has no source yet: open its page so a source is found the usual way. No "Play now". */
    object PickSource : ContinueSource() { override fun toString() = "PickSource" }

    /** Which episode comes next is not known yet (its listing is not cached): open the series page only. */
    object Unknown : ContinueSource() { override fun toString() = "Unknown" }

    val playable: Boolean get() = this is Library || this is Stream
}

/** An episode on a card. [season] and [episode] are null only for a library episode known by its id alone. */
data class ContinueEpisode(val season: Int?, val episode: Int?, val title: String? = null, val still: String? = null, val jellyfinId: String? = null) {
    val code: String? get() = if (season != null && episode != null) "S$season E$episode" else null
}

/** One Continue watching card: a film, or a series with the one episode it offers. Pure data, ready to cache and draw. */
data class ContinueEntry(
    /** Stable for the same film or series; built from every id it is known by. */
    val key: String,
    val kind: Kind,
    val action: ContinueAction,
    /** The film's or series' name; may be blank when nothing on this TV names it yet. */
    val title: String,
    val ids: TitleRef,
    /** The episode to play. Null for a film, and for a series whose next episode is not known yet ([ContinueSource.Unknown]). */
    val episode: ContinueEpisode?,
    /** For [ContinueAction.NEXT_EPISODE]: the episode just finished. */
    val finished: ContinueEpisode?,
    /** The latest genuine playback of this film or series. Cards are ordered by it. */
    val lastPlayedAt: Long,
    /** Only for [ContinueAction.RESUME]. */
    val resumeMs: Long?,
    /** Only for [ContinueAction.RESUME], and only when known. */
    val durationMs: Long?,
    val source: ContinueSource,
    val poster: String? = null,
    val backdrop: String? = null,
    val still: String? = null
) {
    /** How far in, 0 to 1; null unless resuming with a known length. */
    val progress: Float? get() = if (resumeMs != null && durationMs != null && durationMs > 0) (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
    val minutesLeft: Int? get() = if (resumeMs != null && durationMs != null && durationMs > resumeMs) ((durationMs - resumeMs) / 60_000).toInt().coerceAtLeast(1) else null
}

// --- Reconciling ----------------------------------------------------------------------------------

/** Where a playback fact came from. The order breaks ties between facts saved at the same moment. */
enum class PlaybackOrigin { JELLYFIN, HISTORY, STREAM_PROGRESS, SAVED_STREAM }

/**
 * One record that a film or episode was really played. [entry] holds its ids and details only; the playback facts are
 * here. [positionMs] is where playback last was (it moves back when the viewer seeks back); [legacy] marks an old
 * stream record whose position is only the furthest point.
 */
data class PlaybackFact(
    val entry: PlayedEntry,
    val origin: PlaybackOrigin,
    val atMs: Long,
    val positionMs: Long,
    val furthestMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val legacy: Boolean = false,
    val source: ContinueSource? = null
)

/** Where one film or episode stands, all its records taken together. [resumeMs] is null when there is no usable resume point. */
data class PlaybackState(
    val watched: Boolean,
    val completed: Boolean,
    val resumeMs: Long?,
    val furthestMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
    /** The resume point came from an old record that kept only the furthest point. */
    val legacyResume: Boolean,
    /** A source known to play it ([ContinueSource.Library] or [ContinueSource.Stream]), else null. */
    val source: ContinueSource?
)

/** One film or episode: its merged identity and where it stands. */
data class ReconciledPlayback(val entry: PlayedEntry, val state: PlaybackState)

/**
 * Decides what Continue watching offers: one card per film or series, from what was really played here and on the
 * server. Pure: it reads only [ContinueInputs] and a cache lookup for episode listings, and never fetches.
 */
object ContinueEngine {
    /** A resume point saved here counts from one minute in (the player's own rule); the server's counts however short. */
    const val MIN_RESUME_MS = 60_000L
    const val LIMIT = 20
    /** Records this close to the play history's are taken as the same viewing (a pause can hold history's time back). */
    const val SAME_VIEWING_MS = 30 * 60_000L

    /** A Jellyfin id in one form: the server writes it with or without dashes, in either case. */
    fun jellyfinKey(id: String?): String? = id?.replace("-", "")?.lowercase()?.ifBlank { null }

    // --- Facts

    /** Every genuine playback record in [inputs], with ids joined through [ContinueInputs.known]. Catalogue data adds nothing. */
    fun facts(inputs: ContinueInputs): List<PlaybackFact> {
        val known = inputs.known.map { it.copy(jellyfinId = jellyfinKey(it.jellyfinId)) }
        fun tidy(e: PlayedEntry) = RecentlyWatched.enrich(e.copy(jellyfinId = jellyfinKey(e.jellyfinId), seriesJellyfinId = jellyfinKey(e.seriesJellyfinId)), known)
        val out = ArrayList<PlaybackFact>()
        for (e in inputs.history) {
            if (!e.hasId || e.playedAt <= 0) continue
            val furthest = if (e.completed && e.durationMs > 0) maxOf(e.positionMs, e.durationMs) else e.positionMs
            out += PlaybackFact(tidy(e), PlaybackOrigin.HISTORY, e.playedAt, e.positionMs, furthest, e.durationMs, e.completed)
        }
        for ((imdb, ep) in StreamProgress.all(inputs.streamProgress)) {
            // A stream opened for a moment is not watching: the same bar the play history sets.
            if (ep.atMs <= 0 || (ep.positionMs < PlaySession.QUALIFY_MS && !ep.played)) continue
            val entry = PlayedEntry(Kind.SHOW, seriesImdbId = imdb, season = ep.season, episode = ep.episode)
            out += PlaybackFact(tidy(entry), PlaybackOrigin.STREAM_PROGRESS, ep.atMs, if (ep.hasResume) ep.resumeMs else ep.positionMs,
                ep.positionMs, ep.durationMs, PlayCompletion.reached(true, ep.positionMs, ep.durationMs, false), legacy = !ep.hasResume)
        }
        for (s in inputs.savedStreams) {
            val entry = savedStreamEntry(s.payload, inputs.scope) ?: continue
            if (s.atMs <= 0 || s.positionMs <= 0) continue
            out += PlaybackFact(tidy(entry), PlaybackOrigin.SAVED_STREAM, s.atMs, s.positionMs, s.positionMs, s.durationMs,
                PlayCompletion.reached(entry.isEpisode, s.positionMs, s.durationMs, false), source = ContinueSource.Stream(s.payload))
        }
        for (i in inputs.server) {
            if (!i.isMovie && !i.isEpisode) continue
            if (!i.played && i.resumeMs <= 0 && i.playedMs == null) continue   // no user data: a catalogue entry, not playback
            val entry = if (i.isMovie) PlayedEntry(Kind.MOVIE, tmdbId = i.tmdbId, imdbId = i.imdbId, jellyfinId = i.id, title = i.name, source = PlayedEntry.SOURCE_JELLYFIN)
            else PlayedEntry(Kind.SHOW, jellyfinId = i.id, seriesJellyfinId = i.seriesId, title = i.seriesName.orEmpty(), episodeTitle = i.name.ifBlank { null },
                season = i.season, episode = i.episode, source = PlayedEntry.SOURCE_JELLYFIN)
            val length = (i.runtimeMin ?: 0) * 60_000L
            out += PlaybackFact(tidy(entry), PlaybackOrigin.JELLYFIN, i.playedMs ?: 0L, i.resumeMs.coerceAtLeast(0),
                if (i.played) maxOf(length, i.resumeMs) else i.resumeMs, length, i.played,
                source = if (i.missing) null else ContinueSource.Library(i.id))
        }
        return out.filter { it.entry.hasId }
    }

    /**
     * Who a saved stream's payload is for: the identity the launching page gave it, else its IMDb id and episode. Jellyfin
     * ids from another server or user are dropped. Null when it names nothing, or names two different things.
     */
    fun savedStreamEntry(payload: String, scope: String): PlayedEntry? {
        val o = try { JSONObject(payload) } catch (_: Exception) { return null }
        val imdb = o.optString("imdb").ifBlank { null }
        val episode = o.optString("episode").split(":").takeIf { it.size == 2 }?.let { p ->
            val s = p[0].toIntOrNull(); val n = p[1].toIntOrNull()
            if (s != null && n != null) s to n else null
        }
        val target = HistoryTarget.fromJson(o.optString("history").ifBlank { null })
        val given = target?.entry?.let { e -> if (target.scope == scope) e else e.copy(jellyfinId = null, seriesJellyfinId = null) }
        if (given != null) {
            if (episode != null && (!given.isEpisode || (given.season != null && given.season != episode.first) || (given.episode != null && given.episode != episode.second))) return null
            val ownImdb = if (given.isEpisode) given.seriesImdbId else given.imdbId
            if (imdb != null && ownImdb != null && imdb != ownImdb) return null
            val filled = if (given.isEpisode) given.copy(seriesImdbId = ownImdb ?: imdb, season = given.season ?: episode?.first, episode = given.episode ?: episode?.second)
            else given.copy(imdbId = ownImdb ?: imdb)
            return filled.copy(playedAt = 0, positionMs = 0, durationMs = 0, completed = false).takeIf { it.hasId }
        }
        val id = imdb ?: return null
        val name = o.optString("title").substringBefore(" · ")
        return if (episode != null) PlayedEntry(Kind.SHOW, seriesImdbId = id, title = name, season = episode.first, episode = episode.second)
        else PlayedEntry(Kind.MOVIE, imdbId = id, title = name)
    }

    // --- One film or episode

    private val FRESHEST = compareByDescending<PlaybackFact> { it.atMs }.thenBy { it.origin.ordinal }

    /**
     * Where one film or episode stands from all its [facts].
     *
     * - The freshest record wins between this TV and the server; at the same moment, the server does. A server record
     *   with no date still counts when it says played.
     * - Here, the play history decides whether it was finished when it knows the title: it starts afresh at each
     *   viewing, while stream progress keeps its furthest point for ever and a saved stream may be the same viewing's
     *   last moment. Another record that says finished counts too when it is from a later viewing (more than
     *   [SAME_VIEWING_MS] after the history's), such as a short session left in the credits that history never heard
     *   of. Without history, any record past the threshold (or ended) counts.
     * - The resume point is the freshest record's last position, not the furthest point, and needs a minute here.
     */
    fun reconcile(facts: List<PlaybackFact>): PlaybackState {
        if (facts.isEmpty()) return PlaybackState(false, false, null, 0, 0, 0, false, null)
        val ordered = facts.sortedWith(FRESHEST)
        val locals = ordered.filter { it.origin != PlaybackOrigin.JELLYFIN }
        val server = ordered.firstOrNull { it.origin == PlaybackOrigin.JELLYFIN }
        val local = locals.firstOrNull()
        val history = locals.firstOrNull { it.origin == PlaybackOrigin.HISTORY }
        val localDone = if (history == null) locals.any { it.completed }
            else history.completed || locals.any { it.origin != PlaybackOrigin.HISTORY && it.atMs > history.atMs + SAME_VIEWING_MS && endedLater(it) }
        val fromServer = server != null && (local == null || (server.atMs > 0 && server.atMs >= local.atMs) || (server.atMs <= 0 && server.completed))
        val winner = if (fromServer) server!! else local!!
        val completed = if (fromServer) server!!.completed else localDone
        val resume = when {
            completed -> null
            fromServer -> winner.positionMs.takeIf { it > 0 }
            else -> winner.positionMs.takeIf { it >= MIN_RESUME_MS }
        }
        val source = ordered.firstNotNullOfOrNull { it.source as? ContinueSource.Library } ?: locals.firstNotNullOfOrNull { it.source as? ContinueSource.Stream }
        return PlaybackState(true, completed, resume, ordered.maxOf { it.furthestMs }, winner.durationMs.takeIf { it > 0 } ?: ordered.maxOf { it.durationMs },
            ordered.maxOf { it.atMs }, resume != null && !fromServer && winner.legacy, source)
    }

    /**
     * Whether a record from after the play history's viewing shows that viewing was finished. Stream progress keeps its
     * furthest point for ever, so a rewatch paused partway still reads as finished by that point; its latest position says
     * where the viewer really is. Only a record saved without one (legacy) cannot tell the two apart and keeps its flag.
     */
    private fun endedLater(f: PlaybackFact): Boolean =
        if (f.origin == PlaybackOrigin.STREAM_PROGRESS && !f.legacy) PlayCompletion.reached(f.entry.isEpisode, f.positionMs, f.durationMs, false) else f.completed

    /** Every film and episode played, each with its records merged by id (never by name). */
    fun playback(inputs: ContinueInputs): List<ReconciledPlayback> =
        cluster(facts(inputs).sortedWith(FRESHEST.thenBy { factKey(it) }), { a, b -> a.entry.sameAs(b.entry) }, { a, b -> itemsConflict(a.entry, b.entry) })
            .map { group -> ReconciledPlayback(group.drop(1).fold(group.first().entry) { acc, f -> acc.learn(f.entry) }, reconcile(group)) }

    // --- Cards

    /**
     * The Continue watching cards, newest playback first (ties by [ContinueEntry.key]), at most [limit]. [catalogue]
     * must answer from cache only; it is asked once at most per series, and only for a series whose latest episode was
     * finished. [keep] picks the cards a page shows (see PersonalFilter); it is applied before [limit].
     */
    fun select(inputs: ContinueInputs, catalogue: (TitleRef) -> EpisodeCatalogue?, limit: Int = LIMIT, keep: (ContinueEntry) -> Boolean = { true }): List<ContinueEntry> {
        val items = playback(inputs)
        val films = items.filter { !it.entry.isEpisode }.mapNotNull(::filmEntry)
        val series = cluster(items.filter { it.entry.isEpisode }, { a, b -> sameSeries(a.entry, b.entry) }, { a, b -> seriesConflict(a.entry, b.entry) })
            .mapNotNull { seriesEntry(it, catalogue, inputs.today) }
        return (films + series).sortedWith(compareByDescending<ContinueEntry> { it.lastPlayedAt }.thenBy { it.key }).filter(keep).take(limit)
    }

    /** A film is offered only part-watched with a usable resume point. */
    private fun filmEntry(p: ReconciledPlayback): ContinueEntry? {
        val s = p.state
        if (s.completed || s.resumeMs == null || s.lastPlayedAt <= 0) return null
        val e = p.entry
        val ids = TitleRef(e.tmdbId, e.imdbId, e.jellyfinId)
        return ContinueEntry(key("movie", ids), Kind.MOVIE, ContinueAction.RESUME, e.title.trim(), ids, null, null, s.lastPlayedAt,
            s.resumeMs, s.durationMs.takeIf { it > 0 }, s.source ?: ContinueSource.PickSource, e.poster, e.backdrop, null)
    }

    private fun seriesEntry(group: List<ReconciledPlayback>, catalogue: (TitleRef) -> EpisodeCatalogue?, today: String): ContinueEntry? {
        val eps = group.sortedWith(compareByDescending<ReconciledPlayback> { it.state.lastPlayedAt }
            .thenByDescending { it.entry.season ?: -1 }.thenByDescending { it.entry.episode ?: -1 })
        val latest = eps.firstOrNull { it.state.lastPlayedAt > 0 } ?: return null
        // A finished special says nothing about where the series is: go by the latest regular episode instead.
        val pick = if (latest.entry.season == 0 && latest.state.completed) eps.firstOrNull { it.state.lastPlayedAt > 0 && it.entry.season != 0 } ?: return null else latest
        val ids = TitleRef(eps.firstNotNullOfOrNull { it.entry.seriesTmdbId }, eps.firstNotNullOfOrNull { it.entry.seriesImdbId }, eps.firstNotNullOfOrNull { it.entry.seriesJellyfinId })
        val title = eps.firstOrNull { it.entry.title.isNotBlank() }?.entry?.title?.trim().orEmpty()
        val poster = eps.firstNotNullOfOrNull { it.entry.poster }
        val backdrop = eps.firstNotNullOfOrNull { it.entry.backdrop }
        val key = key("show", ids.takeIf { it.tmdbId != null || it.imdbId != null || it.jellyfinId != null } ?: TitleRef(jellyfinId = "ep-" + latest.entry.jellyfinId))
        fun card(action: ContinueAction, ep: ContinueEpisode?, finished: ContinueEpisode?, state: PlaybackState?, source: ContinueSource) = ContinueEntry(
            key, Kind.SHOW, action, title, ids, ep, finished, latest.state.lastPlayedAt,
            state?.resumeMs.takeIf { action == ContinueAction.RESUME }, state?.durationMs?.takeIf { action == ContinueAction.RESUME && it > 0 },
            source, poster, backdrop, ep?.still)
        fun watching(p: ReconciledPlayback, still: String? = null, fallback: ContinueSource = ContinueSource.PickSource): ContinueEntry {
            val ep = ContinueEpisode(p.entry.season, p.entry.episode, p.entry.episodeTitle, p.entry.still ?: still, p.entry.jellyfinId)
            return card(if (p.state.resumeMs != null) ContinueAction.RESUME else ContinueAction.START_OVER, ep, null, p.state, p.state.source ?: fallback)
        }
        if (!pick.state.completed) return watching(pick)

        val done = ContinueEpisode(pick.entry.season, pick.entry.episode, pick.entry.episodeTitle, pick.entry.still, pick.entry.jellyfinId)
        val unknown = card(ContinueAction.NEXT_EPISODE, null, done, null, ContinueSource.Unknown)
        val s = pick.entry.season ?: return unknown
        val n = pick.entry.episode ?: return unknown
        val cat = catalogue(ids) ?: return unknown
        return when (val next = following(cat, s, n, today) { c -> stateOf(eps, c) }) {
            is Next.Found -> {
                val c = next.episode
                val mine = stateOf(eps, c)
                val library = c.jellyfinId?.let { ContinueSource.Library(it) }
                if (mine != null && !mine.state.completed) watching(mine, c.still, library ?: ContinueSource.PickSource)
                else card(ContinueAction.NEXT_EPISODE, ContinueEpisode(c.season, c.episode, c.title, c.still, jellyfinKey(c.jellyfinId)), done, null,
                    library ?: ContinueSource.PickSource)
            }
            Next.None -> null
            Next.NotKnown -> unknown
        }
    }

    private sealed class Next {
        data class Found(val episode: CatalogueEpisode) : Next()
        /** The series is finished, or the next episode has not aired. */
        object None : Next()
        /** The listing has a gap where the next episode would be. */
        object NotKnown : Next()
    }

    /**
     * The episode after S[season]E[episode] in [cat]: specials left out, never wrapping. One already finished is passed
     * over; one not out yet (or with no air date, unless the library has it) stops the search. In a listing that is not
     * complete, only the very next number (or episode 1 of the next season) is trusted.
     */
    private fun following(cat: EpisodeCatalogue, season: Int, episode: Int, today: String, state: (CatalogueEpisode) -> ReconciledPlayback?): Next {
        val list = cat.episodes.filter { it.season > 0 }.distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode }))
        var s = season
        var n = list.firstOrNull { it.season == season && episode in it.episode..it.lastEpisode }?.lastEpisode ?: episode
        for (c in list) {
            if (c.season < s || (c.season == s && c.episode <= n)) continue
            if (!cat.complete && !(c.season == s && c.episode == n + 1) && !(c.season == s + 1 && c.episode == 1)) return Next.NotKnown
            if (c.jellyfinId == null && EpisodeCards.unaired(c.airDate, today)) return Next.None
            if (state(c)?.state?.completed == true) { s = c.season; n = c.lastEpisode; continue }
            return Next.Found(c)
        }
        return if (cat.complete) Next.None else Next.NotKnown
    }

    private fun stateOf(eps: List<ReconciledPlayback>, c: CatalogueEpisode): ReconciledPlayback? = eps.firstOrNull { p ->
        (p.entry.season == c.season && p.entry.episode == c.episode) || (c.jellyfinId != null && p.entry.jellyfinId == jellyfinKey(c.jellyfinId))
    }

    // --- Identity

    private fun differ(a: String?, b: String?) = !a.isNullOrBlank() && !b.isNullOrBlank() && a != b
    private fun differ(a: Int?, b: Int?) = a != null && b != null && a != b
    private fun shares(a: String?, b: String?) = !a.isNullOrBlank() && a == b

    private fun seriesConflict(a: PlayedEntry, b: PlayedEntry) =
        differ(a.seriesTmdbId, b.seriesTmdbId) || differ(a.seriesImdbId, b.seriesImdbId) || differ(a.seriesJellyfinId, b.seriesJellyfinId)

    /** Two records that disagree on an id are two different things, even if another id matches. */
    private fun itemsConflict(a: PlayedEntry, b: PlayedEntry): Boolean {
        if (a.kind != b.kind) return true
        if (!a.isEpisode) return differ(a.tmdbId, b.tmdbId) || differ(a.imdbId, b.imdbId) || differ(a.jellyfinId, b.jellyfinId)
        return differ(a.jellyfinId, b.jellyfinId) || differ(a.season, b.season) || differ(a.episode, b.episode) || seriesConflict(a, b)
    }

    private fun sameSeries(a: PlayedEntry, b: PlayedEntry) =
        shares(a.seriesTmdbId, b.seriesTmdbId) || shares(a.seriesImdbId, b.seriesImdbId) || shares(a.seriesJellyfinId, b.seriesJellyfinId)

    private fun key(kind: String, ids: TitleRef) = "$kind:tmdb=${ids.tmdbId.orEmpty()}|imdb=${ids.imdbId.orEmpty()}|jf=${ids.jellyfinId.orEmpty()}"

    private fun factKey(f: PlaybackFact) = with(f.entry) { "$kind|$tmdbId|$imdbId|$jellyfinId|$seriesTmdbId|$seriesImdbId|$seriesJellyfinId|$season|$episode|${f.positionMs}" }

    /**
     * Groups [items] that are the [same] thing. One that matches a group but disagrees with any of its members starts its
     * own; one that would join groups that disagree with each other also stays on its own. Order of [items] decides ties.
     */
    private fun <T> cluster(items: List<T>, same: (T, T) -> Boolean, conflict: (T, T) -> Boolean): List<List<T>> {
        val groups = ArrayList<MutableList<T>>()
        for (x in items) {
            val hits = groups.filter { g -> g.any { same(it, x) } && g.none { conflict(it, x) } }
            when {
                hits.isEmpty() -> groups += mutableListOf(x)
                hits.size == 1 -> hits[0] += x
                hits.all { g -> hits.all { h -> g === h || g.none { a -> h.any { b -> conflict(a, b) } } } } -> {
                    val into = hits[0]
                    for (h in hits.drop(1)) { into += h; groups.removeAll { it === h } }
                    into += x
                }
                else -> groups += mutableListOf(x)
            }
        }
        return groups
    }
}
