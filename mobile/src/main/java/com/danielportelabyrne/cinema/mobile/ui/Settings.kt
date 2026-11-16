package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.music.SignIn
import com.danielportelabyrne.cinema.data.music.SubsonicApi
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.settings.LibraryOrder
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.mobile.Model
import java.util.Locale

/**
 * Settings, with the TV's options in the TV's order and wording: Jellyfin, Seerr, Sonarr and Radarr, the torrent client,
 * country, library order, integrations, helper services, updates and reset. Each one reads and writes the same
 * CinemaSettings key the TV does, so a value means the same on both. (The TV keeps most settings per device; library
 * order travels with the person's profile.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(model: Model) {
    Column(Modifier.fillMaxSize().imePadding()) {
        TabHeader("Settings", listOf("Jellyfin", "Seerr", "Integrations"), RibbonShape.SWOOP)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Card {
                Label("Jellyfin")
                var name by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { model.bg({ model.client.userName() }) { name = it } }
                Text(model.client.server.ifBlank { "Not signed in" }, color = Palette.ivory)
                name?.let { Text("Signed in as $it", color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { model.client.forget(); model.settings.setupDone = false; model.stack.clear(); model.needsSetup = true }) {
                    Text("Sign out and change server", color = Palette.terracotta)
                }
            }
            Card { Label("Seerr"); SeerrForm(model) }
            Card {
                Label("Sonarr and Radarr")
                Text("Found through Seerr when you sign in as a Seerr admin. Enter them here only if Seerr doesn't share them.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                Gap(8.dp)
                Arr.entries.forEach { arr ->
                    val saved = model.settings.manualArr(arr)
                    var url by rememberSaveable(arr) { mutableStateOf(saved?.baseUrl.orEmpty()) }
                    var key by remember(arr) { mutableStateOf(saved?.apiKey.orEmpty()) }
                    Field("${arr.label} address (port ${arr.defaultPort})", url) { url = it }
                    OutlinedTextField(key, { key = it }, label = { Text("${arr.label} API key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Save { model.settings.setManualArr(arr, if (url.isBlank() || key.isBlank()) null else ManualArr(url.trim(), key.trim())); model.toast("Saved ${arr.label}") }
                    Gap(8.dp)
                }
            }
            if (model.seerr.hasServer) TorrentClients(model)
            Card {
                Label("Country")
                var region by rememberSaveable { mutableStateOf(model.settings.region) }
                Text(Locale("", region).getDisplayCountry(Locale.ENGLISH), color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(region, { region = it.take(2).uppercase() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Two letters, for what is streaming where") })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf("GB", "IE", "US", "CA", "AU", "NZ", "DE", "FR", "ES", "IT", "NL", "SE").forEach { c -> ChoicePill(c, region == c) { region = c } }
                }
                Save {
                    if (!region.matches(Regex("[A-Z]{2}"))) model.toast("Use a two-letter country code, like GB or US.")
                    else { model.settings.region = region; Catalog.country = region; model.rows.clear(); model.toast("Country set to $region") }
                }
            }
            Card {
                Label("Library order")
                Text("How My library lists your films and series.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                Gap(8.dp)
                LibraryOrder.entries.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.label, color = Palette.ivory, style = MaterialTheme.typography.titleSmall)
                            Text(o.detail, color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        ChoicePill(if (model.libraryOrder == o) "In use ✓" else "Use", model.libraryOrder == o) { model.chooseLibraryOrder(o) }
                    }
                }
            }
            Integrations(model)
            Card {
                Label("Helper services")
                Text("Optional services that run next to Seerr. Leave these blank unless you run them.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                Gap(8.dp)
                var helper by rememberSaveable { mutableStateOf(model.settings.requestHelperUrl.orEmpty()) }
                var stream by rememberSaveable { mutableStateOf(model.settings.streamServiceUrl.orEmpty()) }
                Field("Request helper address", helper) { helper = it }
                Text("Watches requests on the server, retries searches, and reports why a request is stuck.", style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(bottom = 8.dp))
                Field("Stream service address", stream) { stream = it }
                Text("Plays a film before it has downloaded. Adds Stream now to title pages.", style = MaterialTheme.typography.bodySmall, color = Palette.dim)
                Save { model.settings.requestHelperUrl = helper.ifBlank { null }; model.settings.streamServiceUrl = stream.ifBlank { null }; model.toast("Saved") }
            }
            Updates(model)
            Reset(model)
        }
    }
}

// --- Integrations -------------------------------------------------------------------------------

/** The TV's Integrations page: each optional service switched on or off, Music with its server, Stremio with its link. */
@Composable
private fun Integrations(model: Model) {
    val integrations = remember { Integration.entries.filterNot { it == Integration.SUGGESTASHOW } }
    val on = remember { mutableStateMapOf<Integration, Boolean>().apply { integrations.forEach { put(it, model.settings.isOn(it)) } } }
    var musicOpen by remember { mutableStateOf(false) }
    Card {
        Label("Integrations")
        Text("Optional services Cinema can add to the menu and to title pages. Switch off any you do not use.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        Gap(8.dp)
        integrations.forEach { i ->
            val enabled = on[i] == true
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(i.label, color = Palette.ivory, style = MaterialTheme.typography.titleSmall)
                    Text(detail(model, i, enabled), color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(enabled, { v -> model.settings.setOn(i, v); on[i] = v },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.butter, checkedThumbColor = Palette.ink, uncheckedTrackColor = Palette.glassStrong, uncheckedThumbColor = Palette.ivoryMuted, uncheckedBorderColor = Palette.hairline))
            }
            if (i == Integration.MUSIC && enabled) {
                TextButton(onClick = { musicOpen = !musicOpen }) { Text(if (model.settings.subsonic == null) "Set up a music server" else "Music server", color = Palette.butter) }
                if (musicOpen) MusicServer(model) { musicOpen = false }
            }
            if (i == Integration.STREMIO && enabled && model.settings.stremioKey == null) {
                Text("Link your Stremio account from Cinema on a TV; the phone shows it once linked.", style = MaterialTheme.typography.bodySmall, color = Palette.dim)
            }
        }
    }
}

