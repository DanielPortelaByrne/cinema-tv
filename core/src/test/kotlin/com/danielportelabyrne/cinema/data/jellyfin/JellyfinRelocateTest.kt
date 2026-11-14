package com.danielportelabyrne.cinema.data.jellyfin

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/** The server moved to a new address (the router handed it another one): the TV follows it and keeps everything. */
class JellyfinRelocateTest {
    private val servers = mutableListOf<HttpServer>()

    /** A stand-in Jellyfin that accepts only [token] for [user]. Returns its address. */
    private fun jellyfin(token: String, user: String = "u1"): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/Users/$user") { ex ->
            val ok = ex.requestHeaders.getFirst("Authorization")?.contains("Token=\"$token\"") == true
            val body = if (ok) """{"Id":"$user"}""".toByteArray() else ByteArray(0)
            ex.sendResponseHeaders(if (ok) 200 else 401, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
        }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}"
    }

    /** An address where nothing answers any more. */
    private fun gone(): String = ServerSocket(0).use { "http://127.0.0.1:${it.localPort}" }

    @After fun stop() = servers.forEach { it.stop(0) }

    private fun signedInAt(address: String) = JellyfinClient(MemoryPlatform()).apply {
        restoreSession(JellyfinSession(address, "secret-token", "u1"))
    }

    @Test fun follows_the_same_server_to_its_new_address_keeping_the_sign_in_and_lists() {
        val old = gone()
        val client = signedInAt(old)
        val scope = client.ownerScope
        val moved = jellyfin("secret-token")
        assertTrue(client.relocate(listOf(FoundServer("steamdeck", moved, "abc"))))
        assertEquals(moved, client.server)
        assertTrue(client.isConnected)
        assertEquals(scope, client.ownerScope)                 // favourites, history and dismissals are still theirs
    }

    @Test fun ignores_a_different_server_that_does_not_know_the_sign_in_and_does_not_sign_out() {
        val old = gone()
        val client = signedInAt(old)
        val stranger = jellyfin("someone-elses-token")
        assertFalse(client.relocate(listOf(FoundServer("neighbour", stranger, "zzz"))))
        assertEquals(old, client.server)
        assertTrue(client.isConnected)
    }

    @Test fun once_its_id_is_known_only_that_server_is_followed() {
        val client = signedInAt(gone())
        val first = jellyfin("secret-token")
        assertTrue(client.relocate(listOf(FoundServer("steamdeck", first, "abc"))))
        servers.first().stop(0)
        val impostor = jellyfin("secret-token")               // even one that would accept the token
        assertFalse(client.relocate(listOf(FoundServer("other", impostor, "different-id"))))
        assertEquals(first, client.server)
    }

    @Test fun choosing_another_server_by_hand_starts_its_lists_afresh() {
        val client = signedInAt(gone())
        assertTrue(client.relocate(listOf(FoundServer("steamdeck", jellyfin("secret-token"), "abc"))))
        client.setServer("http://10.0.0.9:8096")
        assertEquals(com.danielportelabyrne.cinema.data.movies.FavouritesStore.scope("http://10.0.0.9:8096", null), client.ownerScope)
    }
}
