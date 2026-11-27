package com.danielportelabyrne.cinema.mobile.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.LayoutPage
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.R
import com.danielportelabyrne.cinema.mobile.Screen
import com.danielportelabyrne.cinema.mobile.Tab
import kotlinx.coroutines.delay

@Composable
fun CinemaApp(model: Model) {
    var splash by rememberSaveable { mutableStateOf(true) }
    val top = model.stack.lastOrNull()
    AskPermissions(model)
    // While Cinema is open it looks for requested titles every minute; ServerWatchJob takes over when it is not.
    val waiting by remember { derivedStateOf { model.waiting.items.isNotEmpty() } }
    LaunchedEffect(waiting) { while (waiting) { delay(60_000); model.checkWaiting() } }
    val ground = when {
        model.needsSetup -> Grounds.HOME
        top is Screen.TitlePage -> if (top.title.kind == Kind.SHOW) Grounds.SERIES else Grounds.FILMS
        top is Screen.Downloads || top is Screen.Offline || top is Screen.Requests -> Grounds.DOWNLOADS
        top is Screen.Settings || top is Screen.Remote -> Grounds.SETTINGS
        top is Screen.ServicePage || top is Screen.ChannelPage || top is Screen.GenrePage -> Grounds.HOME
        top is Screen.LayoutEditor -> Grounds.SETTINGS
        else -> when (model.tab) {
            Tab.HOME -> Grounds.HOME
            Tab.BROWSE -> when (model.browsePage) { LayoutPage.SERIES -> Grounds.SERIES; LayoutPage.DOCS -> Grounds.HOME; else -> Grounds.FILMS }
            Tab.SEARCH -> Grounds.SEARCH
            Tab.LIBRARY -> Grounds.LIBRARY
            Tab.MORE -> Grounds.SETTINGS
        }
    }
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Ground(ground)
        if (model.needsSetup) SetupScreen(model)
        else Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { NavBar(model) },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = top) {
                    null -> when (model.tab) {
                        Tab.HOME -> HomeScreen(model)
                        Tab.BROWSE -> BrowseScreen(model)
                        Tab.SEARCH -> SearchScreen(model)
                        Tab.LIBRARY -> LibraryScreen(model)
                        Tab.MORE -> MoreScreen(model)
                    }
                    is Screen.TitlePage -> TitleScreen(model, s)
                    is Screen.ServicePage -> ServiceScreen(model, s)
                    is Screen.ChannelPage -> SpecPage(model, "chan:" + s.channel.id, s.channel.name, listOf("TV channel")) { Catalog.channelPage(s.channel) }
                    is Screen.GenrePage -> SpecPage(model, "genre:" + s.genre.code, s.genre.label, listOf("Genre")) { Catalog.genrePage(s.genre) }
                    is Screen.LayoutEditor -> LayoutEditorScreen(model, s.page)
                    Screen.Downloads -> DownloadsScreen(model)
                    Screen.Offline -> OfflineScreen(model)
                    Screen.Remote -> RemoteScreen(model)
                    Screen.Settings -> SettingsScreen(model)
                    Screen.Requests -> RequestsScreen(model)
                }
            }
        }
        if (!model.needsSetup) RequestDialog(model)
        Toast(model, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp))
        model.streaming?.let { Banner(it, Modifier.align(Alignment.Center)) }
        if (splash) Splash { splash = false }
    }
}

/**
 * Android's own questions, one after the other so neither hides the other: showing progress as a notification, the first
 * time something is to be downloaded; then, when a request is to be saved later, running in the background.
 */
@SuppressLint("BatteryLife")
@Composable
private fun AskPermissions(model: Model) {
    val context = LocalContext.current
    val background = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    fun askBackground() {
        if (!model.wantsBackground) return
        model.wantsBackground = false
        if (context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != false) return
        runCatching { background.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askBackground() }
    var asked by rememberSaveable { mutableStateOf(false) }
    val downloading by remember { derivedStateOf { model.offline.next() != null || model.waiting.items.isNotEmpty() } }
    LaunchedEffect(downloading, model.wantsBackground) {
        val needed = Build.VERSION.SDK_INT >= 33 && downloading && !asked &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) { asked = true; notify.launch(Manifest.permission.POST_NOTIFICATIONS) } else askBackground()
    }
}

/** The bottom bar: glass over the ground, the current tab marked with a small stripe pill and lit in cream. */
@Composable
private fun NavBar(model: Model) {
    Column(Modifier.fillMaxWidth().background(Palette.railFill).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Tab.entries.forEach { t ->
                val on = model.tab == t && model.stack.isEmpty()
                Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { model.selectTab(t) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (on) StripeMark(Modifier.size(22.dp, 4.dp), radius = 2.dp) else Box(Modifier.size(22.dp, 4.dp))
                    Icon(painterResource(t.icon()), null, Modifier.padding(top = 5.dp).size(24.dp), tint = if (on) Palette.ivory else Color(0xFFA99B89))
                    Text(t.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Palette.ivory else Color(0xFFA99B89), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> R.drawable.ic_cin_home; Tab.BROWSE -> R.drawable.ic_cin_film; Tab.SEARCH -> R.drawable.ic_cin_search
    Tab.LIBRARY -> R.drawable.ic_cin_lib; Tab.MORE -> R.drawable.ic_gear
}

@Composable
private fun Toast(model: Model, modifier: Modifier) {
    AnimatedVisibility(model.toast != null, modifier, enter = fadeIn(), exit = fadeOut()) { Banner(model.toast.orEmpty(), Modifier) }
}

@Composable
private fun Banner(text: String, modifier: Modifier) {
    Text(text, color = Palette.ivory, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        modifier = modifier.padding(horizontal = 24.dp).background(Color(0xF0241A13), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .drawBehind { }.padding(horizontal = 18.dp, vertical = 12.dp))
}
