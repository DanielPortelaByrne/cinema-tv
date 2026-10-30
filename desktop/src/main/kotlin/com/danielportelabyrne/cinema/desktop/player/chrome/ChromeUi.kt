package com.danielportelabyrne.cinema.desktop.player.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.player.PlayerMath
import com.danielportelabyrne.cinema.data.player.PlayerUi
import com.danielportelabyrne.cinema.desktop.ui.Cin
import com.danielportelabyrne.cinema.desktop.ui.Icon
import com.danielportelabyrne.cinema.desktop.ui.RowHeading
import com.danielportelabyrne.cinema.desktop.ui.Stripes
import com.danielportelabyrne.cinema.desktop.ui.stripeBrush
import kotlin.math.PI
import kotlin.math.sin

/*
 * The TV's player chrome (app/.../ui/PlayerChrome.kt) drawn for the desktop. Every number here is the TV's, in the
 * mockups' pixels (a 1920x1080 frame; one pixel is one dp). Where the TV uses place(x, y) so does this. The picture
 * behind is mpv's; this is only the layer on top.
 */

private val MAROON = Color(0xFF5D1710)
private val RINGS = listOf(MAROON, Cin.STRIPE_RED, Cin.STRIPE_TERRA, Cin.STRIPE_ORANGE, Cin.STRIPE_SAFFRON)
private val CHIP_INK = Color(0xFFDDD0BD)
private val BG = Color(0xFF080605)

private fun Modifier.at(x: Number, y: Number) = offset(x.toFloat().dp, y.toFloat().dp)

private fun runtimeText(min: Int?): String? = min?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }

/** The chrome for [m], on a frame 1920 dp wide and [heightDp] tall; the 1080-high design sits in the middle of it. */
@Composable
fun PlayerChromeUi(m: ChromeModel, heightDp: Float) {
    val dy = (heightDp - 1080f) / 2f
    Box(Modifier.size(1920.dp, heightDp.dp)) {
        when (m.ui) {
            PlayerUi.Hidden -> Unit
            PlayerUi.Paused -> {
                PausedScrim(heightDp)
                Box(Modifier.at(0, dy).size(1920.dp, 1080.dp)) { PausedLayer(m) }
            }
            else -> {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xD9080605), Color(0x1F080605), Color(0x1F080605), Color(0xF2080605)))))
                Box(Modifier.at(0, dy).size(1920.dp, 1080.dp)) { ControlsLayer(m) }
            }
        }
    }
}

// --- Controls and scrubbing (31 / 32) ---------------------------------------------------------------------

@Composable
private fun BoxScope.ControlsLayer(m: ChromeModel) {
    val scrub = m.ui as? PlayerUi.Scrubbing
    val shown = scrub?.targetMs ?: m.positionMs
    val fraction = if (m.durationMs > 0) (shown.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f

    Column(Modifier.at(96, 64)) {
        Eyebrow(listOf(if (scrub != null) "Scrubbing" else "Now playing"), 15)
        Serif(m.title, ts(60, Cin.CREAM, serif = true, tracking = -0.022f), Modifier.padding(top = 14.dp).width(1100.dp), maxLines = 1)
        val meta = listOfNotNull(m.year?.toString(), m.genres.take(2).joinToString(", ").ifBlank { null }, runtimeText(m.runtimeMin)).joinToString(" · ")
        T(meta, ts(21, Cin.MUTED), Modifier.padding(top = 12.dp), maxLines = 1)
    }
    T(m.clock, ts(22, Cin.MUTED, weight = 500), Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 80.dp))

    if (scrub == null) {
        val focus = m.focus
        RoundButton("prev", 72, 44, focus == ChromeFocus.PREVIOUS, Modifier.at(616 - 36, 696 - 36))
        RoundButton("b10", 96, 64, focus == ChromeFocus.BACK_10, Modifier.at(772 - 48, 696 - 48), label = "10")
        PauseDisc(m.playing, focus == ChromeFocus.PAUSE, Modifier.at(960 - PauseBox / 2f, 696 - PauseBox / 2f))
        RoundButton("f10", 96, 64, focus == ChromeFocus.FORWARD_10, Modifier.at(1148 - 48, 696 - 48), label = "10")
        RoundButton("next", 72, 44, focus == ChromeFocus.NEXT, Modifier.at(1304 - 36, 696 - 36), alpha = if (m.hasNext) 0.9f else 0.3f)
    }

    WaveScrubber(fraction, chapterMarks(m), bigPlayhead = m.focus == ChromeFocus.SCRUBBER || scrub != null, Modifier.at(50, 854), amplitude = 13f, thickness = 30f)

    if (scrub == null) {
        val here = PlayerMath.chapterAt(m.chapters.map { com.danielportelabyrne.cinema.data.player.Chapter(it.index, it.name, it.startMs, null) }, m.positionMs)
        if (here != null) Box(Modifier.at(0, 952).fillMaxWidth(), Alignment.TopCenter) { T("CH ${here.index + 1} · ${here.name}".uppercase(), ts(17, Cin.SAFFRON, weight = 600, tracking = 0.14f), maxLines = 1) }
        T(PlayerMath.clock(m.positionMs), ts(23, Cin.CREAM, weight = 500), Modifier.at(96, 946))
        if (m.durationMs > 0) T("${PlayerMath.clock(m.durationMs - m.positionMs)} left", ts(23, Cin.MUTED), Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 946.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Chip("cc", m.subtitlesChip ?: "Subtitles · none", m.focus == ChromeFocus.SUBTITLES)
            m.audioChip?.let { Chip("vol", it, m.focus == ChromeFocus.AUDIO) }
            m.qualityChip?.let { Chip("film", it, m.focus == ChromeFocus.QUALITY) }
        }
        val sliders = m.focus == ChromeFocus.SLIDERS
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 40.dp).size(54.dp).clip(CircleShape).background(if (sliders) Cin.CREAM else Cin.GLASS), Alignment.Center) {
            Icon("sliders", 28.dp, if (sliders) Cin.INK else Cin.CREAM)
        }
    } else {
        ScrubPreview(m, scrub, fraction)
    }
}

