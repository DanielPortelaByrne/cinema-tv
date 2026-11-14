package com.danielportelabyrne.cinema.mobile.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.LayoutPage
import com.danielportelabyrne.cinema.data.movies.Service
import com.danielportelabyrne.cinema.data.movies.ShelfLooks
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Row as CatalogueRow
import com.danielportelabyrne.cinema.mobile.Screen
import com.danielportelabyrne.cinema.mobile.data.watchedFraction
import kotlinx.coroutines.delay

/** Glass text fields: cream text on a few percent of cream, a hairline edge that turns saffron when typing. */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.butter, unfocusedBorderColor = Palette.hairline,
    focusedContainerColor = Palette.glass, unfocusedContainerColor = Palette.glass,
    focusedTextColor = Palette.ivory, unfocusedTextColor = Palette.ivory, cursorColor = Palette.butter,
    focusedLabelColor = Palette.butter, unfocusedLabelColor = Palette.ivoryMuted,
    focusedPlaceholderColor = Palette.dim, unfocusedPlaceholderColor = Palette.dim,
)

/** A pushed page's top bar: back, then its name in Fraunces. */
@Composable
fun PageHeader(model: Model, title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.ivory) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.ivory, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A tab's header: the section's ribbon looping in from the right, an eyebrow, and a big Fraunces title. */
@Composable
fun TabHeader(title: String, eyebrow: List<String> = emptyList(), ribbon: RibbonShape = RibbonShape.LOOP, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth()) {
        Ribbon(ribbon, Modifier.fillMaxWidth().height(190.dp), strokeWidth = 30.dp, opacity = 0.85f)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (eyebrow.isNotEmpty()) Eyebrow(eyebrow, Modifier.padding(bottom = 6.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.ivory, maxLines = 1)
            }
            action?.invoke()
        }
    }
}

// --- Home ---------------------------------------------------------------------------------------

@Composable
fun HomeScreen(model: Model) {
    val rows = catalogueRows(model, LayoutPage.HOME)
    PullToRefreshBox(isRefreshing = model.loading, onRefresh = { model.refresh(force = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Hero(model, rows) }
            if (model.offlineMode) item {
                Column(Modifier.padding(16.dp).fillMaxWidth().glass(18.dp).padding(16.dp)) {
                    Text("Can't reach your Jellyfin server", style = MaterialTheme.typography.titleMedium, color = Palette.ivory)
                    Text("Away from home? Your offline copies are under More → On this phone.", style = MaterialTheme.typography.bodyMedium, color = Palette.ivoryMuted)
                }
            }
            layoutSections(model, LayoutPage.HOME)
        }
    }
}

