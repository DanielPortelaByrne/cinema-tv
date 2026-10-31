package com.danielportelabyrne.cinema.data.pairing

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PairingServerSocketTest {
    private val code = "428193"
    private val key = "dGhlIHNhbXBsZSBub25jZQ=="
    private val messages = LinkedBlockingQueue<String>()
    private val events = LinkedBlockingQueue<String>()
    private var phone: PhoneSocket? = null
    private var server: PairingServer? = null
    private val opened = mutableListOf<Socket>()

    private fun start(pairing: Pairing = Pairing(code = code, ttlMillis = 60_000, reusable = true)): PairingServer {
        val listener = object : PhoneSocketListener {
            override fun onOpen(socket: PhoneSocket) { phone = socket; events += "open" }
            override fun onMessage(socket: PhoneSocket, text: String) { messages += text }
            override fun onClose(socket: PhoneSocket) { events += "close" }
        }
        return PairingServer(pairing, routes = { PairingResponse.html(200, "<p>page</p>") }, listener = listener).also { server = it }
    }

    @After fun tearDown() {
        server?.close()
        opened.forEach { runCatching { it.close() } }
    }

    private fun upgrade(port: Int, path: String = "/ws/$code", wsKey: String = key): Pair<Socket, String> {
        val socket = Socket()
        opened += socket
        socket.soTimeout = 5_000
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.getOutputStream().write(
            ("GET $path HTTP/1.1\r\nHost: tv\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $wsKey\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
        )
        socket.getOutputStream().flush()
        return socket to readHead(socket.getInputStream())
    }

    private fun readHead(input: InputStream): String {
        val out = ByteArrayOutputStream()
        var last = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            last = (last shl 8) or b
            if (last == 0x0D0A0D0A) break
        }
        return String(out.toByteArray(), Charsets.ISO_8859_1)
    }

    /** A masked client frame, as a browser sends it. */
    private fun frame(opcode: Int, text: String, fin: Boolean = true): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(9, 8, 7, 6)
        val masked = ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        return byteArrayOf(((if (fin) 0x80 else 0) or opcode).toByte(), (0x80 or payload.size).toByte()) + mask + masked
    }

    private fun send(socket: Socket, bytes: ByteArray) { socket.getOutputStream().write(bytes); socket.getOutputStream().flush() }

    private fun statusOf(head: String) = head.substringAfter(' ').substringBefore(' ').toInt()

    private fun await(queue: LinkedBlockingQueue<String>): String =
        queue.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("timed out waiting")

    @Test fun an_upgrade_with_the_right_code_gets_the_rfc_accept_key() {
        val s = start()
        val (_, head) = upgrade(s.localPort)
        assertEquals(101, statusOf(head))
        assertTrue(head.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
        assertEquals("open", await(events))
    }

    @Test fun an_upgrade_with_a_wrong_code_is_refused() {
        val s = start()
        val (_, head) = upgrade(s.localPort, path = "/ws/000000")
        assertEquals(403, statusOf(head))
        assertNull(events.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test fun an_upgrade_with_a_bad_key_is_refused() {
        val s = start()
        val (_, head) = upgrade(s.localPort, wsKey = "short")
        assertEquals(400, statusOf(head))
    }

    @Test fun an_upgrade_on_another_route_is_not_found() {
        val s = start()
        val (_, head) = upgrade(s.localPort, path = "/setup/$code")
        assertEquals(404, statusOf(head))
    }

    @Test fun a_text_frame_from_the_phone_reaches_the_listener() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        send(socket, frame(WebSocketFrames.TEXT, "Tsearch\theist"))
        assertEquals("Tsearch\theist", await(messages))
    }

    @Test fun the_tv_can_send_a_frame_to_the_phone() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        assertTrue(phone!!.send("Nsomething"))
        val input = socket.getInputStream()
        assertEquals(0x81, input.read())
        val length = input.read()
        val payload = ByteArray(length).also { var n = 0; while (n < length) n += input.read(it, n, length - n) }
        assertEquals("Nsomething", String(payload, Charsets.UTF_8))
    }

    @Test fun a_second_phone_is_refused_while_the_first_is_connected() {
        val s = start()
        upgrade(s.localPort)
        await(events)
        val (_, head) = upgrade(s.localPort)
        assertEquals(409, statusOf(head))
    }

    @Test fun a_new_phone_is_accepted_after_the_first_has_gone() {
        val s = start()
        val (first, _) = upgrade(s.localPort)
        await(events)
        first.close()
        assertEquals("close", await(events))
        val (_, head) = upgrade(s.localPort)
        assertEquals(101, statusOf(head))
    }

    @Test fun closing_the_server_drops_the_connected_phone() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        s.close()
        val input = socket.getInputStream()
        // A close frame (opcode 8) comes first, then the end of the stream.
        assertEquals(0x88, input.read())
        val rest = try { input.readBytes() } catch (_: IOException) { ByteArray(0) }
        assertTrue(rest.size <= 3)
        assertEquals("close", await(events).let { if (it == "open") await(events) else it })
    }

    @Test fun a_fragmented_frame_gets_a_close_frame() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        send(socket, frame(WebSocketFrames.TEXT, "half", fin = false))
        assertEquals(0x88, socket.getInputStream().read())
    }

    @Test fun an_unmasked_frame_gets_a_close_frame() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        send(socket, byteArrayOf(0x81.toByte(), 0x01, 0x41))
        assertEquals(0x88, socket.getInputStream().read())
    }

    @Test fun a_ping_is_answered_with_a_pong() {
        val s = start()
        val (socket, _) = upgrade(s.localPort)
        await(events)
        send(socket, frame(WebSocketFrames.PING, "hi"))
        assertEquals(0x8A, socket.getInputStream().read())
    }

    @Test fun messages_keep_the_session_alive() {
        var now = 1_000_000L
        val pairing = Pairing(code = code, ttlMillis = 60_000, idleMillis = 60_000, reusable = true, now = { now })
        val s = start(pairing)
        val (socket, _) = upgrade(s.localPort)
        await(events)
        now += 50_000
        send(socket, frame(WebSocketFrames.TEXT, "P"))
        await(messages)
        now += 50_000
        assertEquals(Pairing.Check.OK, pairing.check(code))
    }

    @Test fun the_ordinary_page_still_loads_beside_the_socket() {
        val s = start()
        Socket().use { socket ->
            socket.soTimeout = 5_000
            socket.connect(InetSocketAddress("127.0.0.1", s.localPort), 2_000)
            socket.getOutputStream().write("GET /type/$code HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray())
            val text = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            assertTrue(text.startsWith("HTTP/1.1 200"))
            assertTrue(text.endsWith("<p>page</p>"))
        }
    }
}
