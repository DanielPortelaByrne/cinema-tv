package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class QueueTest {
    private fun record(id: Int, status: String, added: String, size: Long = 0, left: Long = 0, series: String = "The Walking Dead", s: Int = 3, e: Int = id, timeleft: String = "") =
        """{"id":$id,"status":"$status","added":"$added","size":$size,"sizeleft":$left,"timeleft":"$timeleft","series":{"title":"$series","tmdbId":1402},"episode":{"seasonNumber":$s,"episodeNumber":$e,"title":"Ep $e"}}"""

    private fun queue(vararg r: String) = """{"page":1,"totalRecords":${r.size},"records":[${r.joinToString(",")}]}"""

    @Test fun reads_what_is_downloading_and_what_is_waiting() {
        val items = QueueApi.parse(queue(record(1, "downloading", "2026-10-20T12:00:00Z", 273485243, 266145211, timeleft = "00:12:30"), record(2, "queued", "2026-10-20T12:05:00Z")), QueueSource.SONARR)
        assertEquals("The Walking Dead", items[0].title); assertEquals("S3 E1 · Ep 1", items[0].detail); assertEquals(1402L, items[0].tmdbId)
        assertTrue(items[0].isDownloading); assertEquals(0.027f, items[0].progress()!!, 0.001f); assertEquals(750L, items[0].etaSeconds)
        assertTrue(items[1].isWaiting); assertNull(items[1].progress())   // size not known until it starts
    }

    @Test fun uses_the_real_priority_even_when_a_newer_download_is_moved_first() {
        val items = QueueApi.parse(queue(
            record(10, "queued", "2026-10-20T12:30:00Z"), record(11, "downloading", "2026-10-20T12:00:00Z", 100, 50),
            record(12, "queued", "2026-10-20T12:10:00Z"), record(13, "completed", "2026-10-20T11:00:00Z")), QueueSource.SONARR)
            .map { if (it.isWaiting) it.copy(position = if (it.id == 10) 1 else 2) else it }
        assertEquals(listOf(11, 10, 12, 13), QueueApi.ordered(items).map { it.id })
    }

    @Test fun movies_come_from_radarrs_shape() {
        val m = QueueApi.parse("""{"records":[{"id":5,"status":"downloading","size":1000,"sizeleft":250,"movie":{"title":"Dune","year":2021,"tmdbId":438631}}]}""", QueueSource.RADARR).single()
        assertEquals("Dune", m.title); assertEquals("2021", m.detail); assertEquals(438631L, m.tmdbId); assertEquals(0.75f, m.progress()!!, 0.001f)
    }

    @Test fun reads_time_left_with_and_without_days() {
        assertEquals(750L, QueueApi.timeLeftSeconds("00:12:30")); assertEquals(93_784L, QueueApi.timeLeftSeconds("1.02:03:04"))
        assertNull(QueueApi.timeLeftSeconds("")); assertNull(QueueApi.timeLeftSeconds(null)); assertNull(QueueApi.timeLeftSeconds("soon"))
    }

    @Test fun cancelling_also_clears_the_download_client_but_never_blocks_the_release() {
        assertEquals("/api/v3/queue/42?removeFromClient=true&blocklist=false", QueueApi.cancelPath(42))
        assertTrue(QueueApi.parse("junk", QueueSource.SONARR).isEmpty())
    }

    @Test fun season_pack_is_one_download_and_keeps_every_episode_for_cancellation() {
        val hash = "a".repeat(40)
        val rows = (1..22).map { n -> """{"id":$n,"episodeId":$n,"seriesId":10,"downloadId":"$hash","status":"downloading","size":1000,"sizeleft":500,"series":{"title":"Sabrina","tmdbId":605},"episode":{"id":$n,"seasonNumber":5,"episodeNumber":$n}}""" }
        val item = QueueApi.parse(queue(*rows.toTypedArray()), QueueSource.SONARR).single()
        assertEquals("Season 5 · 22 episodes", item.detail)
        assertEquals((1..22).toList(), item.episodeIds)
        assertEquals(1000L, item.sizeBytes) // pack size is not multiplied by episode count
        assertEquals(0.5f, item.progress()!!, 0.001f)
    }

    @Test fun metadata_and_stalled_torrents_do_not_count_as_bytes_downloading() {
        val item = QueueApi.parse(queue(record(1, "queued", "2026-10-20T12:00:00Z")), QueueSource.SONARR).single()
        assertFalse(item.copy(status = "metadata").isDownloading)
        assertFalse(item.copy(status = "stalled").isDownloading)
        assertTrue(item.copy(status = "metadata").isActive)
    }

    @Test fun cancellation_keeps_completed_files_even_when_status_has_not_caught_up() {
        val item = QueueApi.parse(queue(record(1, "downloading", "2026-10-20T12:00:00Z", 1000, 0)), QueueSource.SONARR).single()
        assertFalse(item.canCancel)
        assertFalse(item.copy(status = "completed").canCancel)
        assertTrue(item.copy(sizeLeftBytes = 500).canCancel)
        assertTrue(item.copy(status = "metadata", sizeBytes = 0).canCancel)
    }

    @Test fun finished_downloads_can_be_removed_but_only_when_one_torrent_maps_to_one_episode_or_movie() {
        val hash = "b".repeat(40)
        val ep = """{"id":7,"episodeId":7,"downloadId":"$hash","status":"completed","size":1000,"sizeleft":0,"series":{"title":"Sabrina","tmdbId":605},"episode":{"id":7,"seasonNumber":5,"episodeNumber":1}}"""
        val done = QueueApi.parse(queue(ep), QueueSource.SONARR).single()
        assertTrue(done.isComplete); assertTrue(done.canRemoveCompleted); assertFalse(done.canCancel)
        assertTrue(done.copy(status = "downloading").canRemoveCompleted)        // 100% bytes, status not caught up
        assertFalse(done.copy(sizeLeftBytes = 500, status = "downloading").canRemoveCompleted) // partial row
        assertFalse(done.copy(downloadId = null).canRemoveCompleted)            // no torrent to target
        assertFalse(done.copy(episodeIds = listOf(1, 2, 3)).canRemoveCompleted) // season pack is ambiguous
        val movie = QueueApi.parse("""{"records":[{"id":5,"movieId":9,"downloadId":"$hash","status":"completed","size":1000,"sizeleft":0,"movie":{"title":"Dune","year":2021,"tmdbId":438631}}]}""", QueueSource.RADARR).single()
        assertTrue(movie.canRemoveCompleted)
        assertFalse(movie.copy(movieId = null).canRemoveCompleted)
    }
}
