package com.danielportelabyrne.cinema.mobile.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.danielportelabyrne.cinema.mobile.data.clock
import com.danielportelabyrne.cinema.mobile.ui.Eyebrow
import com.danielportelabyrne.cinema.mobile.ui.Palette
import com.danielportelabyrne.cinema.mobile.ui.Ribbon
import com.danielportelabyrne.cinema.mobile.ui.RibbonShape
import com.danielportelabyrne.cinema.mobile.ui.WaveLoader
import com.danielportelabyrne.cinema.mobile.ui.fraunces
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** One audio or subtitle choice in the tracks panel. */
class TrackOption(val label: String, val selected: Boolean, val select: () -> Unit)

/** Everything the player's controls show; the activity fills it from the player twice a second. */
class PlayerUi {
    var title by mutableStateOf("")
    var eyebrow by mutableStateOf<List<String>>(emptyList())
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var chapters by mutableStateOf<List<Float>>(emptyList())
    var hasNext by mutableStateOf(false)
    var pip by mutableStateOf(false)
    var audio by mutableStateOf<List<TrackOption>>(emptyList())
    var text by mutableStateOf<List<TrackOption>>(emptyList())
    var casting by mutableStateOf(false)
    /** True only while playback is inside an intro the server knows; the "Skip intro" pill shows then and never otherwise. */
    var inIntro by mutableStateOf(false)
    /** "1080p · HD · H.264 · 8.2 Mbps" for the stream being played; blank when unknown or not a library stream. Shown only while paused. */
    var mediaLine by mutableStateOf("")
    /** The trickplay frame for the spot being scrubbed to; null when there is none (no trickplay, or not scrubbing). */
    var thumb by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** Counts touches, so the controls stay up while the viewer is using them. */
    var touches by mutableIntStateOf(0)
}

interface PlayerActions {
    fun back()
    fun playPause()
    fun seekBy(ms: Long)
    fun seekTo(ms: Long)
    fun next()
    fun pip()
    fun skipIntro()
    /** Scrubbing is at [ms]; -1 when it has stopped. */
    fun preview(ms: Long)
    /** The fingers have spread or closed by [factor]. Returns what to call the zoom now. */
    fun zoomBy(factor: Float): String
    /** The fingers have lifted. Returns what to call the zoom it settled on. */
    fun zoomDone(): String
}

/**
 * The player's controls, drawn the TV's way: the wavy scrubber (three hard-stepped bands behind the cream playhead bulb,
 * dots for chapters) in place of a seek bar, a ribbon in the corner, Fraunces titles, and the wave loader while it buffers.
 * Tap shows or hides them; double-tap the left or right half to go back or forward ten seconds; drag the wave to scrub;
 * pinch to zoom the picture to fill the screen, or further.
 */
