package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.desktop.input.NavAction
import com.danielportelabyrne.cinema.desktop.input.NavDeduper
import com.danielportelabyrne.cinema.desktop.input.NavSource
import com.danielportelabyrne.cinema.desktop.input.PadFrame
import com.danielportelabyrne.cinema.desktop.input.PadRepeater
import com.danielportelabyrne.cinema.desktop.jellyfin.ShelfUrls
import com.danielportelabyrne.cinema.desktop.jellyfin.clock
import com.danielportelabyrne.cinema.desktop.jellyfin.runtimeLabel
import com.danielportelabyrne.cinema.desktop.jellyfin.watchedFraction
import com.danielportelabyrne.cinema.desktop.player.MpvLaunch
import com.danielportelabyrne.cinema.desktop.player.MpvMessage
import com.danielportelabyrne.cinema.desktop.player.MpvProtocol
import com.danielportelabyrne.cinema.desktop.player.PlayerControls
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PadTest {
    @Test fun aPressFiresOnceAndAButtonNeverRepeats() {
        val r = PadRepeater()
        assertEquals(listOf(NavAction.SELECT), r.step(PadFrame(a = true), 0))
        assertEquals(emptyList<NavAction>(), r.step(PadFrame(a = true), 2_000))
        assertEquals(emptyList<NavAction>(), r.step(PadFrame(), 2_016))
        assertEquals(listOf(NavAction.SELECT), r.step(PadFrame(a = true), 2_032))
    }

    @Test fun aHeldDirectionRepeatsLikeARemote() {
        val r = PadRepeater(firstRepeatMs = 380, repeatMs = 90)
        assertEquals(listOf(NavAction.RIGHT), r.step(PadFrame(right = true), 0))
        assertEquals(emptyList<NavAction>(), r.step(PadFrame(right = true), 300))
        assertEquals(listOf(NavAction.RIGHT), r.step(PadFrame(right = true), 400))
        assertEquals(emptyList<NavAction>(), r.step(PadFrame(right = true), 450))
        assertEquals(listOf(NavAction.RIGHT), r.step(PadFrame(right = true), 500))
    }

    @Test fun resetForgetsWhatWasHeld() {
        val r = PadRepeater()
        r.step(PadFrame(b = true), 0)
        r.reset()
        assertEquals(listOf(NavAction.BACK), r.step(PadFrame(b = true), 16))
    }

    @Test fun everyButtonMapsToItsAction() {
        val frame = PadFrame(up = true, down = true, left = true, right = true, a = true, b = true, x = true, y = true, lb = true, rb = true, start = true, view = true)
        assertEquals(NavAction.entries.toSet(), NavAction.entries.filter { frame.held(it) }.toSet())
    }

    @Test fun samePressFromKeyboardAndPadIsTakenOnce() {
        var now = 1_000L
        val d = NavDeduper(80) { now }
        assertTrue(d.accept(NavAction.DOWN, NavSource.GAMEPAD))
        now += 10
        assertFalse(d.accept(NavAction.DOWN, NavSource.KEYBOARD))
        now += 10
        assertTrue(d.accept(NavAction.DOWN, NavSource.GAMEPAD))   // the same source again is a real second press
        now += 200
        assertTrue(d.accept(NavAction.DOWN, NavSource.KEYBOARD))
    }
}

class MpvTest {
    @Test fun commandsAreJsonArrays() {
        assertEquals("""{"command":["seek",10,"relative"]}""", MpvProtocol.command("seek", 10, "relative"))
    }

    @Test fun readsEventsAndPropertyChanges() {
        assertEquals(MpvMessage.Event("end-file", "eof", null), MpvProtocol.parse("""{"event":"end-file","reason":"eof","playlist_entry_id":1}"""))
        val pos = MpvProtocol.parse("""{"event":"property-change","id":1,"name":"time-pos","data":12.5}""") as MpvMessage.Property
        assertEquals("time-pos", pos.name); assertEquals(12.5, (pos.value as Number).toDouble(), 0.0)
        assertEquals(MpvMessage.Property("time-pos", null), MpvProtocol.parse("""{"event":"property-change","id":1,"name":"time-pos"}"""))
        assertNull(MpvProtocol.parse("""{"data":null,"request_id":0,"error":"success"}"""))
        assertNull(MpvProtocol.parse("not json"))
    }

    @Test fun launchKeepsTheTokenOffTheCommandLineAndIgnoresTheViewersConfig() {
        val args = MpvLaunch.args("/app/bin/mpv", "/run/x.sock", emptyMap())
        assertEquals("/app/bin/mpv", args.first())
        assertTrue("--no-config" in args)
        assertTrue("--input-ipc-server=/run/x.sock" in args)
        assertTrue("--osc=yes" in args)
        assertTrue("--script-opts=osc-visibility=always" in args)
        assertTrue("--ontop=yes" in args)
        assertTrue(args.none { it.contains("Authorization") || it.contains("Token") })
        assertFalse("--gpu-context=x11egl" in args)
        val themed = MpvLaunch.args("mpv", "s", emptyMap(), "/cache/cinema-osc.lua")
        assertTrue("--osc=no" in themed)
        assertTrue("--script=/cache/cinema-osc.lua" in themed)
        assertFalse("--osc=yes" in themed)
    }

