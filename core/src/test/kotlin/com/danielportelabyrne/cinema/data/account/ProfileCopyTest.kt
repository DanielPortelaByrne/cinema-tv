package com.danielportelabyrne.cinema.data.account

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCopyTest {
    @Test fun eva_starts_as_a_copy_of_daniel_and_keeps_her_own_character() {
        val dan = MemoryPlatform(); val eva = MemoryPlatform(); val tv = MemoryPlatform()
        dan.preferences("favourites").edit().putString("favs_http://jf|DAN", "[1]").commit()
        dan.preferences("cinema_personal").edit().putString("page_layouts", "{\"home\":[]}").putString("avatar", "cowboy").commit()
        dan.preferences("play_history").edit().putLong("n", 3L).commit()
        tv.preferences("cinema_settings").edit().putString("watchlist_films", "[w]").putString("server", "x").commit()
        eva.preferences("cinema_personal").edit().putString("avatar", "cat").putString("stale", "gone").commit()
        val n = ProfileCopy.copy({ dan.preferences(it) }, { eva.preferences(it) }, tv.preferences("cinema_settings"), "DAN", "EVA")
        assertTrue(n >= 4)
        assertEquals("[1]", eva.preferences("favourites").getString("favs_http://jf|EVA", null))
        assertEquals("{\"home\":[]}", eva.preferences("cinema_personal").getString("page_layouts", null))
        assertEquals("[w]", eva.preferences("cinema_personal").getString("watchlist_films", null))
        assertEquals("cat", eva.preferences("cinema_personal").getString("avatar", null))
        assertNull(eva.preferences("cinema_personal").getString("stale", null))
        assertNull(eva.preferences("cinema_personal").getString("server", null))
        assertEquals(3L, eva.preferences("play_history").getLong("n", 0L))
    }
}