@Composable
fun PlayerOverlay(ui: PlayerUi, act: PlayerActions, onCastButton: (android.content.Context) -> MediaRouteButton) {
    var visible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible, ui.playing, ui.touches, panel) {
        if (visible && ui.playing && !panel) { delay(3800); visible = false }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(700); flash = null } }
    if (ui.pip) return
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures(
            onTap = { visible = !visible; ui.touches++ },
            onDoubleTap = { p ->
                val back = p.x < size.width / 2f
                act.seekBy(if (back) -10_000 else 10_000); flash = if (back) "−10 s" else "+10 s"; visible = true; ui.touches++
            },
        )
    }.pointerInput(Unit) {
        // Two fingers: seen before the controls under them, so a pinch never presses anything.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinched = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) pinched = true
                if (pinched) {
                    val factor = event.calculateZoom()
                    if (factor != 1f) flash = act.zoomBy(factor)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (pinched) flash = act.zoomDone()
        }
    }) {
        flash?.let { Text(it, color = Palette.ivory, style = TextStyle(fontFamily = fraunces(500, 40f), fontSize = 30.sp), modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), CircleShape).padding(horizontal = 24.dp, vertical = 12.dp)) }
        if (ui.buffering && !visible) Box(Modifier.align(Alignment.Center).width(180.dp)) { WaveLoader() }
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xCC0B0807), 0.28f to Color(0x330B0807), 0.62f to Color(0x330B0807), 1f to Color(0xE60B0807))))
                // The ribbon looping in from the right behind the top bar.
                Ribbon(RibbonShape.LOOP, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).height(120.dp), strokeWidth = 20.dp, opacity = 0.7f)
                // Top: back, the title, then cast, subtitles and picture-in-picture.
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = act::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.ivory) }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        if (ui.eyebrow.isNotEmpty()) Eyebrow(ui.eyebrow, mark = true)
                        Text(ui.title, style = TextStyle(fontFamily = fraunces(500, 28f), fontSize = 22.sp, letterSpacing = (-0.4).sp), color = Palette.ivory, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        // Paused only: what is being played, in one quiet line.
                        if (!ui.playing && !ui.buffering && ui.mediaLine.isNotBlank()) Text(ui.mediaLine, color = Palette.ivoryMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    AndroidView({ ctx -> onCastButton(ctx) }, Modifier.size(48.dp))
                    IconButton(onClick = { panel = true }) { Icon(Icons.Filled.Subtitles, "Audio and subtitles", tint = Palette.ivory) }
                    IconButton(onClick = act::pip) { Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Palette.ivory) }
                }
                // Centre: back ten, play or pause, forward ten; the wave loader while it buffers.
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { act.seekBy(-10_000); ui.touches++ }, Modifier.size(56.dp)) { Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Palette.ivory, modifier = Modifier.size(36.dp)) }
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Palette.ivory).clickable { act.playPause(); ui.touches++ }, contentAlignment = Alignment.Center) {
                        if (ui.buffering) Box(Modifier.width(52.dp)) { WaveLoader(thickness = 4.dp, amplitude = 3.dp, wavelength = 22.dp) }
                        else Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.playing) "Pause" else "Play", tint = Palette.ink, modifier = Modifier.size(42.dp))
                    }
                    IconButton(onClick = { act.seekBy(10_000); ui.touches++ }, Modifier.size(56.dp)) { Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Palette.ivory, modifier = Modifier.size(36.dp)) }
                }
                // Bottom: the times, the wave, and the next episode.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
                    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
                    val shown = drag ?: progress
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(clock((shown * ui.durationMs).toLong()), color = Palette.ivory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.weight(1f))
                        if (ui.hasNext) IconButton(onClick = act::next, Modifier.size(36.dp)) { Icon(Icons.Filled.SkipNext, "Next episode", tint = Palette.ivory) }
                        Text("−" + clock(((1f - shown) * ui.durationMs).toLong()), color = Palette.ivoryMuted, fontSize = 13.sp)
                    }
                    WaveScrubber(
                        progress = shown, chapters = ui.chapters, scrubbing = drag != null,
                        onScrub = { drag = it; ui.touches++; act.preview((it * ui.durationMs).toLong()) },
                        onDone = { f -> drag = null; act.preview(-1L); if (ui.durationMs > 0) act.seekTo((f * ui.durationMs).toLong()) },
                        onCancel = { drag = null; act.preview(-1L) },
                    )
                }
            }
        }
        // Scrub thumbnail: only while dragging and only when the server has trickplay; it floats over the picture, so nothing moves.
        val thumb = ui.thumb
        val at = drag
        if (at != null && thumb != null) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
            Box(Modifier.weight(at.coerceAtLeast(0.001f)))
            androidx.compose.foundation.Image(thumb, null, Modifier.width(168.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black))
            Box(Modifier.weight((1f - at).coerceAtLeast(0.001f)))
        }
        // Skip intro: one quiet pill above the wave, only while inside an intro the server knows.
        AnimatedVisibility(ui.inIntro && !panel, Modifier.align(Alignment.BottomEnd), enter = fadeIn(), exit = fadeOut()) {
            Text("Skip intro", color = Palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.navigationBarsPadding().padding(end = 24.dp, bottom = 96.dp)
                    .clip(CircleShape).background(Palette.ivory).clickable { act.skipIntro(); ui.touches++ }
                    .padding(horizontal = 20.dp, vertical = 10.dp))
        }
        if (panel) TracksPanel(ui, onClose = { panel = false })
    }
}

