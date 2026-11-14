package com.danielportelabyrne.cinema.mobile.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.remote.RemoteProtocol.Key
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.remote.Tv

@Composable
fun RemoteScreen(model: Model) {
    var tv by remember { mutableStateOf(model.remote.current?.takeIf { model.remote.isPaired(it) }) }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(model, tv?.name ?: "TV remote") {
            if (tv != null) TextButton(onClick = { tv = null }) { Text("Change TV") }
        }
        val t = tv
        if (t == null) PickTv(model) { tv = it } else Pad(model, t)
    }
}

@Composable
private fun PickTv(model: Model, paired: (Tv) -> Unit) {
    var tvs by remember { mutableStateOf<List<Tv>?>(null) }
    var pairing by remember { mutableStateOf<Tv?>(null) }
    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { model.bg({ model.remote.discover() }) { tvs = it.orEmpty() } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        val p = pairing
        if (p != null) {
            Text("Enter the code on ${p.name}", style = MaterialTheme.typography.titleLarge, color = Palette.ivory)
            Gap()
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(4) }, label = { Text("Four-digit code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Gap()
            PrimaryButton("Pair", null) {
                model.bg({ model.remote.confirm(p, code) }) { ok -> if (ok == true) paired(p) else model.toast("That code didn't work. Try again.") }
            }
            TextButton(onClick = { pairing = null; code = "" }) { Text("Cancel") }
            return@Column
        }
        Text("Open Cinema on the TV, then choose it here. The TV shows a code to type in.", color = Palette.ivoryMuted)
        Gap()
        val list = tvs
        if (list == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  Looking for TVs…", color = Palette.ivoryMuted) }
        else if (list.isEmpty()) Text("No TV running Cinema answered. Is Cinema open on it, and is it a recent build?", color = Palette.ivoryMuted)
        list.orEmpty().forEach { tv ->
            Row(Modifier.fillMaxWidth().clickable {
                if (model.remote.isPaired(tv)) { model.remote.current = tv; paired(tv) }
                else model.bg({ model.remote.startPairing(tv) }) { ok -> if (ok == true) pairing = tv else model.toast("Open Cinema on ${tv.name} first") }
            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tv, null, tint = Palette.butter)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(tv.name, color = Palette.ivory, style = MaterialTheme.typography.titleMedium)
                    Text(tv.host + if (model.remote.isPaired(tv)) " · paired" else "", color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Gap(24.dp)
        OutlinedTextField(manual, { manual = it }, label = { Text("Or the TV's IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        TextButton(onClick = {
            val tv = Tv(manual.trim(), manual.trim())
            model.bg({ model.remote.startPairing(tv) }) { ok -> if (ok == true) pairing = tv else model.toast("No answer from ${tv.host}") }
        }) { Text("Pair with this address") }
    }
}

@Composable
private fun Pad(model: Model, tv: Tv) {
    val view = LocalView.current
    var text by remember { mutableStateOf("") }
    fun press(k: Key) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        model.bg({ model.remote.press(tv, k) }) { ok -> if (ok != true) model.toast("${tv.name} didn't answer. Is Cinema open on it?") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Round(Icons.AutoMirrored.Filled.ArrowBack, "Back") { press(Key.BACK) }
            Round(Icons.Filled.Home, "Home") { press(Key.HOME) }
            Round(Icons.Filled.Menu, "Menu") { press(Key.MENU) }
            Round(Icons.Filled.Search, "Search") { press(Key.SEARCH) }
        }
        Gap(28.dp)
        Box(Modifier.size(260.dp).clip(CircleShape).background(Palette.surface)) {
            Arrow(Icons.Filled.KeyboardArrowUp, Modifier.align(Alignment.TopCenter)) { press(Key.UP) }
            Arrow(Icons.Filled.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter)) { press(Key.DOWN) }
            Arrow(Icons.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { press(Key.LEFT) }
            Arrow(Icons.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { press(Key.RIGHT) }
            Box(Modifier.align(Alignment.Center).size(100.dp).clip(CircleShape).background(Palette.selected).clickable { press(Key.OK) }, contentAlignment = Alignment.Center) {
                Text("OK", style = MaterialTheme.typography.titleLarge, color = Palette.ivory)
            }
        }
        Gap(28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Round(Icons.Filled.FastRewind, "Rewind") { press(Key.REWIND) }
            Round(Icons.Filled.PlayArrow, "Play or pause", 72.dp) { press(Key.PLAY_PAUSE) }
            Round(Icons.Filled.FastForward, "Fast forward") { press(Key.FORWARD) }
        }
        Gap(28.dp)
        OutlinedTextField(text, { text = it }, label = { Text("Type on the TV") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { val s = text; text = ""; model.bg({ model.remote.type(tv, s) }) }),
            trailingIcon = { IconButton(onClick = { val s = text; text = ""; model.bg({ model.remote.type(tv, s) }) }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") } })
        Text("Types into the box that has focus on the TV.", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Round(icon: ImageVector, desc: String, size: Dp = 56.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(Palette.raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Palette.ivory)
    }
}

@Composable
private fun Arrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Palette.ivory, modifier = Modifier.size(40.dp))
    }
}
