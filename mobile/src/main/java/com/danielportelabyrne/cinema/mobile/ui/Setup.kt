package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.jellyfin.FoundServer
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinDiscovery
import com.danielportelabyrne.cinema.data.jellyfin.QuickConnectTicket
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.R
import kotlinx.coroutines.delay

private enum class Step { SERVER, SIGN_IN, SEERR, COUNTRY }

@Composable
fun SetupScreen(model: Model) {
    var step by rememberSaveable { mutableStateOf(if (model.client.hasServer) Step.SIGN_IN else Step.SERVER) }
    var jfUser by rememberSaveable { mutableStateOf("") }
    var jfPass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            Ribbon(RibbonShape.LOOP, Modifier.fillMaxSize(), strokeWidth = 30.dp)
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                CinemaMark(46.dp)
                Text("Cinema", style = MaterialTheme.typography.displaySmall, color = Palette.ivory, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Eyebrow(listOf("Welcome", when (step) { Step.SERVER -> "Your server"; Step.SIGN_IN -> "Sign in"; Step.SEERR -> "Requests"; Step.COUNTRY -> "Country" }), Modifier.padding(top = 12.dp))
        Gap(24.dp)
        when (step) {
            Step.SERVER -> ServerStep(model) { step = Step.SIGN_IN }
            Step.SIGN_IN -> SignInStep(model, jfUser, { jfUser = it }, jfPass, { jfPass = it }, onBack = { step = Step.SERVER }) { step = Step.SEERR }
            Step.SEERR -> SeerrForm(model, jfUser, jfPass, intro = true, onDone = { step = Step.COUNTRY })
            Step.COUNTRY -> CountryStep(model) { model.setupFinished() }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.ivory, modifier = Modifier.padding(top = 4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
}

@Composable
private fun ServerStep(model: Model, next: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("http://") }
    var found by remember { mutableStateOf<List<FoundServer>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { model.bg({ JellyfinDiscovery.find() }) { found = it.orEmpty() } }

    fun connect(a: String) {
        busy = true; error = null
        val base = a.trim().trimEnd('/')
        val hostPart = base.substringAfter("://").substringBefore('/')
        val tries = if (hostPart.contains(':') || base.startsWith("https://")) listOf(base) else listOf(base, "$base:8096")
        model.bg({ tries.firstOrNull { model.client.probe(it) } }) { ok ->
            busy = false
            if (ok == null) error = "Couldn't find Jellyfin at $base"
            else { model.client.setServer(ok); next() }
        }
    }

    Heading("Your Jellyfin server", "Cinema plays your Jellyfin library. Pick the server on this Wi-Fi, or type its address. Nothing about your server is built in.")
    when (val f = found) {
        null -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(120.dp)) { WaveLoader(thickness = 6.dp, amplitude = 4.dp, wavelength = 40.dp) }; Text("Looking on this network…", color = Palette.ivoryMuted) }
        else -> f.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { connect(s.address) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, null, tint = Palette.butter)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(s.name, color = Palette.ivory, style = MaterialTheme.typography.titleMedium)
                    Text(s.address, color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Gap(16.dp)
    OutlinedTextField(address, { address = it }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go))
    error?.let { Text(it, color = Palette.terracotta, modifier = Modifier.padding(top = 8.dp)) }
    Gap(16.dp)
    PrimaryButton(if (busy) "Connecting…" else "Connect", Icons.AutoMirrored.Filled.ArrowForward) { if (!busy) connect(address) }
    Text("Away from home, use the address you reach Jellyfin by from outside (for example its https address).", style = MaterialTheme.typography.bodySmall,
        color = Palette.ivoryMuted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SignInStep(model: Model, user: String, setUser: (String) -> Unit, pass: String, setPass: (String) -> Unit, onBack: () -> Unit, next: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ticket by remember { mutableStateOf<QuickConnectTicket?>(null) }

    ticket?.let { t ->
        LaunchedEffect(t) {
            while (true) {
                delay(2500)
                var done = false
                model.bg({ model.client.isApproved(t) && model.client.finishQuickConnect(t) }) { ok -> if (ok == true) { done = true; ticket = null; next() } }
                if (done) break
            }
        }
    }

    Heading("Sign in to Jellyfin", model.client.server)
    if (ticket != null) {
        Text("Quick Connect code", color = Palette.ivoryMuted)
        Text(ticket!!.code, style = MaterialTheme.typography.displaySmall, color = Palette.butter)
        Text("Approve it in Jellyfin on another device (Dashboard or your profile → Quick Connect). This page carries on by itself.",
            color = Palette.ivoryMuted, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { ticket = null }) { Text("Use a password instead") }
        return
    }
    OutlinedTextField(user, setUser, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Gap(8.dp)
    OutlinedTextField(pass, setPass, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    error?.let { Text(it, color = Palette.terracotta, modifier = Modifier.padding(top = 8.dp)) }
    Gap(16.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (busy) "Signing in…" else "Sign in", Icons.Filled.Login) {
            if (busy) return@PrimaryButton
            busy = true; error = null
            model.bg({ model.client.signInByName(user.trim(), pass) }) { err -> busy = false; if (err == null) next() else error = err }
        }
        OutlinedButton(onClick = {
            model.bg({ var e: String? = null; model.client.startQuickConnect(model.client.server) { e = it } to e }) { r ->
                if (r?.first != null) ticket = r.first else error = r?.second ?: "Quick Connect didn't start"
            }
        }) { Text("Quick Connect", color = Palette.ivory) }
    }
    TextButton(onClick = onBack) { Text("Use another server") }
}

/** Seerr's address and sign-in: in setup ([intro]) and in Settings. */
@Composable
fun SeerrForm(model: Model, jfUser: String = "", jfPass: String = "", intro: Boolean = false, onDone: () -> Unit = {}) {
    var address by rememberSaveable { mutableStateOf(model.seerr.server.ifBlank { model.client.server.substringBeforeLast(':').let { "$it:5055" } }) }
    var user by rememberSaveable { mutableStateOf(jfUser) }
    var pass by remember { mutableStateOf(jfPass) }
    var local by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (intro) Heading("Requests (optional)", "With Seerr (Jellyseerr or Overseerr) you can request films and series you don't have yet, and see them download.")
    if (model.seerr.isSignedIn && !intro) {
        Text("Signed in to ${model.seerr.server}", color = Palette.ivory)
        TextButton(onClick = { model.seerr.signOut(); model.toast("Signed out of Seerr") }) { Text("Sign out of Seerr", color = Palette.terracotta) }
        return
    }
    OutlinedTextField(address, { address = it }, label = { Text("Seerr address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    Gap(8.dp)
    OutlinedTextField(user, { user = it }, label = { Text(if (local) "Seerr email" else "Jellyfin username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Gap(8.dp)
    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    TextButton(onClick = { local = !local }) { Text(if (local) "Use my Jellyfin account" else "Use a Seerr email account") }
    error?.let { Text(it, color = Palette.terracotta) }
    Gap(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (busy) "Signing in…" else "Sign in", Icons.Filled.Check) {
            if (busy) return@PrimaryButton
            busy = true; error = null
            val a = address.trim().trimEnd('/')
            model.bg({
                if (!model.seerr.probe(a)) "Couldn't find Seerr at $a"
                else { model.seerr.setServer(a); if (local) model.seerr.signInLocal(user.trim(), pass) else model.seerr.signIn(user.trim(), pass) }
            }) { err -> busy = false; if (err == null) { model.toast("Signed in to Seerr"); onDone() } else error = err }
        }
        if (intro) OutlinedButton(onClick = onDone) { Text("Skip", color = Palette.ivory) }
    }
}

@Composable
private fun CountryStep(model: Model, done: () -> Unit) {
    var region by rememberSaveable { mutableStateOf(model.settings.region) }
    Heading("Your country", "What is streaming where comes from JustWatch for this country. Use its two-letter code (GB, IE, US, PT…).")
    OutlinedTextField(region, { region = it.take(2).uppercase() }, label = { Text("Country") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Gap(16.dp)
    PrimaryButton("Start watching", Icons.Filled.Check) {
        if (region.length == 2) { model.settings.region = region; Catalog.country = region }
        done()
    }
}
