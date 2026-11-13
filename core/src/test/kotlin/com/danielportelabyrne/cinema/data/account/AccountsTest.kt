package com.danielportelabyrne.cinema.data.account

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {
    @Test fun the_first_person_owns_what_was_here_and_others_start_empty() {
        val store = AccountStore(MemoryPlatform())
        store.upsert(CinemaAccount("dan", "Daniel"), "t1")
        store.upsert(CinemaAccount("eva", "Eva"), "t2")
        store.activate("dan")
        assertEquals("dan", ProfileScope.userId); assertTrue(ProfileScope.legacyOwner)
        store.activate("eva")
        assertEquals("eva", ProfileScope.userId); assertFalse(ProfileScope.legacyOwner)
        assertEquals("t2", store.token("eva"))
        assertEquals("favourites~eva", ProfileScope.fileName("favourites"))
        assertEquals("jellyfin", ProfileScope.fileName("jellyfin"))
    }

    @Test fun the_launcher_person_is_matched_by_link_then_by_first_name() {
        val store = AccountStore(MemoryPlatform())
        store.upsert(CinemaAccount("dan", "Daniel Portela"), null)
        store.upsert(CinemaAccount("eva", "Eva"), null)
        assertEquals("dan", store.forLauncher(LauncherPerson("d@x.com", "Daniel (UK)"))?.userId)
        assertEquals("eva", store.forLauncher(LauncherPerson("e@x.com", "Eva"))?.userId)
        assertNull(store.forLauncher(LauncherPerson("g@x.com", "Grandad")))
        store.link("eva", LauncherPerson("g@x.com", "Grandad"))
        assertEquals("eva", store.forLauncher(LauncherPerson("G@X.com ", null))?.userId)
        // A link moves: the same launcher account is only ever one person.
        store.link("dan", LauncherPerson("g@x.com", null))
        assertEquals("dan", store.forLauncher(LauncherPerson("g@x.com", null))?.userId)
        assertTrue(store.all().first { it.userId == "eva" }.launcherIds.isEmpty())
    }

    @Test fun a_snapshot_round_trips_through_jellyfin_prefs_in_pieces() {
        val platform = MemoryPlatform()
        platform.preferences("cinema_personal").edit().putString("watchlist_films", "x".repeat(20_000)).putBoolean("b", true).putLong("l", 5L).commit()
        platform.preferences("favourites").edit().putInt("n", 3).commit()
        val snap = ProfileSync.snapshot(platform)
        val prefs = ProfileSync.toPrefs(snap, 42L)
        assertTrue(prefs.values.all { it.length <= ProfileSync.CHUNK })
        val (back, at) = ProfileSync.fromPrefs(prefs)!!
        assertEquals(42L, at)
        val other = MemoryPlatform()
        other.preferences("favourites").edit().putString("stale", "gone").commit()
        assertTrue(ProfileSync.apply(other, back))
        assertEquals("x".repeat(20_000), other.preferences("cinema_personal").getString("watchlist_films", null))
        assertEquals(3, other.preferences("favourites").getInt("n", 0))
        assertFalse(other.preferences("favourites").contains("stale"))
        assertNull(ProfileSync.fromPrefs(prefs - "cinema.1"))
    }

    @Test fun personal_settings_fall_back_to_the_tv_for_the_original_owner_only() {
        val platform = MemoryPlatform()
        platform.preferences("cinema_settings").edit().putString("page_layouts", "{\"home\":[]}").commit()
        ProfileScope.set("dan", legacyOwner = true)
        assertEquals("{\"home\":[]}", com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform).pageLayouts)
        ProfileScope.set("eva", legacyOwner = false)
        // MemoryPlatform does not split files per person, so give Eva's view an empty personal file.
        platform.preferences("cinema_personal").edit().clear().commit()
        assertNull(com.danielportelabyrne.cinema.data.settings.CinemaSettings(platform).pageLayouts)
        ProfileScope.set(null, legacyOwner = true)
    }
}

class AccountNamesTest {
    @Test fun a_generic_jellyfin_name_takes_the_launchers_name() {
        val store = AccountStore(MemoryPlatform())
        store.upsert(CinemaAccount("dan", "admin"), null)
        store.upsert(CinemaAccount("eva", "Eva"), null)
        store.link("dan", LauncherPerson("d@x.com", "Daniel (UK)"))
        store.link("eva", LauncherPerson("e@x.com", "Evie"))
        assertEquals("Daniel", store.all().first { it.userId == "dan" }.name)
        assertEquals("Eva", store.all().first { it.userId == "eva" }.name)
    }
}

class LauncherLinkTest {
    @Test fun picking_someone_else_once_does_not_make_eva_into_daniel() {
        val store = AccountStore(MemoryPlatform())
        store.upsert(CinemaAccount("dan", "Daniel"), null)
        store.link("dan", LauncherPerson("e@x.com", "Eva"))   // a link made before names were checked
        assertNull(store.forLauncher(LauncherPerson("e@x.com", "Eva")))
        assertFalse(AccountStore.sameName(LauncherPerson("e@x.com", "Eva"), store.all().first()))
        store.upsert(CinemaAccount("eva", "Eva"), null)
        assertEquals("eva", store.forLauncher(LauncherPerson("e@x.com", "Eva"))?.userId)
    }
}
