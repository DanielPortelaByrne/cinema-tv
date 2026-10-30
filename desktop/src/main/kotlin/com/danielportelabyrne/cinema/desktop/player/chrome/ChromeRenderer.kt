package com.danielportelabyrne.cinema.desktop.player.chrome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Draws [PlayerChromeUi] off screen. The frame is 1920 design pixels wide whatever the window is, so a 1280x800 Deck
 * and a 1920x1080 TV show the same chrome; the height follows the window's shape.
 *
 * One scene is kept and its model swapped, so a frame costs one compose and one draw. Not thread safe: callers take
 * turns (the player has one worker thread).
 */
class ChromeRenderer {
    private var model by mutableStateOf(ChromeModel())
    private var heightDp by mutableStateOf(1080f)
    private var scene: ImageComposeScene? = null
    private var sceneW = 0
    private var sceneH = 0
    private var clock = 0L

    /** The chrome for [m] as an image the size of the window. */
    fun render(m: ChromeModel, widthPx: Int, heightPx: Int): Image {
        if (scene == null || sceneW != widthPx || sceneH != heightPx) {
            scene?.close()
            heightDp = 1920f * heightPx / widthPx
            scene = ImageComposeScene(widthPx, heightPx, Density(widthPx / 1920f)) { PlayerChromeUi(model, heightDp) }
            sceneW = widthPx; sceneH = heightPx
        }
        model = m
        val s = scene!!
        // Two passes: the first applies the new model, the second draws what that changed.
        s.render(clock); clock += 16_000_000L
        return s.render(clock).also { clock += 16_000_000L }
    }

    fun png(m: ChromeModel, widthPx: Int, heightPx: Int): ByteArray = render(m, widthPx, heightPx).encodeToData(EncodedImageFormat.PNG)!!.bytes

    /** Premultiplied BGRA, row after row: what mpv's `overlay-add` takes as "bgra". */
    fun bgra(m: ChromeModel, widthPx: Int, heightPx: Int): ByteArray {
        val image = render(m, widthPx, heightPx)
        val info = ImageInfo(widthPx, heightPx, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        image.readPixels(bitmap, 0, 0)
        return bitmap.readPixels(info, widthPx * 4, 0, 0) ?: ByteArray(widthPx * heightPx * 4)
    }

    fun close() { scene?.close(); scene = null }
}