    @Test fun gameModeKeepsMpvOnX11() {
        assertTrue("--gpu-context=x11egl" in MpvLaunch.args("mpv", "s", mapOf("XDG_CURRENT_DESKTOP" to "gamescope")))
        assertTrue(MpvLaunch.isGamescope(mapOf("SteamGamepadUI" to "1")))
        assertFalse(MpvLaunch.isGamescope(mapOf("XDG_CURRENT_DESKTOP" to "KDE")))
    }

    @Test fun languagesFollowTheDesktopThenEnglish() {
        assertEquals("es,en,eng", MpvLaunch.preferredLangs(mapOf("LANG" to "es_ES.UTF-8")))
        assertEquals("en,eng", MpvLaunch.preferredLangs(mapOf("LANG" to "C.UTF-8")))
        assertEquals("en,eng", MpvLaunch.preferredLangs(emptyMap()))
    }

    @Test fun titlesReadLikeTheTvs() {
        val ep = JellyfinItem("1", "Half Loop", 2022, "Episode", null, season = 1, episode = 2, seriesName = "Severance")
        assertEquals("Severance · S1 E2 · Half Loop", MpvLaunch.title(ep))
        assertEquals("Aftersun · 2022", MpvLaunch.title(JellyfinItem("2", "Aftersun", 2022, "Movie", null)))
    }

    @Test fun everyPlayerButtonDoesSomething() {
        NavAction.entries.filter { it != NavAction.BACK }.forEach { assertTrue("$it", PlayerControls.commands.containsKey(it)) }
    }
}

class StorageTest {
    private fun tempDir(): File = Files.createTempDirectory("cinema-test").toFile()

    @Test fun preferencesSurviveARestart() {
        val dir = tempDir()
        val file = File(dir, "prefs/jellyfin.json")
        FilePreferences.open(file).edit().putString("server", "http://a:8096").putBoolean("done", true).putLong("n", 7).apply()
        FilePreferences.forget(file)
        val again = FilePreferences.open(file)
        assertEquals("http://a:8096", again.getString("server", null))
        assertTrue(again.getBoolean("done", false))
        assertEquals(7L, again.getLong("n", 0))
        again.edit().remove("server").apply()
        assertNull(again.getString("server", null))
        again.edit().clear().apply()
        assertFalse(again.contains("done"))
    }

    @Test fun secretsRoundTripAndAreNotStoredInPlainText() {
        val dir = tempDir()
        val store = DesktopPlatform(dir, File(dir, "cache")).secretStore()
        store.put("client_pw_x", "hunter2")
        assertEquals("hunter2", store.get("client_pw_x"))
        assertFalse(File(dir, "prefs/secrets.json").readText().contains("hunter2"))
        store.put("client_pw_x", null)
        assertNull(store.get("client_pw_x"))
    }

    @Test fun aTamperedSecretReadsAsMissing() {
        val dir = tempDir()
        val platform = DesktopPlatform(dir, File(dir, "cache"))
        platform.secretStore().put("k", "v")
        platform.preferences("secrets").edit().putString("k", "AAAA").apply()
        assertNull(platform.secretStore().get("k"))
    }
}

class LibraryLogicTest {
    private fun film(name: String) = JellyfinItem(name, name, 2020, "Movie", null)

    @Test fun addressesWithoutAPortAlsoTryJellyfinsUsualOne() {
        assertEquals(listOf("http://nas", "http://nas:8096"), CinemaModel.addressCandidates("http://nas"))
        assertEquals(listOf("http://nas:9000"), CinemaModel.addressCandidates("http://nas:9000"))
        assertEquals(listOf("https://jf.example.org"), CinemaModel.addressCandidates("https://jf.example.org"))
    }

    @Test fun searchMatchesEveryWordIgnoringCaseAndAccents() {
        val items = listOf(film("Amélie"), film("The Holdovers"), film("Holes"), film("Oppenheimer"))
        assertEquals(listOf("Amélie"), CinemaModel.searchLibrary(items, "amelie").map { it.name })
        assertEquals(listOf("Holes", "The Holdovers"), CinemaModel.searchLibrary(items, "hol").map { it.name })
        assertEquals(listOf("The Holdovers"), CinemaModel.searchLibrary(items, "the hold").map { it.name })
        assertTrue(CinemaModel.searchLibrary(items, "  ").isEmpty())
    }

    @Test fun articlesDoNotDecideTheOrder() {
        assertEquals("godfather", CinemaModel.sortKey("The Godfather"))
    }

    @Test fun progressAndTimes() {
        assertEquals(0.5f, watchedFraction(JellyfinItem("1", "x", null, "Movie", null, resumeMs = 30 * 60_000L, runtimeMin = 60)), 0.001f)
        assertEquals(0f, watchedFraction(JellyfinItem("1", "x", null, "Movie", null, resumeMs = 1000)), 0f)
        assertEquals("1h 52m", runtimeLabel(112))
        assertEquals("48m", runtimeLabel(48))
        assertEquals("1:02:03", clock(3_723_000))
        assertEquals("4:05", clock(245_000))
    }

    @Test fun resumeUsesTheCurrentEndpoint() {
        val url = ShelfUrls.resume("http://s:8096", "u 1")
        assertTrue(url.startsWith("http://s:8096/UserItems/Resume?userId=u+1"))
        assertTrue(JSONObject("{}").length() == 0)
    }
}
