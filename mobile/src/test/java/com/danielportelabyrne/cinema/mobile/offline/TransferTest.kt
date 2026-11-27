package com.danielportelabyrne.cinema.mobile.offline

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class TransferTest {
    @get:Rule val folder = TemporaryFolder()

    private val film = ByteArray(500_000) { (it % 251).toByte() }
    private val servers = ArrayList<ServerSocket>()
    private val requests = ArrayList<List<String>>()

    @After fun close() = servers.forEach { it.close() }

    /** Answers each request in turn with the next reply: a status line, headers, and the bytes actually sent. */
    private fun serve(vararg replies: Triple<String, List<String>, ByteArray>): String {
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).also { servers += it }
        thread(isDaemon = true) {
            for ((status, headers, body) in replies) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return@thread
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    requests += generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                    val head = (listOf("HTTP/1.1 $status", "Connection: close") + headers).joinToString("\r\n") + "\r\n\r\n"
                    runCatching { s.getOutputStream().apply { write(head.toByteArray()); write(body); flush() } }
                }
            }
        }
        return "http://127.0.0.1:${server.localPort}/film"
    }

    private fun whole(sent: ByteArray = film) = Triple("200 OK", listOf("Content-Length: ${film.size}", "ETag: \"v1\""), sent)

    private fun rest(from: Int) = Triple("206 Partial Content",
        listOf("Content-Length: ${film.size - from}", "Content-Range: bytes $from-${film.size - 1}/${film.size}"), film.copyOfRange(from, film.size))

    private fun fetch(url: String, keepGoing: () -> Boolean = { true }, seen: MutableList<Pair<Long, Long>> = ArrayList()) =
        Transfer.fetch(url, mapOf("Authorization" to "test"), folder.root.resolve("a.part"), folder.root.resolve("a.tag"), keepGoing) { d, t -> seen += d to t }

    private val part get() = folder.root.resolve("a.part")

    @Test fun fetchesTheWholeFile() {
        val seen = ArrayList<Pair<Long, Long>>()
        assertTrue(fetch(serve(whole()), seen = seen))
        assertArrayEquals(film, part.readBytes())
        assertEquals(film.size.toLong() to film.size.toLong(), seen.last())
        assertTrue("Authorization: test" in requests[0])
    }

    @Test fun carriesOnAfterADroppedConnection() {
        val url = serve(whole(film.copyOf(200_000)), rest(200_000))
        assertThrows(IOException::class.java) { fetch(url) }
        assertEquals(200_000L, part.length())
        assertTrue(fetch(url))
        assertArrayEquals(film, part.readBytes())
        assertTrue("Range: bytes=200000-" in requests[1])
        assertTrue("If-Range: \"v1\"" in requests[1])
    }

    @Test fun startsAgainWhenTheServerSendsTheWholeFile() {
        part.writeBytes(ByteArray(1000) { 7 })
        assertTrue(fetch(serve(whole())))
        assertArrayEquals(film, part.readBytes())
    }

    @Test fun finishedPartIsRecognised() {
        part.writeBytes(film)
        assertTrue(fetch(serve(Triple("416 Range Not Satisfiable", listOf("Content-Range: bytes */${film.size}", "Content-Length: 0"), ByteArray(0)))))
        assertArrayEquals(film, part.readBytes())
    }

    @Test fun stopsWhenTold() {
        assertFalse(fetch(serve(whole()), keepGoing = { false }))
        assertTrue(part.length() < film.size)
    }

    @Test fun refusalCarriesTheCode() {
        val url = serve(Triple("401 Unauthorized", listOf("Content-Length: 0"), ByteArray(0)))
        assertEquals(401, assertThrows(Transfer.Refused::class.java) { fetch(url) }.code)
    }

    @Test fun readsContentRange() {
        assertEquals(100L, Transfer.rangeStart("bytes 100-199/200"))
        assertEquals(200L, Transfer.rangeTotal("bytes */200"))
        assertEquals(null, Transfer.rangeTotal("bytes 0-1/*"))
    }
}
