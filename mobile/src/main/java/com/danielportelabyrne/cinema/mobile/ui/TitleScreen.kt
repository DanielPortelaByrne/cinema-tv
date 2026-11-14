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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Screen
import com.danielportelabyrne.cinema.mobile.data.clock
import com.danielportelabyrne.cinema.mobile.data.runtimeLabel
import com.danielportelabyrne.cinema.mobile.data.watchedFraction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleScreen(model: Model, page: Screen.TitlePage) {
    val t = page.title
    val lib = page.library
    LaunchedEffect(page.version) { }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                RemoteImage(model, t.backdropUrl ?: t.posterUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(scrim))
                IconButton(onClick = { model.back() }, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.ivory)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
                RemoteImage(model, t.posterUrl, Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 14.dp, top = 52.dp).weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.headlineSmall, color = Palette.ivory)
                    Text(t.metaLine(), style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-36).dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lib != null) {
                        Pill("In your library", Palette.teal)
                        t.imdbScore?.let { Pill("IMDb %.1f".format(it)) }
                    } else when (page.availability) {
                        Availability.REQUESTED -> Pill("Requested", Palette.selected)
                        Availability.PROCESSING, Availability.DOWNLOADING -> Pill("Downloading", Palette.selected)
                        Availability.PARTIAL -> Pill("Partly available", Palette.selected)
                        else -> {}
                    }
                    model.offline.find(lib?.id ?: "")?.let { Pill(if (it.complete) "On this phone" else "Downloading to phone", Palette.terracotta) }
                }
                t.streamingLine()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.butter, modifier = Modifier.padding(top = 10.dp)) }
                Gap()
                Actions(model, page)
                t.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.ivory, modifier = Modifier.padding(top = 16.dp)) }
                if (t.genres.isNotEmpty()) Text(t.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 8.dp))
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lib != null) {
            val resuming = !lib.isSeries && lib.resumeMs > 0
            PrimaryButton(if (lib.isSeries) "Play" else if (resuming) "Resume ${clock(lib.resumeMs)}" else "Play") { model.playTitle(page) }
            if (resuming) Secondary("From the start", Icons.Filled.Replay) { model.playTitle(page, fromStart = true) }
            if (!lib.isSeries) {
                val copy = model.offline.find(lib.id)
                if (copy == null) Secondary("Download", Icons.Filled.Download) { model.toast(model.offline.download(lib)) }
                else Secondary(if (copy.complete) "On phone" else "Downloading", Icons.Filled.DownloadDone) { model.push(Screen.Offline) }
            }
        } else {
            if (model.seerr.isSignedIn && page.availability in setOf(null, Availability.NONE)) PrimaryButton("Request", Icons.Filled.AddCircleOutline) { model.request(page) }
            if (model.seerr.hasStreamService && t.kind == Kind.MOVIE) Secondary("Stream now", Icons.Filled.Bolt) { model.streamNow(t) }
            if (!model.seerr.isSignedIn) Text("Sign in to Seerr in More → Settings to request this.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        }
    }
}

@Composable
fun Secondary(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, null, Modifier.padding(end = 6.dp), tint = Palette.ivory)
        Text(text, color = Palette.ivory)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.seasons(model: Model, page: Screen.TitlePage) {
    val bySeason = page.episodes.groupBy { it.season ?: 0 }.toSortedMap()
    if (bySeason.isEmpty()) return
    item {
        SectionTitle("Episodes")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bySeason.keys.toList()) { s ->
                FilterChip(selected = page.season == s, onClick = { page.season = s },
                    label = { Text(if (s == 0) "Specials" else "Season $s") }, colors = chipColors())
            }
        }
        Gap(8.dp)
    }
    val eps = bySeason[page.season ?: bySeason.firstKey()].orEmpty()
    items(eps, key = { it.id }) { ep -> EpisodeRow(model, ep) }
}

@Composable
private fun EpisodeRow(model: Model, ep: JellyfinItem) {
    val copy = model.offline.find(ep.id)
    Row(Modifier.fillMaxWidth().clickable(enabled = !ep.missing) { model.play(ep) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(model, model.stillOf(ep), Modifier.fillMaxSize())
            val f = watchedFraction(ep)
            if (f > 0f) LinearProgressIndicator(progress = { f }, color = Palette.butter, trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.dp).height(3.dp))
            if (!ep.missing) Icon(Icons.Filled.PlayArrow, null, tint = Palette.ivory,
                modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color(0x88000000)).padding(4.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${ep.episode ?: ""}. ${ep.name}", style = MaterialTheme.typography.titleSmall, color = if (ep.missing) Palette.ivoryMuted else Palette.ivory,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(runtimeLabel(ep.runtimeMin), "Not downloaded yet".takeIf { ep.missing }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        }
        if (ep.played) Icon(Icons.Filled.CheckCircle, "Watched", tint = Palette.teal, modifier = Modifier.padding(horizontal = 4.dp))
        if (!ep.missing) IconButton(onClick = { if (copy == null) model.toast(model.offline.download(ep)) else model.push(Screen.Offline) }) {
            Icon(if (copy == null) Icons.Filled.Download else Icons.Filled.DownloadDone, "Download to phone", tint = if (copy == null) Palette.ivoryMuted else Palette.butter)
        }
    }
}
