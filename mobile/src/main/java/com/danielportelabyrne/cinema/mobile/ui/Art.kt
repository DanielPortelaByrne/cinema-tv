package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- The ground ---------------------------------------------------------------------------------

/**
 * The glow top right, the one bottom left, the faint one in the middle, and the four arch columns: the TV's
 * AmbientBackground. Each section has its own colours.
 */
class GroundPalette(val topRight: Color, val bottomLeft: Color, val centre: Color, val arches: List<Color>, val topAlpha: Float = 0.16f, val bottomAlpha: Float = 0.14f)

object Grounds {
    private fun c(v: Long) = Color(v or 0xFF000000)
    val HOME = GroundPalette(c(0xE27A49), c(0x3AA597), c(0xEDBE56), listOf(c(0xE0A93A), c(0xC4532A), c(0x6E2214), c(0x4D5B2C)))
    val FILMS = GroundPalette(c(0xC2281D), c(0xB8741F), c(0xF08A25), listOf(c(0xE8B03A), c(0xB3261C), c(0x5C1210), c(0x9A4A1E)), 0.18f, 0.12f)
    val SERIES = GroundPalette(c(0x2F8A80), c(0x4D5FA8), c(0x8FC6C0), listOf(c(0x3AA597), c(0x4A5C9E), c(0xE0A93A), c(0x1F4A44)), 0.17f, 0.15f)
    val LIBRARY = GroundPalette(c(0xB3486B), c(0x6A3F8F), c(0xE7A0B0), listOf(c(0xD98C8F), c(0x8E3A63), c(0x5B2D6E), c(0x4A1B2A)), 0.15f, 0.14f)
    val DOWNLOADS = GroundPalette(c(0xF08A25), c(0x9A4A1E), c(0xF6BF33), listOf(c(0xF6BF33), c(0xE2562B), c(0x6B3A1A), c(0xB07A2A)), 0.16f, 0.12f)
    val SEARCH = GroundPalette(c(0x5865C9), c(0x8A4FB3), c(0xB8C2F0), listOf(c(0x6C7BD6), c(0x7E4AA8), c(0xE0A93A), c(0x2A2F66)), 0.15f, 0.13f)
    val SETTINGS = GroundPalette(c(0xA89580), c(0x6E7A78), c(0xD7CBB9), listOf(c(0xB8A58C), c(0x8A6F5A), c(0x4E4640), c(0x5C625A)), 0.10f, 0.09f)
    val MUSIC = GroundPalette(c(0xC23B8E), c(0x5B3FA8), c(0xF2A07B), listOf(c(0xE07AAE), c(0x7A3FB0), c(0xF2A07B), c(0x3A1D4A)), 0.16f, 0.14f)
}

/** The painted ground behind a page; moving between pages cross-fades the colours. */
@Composable
fun Ground(p: GroundPalette, modifier: Modifier = Modifier) {
    val spec = tween<Color>(450)
    val tr by animateColorAsState(p.topRight, spec, label = "tr")
    val bl by animateColorAsState(p.bottomLeft, spec, label = "bl")
    val ce by animateColorAsState(p.centre, spec, label = "ce")
    val a0 by animateColorAsState(p.arches[0], spec, label = "a0")
    val a1 by animateColorAsState(p.arches[1], spec, label = "a1")
    val a2 by animateColorAsState(p.arches[2], spec, label = "a2")
    val a3 by animateColorAsState(p.arches[3], spec, label = "a3")
    Box(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
        val w = size.width; val h = size.height
        drawRect(Palette.bg)
        fun glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float) {
            val centre = Offset(cx, cy)
            scale(1f, ry / rx, pivot = centre) {
                drawCircle(Brush.radialGradient(0f to color.copy(alpha = alpha), 0.7f to color.copy(alpha = 0f), center = centre, radius = rx), radius = rx, center = centre)
            }
        }
        glow(w * 0.95f, -h * 0.04f, w * 1.15f, h * 0.42f, tr, p.topAlpha)
        glow(0f, h * 1.05f, w * 1.05f, h * 0.5f, bl, p.bottomAlpha)
        glow(w * 0.5f, h * 0.5f, w * 0.8f, h * 0.4f, ce, 0.04f)
        // The four arches, fading out toward the left (the TV's 100-degree mask).
        val layer = Path()
        fun arch(x: Float, top: Float, r: Float, color: Color, a: Float = 1f) {
            layer.reset()
            layer.moveTo(x, h * 1.05f); layer.lineTo(x, top)
            layer.arcTo(Rect(x, top - r, x + 2 * r, top + r), 180f, 180f, false)
            layer.lineTo(x + 2 * r, h * 1.05f); layer.close()
            drawPath(layer, color.copy(alpha = 0.2f * a))
        }
        val r = w * 0.15f
        arch(w * 0.44f, h * 0.30f, r, a0)
        arch(w * 0.66f, h * 0.22f, r * 0.9f, a1)
        arch(w * 0.84f, h * 0.44f, r * 0.9f, a2)
        arch(w * 0.55f, h * 0.55f, r * 1.05f, a3, 0.9f)
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Transparent, 0.8f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
    })
}

