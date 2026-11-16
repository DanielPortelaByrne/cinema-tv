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

    @Test fun shuffledSessionsKeepSixIndependentSeriesCursors() {
        val original = (1..6).map { PlaylistSlot(title("Series $it", Kind.SHOW), start = PlaylistEpisode(1, 1)) }
        val playlist = store.create("Six series")
        store.update(playlist.id) { it.copy(slots = original) }
        val first = BroadcastQueue(kotlin.random.Random(17))
        val firstThree = (1..3).map {
            val slot = first.next(original)!!
            val playback = PlaylistPlayback(playlist.id, slot.id, slot.queuedEpisode, playlist.name)
            store.finish(playback)
            store.finish(playback)
            first.completed(playback)
            slot.id
        }.toSet()
        val reopened = PlaylistStore(platform).all().single().slots
        val second = BroadcastQueue(kotlin.random.Random(91))
        val round = (1..6).map { second.next(reopened)!! }
        assertEquals(original.map { it.id }.toSet(), round.map { it.id }.toSet())
        val available = listOf(PlaylistEpisode(1, 1), PlaylistEpisode(1, 2))
        for (slot in round) {
            assertEquals(PlaylistEpisode(1, if (slot.id in firstThree) 2 else 1), slot.nextEpisode(available))
        }
        assertNull(second.next(reopened))
    }

    @Test fun anotherRoundAvoidsAnImmediateRepeatAndDoesNotReplayFinishedFilms() {
        val film = PlaylistSlot(title("Film", Kind.MOVIE))
        val shows = listOf(PlaylistSlot(title("A", Kind.SHOW)), PlaylistSlot(title("B", Kind.SHOW)))
        val slots = listOf(film) + shows
        val queue = BroadcastQueue(kotlin.random.Random(39))
        val first = (1..3).map {
            val slot = queue.next(slots)!!
            queue.completed(PlaylistPlayback("station", slot.id, null))
            slot
        }
        val updated = listOf(film.copy(filmFinished = true)) + shows
        val next = queue.next(updated)!!
        assertTrue(next in shows)
        assertFalse(next.id == first.last().id)
        assertEquals(shows.toSet() - next, setOf(queue.next(updated)))
        assertNull(queue.next(updated))
    }

    @Test fun resetPreservesMembershipAndStartingEpisode() {
        val playlist = store.create("Evening")
        val show = PlaylistSlot(title("Show", Kind.SHOW), "library", PlaylistEpisode(2, 3),
            lastFinished = PlaylistEpisode(2, 3), pending = PlaylistEpisode(2, 4))
        val film = PlaylistSlot(title("Film", Kind.MOVIE), filmFinished = true)
        store.update(playlist.id) { it.copy(slots = listOf(show, film)) }
        store.resetProgress(playlist.id)
        assertEquals(listOf(show.copy(lastFinished = null, pending = null), film.copy(filmFinished = false)),
            store.all().single().slots)
    }

    private fun title(name: String, kind: Kind) =
        Title(name, kind, name, null, null, null, emptyList(), null, null, null, null, null, null)
}
