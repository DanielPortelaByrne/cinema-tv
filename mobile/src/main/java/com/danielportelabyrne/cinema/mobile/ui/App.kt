package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Screen
import com.danielportelabyrne.cinema.mobile.Tab

@Composable
fun CinemaApp(model: Model) {
    Box(Modifier.fillMaxSize().background(Palette.espresso)) {
        if (model.needsSetup) SetupScreen(model)
        else Scaffold(
            containerColor = Palette.espresso,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = Palette.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = model.tab == t, onClick = { model.selectTab(t) },
                            icon = { Icon(t.icon(), contentDescription = null) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Palette.selected, selectedIconColor = Palette.butter,
                                selectedTextColor = Palette.ivory, unselectedIconColor = Palette.ivoryMuted, unselectedTextColor = Palette.ivoryMuted),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = model.stack.lastOrNull()) {
                    null -> when (model.tab) {
                        Tab.HOME -> HomeScreen(model)
                        Tab.BROWSE -> BrowseScreen(model)
                        Tab.SEARCH -> SearchScreen(model)
                        Tab.LIBRARY -> LibraryScreen(model)
                        Tab.MORE -> MoreScreen(model)
                    }
                    is Screen.TitlePage -> TitleScreen(model, s)
                    is Screen.ServicePage -> ServiceScreen(model, s)
                    Screen.Downloads -> DownloadsScreen(model)
                    Screen.Offline -> OfflineScreen(model)
                    Screen.Remote -> RemoteScreen(model)
                    Screen.Settings -> SettingsScreen(model)
                    Screen.Requests -> RequestsScreen(model)
                }
            }
        }
        Toast(model, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        model.streaming?.let { Toast(it, Modifier.align(Alignment.Center)) }
    }
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> Icons.Filled.Home; Tab.BROWSE -> Icons.Filled.Explore; Tab.SEARCH -> Icons.Filled.Search
    Tab.LIBRARY -> Icons.Filled.VideoLibrary; Tab.MORE -> Icons.Filled.MoreHoriz
}

@Composable
private fun Toast(model: Model, modifier: Modifier) {
    AnimatedVisibility(model.toast != null, modifier, enter = fadeIn(), exit = fadeOut()) { Toast(model.toast.orEmpty(), Modifier) }
}

@Composable
private fun Toast(text: String, modifier: Modifier) {
    Text(text, color = Palette.ivory, style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp)).background(Palette.selected).padding(horizontal = 16.dp, vertical = 12.dp))
}
