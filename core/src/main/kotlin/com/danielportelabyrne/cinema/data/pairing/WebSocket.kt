package com.danielportelabyrne.cinema.data.pairing

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** A WebSocket frame the phone sent that this server will not accept (unmasked, oversize, reserved bits). */
class ProtocolError(message: String) : IOException(message)

/**
 * The server side of the RFC 6455 opening handshake. The base64 is written by hand because `java.util.Base64` needs
 * Android API 26 and the TV app supports 24; the only thing encoded is a 20-byte SHA-1 digest.
 */
object WebSocketHandshake {
    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** The `Sec-WebSocket-Accept` value for a client's `Sec-WebSocket-Key`. */
    fun acceptKey(clientKey: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest((clientKey + GUID).toByteArray(Charsets.ISO_8859_1))
        return base64(digest)
    }

    /** A client key is 16 random bytes in base64: 24 characters ending in `==`. */
    fun isValidKey(key: String?): Boolean =
        key != null && key.length == 24 && key.endsWith("==") && key.dropLast(2).all { it in ALPHABET }

    fun switchingProtocolsHead(acceptKey: String): String =
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acceptKey\r\n\r\n"

    internal fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
            out.append(if (i + 1 < bytes.size) ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            out.append(if (i + 2 < bytes.size) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}

/** One WebSocket frame. [payload] is already unmasked. */
class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

/** Reading client frames and writing server frames (RFC 6455 section 5). Pure, so it is tested without sockets. */
object WebSocketFrames {
    const val CONTINUATION = 0
    const val TEXT = 1
    const val BINARY = 2
    const val CLOSE = 8
    const val PING = 9
    const val PONG = 10

    /** A single unfragmented server frame. Server frames are never masked. */
    fun encode(opcode: Int, payload: ByteArray): ByteArray {
        val header = when {
            payload.size < 126 -> byteArrayOf((0x80 or opcode).toByte(), payload.size.toByte())
            payload.size <= 0xFFFF -> byteArrayOf(
                (0x80 or opcode).toByte(), 126.toByte(), (payload.size shr 8).toByte(), payload.size.toByte(),
            )
            else -> {
                val h = ByteArray(10)
                h[0] = (0x80 or opcode).toByte()
                h[1] = 127.toByte()
                // Payload sizes here are bounded by Int, so the top four length bytes stay zero.
                for (i in 0 until 4) h[6 + i] = (payload.size shr (24 - 8 * i)).toByte()
                h
            }
        }
        return header + payload
    }

    /**
     * Reads one client frame. Throws [ProtocolError] for a frame a conforming browser would never send (unmasked,
     * reserved bits, a control frame that is fragmented or longer than 125 bytes) and for a payload over [maxPayload].
     */
    fun read(input: InputStream, maxPayload: Int): Frame {
        val b0 = input.readByteOrThrow()
        val b1 = input.readByteOrThrow()
        if (b0 and 0x70 != 0) throw ProtocolError("reserved bits set")
        if (b1 and 0x80 == 0) throw ProtocolError("client frame is not masked")
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        var length = (b1 and 0x7F).toLong()
        if (length == 126L) {
            length = ((input.readByteOrThrow() shl 8) or input.readByteOrThrow()).toLong()
        } else if (length == 127L) {
            length = 0
            for (i in 0 until 8) length = (length shl 8) or input.readByteOrThrow().toLong()
            if (length < 0) throw ProtocolError("frame too large")
        }
        if (opcode >= CLOSE && (!fin || length > 125)) throw ProtocolError("bad control frame")
        if (length > maxPayload) throw ProtocolError("frame too large")
        val mask = ByteArray(4) { input.readByteOrThrow().toByte() }
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val n = input.read(payload, read, payload.size - read)
            if (n < 0) throw EOFException()
            read += n
        }
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        return Frame(fin, opcode, payload)
    }

    private fun InputStream.readByteOrThrow(): Int {
        val b = read()
        if (b < 0) throw EOFException()
        return b
    }
}