/** Chapter starts as fractions of the film, the first (at 0) left out, as the TV's scrubber marks them. */
private fun chapterMarks(m: ChromeModel): List<Float> = if (m.durationMs <= 0) emptyList() else m.chapters.map { it.startMs.toFloat() / m.durationMs }

@Composable
private fun ScrubPreview(m: ChromeModel, scrub: PlayerUi.Scrubbing, fraction: Float) {
    val sx = 50f + 46f + (1820f - 92f) * fraction
    val cw = 420f
    val cx = (sx - cw / 2f).coerceIn(96f, 1920f - 96f - cw)
    // The card: the stripe set as offset shadows, then the face with the time set in it.
    Box(Modifier.at(cx, 500).size(420.dp, 236.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            design {
                listOf(Cin.STRIPE_ORANGE to 20f, Cin.STRIPE_SAFFRON to 10f).forEach { (c, d) ->
                    drawRoundRect(c, Offset(d, d), Size(420f, 236f), androidx.compose.ui.geometry.CornerRadius(20f))
                }
            }
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1A1310))) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(90.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9080605)))))
            Serif(PlayerMath.clock(scrub.targetMs), ts(44, Cin.CREAM, serif = true, tracking = -0.01f), Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 10.dp))
        }
    }
    val chapters = m.chapters.map { com.danielportelabyrne.cinema.data.player.Chapter(it.index, it.name, it.startMs, null) }
    PlayerMath.chapterAt(chapters, scrub.targetMs)?.let { ch ->
        Column(Modifier.at(cx - 40, 500 + 236 + 40).width(500.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(PlayerMath.chapterLabel(ch, chapters.size).uppercase(), ts(15, Cin.SAFFRON, weight = 600, tracking = 0.2f))
            Serif(ch.name, ts(30, Cin.CREAM, serif = true), Modifier.padding(top = 6.dp), maxLines = 1)
        }
    }
    m.resumeMs?.takeIf { it > 0 && m.durationMs > 0 }?.let { r ->
        val label = "RESUME POINT · ${PlayerMath.clock(r)}"
        val x = 50f + 46f + (1820f - 92f) * (r.toFloat() / m.durationMs)
        Box(Modifier.at(x - 300, 958).width(600.dp), Alignment.TopCenter) { T(label, ts(15, Cin.MUTED, tracking = 0.12f)) }
    }
    Box(Modifier.at(96, 996).size(1728.dp, 40.dp)) {
        T("0:00", ts(18, Cin.DIM), Modifier.align(Alignment.CenterStart))
        KeyHints(listOf("◀ ▶" to "Scrub", "OK" to "Play from here", "Back" to "Cancel"), Modifier.align(Alignment.Center))
        T(PlayerMath.clock(m.durationMs), ts(18, Cin.DIM), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Chip(icon: String, label: String, focused: Boolean) {
    val ink = if (focused) Cin.INK else CHIP_INK
    Row(Modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(if (focused) Cin.CREAM else Cin.GLASS).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 28.dp, ink)
        Spacer(Modifier.width(10.dp))
        T(label, ts(20, ink), maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: String, size: Int, iconSize: Int, focused: Boolean, modifier: Modifier, alpha: Float = 1f, label: String? = null) {
    val tint = if (focused) Cin.INK else Cin.CREAM
    Box(modifier.size(size.dp).alpha(alpha).clip(CircleShape).background(if (focused) Cin.CREAM else Color.Transparent), Alignment.Center) {
        Icon(icon, iconSize.dp, tint)
        label?.let { T(it, ts(17, tint, weight = 700), Modifier.offset(y = 3.dp)) }
    }
}

/** The pause/play disc: five stripe rings round a cream disc; focused it grows a little and glows. */
private const val PauseBox = 112 + 2 * 45 + 2 * 50

@Composable
private fun PauseDisc(playing: Boolean, focused: Boolean, modifier: Modifier) {
    Box(modifier.size(PauseBox.dp).scale(if (focused) 1.06f else 1f), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            design {
                val c = Offset(PauseBox / 2f, PauseBox / 2f)
                if (focused) glow(c, 56f + 9f * 6f, Cin.CREAM.copy(alpha = 0.3f), 30f)
                for (i in 5 downTo 1) drawCircle(RINGS[5 - i], 56f + 9f * i, c)
                drawCircle(Cin.CREAM, 56f, c)
            }
        }
        Icon(if (playing) "pause" else "play", 50.dp, Color(0xFF1B120C))
    }
}

/** Draws in design pixels: 1 unit is one pixel of the 1920-wide frame whatever the window is. */
private inline fun DrawScope.design(crossinline block: DrawScope.() -> Unit) {
    val d = density
    withTransform({ scale(d, d, Offset.Zero) }) { block() }
}

/** A soft round bloom (Android's BlurMaskFilter with [blur] as its radius). */
private fun DrawScope.glow(center: Offset, radius: Float, color: Color, blur: Float) {
    drawIntoCanvas { canvas ->
        val paint = org.jetbrains.skia.Paint().apply {
            this.color = color.toArgb(); isAntiAlias = true
            maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, blur * 0.57735f + 0.5f)
        }
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
    }
}

/**
 * The wavy scrubber (WaveScrubberView): a sine, watched part in three stepped bands with round caps, unwatched in one
 * muted band, chapter dots on the wave and the off-white playhead with a soft glow.
 */
@Composable
fun WaveScrubber(progress: Float, chapters: List<Float>, bigPlayhead: Boolean, modifier: Modifier, amplitude: Float, thickness: Float) {
    Canvas(modifier.size(1820.dp, 92.dp)) {
        design {
            val inset = 46f; val wave = 150f
            val x0 = inset; val x1 = 1820f - inset
            val base = 92f / 2f
            val path = Path()
            PlayerMath.waveSamples(x0, x1, 4f).forEachIndexed { i, x ->
                val y = PlayerMath.waveY(x, x0, base, amplitude, wave)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            fun band(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val px = PlayerMath.playheadX(progress, x0, x1)
            clipRect(left = px, top = 0f, right = 1820f, bottom = 92f) { drawPath(path, Color(0xFF4A423B), style = band(thickness * 0.7f)) }
            clipRect(left = 0f, top = 0f, right = px, bottom = 92f) {
                drawPath(path, MAROON, style = band(thickness))
                drawPath(path, Cin.STRIPE_TERRA, style = band(thickness * 0.667f))
                drawPath(path, Cin.STRIPE_SAFFRON, style = band(thickness * 0.333f))
            }
            chapters.filter { it > 0f && it < 1f }.forEach { f ->
                val x = PlayerMath.playheadX(f, x0, x1)
                drawCircle(if (x < px) Color(0xFF1B0D06) else Cin.CREAM.copy(alpha = 0.55f), 6f, Offset(x, PlayerMath.waveY(x, x0, base, amplitude, wave)))
            }
            val py = PlayerMath.waveY(px, x0, base, amplitude, wave)
            drawCircle(Brush.radialGradient(0f to Cin.CREAM.copy(alpha = 0.55f), 0.45f to Cin.CREAM.copy(alpha = 0.18f), 1f to Cin.CREAM.copy(alpha = 0f), center = Offset(px, py), radius = 46f), 46f, Offset(px, py))
            drawCircle(Color(0xFFF7EFE0), if (bigPlayhead) 22f else 17f, Offset(px, py))
        }
    }
}

// --- Paused (33) -------------------------------------------------------------------------------------------

@Composable
private fun PausedScrim(heightDp: Float) {
    Box(Modifier.size(1920.dp, heightDp.dp)) {
        fun c(a: Float) = BG.copy(alpha = a)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF0B0807).copy(alpha = .80f), .30f to Color(0xFF0B0807).copy(alpha = .62f), .58f to Color(0xFF0B0807).copy(alpha = .22f), 1f to Color(0xFF0B0807).copy(alpha = .10f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xFF0B0807).copy(alpha = .30f), .18f to Color(0xFF0B0807).copy(alpha = 0f), .48f to Color(0xFF0B0807).copy(alpha = 0f), .76f to Color(0xFF0B0807).copy(alpha = .72f), 1f to Color(0xFF0B0807).copy(alpha = .90f))))
    }
}

