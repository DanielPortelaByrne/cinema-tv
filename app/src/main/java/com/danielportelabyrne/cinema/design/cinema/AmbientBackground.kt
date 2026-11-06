package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Cinema ground behind every page (`.amb` and `ARCH` in the mockups): espresso, a warm glow top right, a teal one
 * bottom left, and four faint 70s arch columns on the right that fade out toward the left.
 *
 * It is painted once per size into a half-resolution bitmap and then only blitted, so scrolling the page above it
 * costs one bitmap draw a frame.
 */
class AmbientBackground(context: Context, private val arches: Boolean = true) : View(context) {
    /**
     * The ground's colours: the big glow top right, the one bottom left, the faint one in the middle and the four arch
     * columns. Every page keeps the same shapes; each section of the app has its own colours.
     */
    class Palette(val topRight: Int, val bottomLeft: Int, val centre: Int, val arches: IntArray, val topAlpha: Float = 0.16f, val bottomAlpha: Float = 0.14f)

    private var cache: Bitmap? = null
    /** The ground being faded out after a change of palette. */
    private var previous: Bitmap? = null
    private var fade = 1f
    private var fader: android.animation.ValueAnimator? = null
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /**
     * Changing it cross-fades from the old colours to the new over [FADE_MS]. Each palette is painted once, off the main
     * thread, and the last few are kept, so moving between pages never waits on painting.
     */
    var palette: Palette = HOME
        set(value) {
            if (value === field) return
            field = value
            val w = width / 2; val h = height / 2
            if (w == 0 || h == 0) return
            painted(value, w, h)?.let { show(it); return }
            painter.execute {
                val bmp = paint(w, h, arches, value)
                post { remember(value, bmp); if (palette === value && width / 2 == w) show(bmp) }
            }
        }

