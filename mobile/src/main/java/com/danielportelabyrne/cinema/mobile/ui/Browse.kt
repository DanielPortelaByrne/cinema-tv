package com.danielportelabyrne.cinema.mobile.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.Service
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.R
import com.danielportelabyrne.cinema.mobile.Screen

/** A pushed page's top bar: back, then its name. */
@Composable
fun PageHeader(model: Model, title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.ivory) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.ivory, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun TabHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.ivory, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun HomeScreen(model: Model) {
    val shelves = model.shelves
    val rows = model.rowsFor("home") { Catalog.homePage() }
    PullToRefreshBox(isRefreshing = model.loading, onRefresh = { model.refresh(force = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.cinema_mark), null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(32.dp))
                    Text("Cinema", style = MaterialTheme.typography.headlineMedium, color = Palette.ivory, modifier = Modifier.padding(start = 10.dp).weight(1f))
                }
            }
            if (model.offlineMode) item {
                Column(Modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Palette.surface).padding(16.dp)) {
                    Text("Can't reach your Jellyfin server", style = MaterialTheme.typography.titleMedium, color = Palette.ivory)
                    Text("Away from home? Your offline copies are under More → On this phone.", style = MaterialTheme.typography.bodyMedium, color = Palette.ivoryMuted)
                }
            }
            rows.firstOrNull()?.titles?.firstOrNull { it.backdropUrl != null }?.let { hero -> item { Hero(model, hero) } }
            if (shelves != null) {
                item { ItemRow(model, "Continue watching", shelves.resume, wide = true) }
                item { ItemRow(model, "Next up", shelves.nextUp, wide = true) }
            }
            item { Services(model) }
            if (shelves != null) {
                item { ItemRow(model, "Films in your library", shelves.films) }
                item { ItemRow(model, "Series in your library", shelves.series) }
            }
            items(rows, key = { it.spec.heading }) { TitleRow(model, it) }
        }
    }
}

@Composable
private fun Hero(model: Model, t: com.danielportelabyrne.cinema.data.movies.Title) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clickable { model.open(t) }) {
        RemoteImage(model, t.backdropUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(scrim))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(t.name, style = MaterialTheme.typography.displaySmall, color = Palette.ivory, maxLines = 2)
            Text(t.metaLine(), style = MaterialTheme.typography.bodyMedium, color = Palette.ivoryMuted)
            t.streamingLine()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.butter) }
        }
    }
}

@Composable
private fun Services(model: Model) {
    val services = model.settings.followedServices
    if (services.isEmpty()) return
    SectionTitle("Browse by service")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(services, key = { it.code }) { s -> ServiceChip(model, s) }
    }
}

@Composable
private fun ServiceChip(model: Model, s: Service) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.raised).clickable { model.push(Screen.ServicePage(s)) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        s.icon?.let { RemoteImage(model, "https://images.justwatch.com$it", Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))) }
        Text(s.label, style = MaterialTheme.typography.titleSmall, color = Palette.ivory, modifier = Modifier.padding(start = 10.dp, end = 4.dp))
    }
}

@Composable
fun ServiceScreen(model: Model, page: Screen.ServicePage) {
    val rows = model.rowsFor("svc:" + page.service.code) { Catalog.servicePage(page.service) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(model, page.service.label)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { items(rows, key = { it.spec.heading }) { TitleRow(model, it) } }
    }
}

@Composable
fun BrowseScreen(model: Model) {
    var which by rememberSaveable { mutableStateOf(0) }
    val rows = if (which == 0) model.rowsFor("films") { Catalog.filmsPage() } else model.rowsFor("series") { Catalog.seriesPage() }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Browse")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Films", "Series").forEachIndexed { i, label ->
                FilterChip(selected = which == i, onClick = { which = i }, label = { Text(label) }, colors = chipColors())
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Services(model) }
            items(rows, key = { it.spec.heading }) { TitleRow(model, it) }
        }
    }
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.selected, selectedLabelColor = Palette.ivory, labelColor = Palette.ivoryMuted)

@Composable
fun SearchScreen(model: Model) {
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { model.search(it) }
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Search")
        OutlinedTextField(
            value = model.query, onValueChange = { model.search(it) }, singleLine = true,
            placeholder = { Text("Films, series, people…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (model.query.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Filled.Clear, "Clear") }
                else IconButton(onClick = {
                    runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                        .onFailure { model.toast("Voice search isn't available on this phone") }
                }) { Icon(Icons.Filled.Mic, "Voice search") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (model.query.isBlank()) { Empty("Search your library and everything streaming in ${model.settings.region}."); return }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ItemRow(model, "In your library", model.searchLibrary) }
            items(model.searchRows, key = { it.spec.heading }) { TitleRow(model, it) }
        }
    }
}

@Composable
fun LibraryScreen(model: Model) {
    var which by rememberSaveable { mutableStateOf(0) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    val all = model.library
    val shown = remember(all, which, sortByName) {
        val f = when (which) { 0 -> all.filter { it.isMovie }; else -> all.filter { it.isSeries } }
        if (sortByName) f.sortedBy { Model.fold(it.name).removePrefix("the ") } else f.sortedByDescending { it.addedMs ?: 0L }
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader("My library") { IconButton(onClick = { model.refresh(force = true) }) { Icon(Icons.Filled.Refresh, "Refresh", tint = Palette.ivory) } }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(which == 0, { which = 0 }, { Text("Films") }, colors = chipColors())
            FilterChip(which == 1, { which = 1 }, { Text("Series") }, colors = chipColors())
            FilterChip(sortByName, { sortByName = !sortByName }, { Text(if (sortByName) "A–Z" else "Newest") }, colors = chipColors())
        }
        if (shown.isEmpty()) { Empty(if (model.client.isConnected) "Nothing here yet." else "Sign in to Jellyfin in More → Settings."); return }
        LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shown, key = { it.id }) { item ->
                PosterCard(model, model.posterOf(item), item.name, item.year?.toString(), width = 200.dp,
                    progress = com.danielportelabyrne.cinema.mobile.data.watchedFraction(item).takeIf { it > 0f }) { model.openItem(item) }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.PlayArrow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = Palette.butter, contentColor = Palette.espresso)) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 6.dp))
        Text(text)
    }
}
