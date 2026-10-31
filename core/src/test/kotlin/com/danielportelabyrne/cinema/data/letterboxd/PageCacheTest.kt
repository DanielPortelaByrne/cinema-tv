package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PageCacheTest {
    @Test fun saved_pages_are_delivered_while_both_network_workers_are_blocked() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val cache = PageCache(dir, { path ->
            if (path.startsWith("/slow")) { started.countDown(); release.await(3, TimeUnit.SECONDS) }
            path
        })
        try {
            assertEquals("/saved", cache.pageNow("/saved", 60000))
            repeat(2) { cache.load("/slow$it", 0, {}, {}) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val delivered = CountDownLatch(1)
            cache.load("/saved", 60000, { assertEquals("/saved", it); delivered.countDown() }, { fail("Missing saved page") })
            assertTrue("A saved page queued behind network I/O", delivered.await(500, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun concurrent_cold_readers_share_one_request_and_all_receive_the_result() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val cache = PageCache(dir, { calls.incrementAndGet(); release.await(3, TimeUnit.SECONDS); "fresh" })
        try {
            val delivered = CountDownLatch(8)
            repeat(8) { cache.load("/same", 60000, { assertEquals("fresh", it); delivered.countDown() }, { fail("Missing page") }) }
            Thread.sleep(100)
            release.countDown()
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun stale_copy_is_immediate_and_a_changed_refresh_is_delivered_once() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        var now = 1000L
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val cache = PageCache(dir, {
            if (calls.incrementAndGet() == 1) "old" else { release.await(3, TimeUnit.SECONDS); "new" }
        }, { now })
        try {
            assertEquals("old", cache.pageNow("/page", 0))
            now += 2000
            val old = CountDownLatch(1)
            val fresh = CountDownLatch(1)
            cache.load("/page", 1000, { if (it == "old") old.countDown() else if (it == "new") fresh.countDown() }, { fail() })
            assertTrue(old.await(500, TimeUnit.MILLISECONDS))
            assertEquals(1L, fresh.count)
            release.countDown()
            assertTrue(fresh.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun disk_copy_survives_a_new_cache_instance_and_failed_refresh() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val first = PageCache(dir, { "saved" })
        assertEquals("saved", first.pageNow("/page", 60000))
        first.close()
        val second = PageCache(dir, { null })
        try {
            val delivered = CountDownLatch(1)
            second.load("/page", -1, { assertEquals("saved", it); delivered.countDown() }, { fail("Lost offline copy") })
            assertTrue(delivered.await(1, TimeUnit.SECONDS))
            assertEquals("saved", second.pageNow("/page", -1))
        } finally { second.close(); dir.deleteRecursively() }
    }

    /** A cache with one network worker whose first fetch holds until [release], recording the order the rest are fetched in. */
    private class OrderedFetches {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val release = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        private val first = AtomicInteger()
        val cache = PageCache(dir, { path ->
            if (first.getAndIncrement() == 0) { firstStarted.countDown(); release.await(5, TimeUnit.SECONDS) }
            order += path
            path
        }, networkThreads = 1)

        /** Starts the blocking fetch, so everything asked for after this queues behind it. */
        fun hold() { cache.load("/held", 0, {}, {}); assertTrue(firstStarted.await(2, TimeUnit.SECONDS)) }
        fun finishAndWait(expected: Int) {
            Thread.sleep(300)   // the loads queue their fetches from a worker thread
            release.countDown()
            val until = System.currentTimeMillis() + 5000
            while (order.size < expected && System.currentTimeMillis() < until) Thread.sleep(20)
        }
        fun close() { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun a_page_the_owner_opens_is_fetched_before_queued_background_lookups() {
        val t = OrderedFetches()
        try {
            t.hold()
            repeat(5) { t.cache.load("/bulk$it", 0, {}, {}, background = true); Thread.sleep(30) }
            Thread.sleep(100)
            t.cache.load("/film", 0, {}, {})
            t.finishAndWait(7)
            assertEquals(listOf("/held", "/film", "/bulk0", "/bulk1", "/bulk2", "/bulk3", "/bulk4"), t.order.toList())
        } finally { t.close() }
    }

    @Test fun background_lookups_still_go_in_the_order_they_were_asked() {
        val t = OrderedFetches()
        try {
            t.hold()
            listOf("/a", "/b", "/c", "/d").forEach { t.cache.load(it, 0, {}, {}, background = true); Thread.sleep(30) }
            t.finishAndWait(5)
            assertEquals(listOf("/held", "/a", "/b", "/c", "/d"), t.order.toList())
        } finally { t.close() }
    }

    @Test fun the_owners_pages_go_in_the_order_they_were_asked_too() {
        val t = OrderedFetches()
        try {
            t.hold()
            listOf("/x", "/y", "/z").forEach { t.cache.load(it, 0, {}, {}); Thread.sleep(30) }
            t.finishAndWait(4)
            assertEquals(listOf("/held", "/x", "/y", "/z"), t.order.toList())
        } finally { t.close() }
    }

    @Test fun a_background_lookup_the_owner_then_asks_for_moves_up_and_is_fetched_once() {
        val t = OrderedFetches()
        try {
            t.hold()
            listOf("/a", "/b", "/c").forEach { t.cache.load(it, 0, {}, {}, background = true); Thread.sleep(30) }
            val delivered = CountDownLatch(1)
            t.cache.load("/c", 0, { delivered.countDown() }, {})
            t.finishAndWait(4)
            assertEquals(listOf("/held", "/c", "/a", "/b"), t.order.toList())
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
        } finally { t.close() }
    }
}
