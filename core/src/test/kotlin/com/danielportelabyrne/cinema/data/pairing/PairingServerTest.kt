package com.danielportelabyrne.cinema.data.pairing

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class PairingServerTest {
    private class ManualClock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private val code = "428193"
    private val clock = ManualClock()
    private val seen = mutableListOf<PairingRequest>()
    private var routeStatus = 200
    private var server: PairingServer? = null

    private fun start(): PairingServer {
        val pairing = Pairing(code = code, now = clock)
        return PairingServer(pairing, routes = { request ->
            seen += request
            when {
                request.route !in setOf("setup", "save", "restore") -> PairingResponse.text(404, "Not found")
                request.method == "GET" -> PairingResponse.html(200, "<p>page</p>")
                else -> PairingResponse.text(routeStatus, "submitted ${request.form["name"]}")
            }
        }).also { server = it }
    }

    @After fun tearDown() { server?.close() }

    /** Sends one raw HTTP request and reads the answer until the server closes the connection. */
    private fun send(port: Int, raw: String): Pair<Int, String> {
        Socket().use { socket ->
            socket.soTimeout = 5_000
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.getOutputStream().write(raw.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
            val text = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val status = text.substringAfter(' ').substringBefore(' ').toInt()
            return status to text.substringAfter("\r\n\r\n")
        }
    }

    private fun get(port: Int, path: String) = send(port, "GET $path HTTP/1.1\r\nHost: tv\r\n\r\n")

    /**
     * After close, a page must not be served. The listening socket can take a connection for a moment after close(), so
     * "refused" means the connection is refused or dropped, never a page.
     */
    private fun assertRefused(port: Int, path: String) {
        try {
            val status = get(port, path).first
            fail("expected no page after close, got HTTP $status")
        } catch (_: IOException) {
            // Refused or dropped: nothing is being served.
        }
    }

    private fun post(port: Int, path: String, body: String) = send(
        port,
        "POST $path HTTP/1.1\r\nHost: tv\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body",
    )

    @Test fun a_wrong_code_is_refused_before_any_route_runs() {
        val s = start()
        assertEquals(403, get(s.localPort, "/setup/000000").first)
        assertTrue(seen.isEmpty())
    }

    @Test fun the_right_code_loads_the_page() {
        val s = start()
        val (status, body) = get(s.localPort, "/setup/$code")
        assertEquals(200, status)
        assertEquals("<p>page</p>", body)
        assertEquals("setup", seen.single().route)
    }

    @Test fun a_successful_submission_spends_the_code_and_the_server_shuts() {
        val s = start()
        assertEquals(200, post(s.localPort, "/setup/$code", "name=Dan+Smith&pass=a%26b").first)
        assertEquals("Dan Smith", seen.single().form["name"])
        waitUntilClosed(s)
        assertFalse(s.isOpen)
        assertRefused(s.localPort, "/setup/$code")
    }

    @Test fun a_failed_submission_leaves_the_code_usable() {
        val s = start()
        routeStatus = 400
        assertEquals(400, post(s.localPort, "/setup/$code", "name=typo").first)
        routeStatus = 200
        assertEquals(200, post(s.localPort, "/setup/$code", "name=right").first)
    }

    @Test fun unknown_routes_and_methods_are_refused() {
        val s = start()
        assertEquals(404, get(s.localPort, "/nope/$code").first)
        assertEquals(404, get(s.localPort, "/$code").first)
        assertEquals(405, send(s.localPort, "DELETE /setup/$code HTTP/1.1\r\nHost: tv\r\n\r\n").first)
    }

    @Test fun an_expired_code_is_refused_and_the_server_shuts_itself() {
        val s = start()
        clock.now += Pairing.TEN_MINUTES
        waitUntilClosed(s)
        assertFalse(s.isOpen)
        assertRefused(s.localPort, "/setup/$code")
    }

    /** Phone browsers open spare connections that send nothing; they must not hold up the page that is really asked for. */
    @Test fun an_idle_connection_does_not_hold_up_the_page() {
        val s = start()
        Socket().use { idle ->
            idle.connect(InetSocketAddress("127.0.0.1", s.localPort), 2_000)
            val started = System.currentTimeMillis()
            assertEquals(200, get(s.localPort, "/setup/$code").first)
            assertTrue("page waited ${System.currentTimeMillis() - started} ms behind an idle connection",
                System.currentTimeMillis() - started < 2_000)
        }
    }

    @Test fun close_stops_listening() {
        val s = start()
        assertEquals(200, get(s.localPort, "/setup/$code").first)
        s.close()
        assertFalse(s.isOpen)
        assertRefused(s.localPort, "/setup/$code")
    }

    @Test fun the_url_for_a_page_carries_the_code() {
        val s = start()
        assertEquals("http://192.168.1.217:${s.localPort}/restore/$code", s.urlFor("192.168.1.217", "restore"))
    }

    private fun waitUntilClosed(s: PairingServer) {
        val deadline = System.currentTimeMillis() + 5_000
        while (s.isOpen && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }
}
