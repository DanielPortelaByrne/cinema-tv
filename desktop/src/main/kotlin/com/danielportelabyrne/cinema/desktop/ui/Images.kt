package com.danielportelabyrne.cinema.desktop.ui

import com.danielportelabyrne.cinema.data.exchange
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.desktop.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where pictures come from. The real one fetches from Jellyfin; screenshot runs use one that draws nothing. */
fun interface ImageSource {
    /** Already in memory, without waiting. */
    fun cached(url: String): ImageBitmap? = null

    /** Blocking. Null when there is no picture. */
    fun load(url: String): ImageBitmap?
}

/**
 * Pictures from anywhere (Jellyfin, JustWatch, TMDB, Letterboxd). [headersFor] gives the headers for one address: the
 * Jellyfin sign-in for the owner's server only, never for anyone else. Kept decoded in memory (the last
 * [memoryItems]) and as files in the cache folder, so the next start draws its rows without asking the server.
 */
class CachedImages(private val headersFor: (String) -> Map<String, String>, private val memoryItems: Int = 160) : ImageSource {
    private companion object { const val MAX_WIDTH = 1280 }
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > memoryItems
    }
    private val dir = File(AppDirs.cache, "images").apply { mkdirs() }
    private val missing = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    override fun cached(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    override fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (url in missing) return null
        val file = File(dir, sha1(url))
        val bytes = file.takeIf { it.exists() }?.readBytes() ?: fetch(url)?.also { file.writeBytes(it) }
        val image = bytes?.let { decode(it) }
        if (image == null) missing += url else synchronized(memory) { memory[url] = image }
        return image
    }

    private fun fetch(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 15000
        headersFor(url).forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Cinema/1.0")
        c.exchange { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null }
    } catch (_: Exception) { null }

    private fun decode(bytes: ByteArray): ImageBitmap? = try {
        val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        // Backdrops arrive up to 1920 wide; nothing is drawn wider than 1280 design pixels on a Deck, so keep less in memory.
        if (img.width > MAX_WIDTH) {
            val h = img.height * MAX_WIDTH / img.width
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(MAX_WIDTH, h)
            surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(MAX_WIDTH.toFloat(), h.toFloat()))
            surface.makeImageSnapshot().toComposeImageBitmap()
        } else img.toComposeImageBitmap()
    } catch (_: Exception) { null }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

val LocalImages = staticCompositionLocalOf<ImageSource> { ImageSource { null } }

@OptIn(ExperimentalCoroutinesApi::class)
internal val imageDispatcher = Dispatchers.IO.limitedParallelism(6)

/**
 * A picture from [url], over a flat [placeholder] until it arrives. With no picture at all, [fallback] (a title) is
 * shown as its initials, as the TV does for a poster-less film.
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallback: String? = null,
    placeholder: androidx.compose.ui.graphics.Color = Cin.PLACEHOLDER,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val source = LocalImages.current
    val image by produceState(url?.let { source.cached(it) }, url, source) {
        value = url?.let { source.cached(it) }
        if (value == null && url != null) value = withContext(imageDispatcher) { source.load(url) }
    }
    Box(modifier.background(placeholder), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) Image(img, contentDescription = fallback, contentScale = contentScale, modifier = Modifier.matchParentSize())
        else if (fallback != null) BasicText(initials(fallback), style = ts(64, Cin.DIM, serif = true).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(10.dp))
    }
}

/** "The Big Short" -> "BS". */
fun initials(name: String): String = name.split(' ').filter { it.isNotBlank() && it.lowercase() !in setOf("the", "a", "an", "of", "and") }
    .take(2).joinToString("") { it.first().uppercase() }.ifBlank { name.take(1).uppercase() }
