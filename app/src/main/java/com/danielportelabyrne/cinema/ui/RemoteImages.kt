package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.data.exchange
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posters and backdrops from the web, for the whole process (Movies and the player share it): decoded pictures in
 * memory keyed by address and width, the downloaded bytes on disk (`cacheDir/images`, 150 MB, least recently used
 * go first), one download per picture however many cards want it, newest requests first, and [cancel] for a page
 * that is rebuilt. [headers] carry a sign-in for images served by the owner's own Jellyfin.
 */
class RemoteImages private constructor(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val core = ImageCache(
        dir = File(context.cacheDir, "images"),
        diskCapBytes = 150L * 1024 * 1024,
        memoryCapBytes = (Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(8 * 1024 * 1024),
        fetch = ::download,
        decode = ::decodeTo,
        sizeOf = { it.byteCount },
        deliver = { r -> main.post(r) },
        largeCapBytes = 16L * 1024 * 1024,   // about four full-screen backdrops, kept apart from the posters
        priorityWorkers = 1
    )

    /**
     * Calls [done] on the main thread with the picture, or never if it cannot be had. [maxWidth] (pixels) is the
     * widest it will be shown; it is decoded to that width, not to the size it was stored at. [tag] groups requests
     * that [cancel] can drop together.
     */
    fun load(url: String, headers: Map<String, String> = emptyMap(), maxWidth: Int = 0, tag: Any? = null, done: (Bitmap) -> Unit) =
        load(url, headers, maxWidth, tag, "", false, done)

    /** Visible profile pictures and favourites go ahead of ordinary poster requests. */
    fun loadPriority(url: String, maxWidth: Int, tag: Any?, done: (Bitmap) -> Unit) {
        val at = Perf.now()
        load(url, emptyMap(), maxWidth, tag, "", true) { b ->
            Perf.took("letterboxd image $maxWidth", Perf.now() - at)
            done(b)
        }
    }

    /** A backdrop: loaded ahead of every queued poster, and kept in memory apart from them. */
    fun loadBackdrop(url: String, headers: Map<String, String> = emptyMap(), maxWidth: Int, done: (Bitmap) -> Unit) =
        load(url, headers, maxWidth, null, "", true, done)

    /**
     * A poster with the card's foot shading (transparent to 25 % black over its lower half, the old live foreground
     * gradient) painted into the picture once, so the card has one layer less to draw every frame.
     */
    fun loadShadedPoster(url: String, headers: Map<String, String>, maxWidth: Int, tag: Any?, done: (Bitmap) -> Unit) =
        load(url, headers, maxWidth, tag, SHADED, false, done)

    fun loadShadedPosterPriority(url: String, maxWidth: Int, tag: Any?, done: (Bitmap) -> Unit) {
        val at = Perf.now()
        load(url, emptyMap(), maxWidth, tag, SHADED, true) { b ->
            Perf.took("letterboxd poster image", Perf.now() - at)
            done(b)
        }
    }

    private fun load(url: String, headers: Map<String, String>, maxWidth: Int, tag: Any?, variant: String, urgent: Boolean, done: (Bitmap) -> Unit) {
        // Already decoded: draw it now, in this frame, rather than one post later.
        if (Looper.myLooper() == Looper.getMainLooper()) core.cached(url, maxWidth, variant)?.let { done(it); return }
        core.load(url, headers, maxWidth, tag, variant, urgent, done)
    }

    /** Drops every waiting request made with [tag] (a page that has been replaced). */
    fun cancel(tag: Any) = core.cancel(tag)

    /** Kept for callers that used to own an instance; the shared one lives as long as the process. */
    fun close() {}

    companion object {
        @Volatile private var shared: RemoteImages? = null
        private const val SHADED = "shaded"

        fun shared(context: Context): RemoteImages = shared ?: synchronized(this) {
            shared ?: RemoteImages(context.applicationContext).also { shared = it }
        }

        /**
         * Turns a `scheme:path` key into the address to fetch, signed at the last moment, so no cache or log holds a
         * credential. Keyed by scheme, e.g. [MUSIC].
         */
        val signers = java.util.concurrent.ConcurrentHashMap<String, (String) -> String?>()
        const val MUSIC = "subsonic"

        private fun download(url: String, headers: Map<String, String>): ByteArray? = try {
            val signer = signers[url.substringBefore(':')]
            val real = if (signer != null) signer(url.substringAfter(':')) ?: return null else url
            val c = URL(real).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.exchange {
                val code = c.responseCode
                if (code != 200) { Perf.mark("image ${url.substringBefore('?')} -> HTTP $code"); null } else c.inputStream.use { it.readBytes() }
            }
        } catch (e: Exception) { Perf.mark("image ${url.substringBefore('?')} failed: ${e.javaClass.simpleName}"); null }

        /**
         * Decodes straight to [maxWidth]: a power-of-two subsample first (cheap), then the decoder's own scaling for the
         * rest, so a 1440-pixel picture shown 615 wide is not kept at 720.
         */
        private fun decodeTo(bytes: ByteArray, maxWidth: Int, variant: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            if (maxWidth > 0 && bounds.outWidth > maxWidth) {
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
                o.inSampleSize = sample
                val sampled = bounds.outWidth / sample
                if (sampled > maxWidth) { o.inScaled = true; o.inDensity = sampled; o.inTargetDensity = maxWidth }
            }
            if (variant == SHADED) o.inMutable = true
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) ?: return null
            if (variant == SHADED && bmp.isMutable) {
                val h = bmp.height.toFloat()
                android.graphics.Canvas(bmp).drawRect(0f, 0f, bmp.width.toFloat(), h, android.graphics.Paint().apply { isDither = true
                    shader = android.graphics.LinearGradient(0f, 0f, 0f, h, intArrayOf(0, 0, 0x40000000), null, android.graphics.Shader.TileMode.CLAMP)
                })
            }
            // Hand the texture upload to the render thread ahead of the first draw, instead of inside a scrolling frame.
            bmp.prepareToDraw()
            return bmp
        }
    }
}
