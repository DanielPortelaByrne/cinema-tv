package com.danielportelabyrne.cinema.data.stremio

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioAddonSyncTest {
    private val torrentio = StremioAddon("Torrentio", listOf("movie", "series"), "https://torrentio.example/x/manifest.json", true)
    private val other = StremioAddon("Other", listOf("series"), "https://other.example/manifest.json", true)

    @Test fun fetchesFromTheAccountWhenNothingIsSaved() {
        var saved = emptyList<StremioAddon>()
        val sync = StremioAddonSync({ listOf(torrentio) }, { 0L })
        assertEquals(listOf(torrentio), sync.current("key", saved) { saved = it })
        assertEquals(listOf(torrentio), saved)
    }

    @Test fun withoutALinkedAccountTheSavedListIsUsed() {
        var fetched = 0
        val sync = StremioAddonSync({ fetched++; listOf(other) }, { 0L })
        assertEquals(listOf(torrentio), sync.current(null, listOf(torrentio)) { error("must not save") })
        assertEquals(0, fetched)
    }

    @Test fun refreshesOncePerPeriodAndPicksUpNewAddons() {
        var now = 0L
        var fetched = 0
        var account = listOf(torrentio)
        var saved = emptyList<StremioAddon>()
        val sync = StremioAddonSync({ fetched++; account }, { now }, maxAgeMs = 1000)
        sync.current("key", saved) { saved = it }
        now = 500; account = listOf(torrentio, other)
        assertEquals(listOf(torrentio), sync.current("key", saved) { saved = it })
        assertEquals(1, fetched)
        now = 1500
        assertEquals(listOf(torrentio, other), sync.current("key", saved) { saved = it })
        assertEquals(listOf(torrentio, other), saved)
        assertEquals(2, fetched)
    }

    @Test fun keepsTheSavedListWhenStremioIsUnreachable() {
        val sync = StremioAddonSync({ null }, { 0L })
        assertEquals(listOf(torrentio), sync.current("key", listOf(torrentio)) { error("must not save") })
    }
}
