package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.ui.Perf
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Cache reads never queue behind the network. Concurrent readers share one refresh per path.
 *
 * Fetches wait in a priority queue. A page the owner asked for goes ahead of background work (the hundreds of "has she
 * watched this" and rating lookups a long list makes as it is built), and a background fetch that the owner then wants
 * for themselves moves up with theirs. Within each kind, fetches go in the order they were asked.
 */
class PageCache(
    private val dir: File,
    private val fetch: (String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** How many fetches run at once. */
    networkThreads: Int = 2,
) {
    private data class Page(val html: String, val at: Long)

    /** One queued fetch. Ordered by [background] (the owner's first), then by when it was asked. */
    private class Job(val path: String, @Volatile var background: Boolean, private val seq: Long, private val work: () -> Unit) : Runnable, Comparable<Job> {
        override fun run() = work()
        override fun compareTo(other: Job): Int =
            if (background != other.background) (if (background) 1 else -1) else seq.compareTo(other.seq)
    }

    private val reads = Executors.newFixedThreadPool(2)
    private val queue = PriorityBlockingQueue<Runnable>()
    private val network = ThreadPoolExecutor(networkThreads, networkThreads, 0L, TimeUnit.MILLISECONDS, queue)
    private val lock = Any()
    private val memory = LinkedHashMap<String, Page>(32, 0.75f, true)
    private val pending = HashMap<String, MutableList<(Page?) -> Unit>>()
    private val jobs = HashMap<String, Job>()
    private var sequence = 0L
    @Volatile private var closed = false

    init { dir.mkdirs() }

    fun load(path: String, maxAgeMs: Long, done: (String) -> Unit, failed: () -> Unit, background: Boolean = false) {
        if (closed) return
        reads.execute {
            val saved = saved(path)
            Perf.mark("lb page $path ${if (saved == null) "miss" else "saved"}")
            saved?.let { done(it.html) }
            if (saved == null || clock() - saved.at > maxAgeMs) refresh(path, background) { fresh ->
                if (fresh == null) { if (saved == null) failed() }
                else if (fresh.html != saved?.html) done(fresh.html)
            }
        }
    }

    /** Blocking, and for the owner's own page, so never background. */
    fun pageNow(path: String, maxAgeMs: Long): String? {
        val saved = saved(path)
        if (saved != null && clock() - saved.at <= maxAgeMs) return saved.html
        val latch = CountDownLatch(1)
        var result: String? = null
        refresh(path, false) { result = it?.html; latch.countDown() }
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

    private fun refresh(path: String, background: Boolean, done: (Page?) -> Unit) {
        synchronized(lock) {
            if (closed) { done(null); return }
            pending[path]?.let { waiting ->
                waiting.add(done)
                // The same page asked for again by the owner while it waits as background work: it now goes with their pages.
                jobs[path]?.takeIf { it.background && !background }?.let { job ->
                    // Taken out and put back, so the queue orders it afresh. A job already running has nothing to reorder.
                    if (queue.remove(job)) { job.background = false; queue.add(job) } else job.background = false
                }
                return
            }
            pending[path] = mutableListOf(done)
            val queued = Perf.now()
            val job = Job(path, background, sequence++) {
                Perf.took("lb fetch wait $path${if (background) " (background)" else ""}", Perf.now() - queued)
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
                val callbacks = synchronized(lock) { jobs.remove(path); pending.remove(path).orEmpty().toList() }
                callbacks.forEach { callback -> runCatching { callback(page) } }
            }
            jobs[path] = job
            network.execute(job)
        }
    }

    fun close() {
        synchronized(lock) { closed = true; pending.clear(); jobs.clear(); memory.clear() }
        reads.shutdownNow(); network.shutdownNow()
    }
}
