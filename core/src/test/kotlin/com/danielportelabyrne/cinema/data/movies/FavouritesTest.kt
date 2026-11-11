package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FavouritesTest {
    private fun fav(kind: Kind = Kind.MOVIE, tmdb: String? = null, imdb: String? = null, jf: String? = null, name: String = "Heat") =
        Favourite(kind, tmdb, imdb, jf, name, 1995, null, null, 0)

    private val scope = FavouritesStore.scope("http://192.168.1.20:8096", "u1")

    @Test fun addRemoveAndReAddOrdering() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(tmdb = "1", name = "A")); s.toggle(scope, fav(tmdb = "2", name = "B"))
        assertEquals(listOf("B", "A"), s.list(scope).map { it.name })
        assertFalse(s.toggle(scope, fav(tmdb = "1")))
        assertEquals(listOf("B"), s.list(scope).map { it.name })
        s.toggle(scope, fav(tmdb = "1", name = "A"))
        assertEquals(listOf("A", "B"), s.list(scope).map { it.name })
    }

    @Test fun survivesReload() {
        val p = MemoryPlatform()
        FavouritesStore(p).toggle(scope, fav(tmdb = "1", imdb = "tt1", jf = "j1"))
        assertEquals(fav(tmdb = "1", imdb = "tt1", jf = "j1"), FavouritesStore(p).list(scope).single())
    }

    @Test fun filmAndSeriesWithSameIdAreSeparate() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(Kind.MOVIE, tmdb = "5")); s.toggle(scope, fav(Kind.SHOW, tmdb = "5"))
        assertEquals(2, s.list(scope).size)
        assertTrue(s.isFavourite(scope, fav(Kind.SHOW, tmdb = "5")))
        s.toggle(scope, fav(Kind.SHOW, tmdb = "5"))
        assertEquals(listOf(Kind.MOVIE), s.list(scope).map { it.kind })
    }

    @Test fun streamAndLibraryVersionsMerge() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(tmdb = "9", imdb = "tt9"))             // streamed copy
        assertTrue(s.isFavourite(scope, fav(imdb = "tt9", jf = "j9"))) // library copy knows the IMDb id
        val merged = Favourites.add(s.list(scope), fav(imdb = "tt9", jf = "j9"))
        assertEquals(1, merged.size)
        assertEquals("9", merged[0].tmdbId); assertEquals("j9", merged[0].jellyfinId)
        assertTrue(Favourites.contains(merged, fav(jf = "j9")))      // now found by the Jellyfin id alone
    }

    @Test fun neverMatchesByTitleAndSkipsIdless() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(tmdb = "1", name = "Heat")); s.toggle(scope, fav(tmdb = "2", name = "Heat"))
        assertEquals(2, s.list(scope).size)
        assertTrue(Favourites.add(emptyList(), fav(name = "No ids")).isEmpty())
    }

    @Test fun jellyfinIdIsServerScoped() {
        val s = FavouritesStore(MemoryPlatform())
        val a = FavouritesStore.scope("http://192.168.1.20:8096", "u1")
        val b = FavouritesStore.scope("http://192.168.1.21:8096", "u1")
        s.toggle(a, fav(jf = "same"))
        assertTrue(s.isFavourite(a, fav(jf = "same")))
        assertFalse(s.isFavourite(b, fav(jf = "same")))
    }

    @Test fun usersAreSeparate() {
        val s = FavouritesStore(MemoryPlatform())
        val other = FavouritesStore.scope("http://192.168.1.20:8096", "u2")
        s.toggle(scope, fav(tmdb = "1"))
        assertTrue(s.list(other).isEmpty())
    }

    @Test fun favouritesFromBeforeJellyfinCarryOverToTheFirstServerAndStayThere() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle("", fav(tmdb = "1", name = "Local film"))
        s.toggle("", fav(Kind.SHOW, imdb = "tt5", jf = "stray", name = "Local series"))
        // Connected: the new scope starts from the local list, with no Jellyfin id carried across.
        assertEquals(listOf("Local series", "Local film"), s.list(scope).map { it.name })
        assertTrue(s.list(scope).all { it.jellyfinId == null })
        // Once it has a list of its own, the local one no longer decides it.
        s.toggle(scope, fav(tmdb = "1"))
        assertEquals(listOf("Local series"), s.list(scope).map { it.name })
        assertEquals(2, s.list("").size)
    }

    @Test fun anotherUsersOwnListIsNeverMixedWithTheLocalOne() {
        val s = FavouritesStore(MemoryPlatform())
        val other = FavouritesStore.scope("http://192.168.1.20:8096", "u2")
        s.toggle(other, fav(tmdb = "7"))
        s.toggle("", fav(tmdb = "1"))
        assertEquals(listOf("7"), s.list(other).map { it.tmdbId })
    }

    @Test fun badJsonIsEmpty() { assertTrue(Favourites.parse("nope").isEmpty()) }

    @Test fun shelfIsNewestFirstAndDeduped() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(tmdb = "1", name = "A")); s.toggle(scope, fav(tmdb = "2", name = "B")); s.toggle(scope, fav(tmdb = "3", name = "C"))
        assertEquals(listOf("C", "B", "A"), Favourites.shelf(s.list(scope)).map { it.name })
        assertEquals(1, Favourites.shelf(listOf(fav(tmdb = "1"), fav(tmdb = "1", jf = "j"))).size)
    }

    @Test fun removingUpdatesShelfAndKeepsOrder() {
        val s = FavouritesStore(MemoryPlatform())
        s.toggle(scope, fav(tmdb = "1", name = "A")); s.toggle(scope, fav(tmdb = "2", name = "B")); s.toggle(scope, fav(tmdb = "3", name = "C"))
        assertFalse(s.toggle(scope, fav(tmdb = "2")))
        assertEquals(listOf("C", "A"), Favourites.shelf(s.list(scope)).map { it.name })
    }

    @Test fun focusAfterRemoval() {
        assertEquals(1, Favourites.focusAfterRemoval(1, 3))   // next card now sits at the same index
        assertEquals(1, Favourites.focusAfterRemoval(2, 2))   // removed the last: the previous one
        assertEquals(-1, Favourites.focusAfterRemoval(0, 0))
    }

    @Test fun nearestVisibleShelf() {
        assertEquals(2, Favourites.nearestVisible(listOf(true, true, true), 1))
        assertEquals(0, Favourites.nearestVisible(listOf(true, true, false), 1))
        assertEquals(-1, Favourites.nearestVisible(listOf(false, true), 1))
    }
}
