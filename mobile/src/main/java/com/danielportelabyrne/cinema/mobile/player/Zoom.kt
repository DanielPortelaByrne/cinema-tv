package com.danielportelabyrne.cinema.mobile.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pinch to zoom the picture. 1 is the whole frame fitted to the screen; "fill" is where the frame covers it edge to
 * edge. It goes on past that, for films with black bars of their own inside the frame.
 */
object Zoom {
    /** The zoom at which a picture fitted as [videoW] by [videoH] covers a [screenW] by [screenH] screen. */
    fun fill(screenW: Int, screenH: Int, videoW: Int, videoH: Int): Float =
        if (videoW <= 0 || videoH <= 0) 1f else max(screenW.toFloat() / videoW, screenH.toFloat() / videoH).coerceAtLeast(1f)

    fun limit(fill: Float): Float = max(2f, fill * 1.6f)

    fun clamp(zoom: Float, fill: Float): Float = zoom.coerceIn(1f, limit(fill))

    /** Where it comes to rest when the fingers lift: a little off the whole frame or off filling the screen snaps to it. */
    fun settle(zoom: Float, fill: Float): Float = when {
        zoom <= 1.05f -> 1f
        fill > 1.05f && abs(zoom - fill) <= fill * 0.04f -> fill
        else -> zoom
    }

    fun label(zoom: Float, fill: Float): String = when {
        zoom <= 1.001f -> "Original"
        abs(zoom - fill) < 0.005f -> "Fill screen"
        else -> "${(zoom * 100).roundToInt()}%"
    }
}