    private fun show(next: Bitmap) {
        if (next === cache) return
        fader?.cancel()
        previous = cache
        cache = next
        if (previous == null || !Motion.animationsOn(this) || !isShown) { previous = null; invalidate(); return }
        fade = 0f
        fader = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_MS
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { previous = null; fade = 1f; invalidate() }
            })
            start()
        }
    }

    init { isFocusable = false; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fader?.cancel(); previous = null; cache = null
        if (w > 0 && h > 0) cache = painted(palette, w / 2, h / 2) ?: paint(w / 2, h / 2, arches, palette).also { remember(palette, it) }
    }

    override fun onDraw(canvas: Canvas) {
        val c = cache ?: run { canvas.drawColor(Cinema.BG); return }
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        val old = previous
        if (old != null && fade < 1f) {
            canvas.drawBitmap(old, null, dst, blit)
            fadePaint.alpha = (fade * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(c, null, dst, fadePaint)
        } else canvas.drawBitmap(c, null, dst, blit)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); fader?.cancel(); previous = null }

    /** The grounds painted lately, by palette (each about 2 MB): the pages moved between most stay ready. */
    private val recent = object : LinkedHashMap<Palette, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Palette, Bitmap>?) = size > KEEP
    }
    private fun painted(p: Palette, w: Int, h: Int): Bitmap? = recent[p]?.takeIf { it.width == w && it.height == h }
    private fun remember(p: Palette, b: Bitmap) { recent[p] = b }

    companion object {
        const val FADE_MS = 320L
        private const val KEEP = 4
        private val painter = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ambient").apply { priority = Thread.MIN_PRIORITY } }

        private fun c(v: Long) = v.toInt()

        /** Home, and anything without its own: terracotta and teal, the arches in saffron, terracotta, maroon and olive. */
        val HOME = Palette(c(0xFFE27A49), c(0xFF3AA597), c(0xFFEDBE56), intArrayOf(c(0xFFE0A93A), c(0xFFC4532A), c(0xFF6E2214), c(0xFF4D5B2C)))
        /** Films: a deep red picture-house glow with amber, the arches in gold, crimson, oxblood and rust. */
        val FILMS = Palette(c(0xFFC2281D), c(0xFFB8741F), c(0xFFF08A25), intArrayOf(c(0xFFE8B03A), c(0xFFB3261C), c(0xFF5C1210), c(0xFF9A4A1E)), 0.18f, 0.12f)
        /** Series: teal and a dusky blue, the arches in teal, slate blue, saffron and pine. */
        val SERIES = Palette(c(0xFF2F8A80), c(0xFF4D5FA8), c(0xFF8FC6C0), intArrayOf(c(0xFF3AA597), c(0xFF4A5C9E), c(0xFFE0A93A), c(0xFF1F4A44)), 0.17f, 0.15f)
        /** Documentaries: olive and sand, the arches in sand, olive, moss and clay. */
        val DOCS = Palette(c(0xFF8A9A3E), c(0xFFC9A066), c(0xFFE6D3A3), intArrayOf(c(0xFFD9B871), c(0xFF6E7D34), c(0xFF3F4A22), c(0xFFA5603A)), 0.15f, 0.13f)
        /** My library: plum and rose, the arches in rose, plum, mauve and wine. */
        val LIBRARY = Palette(c(0xFFB3486B), c(0xFF6A3F8F), c(0xFFE7A0B0), intArrayOf(c(0xFFD98C8F), c(0xFF8E3A63), c(0xFF5B2D6E), c(0xFF4A1B2A)), 0.15f, 0.14f)
        /** Downloads: copper and ember, the arches in saffron, orange, brown and bronze. */
        val DOWNLOADS = Palette(c(0xFFF08A25), c(0xFF9A4A1E), c(0xFFF6BF33), intArrayOf(c(0xFFF6BF33), c(0xFFE2562B), c(0xFF6B3A1A), c(0xFFB07A2A)), 0.16f, 0.12f)
        /** Search: night blue and violet, the arches in indigo, violet, saffron and navy. */
        val SEARCH = Palette(c(0xFF5865C9), c(0xFF8A4FB3), c(0xFFB8C2F0), intArrayOf(c(0xFF6C7BD6), c(0xFF7E4AA8), c(0xFFE0A93A), c(0xFF2A2F66)), 0.15f, 0.13f)
        /** Letterboxd: its own three dots, orange, green and blue, softened. */
        val LETTERBOXD = Palette(c(0xFFFF8000), c(0xFF00B848), c(0xFF40BCF4), intArrayOf(c(0xFFFF9A3A), c(0xFF2FAF63), c(0xFF3C9FCF), c(0xFF2B4A3A)), 0.13f, 0.12f)
        /** Music: magenta and violet, the arches in magenta, violet, peach and aubergine. */
        val MUSIC = Palette(c(0xFFC23B8E), c(0xFF5B3FA8), c(0xFFF2A07B), intArrayOf(c(0xFFE07AAE), c(0xFF7A3FB0), c(0xFFF2A07B), c(0xFF3A1D4A)), 0.16f, 0.14f)
        /** Settings: quiet warm greys. */
        val SETTINGS = Palette(c(0xFFA89580), c(0xFF6E7A78), c(0xFFD7CBB9), intArrayOf(c(0xFFB8A58C), c(0xFF8A6F5A), c(0xFF4E4640), c(0xFF5C625A)), 0.1f, 0.09f)

        private val made = HashMap<Pair<Int, Int>, Palette>()

        /** A palette from one key colour (a service's or a genre's), with a second for the low glow. The same colours give the same palette. */
        fun from(key: Int, second: Int = c(0xFF3AA597)): Palette = made.getOrPut(key to second) {
            fun mix(a: Int, b: Int, t: Float): Int {
                fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() shl shift
                return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
            }
            val light = mix(key, c(0xFFF6ECDB), 0.35f)
            Palette(light, second, mix(key, c(0xFFEDBE56), 0.5f),
                intArrayOf(light, key, mix(key, c(0xFF0B0807), 0.55f), mix(second, c(0xFF0B0807), 0.3f)), 0.18f, 0.12f)
        }

        /** Paints the ground at [w] x [h]; the mockup geometry is in a 1920 x 1080 frame and scaled to fit. */
        fun paint(w: Int, h: Int, arches: Boolean, palette: Palette = HOME): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Cinema.BG)
            val sx = w / 1920f; val sy = h / 1080f
            fun glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
                // CSS radial-gradient(rx ry at cx cy, color, transparent 70%): an ellipse, drawn as a scaled circle.
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
                p.shader = RadialGradient(0f, 0f, rx * sx, intArrayOf(Cinema.alpha(color, alpha), Cinema.alpha(color, 0f)), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
                c.save(); c.translate(cx * sx, cy * sy); c.scale(1f, (ry * sy) / (rx * sx))
                c.drawCircle(0f, 0f, rx * sx, p); c.restore()
            }
            glow(1690f, -108f, 900f, 600f, palette.topRight, palette.topAlpha)
            glow(0f, 1188f, 900f, 700f, palette.bottomLeft, palette.bottomAlpha)
            glow(960f, 540f, 700f, 500f, palette.centre, 0.04f)
            if (arches) drawArches(c, w, h, 0.2f, palette.arches)
            return bmp
        }

        /** The four arch columns, at [opacity], masked to fade toward the left (CSS `linear-gradient(100deg, transparent 30%, #000 80%)`). */
        fun drawArches(c: Canvas, w: Int, h: Int, opacity: Float, colours: IntArray = HOME.arches) {
            val layer = c.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
            val m = Matrix().apply { setScale(w / 1920f, h / 1080f) }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun arch(x: Float, top: Float, r: Float, color: Int, a: Float = 1f) {
                val path = Path().apply {
                    moveTo(x, 1100f); lineTo(x, top)
                    arcTo(RectF(x, top - r, x + 2 * r, top + r), 180f, 180f)
                    lineTo(x + 2 * r, 1100f); close()
                    transform(m)
                }
                p.color = Cinema.alpha(color, opacity * a)
                c.drawPath(path, p)
            }
            arch(1170f, 560f, 150f, colours[0])
            arch(1400f, 470f, 130f, colours[1])
            arch(1640f, 720f, 140f, colours[2])
            arch(1290f, 820f, 160f, colours[3], 0.9f)
            // Fade mask along a line 100 degrees from vertical, as CSS measures it.
            val rad = Math.toRadians(100.0)
            val dx = sin(rad).toFloat(); val dy = -cos(rad).toFloat()
            val len = abs(w * dx) + abs(h * dy)
            val cx = w / 2f; val cy = h / 2f
            val mask = Paint().apply {
                shader = LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
                    intArrayOf(0, 0, -0x1000000, -0x1000000), floatArrayOf(0f, 0.3f, 0.8f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), mask)
            c.restoreToCount(layer)
        }
    }
}