// --- Ribbons ------------------------------------------------------------------------------------

/** The decorative ribbon: one path stroked five times, widest maroon to narrowest saffron, so it reads as nested stripes. */
enum class RibbonShape { LOOP, STEP, SWOOP }

/**
 * The TV's ribbons in a 1080-wide frame, scaled to the width. LOOP is Downloads' hairpin entering from the right edge;
 * STEP is the loading ribbon falling from the top left in two steps; SWOOP is a long S across the page.
 */
@Composable
fun Ribbon(shape: RibbonShape, modifier: Modifier = Modifier, strokeWidth: Dp = 34.dp, opacity: Float = 0.9f, fadeToLeft: Boolean = true) {
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = opacity }.drawBehind {
        val s = size.width / 1080f
        val path = Path()
        when (shape) {
            RibbonShape.LOOP -> {
                path.moveTo(1130f * s, 70f * s); path.lineTo(760f * s, 70f * s)
                path.arcTo(Rect(698f * s, 70f * s, 822f * s, 194f * s), 270f, -180f, false)
                path.lineTo(1130f * s, 194f * s)
            }
            RibbonShape.STEP -> {
                path.moveTo(560f * s, -40f * s); path.lineTo(560f * s, 110f * s)
                path.arcTo(Rect(560f * s, 30f * s, 720f * s, 190f * s), 180f, -90f, false)
                path.lineTo(860f * s, 190f * s)
                path.arcTo(Rect(780f * s, 190f * s, 940f * s, 350f * s), 270f, 90f, false)
                path.lineTo(940f * s, 520f * s)
            }
            RibbonShape.SWOOP -> {
                path.moveTo(1130f * s, 40f * s)
                path.cubicTo(760f * s, 40f * s, 760f * s, 200f * s, 520f * s, 200f * s)
                path.cubicTo(300f * s, 200f * s, 300f * s, 330f * s, 90f * s, 330f * s)
            }
        }
        val rings = Palette.rings
        rings.forEachIndexed { i, c ->
            drawPath(path, c, style = Stroke(width = strokeWidth.toPx() * (rings.size - i) / rings.size, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        }
        if (fadeToLeft) drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.35f to Color.Transparent, 0.8f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
        else drawRect(Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    })
}

// --- Stripes ------------------------------------------------------------------------------------

/** The stripe set as even bands inside a rounded rectangle: stacked ([horizontal]) or side by side. [fraction] fills only the leading part. */
@Composable
fun StripeMark(modifier: Modifier = Modifier, horizontal: Boolean = true, fraction: Float = 1f, radius: Dp = 3.dp, colors: List<Color> = Palette.stripes) {
    Canvas(modifier) {
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w <= 0f) return@Canvas
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, size.height, CornerRadius(radius.toPx()))) }
        clipPath(clip) {
            val n = colors.size
            colors.forEachIndexed { i, c ->
                if (horizontal) { val bh = size.height / n; drawRect(c, Offset(0f, i * bh), Size(w, bh + 0.5f)) }
                else { val bw = w / n; drawRect(c, Offset(i * bw, 0f), Size(bw + 0.5f, size.height)) }
            }
        }
    }
}