/**
 * The wavy scrubber: a sine across the bar. Watched is three hard-stepped bands, maroon (full width), terracotta (2/3) and
 * saffron (1/3), with round caps; unwatched is one solid band at 70% width; chapters are small solid dots on the wave;
 * the playhead is an off-white bulb with a soft glow that swells while it is held. Touch or drag anywhere on it.
 */
@Composable
fun WaveScrubber(progress: Float, chapters: List<Float>, scrubbing: Boolean, onScrub: (Float) -> Unit, onDone: (Float) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(64.dp).pointerInput(Unit) {
        awaitEachGesture {
            val inset = 24.dp.toPx()
            fun frac(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
            val down = awaitFirstDown()
            var f = frac(down.position.x)
            onScrub(f)
            do {
                val e = awaitPointerEvent()
                val c = e.changes.first()
                // Taken for a pinch: leave the film where it was.
                if (c.isConsumed) { onCancel(); return@awaitEachGesture }
                f = frac(c.position.x)
                c.consume()
                onScrub(f)
            } while (e.changes.any { it.pressed })
            onDone(f)
        }
    }) {
        val inset = 24.dp.toPx(); val x0 = inset; val x1 = size.width - inset
        val amp = 7.dp.toPx(); val wave = 72.dp.toPx(); val thick = 12.dp.toPx(); val base = size.height / 2f
        fun y(x: Float) = base + amp * sin(((x - x0) / wave * 2 * PI)).toFloat()
        val path = androidx.compose.ui.graphics.Path()
        var x = x0; var first = true
        while (x < x1) { if (first) path.moveTo(x, y(x)) else path.lineTo(x, y(x)); first = false; x += 3.dp.toPx() }
        path.lineTo(x1, y(x1))
        val px = x0 + (x1 - x0) * progress.coerceIn(0f, 1f)
        fun band(c: Color, w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        clipRect(px, 0f, size.width, size.height) { drawPath(path, Color(0xFF4A423B), style = band(Color.Unspecified, thick * 0.7f)) }
        clipRect(0f, 0f, px, size.height) {
            drawPath(path, Palette.maroon, style = band(Color.Unspecified, thick))
            drawPath(path, Palette.stripeTerra, style = band(Color.Unspecified, thick * 0.667f))
            drawPath(path, Palette.stripeSaffron, style = band(Color.Unspecified, thick * 0.333f))
        }
        chapters.filter { it > 0f && it < 1f }.forEach { f ->
            val cx = x0 + (x1 - x0) * f
            drawCircle(if (cx < px) Color(0xFF1B0D06) else Palette.ivory.copy(alpha = 0.55f), 3.5.dp.toPx(), Offset(cx, y(cx)))
        }
        val py = y(px)
        val glow = (if (scrubbing) 30.dp else 22.dp).toPx()
        drawCircle(Brush.radialGradient(listOf(Palette.ivory.copy(alpha = 0.55f), Palette.ivory.copy(alpha = 0.18f), Color.Transparent), center = Offset(px, py), radius = glow), glow, Offset(px, py))
        drawCircle(Color(0xFFF7EFE0), (if (scrubbing) 11.dp else 8.5.dp).toPx(), Offset(px, py))
    }
}

/** Audio tracks and subtitles, as glass lists over the picture. */
@Composable
private fun TracksPanel(ui: PlayerUi, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxHeight(0.8f).padding(24.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xF2120D0A)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            TrackColumn("Subtitles", ui.text, Modifier.weight(1f)) { onClose() }
            TrackColumn("Audio", ui.audio, Modifier.weight(1f)) { onClose() }
        }
    }
}

@Composable
private fun TrackColumn(heading: String, options: List<TrackOption>, modifier: Modifier, picked: () -> Unit) {
    Column(modifier) {
        Eyebrow(listOf(heading))
        LazyColumn(Modifier.padding(top = 10.dp)) {
            items(options) { o ->
                Text(o.label + if (o.selected) "  ✓" else "", color = if (o.selected) Palette.butter else Palette.ivory, fontSize = 16.sp, fontWeight = if (o.selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { o.select(); picked() }.padding(vertical = 10.dp))
            }
            if (options.isEmpty()) item { Text("None", color = Palette.dim) }
        }
    }
}
