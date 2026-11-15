package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    private val platform = MemoryPlatform()
    private val store = PlaylistStore(platform)

    @Test fun aFinishedSlotAdvancesAndAnInterruptedSlotKeepsItsStartingEpisode() {
        val a = PlaylistSlot(title("A", Kind.SHOW), start = PlaylistEpisode(0, 1))
        val b = PlaylistSlot(title("B", Kind.SHOW), start = PlaylistEpisode(3, 2))
        val playlist = store.create("Evening")
        store.update(playlist.id) { it.copy(slots = listOf(a, b)) }

        val playback = PlaylistPlayback(playlist.id, a.id, a.start)
        store.finish(PlaylistPlayback.decode(playback.encode())!!)
        val reopened = PlaylistStore(platform).all().single()
        val (first, second) = reopened.slots
        assertEquals(PlaylistEpisode(0, 2), first.nextEpisode(listOf(PlaylistEpisode(1, 1), PlaylistEpisode(0, 2))))
        assertEquals(PlaylistEpisode(3, 2), second.nextEpisode(listOf(PlaylistEpisode(3, 3))))
        assertNull(second.lastFinished)

        store.finish(PlaylistPlayback(playlist.id, a.id, PlaylistEpisode(0, 2)))
        store.finish(playback)
        assertEquals(PlaylistEpisode(0, 2), store.all().single().slots.first().lastFinished)
    }

    @Test fun aQueuedEpisodeSurvivesChangesToTheAvailableSources() {
        val slot = PlaylistSlot(title("Show", Kind.SHOW))
        val playlist = store.create("Evening")
        store.update(playlist.id) { it.copy(slots = listOf(slot)) }

        val episode = PlaylistEpisode(2, 3)
        val playback = PlaylistPlayback(playlist.id, slot.id, episode)
        store.queue(playback)
        val reopened = PlaylistStore(platform)
        val available = listOf(PlaylistEpisode(2, 4))
        assertEquals(episode, reopened.all().single().slots.single().nextEpisode(available))
        reopened.finish(playback)
        assertEquals(available.single(), reopened.all().single().slots.single().nextEpisode(available))
    }

    @Test fun aFilmIsFinishedOnlyInTheSlotThatPlayedIt() {
        val film = title("Film", Kind.MOVIE)
        val first = PlaylistSlot(film)
        val second = PlaylistSlot(film)
        val playlist = store.create("Double feature")
        store.update(playlist.id) { it.copy(slots = listOf(first, second)) }
        store.finish(PlaylistPlayback(playlist.id, first.id, null))
        val saved = store.all().single().slots
        assertTrue(saved[0].filmFinished)
        assertFalse(saved[1].filmFinished)
    }

    @Test fun aSeriesRollsIntoTheNextSeasonAndCanPickUpNewEpisodesLater() {
        val slot = PlaylistSlot(title("Show", Kind.SHOW), lastFinished = PlaylistEpisode(1, 6))
        assertNull(slot.nextEpisode(listOf(PlaylistEpisode(1, 6))))
        assertEquals(PlaylistEpisode(2, 1), slot.nextEpisode(listOf(PlaylistEpisode(1, 6), PlaylistEpisode(2, 1))))
    }

    private fun title(name: String, kind: Kind) =
        Title(name, kind, name, null, null, null, emptyList(), null, null, null, null, null, null)
}