/** The eyebrow: the stripe pill, then tracked saffron capitals, dotted apart ("FEATURED · #1 IN FILMS TODAY"). */
@Composable
fun Eyebrow(parts: List<String>, modifier: Modifier = Modifier, color: Color = Palette.butter, mark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (mark) StripeMark(Modifier.padding(end = 10.dp).size(24.dp, 6.dp), radius = 3.dp)
        parts.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.padding(horizontal = 8.dp).size(3.dp).drawBehind { drawCircle(color.copy(alpha = 0.6f)) })
            Text(p.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row's title with the side-by-side stripe marker before it. */
@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StripeMark(Modifier.padding(end = 10.dp).size(14.dp, 22.dp), horizontal = false, radius = 3.dp)
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = Palette.ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A poster's stacked stripe shadow (saffron, orange, terracotta, red): four solid offset copies of its shape fanning down
 * and right. The caller leaves [step]x4 of room to the right and below.
 */
fun Modifier.stripeShadow(step: Dp = 5.dp, radius: Dp = 12.dp): Modifier = drawBehind {
    val bands = listOf(Palette.stripeSaffron, Palette.stripeOrange, Palette.stripeTerra, Palette.stripeRed)
    for (i in bands.indices.reversed()) {
        val d = step.toPx() * (i + 1)
        drawRoundRect(bands[i], Offset(d, d), size, CornerRadius(radius.toPx()))
    }
}

// --- Waves --------------------------------------------------------------------------------------

private val bandColors = listOf(Palette.maroon to 1f, Palette.stripeTerra to 0.667f, Palette.stripeSaffron to 0.333f)

private fun DrawScope.wavePath(x0: Float, x1: Float, baseY: Float, amp: Float, wavelength: Float, phase: Float = 0f): Path {
    val p = Path()
    var x = x0
    var first = true
    val step = 3.dp.toPx()
    while (x < x1) {
        val y = baseY + amp * sin(((x - x0 + phase) / wavelength * 2 * PI)).toFloat()
        if (first) p.moveTo(x, y) else p.lineTo(x, y)
        first = false; x += step
    }
    p.lineTo(x1, baseY + amp * sin(((x1 - x0 + phase) / wavelength * 2 * PI)).toFloat())
    return p
}

private fun DrawScope.bulb(at: Offset, r: Float, alpha: Float, lit: Boolean) {
    if (lit) drawCircle(Brush.radialGradient(listOf(Palette.ivory.copy(alpha = 0.5f * alpha), Color.Transparent), center = at, radius = r * 3f), radius = r * 3f, center = at)
    drawCircle(Color(0xFFF7EFE0).copy(alpha = alpha), r, at)
}

/**
 * The wavy scrubber in miniature, for a card: what is watched in three hard-stepped bands (maroon, terracotta, saffron),
 * the rest a faint cream band, and a small cream bulb at the point reached. The TV's WaveBar.
 */
@Composable
fun WaveBar(progress: Float, modifier: Modifier = Modifier, thickness: Dp = 5.dp, amplitude: Dp = 2.dp, wavelength: Dp = 30.dp, lit: Boolean = false) {
    Canvas(modifier.fillMaxWidth().height(thickness * 2.6f)) {
        val thick = thickness.toPx(); val r = thick * 0.75f; val inset = r + 2.dp.toPx()
        val path = wavePath(inset, size.width - inset, size.height / 2f, amplitude.toPx(), wavelength.toPx())
        val px = inset + (size.width - 2 * inset) * progress.coerceIn(0f, 1f)
        clipRect(px, 0f, size.width, size.height) { drawPath(path, Palette.ivory.copy(alpha = 0.16f), style = Stroke(thick * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        if (progress <= 0f) return@Canvas
        clipRect(0f, 0f, px, size.height) {
            bandColors.forEach { (c, f) -> drawPath(path, c, style = Stroke(thick * f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
        val py = size.height / 2f + amplitude.toPx() * sin(((px - inset) / wavelength.toPx() * 2 * PI)).toFloat()
        bulb(Offset(px, py), r, 1f, lit)
    }
}

/**
 * Cinema's loading mark, in place of a spinner: a ribbon of the scrubber's three bands runs along a gently swaying wave,
 * led by the cream bulb and fading out behind it, again and again.
 */
@Composable
fun WaveLoader(modifier: Modifier = Modifier, thickness: Dp = 9.dp, amplitude: Dp = 6.dp, wavelength: Dp = 60.dp) {
    val t by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "s")
    Canvas(modifier.fillMaxWidth().height(thickness * 5f)) {
        val thick = thickness.toPx(); val bulbR = thick * 0.72f
        val x0 = bulbR * 3.4f; val x1 = size.width - x0; val span = x1 - x0
        val wl = wavelength.toPx()
        val a = amplitude.toPx() * (0.8f + 0.2f * sin(t * 2 * PI * 2).toFloat())
        val path = wavePath(x0, x1, size.height / 2f, a, wl, sway * wl)
        drawPath(path, Palette.ivory.copy(alpha = 0.10f), style = Stroke(thick * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fun ease(v: Float) = ((1 - cos(v.coerceIn(0f, 1f) * PI)) / 2).toFloat()
        val head = x0 + span * ease(t / 0.68f)
        val tail = x0 + span * ease((t - 0.32f) / 0.68f)
        val length = head - tail
        if (length > thick * 2) {
            clipRect(tail, 0f, head, size.height) {
                bandColors.forEach { (c, f) ->
                    drawPath(path, Brush.horizontalGradient(0f to c.copy(alpha = 0f), 0.75f to c, 1f to c, startX = tail, endX = head),
                        style = Stroke(thick * f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        val shown = (length / (span * 0.12f)).coerceIn(0f, 1f)
        if (shown > 0f) {
            val hy = size.height / 2f + a * sin(((head - x0 + sway * wl) / wl * 2 * PI)).toFloat()
            bulb(Offset(head, hy), bulbR * (0.7f + 0.3f * shown), shown, true)
        }
    }
}

/** The loader with a quiet word beside it, for a row or a page still on its way. */
@Composable
fun LoadingLine(label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(96.dp)) { WaveLoader(thickness = 6.dp, amplitude = 4.dp, wavelength = 40.dp) }
        Text(label, color = Palette.ivoryMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

// --- The mark and the landing badge -------------------------------------------------------------

/** The Cinema mark: five concentric rings, maroon to saffron, and the dark play triangle (cinema_mark.xml, 56-unit box). */
fun DrawScope.drawMark(topLeft: Offset, side: Float, alpha: Float = 1f) {
    val u = side / 56f
    val centre = topLeft + Offset(28f * u, 28f * u)
    listOf(27f to Palette.stripeMaroon, 22f to Palette.stripeRed, 17f to Palette.stripeTerra, 12f to Palette.stripeOrange, 7.2f to Palette.stripeSaffron)
        .forEach { (r, c) -> drawCircle(c.copy(alpha = alpha), r * u, centre) }
    val tri = Path().apply {
        moveTo(topLeft.x + 25.2f * u, topLeft.y + 24.2f * u); lineTo(topLeft.x + 25.2f * u, topLeft.y + 31.8f * u)
        lineTo(topLeft.x + 33.6f * u, topLeft.y + 28f * u); close()
    }
    drawPath(tri, Color(0xFF1B0D06).copy(alpha = alpha))
}

@Composable
fun CinemaMark(size: Dp, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) { drawMark(Offset.Zero, this.size.minDimension) }

/**
 * The corner of Home that makes it a branded poster: tall rounded columns in the stripe colours hanging from the top edge,
 * their lower ends rising and falling in a slow wave, thinning out toward the left. The TV's LandingBadge.
 */
@Composable
fun HangingColumns(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "cols").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier) {
        val w = 14.dp.toPx(); val gap = 6.dp.toPx()
        val count = ((size.width + gap) / (w + gap)).toInt().coerceAtLeast(1)
        val x0 = size.width - count * (w + gap) + gap
        for (i in 0 until count) {
            val t = i / (count - 1f).coerceAtLeast(1f)
            val wave = 0.5f + 0.5f * sin(phase + i * 0.62f) * (0.6f + 0.4f * sin(phase * 0.5f + i * 0.21f))
            val length = size.height * (0.46f + 0.54f * wave) * (0.55f + 0.45f * t)
            val c = Palette.stripes[(i + (count - 1 - i) / 3) % Palette.stripes.size]
            drawRoundRect(c.copy(alpha = 0.10f + 0.80f * t * t), Offset(x0 + i * (w + gap), -w), Size(w, length + w), CornerRadius(w / 2))
        }
    }
}

/** A glass pill background: cream at a few percent with a hairline edge. */
fun Modifier.glass(radius: Dp = 16.dp, fill: Color = Palette.glass): Modifier = this
    .drawBehind {
        drawRoundRect(fill, cornerRadius = CornerRadius(radius.toPx()))
        drawRoundRect(Palette.hairline, cornerRadius = CornerRadius(radius.toPx()), style = Stroke(1.dp.toPx()))
    }

/** Seconds-based clock helper for animated art (kept here so every piece shares one convention). */
internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
internal fun easeOut(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }
internal fun easeInOut(t: Float) = ((1 - cos(t.coerceIn(0f, 1f) * PI)) / 2).toFloat()