/**
 * The landing: the featured titles turning over a full-bleed picture, the mark
 * and name at the top, and the title's words over a dark that falls away like weather.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(model: Model, rows: List<CatalogueRow>) {
    // Each pick keeps the words for its eyebrow: "Featured · #1 in Top 10 films today", or "Featured · Film".
    val picks = remember(rows.map { it.titles }) {
        rows.flatMap { r ->
            (r.titles ?: emptyList()).withIndex().filter { it.value.backdropUrl != null }.take(2)
                .map { (i, t) -> t to if (r.spec.ranked) "#${i + 1} in ${r.spec.heading}" else t.kindLabel() }
        }.distinctBy { it.first.key }.take(5)
    }
    Box(Modifier.fillMaxWidth().height(470.dp)) {
        if (picks.isEmpty()) {
            Box(Modifier.fillMaxSize()) { Ribbon(RibbonShape.STEP, Modifier.fillMaxSize(), strokeWidth = 36.dp, fadeToLeft = false) }
        } else {
            val pager = rememberPagerState { picks.size }
            LaunchedEffect(picks.size) { while (picks.size > 1) { delay(7000); pager.animateScrollToPage((pager.currentPage + 1) % picks.size) } }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val (t, label) = picks[page]
                Box(Modifier.fillMaxSize().clickable { model.open(t) }) {
                    RemoteImage(model, t.backdropUrl, Modifier.fillMaxSize())
                    // The dark that lets the words read: deepest at the bottom left, thinning up and across.
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.28f to Color.Transparent, 0.62f to Color(0x990B0807), 1f to Palette.bg)))
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(0f to Color(0xCC0B0807), 1f to Color.Transparent, center = androidx.compose.ui.geometry.Offset(0f, 1400f), radius = 1300f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 46.dp)) {
                        Eyebrow(listOf("Featured", label))
                        Text(t.name, style = MaterialTheme.typography.displayMedium, color = Palette.ivory, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
                        MetaLine(t.age, listOfNotNull(t.year?.toString(), t.genres.firstOrNull(), t.runtimeMin?.let { "${it / 60}h ${it % 60}m" }), t.imdbScore, Modifier.padding(top = 10.dp))
                        t.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.reading, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CreamButton("View", Icons.Filled.PlayArrow) { model.open(t) }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(picks.size) { i ->
                    if (i == pager.currentPage) StripeMark(Modifier.size(30.dp, 5.dp), radius = 3.dp)
                    else Box(Modifier.size(10.dp, 5.dp).background(Palette.hairline, RoundedCornerShape(3.dp)))
                }
            }
        }
        // The brand's corner: the mark and name.
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 18.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CinemaMark(30.dp)
            Text("Cinema", style = TextStyle(fontFamily = fraunces(500, 28f), fontSize = 24.sp, letterSpacing = (-0.5).sp, shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA000000), blurRadius = 16f)),
                color = Palette.ivory, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

private fun Title.kindLabel() = if (kind == com.danielportelabyrne.cinema.data.movies.Kind.SHOW) "Series" else "Film"

@Composable
fun ServiceScreen(model: Model, page: Screen.ServicePage) =
    SpecPage(model, "svc:" + page.service.code, page.service.label, listOf("On the service")) { Catalog.servicePage(page.service) }

@Composable
fun BrowseScreen(model: Model) {
    val page = model.browsePage
    Column(Modifier.fillMaxSize()) {
        TabHeader(page.label, listOf("Browse", model.settings.region))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LayoutPage.FILMS, LayoutPage.SERIES, LayoutPage.DOCS).forEach { p -> ChoicePill(p.label, page == p) { model.browsePage = p } }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { layoutSections(model, page) }
    }
}

@Composable
fun SearchScreen(model: Model) {
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { model.search(it) }
    }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Search", listOf("Your library", "Everywhere"), RibbonShape.SWOOP)
        OutlinedTextField(
            value = model.query, onValueChange = { model.search(it) }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(50),
            placeholder = { Text("Films, series, people…") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Palette.ivoryMuted) },
            trailingIcon = {
                if (model.query.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Filled.Clear, "Clear", tint = Palette.ivoryMuted) }
                else IconButton(onClick = {
                    runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                        .onFailure { model.toast("Voice search isn't available on this phone") }
                }) { Icon(Icons.Filled.Mic, "Voice search", tint = Palette.butter) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (model.query.isBlank()) { Empty("Search your library and everything streaming in ${model.settings.region}."); return }
        val plan = ShelfLooks.plan(model.searchRows.map { it.spec }).map { (spec, look) -> model.searchRows.first { it.spec === spec } to look }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ItemRow(model, "In your library", model.searchLibrary) }
            items(plan, key = { it.first.spec.heading }) { (r, l) -> ShelfRow(model, r, l) }
        }
    }
}

@Composable
fun LibraryScreen(model: Model) {
    var rowsView by remember { mutableStateOf(true) }
    var which by remember { mutableStateOf(0) }
    val all = model.library
    val order = model.libraryOrder
    val shown = remember(all, which, order) { order.sort(if (which == 0) all.filter { it.isMovie } else all.filter { it.isSeries }) }
    Column(Modifier.fillMaxSize()) {
        TabHeader("My library", listOf("${all.size} titles", order.label), RibbonShape.SWOOP) { IconButton(onClick = { model.refresh(force = true) }) { Icon(Icons.Filled.Refresh, "Refresh", tint = Palette.ivory) } }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Rows", rowsView) { rowsView = true }
            ChoicePill("All titles", !rowsView) { rowsView = false }
            if (!rowsView) {
                ChoicePill("Films", which == 0) { which = 0 }
                ChoicePill("Series", which == 1) { which = 1 }
                // The same setting as Settings, Library order: tapping moves to the next one.
                val entries = com.danielportelabyrne.cinema.data.settings.LibraryOrder.entries
                ChoicePill(order.label, false) { model.chooseLibraryOrder(entries[(entries.indexOf(order) + 1) % entries.size]) }
            }
        }
        if (rowsView) { LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { layoutSections(model, LayoutPage.LIBRARY) }; return }
        if (shown.isEmpty()) { Empty(if (model.client.isConnected) "Nothing here yet." else "Sign in to Jellyfin in More → Settings."); return }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shown, key = { it.id }) { item ->
                PosterCard(model, model.posterOf(item), item.name, item.year?.toString(), width = 200.dp,
                    progress = watchedFraction(item).takeIf { it > 0f }) { model.openItem(item) }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.PlayArrow, modifier: Modifier = Modifier, onClick: () -> Unit) =
    CreamButton(text, icon, modifier, onClick)
