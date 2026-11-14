package com.danielportelabyrne.cinema.ui.account

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.media.FaceDetector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Finding the face in a cast photo, so a profile picture is a proper headshot: the face centred and the same size in
 * every disc, whatever the photo's framing. TMDB's portraits vary a lot (some tight, some full-length, some with
 * someone else in shot), so a photo only counts as a headshot when Android's face detector finds one clear face of a
 * reasonable size in it. Pure arithmetic apart from [find], so the framing is tested off a device.
 */
object Headshots {
    /** A face in a photo, in the photo's own pixels: the point between the eyes and the distance between them. */
    data class Face(val x: Float, val y: Float, val eyes: Float, val confidence: Float)

    /** The detector works on a small copy, which is quicker and finds faces just as well. */
    private const val DETECT_WIDTH = 240

    /**
     * The one clear face in [photo], or null: no face, more than one (a group or a co-star in shot), or one too small
     * to make a headshot of (a distant, full-length photo). Blocks for a few tens of milliseconds: not the main thread.
     */
    fun find(photo: Bitmap): Face? = try {
        val scale = DETECT_WIDTH.toFloat() / photo.width
        val w = DETECT_WIDTH and 1.inv()                                  // the detector needs an even width
        val h = max(2, (photo.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(photo, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val found = arrayOfNulls<FaceDetector.Face>(3)
        val count = FaceDetector(w, h, found.size).findFaces(small, found)
        small.recycle()
        val faces = found.take(count).filterNotNull().filter { it.confidence() >= FaceDetector.Face.CONFIDENCE_THRESHOLD }
        val mid = PointF()
        faces.singleOrNull()?.let { f ->
            f.getMidPoint(mid)
            Face(mid.x / scale, mid.y / scale, f.eyesDistance() / scale, f.confidence())
        }?.takeIf { usable(it, photo.width, photo.height) }
    } catch (_: Exception) { null }

    /** Big enough to be a headshot, and with room around it to frame the head without running off the photo. */
    fun usable(face: Face, width: Int, height: Int): Boolean =
        face.eyes >= width * 0.14f && face.y >= face.eyes * 0.7f && face.y <= height * 0.75f

    /**
     * The square of the photo to show in a disc: the face a little above the middle (the eyes sit about two fifths of
     * the way down, as in a portrait), wide enough for the whole head with a little space and collar around it. Kept inside
     * the photo, shrinking only if it has to.
     */
    fun frame(face: Face, width: Int, height: Int): RectF {
        val side = min(face.eyes * 4.9f, min(width, height).toFloat())
        val left = (face.x - side / 2f).coerceIn(0f, width - side)
        val top = (face.y - side * 0.43f).coerceIn(0f, height - side)
        return RectF(left, top, left + side, top + side)
    }

    /** How good a headshot this is, for putting the best first: a confident, larger, well-centred face. */
    fun score(face: Face, width: Int): Float =
        face.confidence + min(1f, face.eyes / (width * 0.3f)) - abs(face.x - width / 2f) / width
}
