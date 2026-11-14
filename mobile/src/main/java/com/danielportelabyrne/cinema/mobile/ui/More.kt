package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.ByteSize
import com.danielportelabyrne.cinema.data.movies.QueueItem
import com.danielportelabyrne.cinema.data.movies.SeerrRequest
import com.danielportelabyrne.cinema.data.movies.Stage
import com.danielportelabyrne.cinema.data.update.AvailableUpdate
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Screen
import kotlinx.coroutines.delay

@Composable
fun MoreScreen(model: Model) {
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabHeader("More")
        LazyColumn {
            item { Entry(Icons.Filled.SettingsRemote, "TV remote", model.remote.current?.let { "Paired with ${it.name}" } ?: "Control Cinema on a TV") { model.push(Screen.Remote) } }
            item { Entry(Icons.Filled.PhoneAndroid, "On this phone", "${model.offline.copies.size} offline ${if (model.offline.copies.size == 1) "copy" else "copies"}") { model.push(Screen.Offline) } }
            item { Entry(Icons.Filled.CloudDownload, "Downloads", "What Sonarr and Radarr are fetching") { model.push(Screen.Downloads) } }
            item { Entry(Icons.Filled.PlaylistAdd, "Requests", if (model.seerr.isSignedIn) "Your Seerr requests" else "Sign in to Seerr in Settings") { model.push(Screen.Requests) } }
            item { Entry(Icons.Filled.Settings, "Settings", "Jellyfin, Seerr, country, helper services") { model.push(Screen.Settings) } }
            item {
                val sub = when {
                    checking -> "Checking…"
                    update != null -> "Build ${update!!.build} is ready. Tap to install."
                    else -> "Build ${model.updater.currentBuild}. Tap to check."
                }
                Entry(Icons.Filled.SystemUpdate, "Updates", sub) {
                    val u = update
                    if (u != null) { model.toast("Downloading build ${u.build}…"); model.bg({ model.updater.install(u) }) { ok -> if (ok != true) model.toast("The update didn't download") } }
                    else { checking = true; model.bg({ model.updater.check() }) { checking = false; update = it; if (it == null) model.toast("Cinema is up to date") } }
                }
            }
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.butter)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.ivory)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.ivoryMuted)
    }
    HorizontalDivider(color = Palette.surface)
}

@Composable
fun OfflineScreen(model: Model) {
    LaunchedEffect(Unit) { while (true) { model.offline.poll(); delay(2000) } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(model, "On this phone")
        val copies = model.offline.copies
        if (copies.isEmpty()) { Empty("Nothing kept on this phone yet. Use Download on a film or an episode; it downloads over Wi-Fi."); return }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(copies, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clickable(enabled = c.complete) { model.playOffline(c.id) }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(model, c.poster, Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, color = Palette.ivory)
                        c.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted) }
                        when {
                            c.failed -> Text("Download failed. Delete it and try again.", style = MaterialTheme.typography.bodySmall, color = Palette.terracotta)
                            !c.complete -> LinearProgressIndicator(progress = { c.progress }, color = Palette.butter, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp))
                            else -> c.sizeBytes?.let { Text(ByteSize.format(it), style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted) }
                        }
                    }
                    if (c.complete) Icon(Icons.Filled.PlayArrow, null, tint = Palette.butter)
                    IconButton(onClick = { model.offline.remove(c.id) }) { Icon(Icons.Filled.Delete, "Delete from phone", tint = Palette.ivoryMuted) }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(model: Model) {
    var items by remember { mutableStateOf<List<QueueItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            model.bg({ model.queue.items() }) { r -> failed = r == null; items = r.orEmpty() }
            delay(5000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(model, "Downloads")
        val list = items
        when {
            list == null -> LoadingRow()
            failed && list.isEmpty() -> Empty("Couldn't read Sonarr or Radarr. Sign in to Seerr as an admin, or add them in Settings.")
            list.isEmpty() -> Empty("Nothing is downloading.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { "${it.source}-${it.id}" }) { q ->
                    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(q.title, style = MaterialTheme.typography.titleSmall, color = Palette.ivory)
                                Text(listOfNotNull(q.detail, q.status.replaceFirstChar { it.uppercase() },
                                    q.etaSeconds?.let { "${it / 60} min left" }, q.speedBytes?.let { "${ByteSize.format(it)}/s" }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                            }
                            if (q.canCancel) TextButton(onClick = {
                                model.bg({ model.queue.cancel(q) }) { ok -> model.toast(if (ok == true) "Cancelled ${q.title}" else "Couldn't cancel ${q.title}"); items = items?.filterNot { it === q } }
                            }) { Text("Cancel", color = Palette.terracotta) }
                        }
                        q.progress()?.let { LinearProgressIndicator(progress = { it }, color = Palette.butter, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestsScreen(model: Model) {
    var list by remember { mutableStateOf<List<SeerrRequest>?>(null) }
    val names = remember { mutableStateMapOf<Int, Pair<String, String?>>() }
    LaunchedEffect(Unit) {
        model.bg({ model.seerr.requestsOrNull() }) { r ->
            list = r.orEmpty()
            r.orEmpty().forEach { req -> model.bg({ model.seerr.titleInfo(req.kind, req.tmdbId) }) { info -> if (info != null) names[req.id] = info } }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(model, "Requests")
        val l = list
        when {
            !model.seerr.isSignedIn -> Empty("Sign in to Seerr in Settings to see your requests.")
            l == null -> LoadingRow()
            l.isEmpty() -> Empty("No requests yet. Find a title and press Request.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(l, key = { it.id }) { r ->
                    val info = names[r.id]
                    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(model, info?.second, Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(info?.first ?: "…", style = MaterialTheme.typography.titleSmall, color = Palette.ivory)
                            Text(when (r.stage) {
                                Stage.WAITING_APPROVAL -> "Waiting for approval"; Stage.SEARCHING -> "Searching"; Stage.DOWNLOADING -> "Downloading"
                                Stage.READY -> "Ready to watch"; Stage.DECLINED -> "Declined"; Stage.FAILED -> "Failed"
                            } + if (r.seasons.isNotEmpty()) " · Seasons ${r.seasons.joinToString(", ")}" else "", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                            r.progress()?.takeIf { r.stage == Stage.DOWNLOADING }?.let {
                                LinearProgressIndicator(progress = { it }, color = Palette.butter, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp))
                            }
                        }
                        if (r.canCancel) TextButton(onClick = {
                            model.bg({ model.queue.cancelRequest(r) }) { ok -> model.toast(if (ok == true) "Request cancelled" else "Couldn't cancel"); if (ok == true) list = list?.filterNot { it.id == r.id } }
                        }) { Text("Cancel", color = Palette.terracotta) }
                    }
                }
            }
        }
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Palette.surface).padding(16.dp)) { content() }
}