private fun detail(model: Model, i: Integration, on: Boolean): String {
    if (!on) return "Off · ${i.summary}"
    return when (i) {
        Integration.LETTERBOXD, Integration.SUGGESTASHOW -> "On · ${i.summary}"
        Integration.MUSIC -> model.settings.subsonic?.let { "On · ${it.baseUrl}" + if (it.username.isNotBlank()) " · ${it.username}" else "" } ?: "On · set up a server to add Music"
        Integration.STREMIO -> if (model.settings.stremioKey != null) "On · linked" else "On · not linked yet"
    }
}

@Composable
private fun MusicServer(model: Model, close: () -> Unit) {
    val current = model.settings.subsonic
    var address by rememberSaveable { mutableStateOf(current?.baseUrl.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Field("Music server address", address) { address = it }
    Field("Username", user) { user = it }
    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    OutlinedTextField(key, { key = it }, label = { Text("API key (instead of a password)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Text("Leave the password blank to keep the saved one.", style = MaterialTheme.typography.bodySmall, color = Palette.dim)
    status?.let { Text(it, color = Palette.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (busy) "Connecting…" else "Connect", Icons.Filled.Check) {
            if (busy) return@PrimaryButton
            val a = address.trim(); val u = user.trim()
            val pw = password.ifEmpty { if (key.isEmpty()) current?.password.orEmpty() else "" }
            val k = key.ifEmpty { if (password.isEmpty()) current?.apiKey.orEmpty() else "" }
            if (a.isEmpty()) { status = "Enter the music server's address."; return@PrimaryButton }
            if (k.isEmpty() && (u.isEmpty() || pw.isEmpty())) { status = "Enter a username and password, or an API key."; return@PrimaryButton }
            val config = SubsonicConfig(SubsonicApi.normalize(a), u, pw, k)
            busy = true; status = null
            model.bg({ SubsonicClient(config).signIn() }) { r ->
                busy = false
                when (r) {
                    SignIn.OK -> { model.settings.subsonic = config; model.settings.setOn(Integration.MUSIC, true); model.toast("Music server connected"); close() }
                    SignIn.WRONG_CREDENTIALS -> status = "The server turned down that sign-in."
                    SignIn.NEEDS_API_KEY -> status = "This server needs an API key. Make one in its settings."
                    SignIn.NOT_SUBSONIC -> status = "That address is not a Subsonic server."
                    else -> status = "Couldn't reach $a. Check the address."
                }
            }
        }
        if (current != null) TextButton(onClick = { model.settings.subsonic = null; model.toast("Music server forgotten"); close() }) { Text("Forget server", color = Palette.terracotta) }
    }
}

// --- Torrent client -----------------------------------------------------------------------------

@Composable
private fun TorrentClients(model: Model) {
    var found by remember { mutableStateOf<List<Pair<QueueClient.FoundClient, Boolean>>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf<QueueClient.FoundClient?>(null) }
    fun check() {
        checking = true
        model.bg({ model.queue.detectClients().map { it to (it.config?.let { c -> model.queue.needsLogin(c) } ?: false) } }) { r -> checking = false; found = r.orEmpty() }
    }
    Card {
        Label("Torrent client")
        Text("Download controls for qBittorrent, Transmission and Deluge. Progress works with any client.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        found?.let { list ->
            if (list.isEmpty()) Text("No torrent client found. Cinema needs Sonarr or Radarr to find it.", style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(top = 8.dp))
            list.forEach { (c, needsLogin) ->
                val kind = c.config?.kind?.label ?: c.listed.implementation
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.listed.name} · $kind", color = Palette.ivory, style = MaterialTheme.typography.titleSmall)
                        Text("${c.listed.host} · " + when { c.config == null -> "Progress only"; needsLogin -> "Needs your sign-in for the controls"; else -> "Ready ✓" },
                            style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                    }
                    if (c.config != null) ChoicePill(if (needsLogin) "Sign in" else "Sign in again", needsLogin) { signIn = c }
                }
            }
        }
        Gap(8.dp)
        GlassButton(if (checking) "Checking…" else "Check") { if (!checking) check() }
    }
    signIn?.let { c ->
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        val config = c.config!!
        AlertDialog(onDismissRequest = { signIn = null }, containerColor = androidx.compose.ui.graphics.Color(0xFF241A13),
            title = { Text("Sign in to ${config.kind.label}", color = Palette.ivory) },
            text = {
                Column {
                    if (config.kind.needsUsername) Field("Username", user) { user = it }
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    model.bg({ model.queue.signIn(config, user.trim(), pass) }) { ok -> model.toast(if (ok == true) "Signed in to ${config.kind.label}" else "That sign-in didn't work"); if (ok == true) check() }
                    signIn = null
                }) { Text("Sign in", color = Palette.butter) }
            },
            dismissButton = { TextButton(onClick = { signIn = null }) { Text("Cancel", color = Palette.ivoryMuted) } })
    }
}

// --- Updates and reset --------------------------------------------------------------------------

@Composable
private fun Updates(model: Model) {
    var line by remember { mutableStateOf("Cinema build ${model.updater.currentBuild} · Android asks you to confirm each update") }
    var update by remember { mutableStateOf<com.danielportelabyrne.cinema.data.update.AvailableUpdate?>(null) }
    Card {
        Label("Updates")
        Text(line, color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
        Gap(8.dp)
        GlassButton(if (update != null) "Install build ${update!!.build}" else "Check for updates") {
            val u = update
            if (u != null) { model.toast("Downloading build ${u.build}…"); model.bg({ model.updater.install(u) }) { ok -> if (ok != true) model.toast("The update didn't download") } }
            else {
                line = "Checking for a newer build…"
                model.bg({ model.updater.check() }) { r ->
                    update = r
                    line = if (r != null) "Build ${r.build} is ready" else "Up to date · build ${model.updater.currentBuild}"
                }
            }
        }
    }
}

@Composable
private fun Reset(model: Model) {
    var confirm by remember { mutableStateOf(false) }
    Card {
        Label("Reset")
        Text("Forget every server, sign-in and setting on this phone.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        TextButton(onClick = { confirm = true }) { Text("Reset everything", color = Palette.terracotta) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = androidx.compose.ui.graphics.Color(0xFF241A13),
        title = { Text("Reset everything?", color = Palette.ivory) },
        text = { Text("This phone forgets Jellyfin, Seerr, Sonarr, Radarr, download-client logins and the country. Your servers are not changed. Offline copies stay on the phone.", color = Palette.ivoryMuted) },
        confirmButton = { TextButton(onClick = { confirm = false; model.resetEverything() }) { Text("Reset", color = Palette.terracotta) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Palette.ivoryMuted) } })
}

@Composable
private fun Label(s: String) = Text(s, style = MaterialTheme.typography.titleMedium, color = Palette.butter, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
}

@Composable
private fun Save(onClick: () -> Unit) { Gap(8.dp); PrimaryButton("Save", Icons.Filled.Check, onClick = onClick) }