@Composable
private fun BoxScope.PausedLayer(m: ChromeModel) {
    Column(Modifier.at(96, 70)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(stripeBrush(horizontalBands = true, length = 30f)), Alignment.Center) { Icon("pause", 16.dp, Color(0xFF1B0D06)) }
            Spacer(Modifier.width(14.dp))
            T("PAUSED", ts(17, Cin.SAFFRON, weight = 600, tracking = 0.24f), maxLines = 1)
        }
        val size = when { m.title.length > 30 -> 68; m.title.length > 16 -> 84; else -> 104 }
        Serif(m.title, ts(size, Cin.CREAM, serif = true, tracking = -0.022f), Modifier.padding(top = 22.dp).width(900.dp), maxLines = 2)
        Meta(m.certificate?.substringAfter("GB-"), listOfNotNull(m.year?.toString(), runtimeText(m.runtimeMin), m.genres.take(3).joinToString(" · ").ifBlank { null }),
            m.rating?.let { String.format(java.util.Locale.UK, "%.1f", it) }, modifier = Modifier.padding(top = 22.dp))
        if (!m.overview.isNullOrBlank()) T(m.overview, ts(24, Cin.READING, weight = 300, lineHeight = 40f / 24f), Modifier.padding(top = 20.dp).width(800.dp), maxLines = 3)
        Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Resume", "play", primary = true, focused = m.focus == ChromeFocus.RESUME)
            Spacer(Modifier.width(18.dp))
            PillButton("Start over", "back", primary = false, focused = m.focus == ChromeFocus.START_OVER)
            m.endsAt?.let { Spacer(Modifier.width(32.dp)); EndsAt(it) }
        }
    }
    StreamCard(m)
    if (m.chapters.isNotEmpty()) {
        RowHeading("Chapters", m.chapters.size.toString(), Modifier.at(96, 580))
        ChapterRow(m)
    }
    WaveScrubber(if (m.durationMs > 0) m.positionMs.toFloat() / m.durationMs else 0f, chapterMarks(m), bigPlayhead = false, Modifier.at(50, 854), amplitude = 11f, thickness = 26f)
    T(PlayerMath.clock(m.positionMs), ts(21, Cin.CREAM, weight = 500), Modifier.at(96, 946))
    if (m.durationMs > 0) T("${PlayerMath.clock(m.durationMs - m.positionMs)} left", ts(21, Cin.MUTED), Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 946.dp))
}

