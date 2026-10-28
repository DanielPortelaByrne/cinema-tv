package com.danielportelabyrne.cinema.data.music

import kotlin.math.sqrt

/** The pixel work behind the blurred cover that fills the now-playing screen. ARGB arrays, so it runs anywhere. */
object Backdrop {
    /** Three box blurs of radius [r] in place, close to a gaussian. Edges repeat. */
    fun blur(px: IntArray, w: Int, h: Int, r: Int) { repeat(3) { box(px, w, h, r) } }

    /** Richer colour, lower light: the cover as a glow behind white text. */
    fun tint(px: IntArray, saturation: Float = 1.35f, brightness: Float = 0.72f) {
        for (i in px.indices) {
            val c = px[i]
            val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            val lum = 0.3f * r + 0.59f * g + 0.11f * b
            fun ch(v: Float) = ((lum + (v - lum) * saturation) * brightness * 255f).toInt().coerceIn(0, 255)
            px[i] = (c and -0x1000000) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }

    /** Fades alpha to nothing toward the edge of the inscribed circle, so a turning copy shows no corners. */
    fun roundOff(px: IntArray, w: Int, h: Int) {
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f; val radius = minOf(w, h) / 2f
        for (y in 0 until h) for (x in 0 until w) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius
            val a = ((1f - d) / 0.45f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            val i = y * w + x
            px[i] = (px[i] and 0x00FFFFFF) or ((a * ((px[i] ushr 24) and 0xFF)).toInt() shl 24)
        }
    }

    private fun box(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        val n = 2 * r + 1
        for (o in 0 until outer) {
            fun at(j: Int) = src[if (horizontal) o * w + j.coerceIn(0, inner - 1) else j.coerceIn(0, inner - 1) * w + o]
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (j in -r..r) { val c = at(j); sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            for (i in 0 until inner) {
                dst[if (horizontal) o * w + i else i * w + o] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = at(i + r + 1); val sub = at(i - r)
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
