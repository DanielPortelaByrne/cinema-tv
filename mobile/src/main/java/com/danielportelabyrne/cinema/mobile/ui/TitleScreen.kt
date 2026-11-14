package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Screen
import com.danielportelabyrne.cinema.mobile.data.clock
import com.danielportelabyrne.cinema.mobile.data.runtimeLabel
import com.danielportelabyrne.cinema.mobile.data.watchedFraction

/**
 * The title page, laid out like the TV's: the backdrop under a dark that falls away, the poster with its stacked stripe
 * shadow hanging off the picture's edge, the eyebrow, a Fraunces name, the meta line with its certificate box and IMDb
 * tag, a "Streaming on" pill, the cream main action, and for a series its seasons and episodes with the wave scrubber.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleScreen(model: Model, page: Screen.TitlePage) {
    val t = page.title
    val lib = page.library
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                RemoteImage(model, t.backdropUrl ?: t.posterUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x77000000), 0.35f to Color.Transparent, 0.75f to Color(0xCC0B0807), 1f to Palette.bg)))
                IconButton(onClick = { model.back() }, modifier = Modifier.statusBarsPadding().padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.ivory)
                }
                Row(Modifier.align(Alignment.BottomStart).offset(y = 74.dp).padding(start = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.padding(end = 22.dp, bottom = 22.dp)) {
                        RemoteImage(model, t.posterUrl, Modifier.width(112.dp).aspectRatio(2f / 3f).stripeShadow(5.dp, 12.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Column(Modifier.weight(1f).padding(end = 16.dp, bottom = 24.dp)) {
                        Eyebrow(listOfNotNull(if (t.kind == Kind.SHOW) "Series" else "Film", t.year?.toString()), mark = false)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 92.dp)) {
                Text(t.name, style = TextStyle(fontFamily = fraunces(500, 56f), fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp), color = Palette.ivory, maxLines = 3, overflow = TextOverflow.Ellipsis)
                MetaLine(t.age, listOfNotNull(t.year?.toString(), t.genres.firstOrNull(), t.runtimeMin?.let { "${it / 60}h ${it % 60}m" }), t.imdbScore, Modifier.padding(top = 10.dp))
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.streamingLine()?.let { ProviderPill(it) }
                    if (lib != null) Pill("In your library", Palette.teal.copy(alpha = 0.55f))
                    else when (page.availability) {
                        Availability.REQUESTED -> Pill("Requested")
                        Availability.PROCESSING, Availability.DOWNLOADING -> Pill("Downloading")
                        Availability.PARTIAL -> Pill("Partly available")
                        else -> {}
                    }
                    model.offline.find(lib?.id ?: "")?.let { Pill(if (it.complete) "On this phone" else "Downloading to phone", Palette.terracotta.copy(alpha = 0.5f)) }
                }
                Gap(16.dp)
                Actions(model, page)
                t.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp), color = Palette.reading, modifier = Modifier.padding(top = 18.dp)) }
                if (t.genres.isNotEmpty()) Text(t.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (lib?.isSeries == true) seasons(model, page)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(model: Model, page: Screen.TitlePage) {
    val lib = page.library
    val t = page.title
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (lib != null) {
            val resuming = !lib.isSeries && lib.resumeMs > 0
            CreamButton(if (lib.isSeries) "Play" else if (resuming) "Resume ${clock(lib.resumeMs)}" else "Play", Icons.Filled.PlayArrow) { model.playTitle(page) }
            if (resuming) Secondary("From the start", Icons.Filled.Replay) { model.playTitle(page, fromStart = true) }
            if (!lib.isSeries) {
                val copy = model.offline.find(lib.id)
                if (copy == null) Secondary("Download", Icons.Filled.Download) { model.toast(model.offline.download(lib)) }
                else Secondary(if (copy.complete) "On phone" else "Downloading", Icons.Filled.DownloadDone) { model.push(Screen.Offline) }
            }
        } else {
            if (model.seerr.isSignedIn && page.availability in setOf(null, Availability.NONE)) TerraButton("Request", Icons.Filled.AddCircleOutline) { model.request(page) }
            if (model.seerr.hasStreamService && t.kind == Kind.MOVIE) Secondary("Stream now", Icons.Filled.Bolt) { model.streamNow(t) }
            if (!model.seerr.isSignedIn) Text("Sign in to Seerr in More → Settings to request this.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        }
    }
}

@Composable
fun Secondary(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) = GlassButton(text, icon, onClick = onClick)

private fun androidx.compose.foundation.lazy.LazyListScope.seasons(model: Model, page: Screen.TitlePage) {
    val bySeason = page.episodes.groupBy { it.season ?: 0 }.toSortedMap()
    if (bySeason.isEmpty()) return
    item {
        RowTitle("Episodes")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bySeason.keys.toList()) { s -> ChoicePill(if (s == 0) "Specials" else "Season $s", page.season == s) { page.season = s } }
        }
        Spacer(Modifier.height(8.dp))
    }
    val eps = bySeason[page.season ?: bySeason.firstKey()].orEmpty()
    items(eps, key = { it.id }) { ep -> EpisodeRow(model, ep) }
}

@Composable
private fun EpisodeRow(model: Model, ep: JellyfinItem) {
    val copy = model.offline.find(ep.id)
    Row(Modifier.fillMaxWidth().clickable(enabled = !ep.missing) { model.play(ep) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(138.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                RemoteImage(model, model.stillOf(ep), Modifier.fillMaxSize())
                if (!ep.missing) Icon(Icons.Filled.PlayArrow, null, tint = Palette.ivory, modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color(0x88000000)).padding(4.dp))
            }
            val f = watchedFraction(ep)
            if (f > 0f) WaveBar(f, Modifier.padding(top = 2.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${ep.episode ?: ""}. ${ep.name}", style = TextStyle(fontFamily = fraunces(500, 20f), fontSize = 16.sp, lineHeight = 19.sp), color = if (ep.missing) Palette.dim else Palette.ivory, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(runtimeLabel(ep.runtimeMin), "Not downloaded yet".takeIf { ep.missing }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 2.dp))
            ep.overview?.takeIf { !ep.missing }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (ep.played) Icon(Icons.Filled.CheckCircle, "Watched", tint = Palette.teal, modifier = Modifier.padding(horizontal = 4.dp))
        if (!ep.missing) IconButton(onClick = { if (copy == null) model.toast(model.offline.download(ep)) else model.push(Screen.Offline) }) {
            Icon(if (copy == null) Icons.Filled.Download else Icons.Filled.DownloadDone, "Download to phone", tint = if (copy == null) Palette.ivoryMuted else Palette.butter)
        }
    }
}