@Composable
private fun EndsAt(time: String) {
    Row { T("Ends at ", ts(20, Cin.MUTED)); T(time, ts(20, Cin.CREAM, weight = 700)) }
}

@Composable
private fun PillButton(text: String, icon: String, primary: Boolean, focused: Boolean) {
    val tint = if (focused) Cin.INK else Cin.CREAM
    val bg = if (focused) Cin.CREAM else if (primary) Cin.GLASS_STRONG else Cin.GLASS
    Row(
        Modifier.height(76.dp).glowBehind(focused, 38f).clip(RoundedCornerShape(38.dp)).background(bg).padding(start = 30.dp, end = 38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 30.dp, tint); Spacer(Modifier.width(14.dp)); T(text, ts(25, tint, weight = 600), maxLines = 1)
    }
}

/** The soft bloom behind a focused pill (CinemaButton's halo). */
private fun Modifier.glowBehind(on: Boolean, radius: Float): Modifier = if (!on) this else this.then(
    Modifier.drawBehind {
        val d = density
        drawIntoCanvas { canvas ->
            val paint = org.jetbrains.skia.Paint().apply {
                color = Cin.CREAM.copy(alpha = 0.32f).toArgb(); isAntiAlias = true
                maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, 22f * 0.57735f * d + 0.5f)
            }
            val s = 4f * d
            canvas.nativeCanvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(-s, -s, size.width + 2 * s, size.height + 2 * s, radius * d + s), paint)
        }
    }
)

