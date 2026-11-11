package com.danielportelabyrne.cinema.ui

import android.app.Activity
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport
import com.danielportelabyrne.cinema.data.movies.HistoryTarget
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.PlayedEntry
import com.danielportelabyrne.cinema.data.stream.RankedStream
import com.danielportelabyrne.cinema.data.stream.StreamCandidate
import com.danielportelabyrne.cinema.data.stream.StreamMetadata
import com.danielportelabyrne.cinema.data.stream.Verdict

/** Plays something from the owner's Jellyfin in the app's own player, and reports how far the viewer gets. */
object JellyfinPlayback {
    data class Plan(val streams: List<RankedStream>, val title: String, val resumeKey: String, val report: JellyfinReport, val history: HistoryTarget? = null)
    /**
     * The file as stored first, then a copy Jellyfin re-encodes for this TV, which the player falls back to on its
     * own. [resumeKey] keeps a separate "where I got to" for each film or episode. Playback starts where Jellyfin says
     * the viewer left off, and its progress is sent back so the server's "continue watching" stays right.
     */
    fun play(activity: Activity, client: JellyfinClient, item: JellyfinItem, title: String, resumeKey: String, fromStart: Boolean = false, series: Pair<String?, String?>? = null, startMs: Long? = null, settledAtMs: Long = 0L) {
        Perf.mark("library.play")
        val plan = prepare(client, item, title, resumeKey, series)
        // The viewer's place will have moved by the time they come back, so the cached library must be asked again.
        client.invalidateLibrary()
        // Restart: from 0:00, whatever the server or this TV remembered.
        // [startMs] is a resume point already settled (Continue watching's) from records as fresh as [settledAtMs], which may
        // be fresher than the item's own: the player keeps it over an older server position (JellyfinReport.freshStart).
        val report = if (fromStart) plan.report.copy(startMs = 0L) else if (startMs != null) plan.report.copy(startMs = startMs, settledAtMs = settledAtMs) else plan.report
        PlayerActivity.launch(activity, plan.streams, plan.title, plan.resumeKey, report, fromStart = fromStart, history = plan.history)
    }

    /** Builds the next playback locally, so the countdown never waits on an extra item lookup. */
    fun prepare(client: JellyfinClient, item: JellyfinItem, title: String, resumeKey: String, series: Pair<String?, String?>? = null): Plan {
        val headers = client.playbackHeaders()
        fun ranked(label: String, url: String) = RankedStream(
            StreamCandidate("jellyfin:${item.id}:$label", "Jellyfin", label, url, null, StreamMetadata(), headers = headers),
            100, 0L, false, Verdict.RECOMMENDED, null, emptyList()
        )
        val report = JellyfinReport(JellyfinApi.cleanBase(client.server), item.id, item.sourceId, if (item.played) 0L else item.resumeMs)
        return Plan(listOf(ranked("Original file", client.streamUrl(item)), ranked("Converted for this TV", client.transcodeUrl(item))), title, resumeKey, report, historyFor(client, item, series))
    }

    /**
     * Who this playback is for, in the viewer's play history. [series] is the series' (IMDb, TMDB) ids when the caller knows
     * them, so a library episode and a streamed copy of it are one record. Null for anything that is not a film or episode.
     */
    fun historyFor(client: JellyfinClient, item: JellyfinItem, series: Pair<String?, String?>?): HistoryTarget? {
        val base = JellyfinApi.cleanBase(client.server)
        val entry = if (item.isEpisode) {
            val seriesId = item.seriesId
            PlayedEntry(Kind.SHOW, jellyfinId = item.id, seriesJellyfinId = seriesId, seriesImdbId = series?.first, seriesTmdbId = series?.second,
                title = item.seriesName.orEmpty(), episodeTitle = item.name.ifBlank { null }, season = item.season, episode = item.episode,
                poster = seriesId?.let { JellyfinApi.posterUrl(base, it) }, backdrop = seriesId?.let { JellyfinApi.backdropUrl(base, it) },
                still = if (item.hasPrimaryImage) JellyfinApi.posterUrl(base, item.id) else null, source = PlayedEntry.SOURCE_JELLYFIN)
        } else {
            PlayedEntry(Kind.MOVIE, tmdbId = item.tmdbId, imdbId = item.imdbId, jellyfinId = item.id, title = item.name,
                poster = JellyfinApi.posterUrl(base, item.id), backdrop = if (item.hasBackdrop) JellyfinApi.backdropUrl(base, item.id) else null,
                source = PlayedEntry.SOURCE_JELLYFIN)
        }
        return entry.takeIf { it.hasId }?.let { HistoryTarget(client.ownerScope, it) }
    }
}
