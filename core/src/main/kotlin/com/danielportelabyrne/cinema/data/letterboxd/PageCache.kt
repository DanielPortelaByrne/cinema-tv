package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.ui.Perf
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Cache reads never queue behind the network. Concurrent readers share one refresh per path. */
class PageCache(
    private val dir: File,
    private val fetch: (String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Page(val html: String, val at: Long)
    private val reads = Executors.newFixedThreadPool(2)
    private val network = Executors.newFixedThreadPool(2)
    private val lock = Any()
    private val memory = LinkedHashMap<String, Page>(32, 0.75f, true)
    private val pending = HashMap<String, MutableList<(Page?) -> Unit>>()
    @Volatile private var closed = false

    init { dir.mkdirs() }

    fun load(path: String, maxAgeMs: Long, done: (String) -> Unit, failed: () -> Unit) {
        if (closed) return
        reads.execute {
            val saved = saved(path)
            Perf.mark("lb page $path ${if (saved == null) "miss" else "saved"}")
            saved?.let { done(it.html) }
            if (saved == null || clock() - saved.at > maxAgeMs) refresh(path) { fresh ->
                if (fresh == null) { if (saved == null) failed() }
                else if (fresh.html != saved?.html) done(fresh.html)
            }
        }
    }

    fun pageNow(path: String, maxAgeMs: Long): String? {
        val saved = saved(path)
        if (saved != null && clock() - saved.at <= maxAgeMs) return saved.html
        val latch = CountDownLatch(1)
        var result: String? = null
        refresh(path) { result = it?.html; latch.countDown() }
        latch.await(60, TimeUnit.SECONDS)
        return result ?: saved?.html
    }

    private fun file(path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(("v2:" + path).toByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return File(dir, "$hash.html")
    }

    private fun remember(path: String, page: Page) = synchronized(lock) {
        memory[path] = page
        while (memory.size > 24) memory.remove(memory.keys.first())
    }

    private fun saved(path: String): Page? {
        synchronized(lock) { memory[path] }?.let { return it }
        val file = file(path)
        return runCatching { Page(file.readText(), file.lastModified()) }.getOrNull()?.also { remember(path, it) }
    }

    private fun refresh(path: String, done: (Page?) -> Unit) {
        synchronized(lock) {
            if (closed) { done(null); return }
            pending[path]?.let { it.add(done); return }
            pending[path] = mutableListOf(done)
            val queued = Perf.now()
            network.execute {
                Perf.took("lb fetch wait $path", Perf.now() - queued)
                val started = Perf.now()
                val page = runCatching { fetch(path)?.let { Page(it, clock()) } }.getOrNull()
                Perf.took("lb fetch $path ${if (page == null) "failed" else "ok"}", Perf.now() - started)
                if (page != null) {
                    runCatching {
                        val target = file(path)
                        val tmp = File(dir, target.name + ".tmp")
                        tmp.writeText(page.html)
                        if (!tmp.renameTo(target)) tmp.delete()
                    }
                    remember(path, page)
                }
                val callbacks = synchronized(lock) { pending.remove(path).orEmpty().toList() }
                callbacks.forEach { callback -> runCatching { callback(page) } }
            }
        }
    }

    fun close() {
        synchronized(lock) { closed = true; pending.clear(); memory.clear() }
        reads.shutdownNow(); network.shutdownNow()
    }
}
