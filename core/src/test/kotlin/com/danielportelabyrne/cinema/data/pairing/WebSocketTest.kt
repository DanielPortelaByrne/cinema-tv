package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class WebSocketTest {
    /** A client frame, masked as a browser does. */
    private fun clientFrame(opcode: Int, payload: ByteArray, fin: Boolean = true, mask: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray {
        val first = ((if (fin) 0x80 else 0) or opcode).toByte()
        val header = when {
            payload.size < 126 -> byteArrayOf(first, (0x80 or payload.size).toByte())
            payload.size <= 0xFFFF -> byteArrayOf(first, (0x80 or 126).toByte(), (payload.size shr 8).toByte(), payload.size.toByte())
            else -> byteArrayOf(first, (0x80 or 127).toByte(), 0, 0, 0, 0,
                (payload.size shr 24).toByte(), (payload.size shr 16).toByte(), (payload.size shr 8).toByte(), payload.size.toByte())
        }
        val masked = ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        return header + mask + masked
    }

    private fun read(bytes: ByteArray, max: Int = 4096) = WebSocketFrames.read(ByteArrayInputStream(bytes), max)

    @Test fun accept_key_matches_the_rfc_example() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocketHandshake.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test fun a_client_key_is_sixteen_bytes_of_base64() {
        assertTrue(WebSocketHandshake.isValidKey("dGhlIHNhbXBsZSBub25jZQ=="))
        assertFalse(WebSocketHandshake.isValidKey(null))
        assertFalse(WebSocketHandshake.isValidKey("dGhlIHNhbXBsZSBub25j"))
        assertFalse(WebSocketHandshake.isValidKey("dGhlIHNhbXBsZSBub25jZQ=?"))
        assertFalse(WebSocketHandshake.isValidKey("dGhlIHNhbXBsZSBub25jZQ!!"))
    }

    @Test fun the_switching_protocols_head_carries_the_accept_key() {
        val head = WebSocketHandshake.switchingProtocolsHead("abc=")
        assertTrue(head.startsWith("HTTP/1.1 101 Switching Protocols\r\n"))
        assertTrue(head.contains("Sec-WebSocket-Accept: abc=\r\n"))
        assertTrue(head.endsWith("\r\n\r\n"))
    }

    @Test fun a_masked_hello_from_the_rfc_reads_back() {
        val bytes = byteArrayOf(0x81.toByte(), 0x85.toByte(), 0x37, 0xfa.toByte(), 0x21, 0x3d, 0x7f, 0x9f.toByte(), 0x4d, 0x51, 0x58)
        val frame = read(bytes)
        assertTrue(frame.fin)
        assertEquals(WebSocketFrames.TEXT, frame.opcode)
        assertEquals("Hello", String(frame.payload, Charsets.UTF_8))
    }

    @Test fun a_server_hello_is_unmasked() {
        val encoded = WebSocketFrames.encode(WebSocketFrames.TEXT, "Hello".toByteArray())
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f), encoded)
    }

    @Test fun long_payloads_round_trip_through_the_length_forms() {
        for (size in listOf(125, 126, 300, 65535, 65536, 70_000)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val frame = read(clientFrame(WebSocketFrames.TEXT, payload), max = 100_000)
            assertArrayEquals("size $size", payload, frame.payload)
        }
    }

    @Test fun a_server_frame_with_a_long_payload_has_the_right_length_header() {
        val medium = WebSocketFrames.encode(WebSocketFrames.TEXT, ByteArray(300))
        assertEquals(126, medium[1].toInt() and 0xFF)
        assertEquals(300, ((medium[2].toInt() and 0xFF) shl 8) or (medium[3].toInt() and 0xFF))
        val large = WebSocketFrames.encode(WebSocketFrames.TEXT, ByteArray(70_000))
        assertEquals(127, large[1].toInt() and 0xFF)
        val length = (6..9).fold(0) { acc, i -> (acc shl 8) or (large[i].toInt() and 0xFF) }
        assertEquals(70_000, length)
    }

    @Test fun an_unmasked_client_frame_is_refused() {
        assertThrows(ProtocolError::class.java) { read(byteArrayOf(0x81.toByte(), 0x01, 0x41)) }
    }

    @Test fun reserved_bits_are_refused() {
        assertThrows(ProtocolError::class.java) { read(clientFrame(WebSocketFrames.TEXT, byteArrayOf(1)).also { it[0] = (it[0].toInt() or 0x40).toByte() }) }
    }

    @Test fun a_payload_over_the_limit_is_refused_before_it_is_read() {
        val frame = clientFrame(WebSocketFrames.TEXT, ByteArray(200))
        assertThrows(ProtocolError::class.java) { read(frame, max = 100) }
    }

    @Test fun a_fragmented_frame_reports_fin_false() {
        assertFalse(read(clientFrame(WebSocketFrames.TEXT, byteArrayOf(65), fin = false)).fin)
    }

    @Test fun a_fragmented_or_oversize_control_frame_is_refused() {
        assertThrows(ProtocolError::class.java) { read(clientFrame(WebSocketFrames.PING, byteArrayOf(1), fin = false)) }
        assertThrows(ProtocolError::class.java) { read(clientFrame(WebSocketFrames.PING, ByteArray(126))) }
    }

    @Test fun a_stream_that_ends_inside_a_frame_throws_eof() {
        val whole = clientFrame(WebSocketFrames.TEXT, "hello".toByteArray())
        assertThrows(EOFException::class.java) { read(whole.copyOf(whole.size - 2)) }
        assertThrows(EOFException::class.java) { read(ByteArray(0)) }
    }

    @Test fun control_frames_read_with_their_opcodes() {
        assertEquals(WebSocketFrames.PING, read(clientFrame(WebSocketFrames.PING, byteArrayOf(9))).opcode)
        assertEquals(WebSocketFrames.CLOSE, read(clientFrame(WebSocketFrames.CLOSE, byteArrayOf(3, 232.toByte()))).opcode)
    }
}