@Composable
private fun BoxScope.StreamCard(m: ChromeModel) {
    if (m.specs.isEmpty() && m.bitrateSamples.size < 2) return
    Column(Modifier.at(1130, 70).width(694.dp).clip(RoundedCornerShape(30.dp)).background(Color(0x9E140E0B)).padding(start = 36.dp, top = 30.dp, end = 36.dp, bottom = 28.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Eyebrow(listOf("Stream"), 15)
            m.health?.let { (text, good) ->
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (good) Color(0xFF5FD08A) else Cin.SAFFRON))
                    Spacer(Modifier.width(10.dp))
                    T(text, ts(18, if (good) Color(0xFF8FD6A8) else Cin.SAFFRON, weight = 500))
                }
            }
        }
        Column(Modifier.padding(top = 26.dp).fillMaxWidth()) {
            m.specs.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth().padding(top = if (r > 0) 26.dp else 0.dp)) {
                    pair.forEach { s ->
                        Column(Modifier.weight(1f)) {
                            T(s.label.uppercase(), ts(14, Cin.DIM, weight = 600, tracking = 0.16f))
                            T(s.value, ts(25, Cin.CREAM, weight = 500), Modifier.padding(top = 6.dp), maxLines = 1)
                            s.sub?.let { T(it, ts(17, Cin.MUTED), Modifier.padding(top = 3.dp), maxLines = 1) }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (m.bitrateSamples.size >= 2) {
            Row(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                T("BITRATE · LAST 60 S", ts(14, Cin.DIM, weight = 600, tracking = 0.16f), Modifier.weight(1f))
                m.bitratePeak?.let { T("PEAK ${it.uppercase()}", ts(14, Cin.SAFFRON, weight = 600, tracking = 0.16f)) }
            }
            Sparkline(m.bitrateSamples, Modifier.padding(top = 12.dp).fillMaxWidth().height(62.dp))
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier) {
    Canvas(modifier) {
        design {
            val w = size.width / density; val h = size.height / density
            val pad = 6f
            val max = values.max().toFloat(); val min = values.min().toFloat()
            val span = (max - min).coerceAtLeast(max * 0.1f).coerceAtLeast(1f)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = pad + (w - 2 * pad) * i / (values.size - 1)
                val y = pad + (h - 2 * pad) * (1f - (v - min) / span)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            RINGS.forEachIndexed { i, c -> drawPath(path, c, style = Stroke(width = 10f * (5 - i) / 5 + 1, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

@Composable
private fun BoxScope.ChapterRow(m: ChromeModel) {
    val chapters = m.chapters.map { com.danielportelabyrne.cinema.data.player.Chapter(it.index, it.name, it.startMs, null) }
    val now = PlayerMath.chapterAt(chapters, m.positionMs)
    val scroll = ((now?.index ?: 0) * (268 + 24) - 300).coerceAtLeast(0)
    Box(Modifier.at(96, 626).size(1000.dp, 214.dp).clip(androidx.compose.ui.graphics.RectangleShape)) {
        chapters.forEach { c ->
            val current = now?.index == c.index
            val focused = m.focus == ChromeFocus.CHAPTERS && m.chapterFocus == c.index
            val lit = current || focused
            Column(Modifier.at(c.index * (268 + 24) - scroll, 0).width(268.dp).scale(if (lit) 1.06f else 1f)) {
                Box(Modifier.size(268.dp, 151.dp).clip(RoundedCornerShape(16.dp)).background(Cin.PLACEHOLDER)) {
                    Box(Modifier.at(10, 10).clip(RoundedCornerShape(9.dp)).background(Color(0xB30B0807)).padding(horizontal = 10.dp, vertical = 4.dp)) { T("${c.index + 1}", ts(15, Cin.CREAM, weight = 700)) }
                    if (current) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).clip(RoundedCornerShape(9.dp)).background(stripeBrush(true, 24f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        T("NOW", ts(13, Color(0xFF1B0D06), weight = 700, tracking = 0.14f))
                    }
                    if (current) Box(Modifier.align(Alignment.BottomStart).height(5.dp).width((268 * PlayerMath.chapterProgress(chapters, c, m.positionMs, m.durationMs)).coerceAtLeast(1f).dp).background(stripeBrush(true, 5f)))
                }
                T(c.name, ts(21, if (lit) Cin.CREAM else Color(0xFFC4B6A3), weight = 500), Modifier.padding(top = 14.dp).width(268.dp), maxLines = 1)
                T(PlayerMath.clock(c.startMs), ts(16, Cin.DIM), Modifier.padding(top = 3.dp))
            }
        }
    }
}

// --- Text -------------------------------------------------------------------------------------------------

/*
 * The TV sets its sans (Inter) text with font padding on, so each line's box runs from the font's top to its bottom
 * rather than ascent to descent: Inter's top is 1.108 em above the baseline against an ascent of 0.969, and its bottom
 * 0.322 em below against a descent of 0.241. Compose leaves that padding out, so it is put back here, or every line of
 * sans text would sit about a seventh of its size higher than on the TV. The serif (Fraunces) is set without it.
 */
private const val PAD_TOP = 0.17f
private const val PAD_BOTTOM = 0.085f

@Composable
private fun T(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val size = style.fontSize.value
    Box(modifier.padding(top = (size * PAD_TOP).dp, bottom = (size * PAD_BOTTOM).dp)) { BasicText(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Serif(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** `.eyebrow`: the stripe pill, then tracked saffron capitals. */
@Composable
private fun Eyebrow(parts: List<String>, sizePx: Int = 17) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stripes(40.dp, 10.dp, horizontalBands = true); Spacer(Modifier.width(14.dp))
        parts.forEach { T(it.uppercase(), ts(sizePx, Cin.SAFFRON, weight = 600, tracking = 0.24f), maxLines = 1) }
    }
}

/** `Cinema.meta`: certificate box, the facts with faint dots between, then the star score. */
@Composable
private fun Meta(certificate: String?, items: List<String>, rating: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        var first = true
        @Composable fun sep() { if (!first) { Spacer(Modifier.width(16.dp)); Box(Modifier.size(4.dp).clip(CircleShape).background(Cin.DIM)); Spacer(Modifier.width(16.dp)) } }
        certificate?.takeIf { it.isNotBlank() }?.let { c ->
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Cin.CREAM.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 1.dp)) { T(c, ts(19, Cin.CREAM, weight = 600, tracking = 0.03f), maxLines = 1) }
            first = false
        }
        items.filter { it.isNotBlank() }.forEach { sep(); T(it, ts(23, Cin.MUTED), maxLines = 1); first = false }
        rating?.let { sep(); Icon("star", 22.dp, Cin.SAFFRON); Spacer(Modifier.width(8.dp)); T(it, ts(23, Cin.CREAM, weight = 500)) }
    }
}

/** `Cinema.keyHints`: a glass key cap, then what it does. */
@Composable
private fun KeyHints(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (k, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Cin.GLASS_STRONG).padding(horizontal = 12.dp, vertical = 5.dp)) { T(k, ts(17, Cin.CREAM, weight = 600)) }
                Spacer(Modifier.width(10.dp))
                T(label, ts(18, Cin.DIM, tracking = 0.04f))
            }
        }
    }
}

/**
 * A text style in design pixels. Optical size follows the size in the 1920x1080 design (as on the TV) rather than the size
 * drawn, so a line breaks and measures the same on a 1280x800 window as it does on the TV.
 */
private fun ts(sizePx: Number, color: Color = Cin.CREAM, serif: Boolean = false, weight: Int = 400, tracking: Float = 0f, lineHeight: Float? = null): TextStyle {
    val size = sizePx.toFloat()
    return TextStyle(
        fontFamily = com.danielportelabyrne.cinema.desktop.ui.Fonts.family(serif, size, weight, scale = 1f),
        fontWeight = androidx.compose.ui.text.font.FontWeight(weight),
        fontSize = androidx.compose.ui.unit.TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(tracking, androidx.compose.ui.unit.TextUnitType.Em),
        lineHeight = lineHeight?.let { androidx.compose.ui.unit.TextUnit(size * it, androidx.compose.ui.unit.TextUnitType.Sp) } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        color = color,
    )
}
