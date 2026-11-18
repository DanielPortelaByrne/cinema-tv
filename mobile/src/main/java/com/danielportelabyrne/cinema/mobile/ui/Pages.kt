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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.LayoutPage
import com.danielportelabyrne.cinema.data.movies.Mix
import com.danielportelabyrne.cinema.data.movies.Mixes
import com.danielportelabyrne.cinema.data.movies.PageLayouts
import com.danielportelabyrne.cinema.data.movies.Picks
import com.danielportelabyrne.cinema.data.movies.Section
import com.danielportelabyrne.cinema.data.movies.SectionOption
import com.danielportelabyrne.cinema.data.movies.SectionType
import com.danielportelabyrne.cinema.data.movies.ShelfLook
import com.danielportelabyrne.cinema.data.movies.ShelfSpec
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.data.stremio.StremioApi
import com.danielportelabyrne.cinema.data.stremio.StremioFilm
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Row as CatalogueRow
import com.danielportelabyrne.cinema.mobile.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The rows on a page take turns through these looks, as on the TV; a Top 10 keeps its numbers. */
private val CYCLE = listOf(ShelfLook.SPOTLIGHT, ShelfLook.STILLS, ShelfLook.POSTERS, ShelfLook.GRID, ShelfLook.SMALL_STILLS)

/** A row of titles we already hold (the library, favourites, the watchlist), drawn by the same row looks as the catalogue's. */
private fun held(heading: String, titles: List<Title>) = CatalogueRow(ShelfSpec(heading, emptyList(), emptyList())).also { it.titles = titles }

/** The catalogue rows a page's layout asks for, in order: the featured titles come from these. */
fun catalogueRows(model: Model, page: LayoutPage): List<CatalogueRow> =
    model.layoutOf(page).filter { it.type.catalogue }.flatMap { s -> model.rowsFor("sec:" + s.id) { PageLayouts.specs(s) } }

/**
 * Draws [page] as the owner laid it out (on the TV or on this phone: the saved layout is the same one): each section in
 * order, the catalogue's rows from JustWatch and the app's own rows (Continue watching, favourites, the library, the
 * service cards...) where they were placed. The page ends with the way to change it.
 */
fun LazyListScope.layoutSections(model: Model, page: LayoutPage) {
    var n = 0
    fun look(spec: ShelfSpec) = if (spec.ranked) ShelfLook.RANKED else CYCLE[n++ % CYCLE.size]
    fun library(key: String, heading: String, items: List<JellyfinItem>) {
        if (items.isEmpty()) return
        val row = held(heading, items.map { model.libraryTitle(it) })
        val l = CYCLE[n++ % CYCLE.size]
        item(key = key) { ShelfRow(model, row, l) }
    }
    val ordered = model.libraryOrder.sort(model.library)
    for (s in model.layoutOf(page)) {
        if (s.type.catalogue) {
            model.rowsFor("sec:" + s.id) { PageLayouts.specs(s) }.forEach { r -> val l = look(r.spec); item(key = s.id + "/" + r.spec.heading) { ShelfRow(model, r, l) } }
            continue
        }
        when (s.type) {
            SectionType.CONTINUE -> model.shelves?.let { sh ->
                if (sh.resume.isNotEmpty()) item(key = s.id) { ItemRow(model, "Continue watching", sh.resume, wide = true) }
                if (sh.nextUp.isNotEmpty()) item(key = s.id + "/next") { ItemRow(model, "Next up", sh.nextUp, wide = true) }
            }
            SectionType.FAVOURITES -> {
                val favs = model.favourites.list(model.client.ownerScope)
                if (favs.isNotEmpty()) {
                    val row = held("Your favourites", favs.map { f ->
                        Title(key = "fav:" + (f.tmdbId ?: f.imdbId ?: f.jellyfinId ?: f.name), kind = f.kind, name = f.name, year = f.year, overview = null, runtimeMin = null, genres = emptyList(),
                            age = null, imdbScore = null, posterUrl = f.poster, backdropUrl = f.backdrop, imdbId = f.imdbId, tmdbId = f.tmdbId)
                    })
                    val l = CYCLE[n++ % CYCLE.size]
                    item(key = s.id) { ShelfRow(model, row, l) }
                }
            }
            SectionType.RECENT -> library(s.id, "Recently watched", model.library.filter { (it.playedMs ?: 0L) > 0L }.sortedByDescending { it.playedMs }.take(18))
            SectionType.PICKS -> item(key = s.id) { PicksSection(model) }
            SectionType.SERVICES -> item(key = s.id) { Services(model) }
            SectionType.CHANNELS -> item(key = s.id) { Channels(model) }
            SectionType.GENRE_TILES -> item(key = s.id) { GenreTiles(model) }
            SectionType.MY_LIBRARY -> library(s.id, "My library", ordered.take(18))
            SectionType.WATCHLIST -> {
                val want = model.settings.watchlist.filter { f -> when (s.arg) { "films" -> !f.isSeries; "series" -> f.isSeries; else -> true } }
                if (want.isNotEmpty()) {
                    val row = held(when (s.arg) { "films" -> "Films to watch"; "series" -> "Series to watch"; else -> "Watchlist" },
                        want.map { f -> StremioApi.bare(StremioFilm(f.imdbId, f.name, f.poster, f.year, false, "")).let { if (f.isSeries) it.copy(kind = Kind.SHOW) else it } })
                    val l = CYCLE[n++ % CYCLE.size]
                    item(key = s.id) { ShelfRow(model, row, l) }
                }
            }
            SectionType.LIBRARY_LATEST -> library(s.id, model.libraryOrder.label, ordered.take(20))
            SectionType.LIBRARY_FILMS -> library(s.id, "Your Films", ordered.filter { it.isMovie })
            SectionType.LIBRARY_SERIES -> library(s.id, "Your Series", ordered.filter { it.isSeries })
            SectionType.LIBRARY_DOCS -> library(s.id, "Your Documentaries", ordered.filter { i -> i.genres.any { it.startsWith("documentar", ignoreCase = true) } })
            SectionType.LIBRARY_GENRES -> ordered.flatMap { i -> i.genres.map { g -> g to i } }.groupBy({ it.first }, { it.second })
                .filterValues { it.size >= 2 }.entries.sortedByDescending { it.value.size }.take(6).forEach { (g, list) -> library(s.id + "/" + g, "Your $g", list) }
            else -> Unit
        }
    }
    item(key = "customise") { CustomiseCard(model, page) }
}

@Composable
private fun CustomiseCard(model: Model, page: LayoutPage) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 28.dp).fillMaxWidth().glass(20.dp).clip(RoundedCornerShape(20.dp)).clickable { model.push(Screen.LayoutEditor(page)) }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Customise ${page.label}", style = MaterialTheme.typography.titleMedium, color = Palette.ivory)
            Text("Add, remove and reorder its rows", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.butter)
    }
}

// --- The app's own rows -------------------------------------------------------------------------

@Composable
private fun PicksSection(model: Model) {
    val picks by produceState<List<com.danielportelabyrne.cinema.data.movies.Pick>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { model.picks.saved() }.getOrNull().orEmpty() }.takeIf { it.isNotEmpty() }
        withContext(Dispatchers.IO) { runCatching { model.picks.fresh() }.getOrNull() }?.let { value = it }
    }
    val list = picks ?: return
    if (list.isEmpty()) return
    val titles = list.map { p ->
        val s = p.suggestion
        Title(key = "tmdb:" + Picks.key(s.kind, s.tmdbId), kind = s.kind, name = s.name, year = s.year, overview = s.overview, runtimeMin = null, genres = s.genres, age = null, imdbScore = null,
            posterUrl = s.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }, backdropUrl = s.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }, imdbId = null, tmdbId = s.tmdbId.toString())
    }
    ShelfRow(model, held("Picked for you", titles), ShelfLook.POSTERS)
}

@Composable
fun Services(model: Model) {
    val services = model.settings.followedServices
    if (services.isEmpty()) return
    RowTitle("Browse by service")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(services, key = { it.code }) { s ->
            Row(Modifier.glass(18.dp).clip(RoundedCornerShape(18.dp)).clickable { model.push(Screen.ServicePage(s)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                s.icon?.let { RemoteImage(model, "https://images.justwatch.com$it", Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) }
                Text(s.label, style = MaterialTheme.typography.titleSmall, color = Palette.ivory, modifier = Modifier.padding(start = 10.dp, end = 6.dp))
            }
        }
    }
}

@Composable
private fun Channels(model: Model) {
    val channels = model.settings.followedChannels
    if (channels.isEmpty()) return
    RowTitle("Browse by channel")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(channels, key = { it.id }) { c ->
            Text(c.name, style = MaterialTheme.typography.titleSmall, color = Palette.ivory, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.glass(18.dp).clip(RoundedCornerShape(18.dp)).clickable { model.push(Screen.ChannelPage(c)) }.padding(horizontal = 16.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun GenreTiles(model: Model) {
    RowTitle("Browse by genre")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Catalog.GENRES, key = { it.code }) { g ->
            Row(Modifier.glass(18.dp).clip(RoundedCornerShape(18.dp)).clickable { model.push(Screen.GenrePage(g)) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                StripeMark(Modifier.padding(end = 10.dp).size(8.dp, 18.dp), horizontal = false, radius = 2.dp)
                Text(g.label, style = MaterialTheme.typography.titleSmall, color = Palette.ivory)
            }
        }
    }
}

/** A page of catalogue rows asked for by something other than a layout: a service, a channel, a genre. */
@Composable
fun SpecPage(model: Model, key: String, title: String, eyebrow: List<String>, specs: () -> List<ShelfSpec>) {
    val rows = model.rowsFor(key, specs)
    var n = 0
    Column(Modifier.fillMaxSize()) {
        TabHeader(title, eyebrow, RibbonShape.SWOOP)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, key = { it.spec.heading }) { r -> ShelfRow(model, r, if (r.spec.ranked) ShelfLook.RANKED else CYCLE[n++ % CYCLE.size]) }
        }
    }
}

// --- The editor ---------------------------------------------------------------------------------

/**
 * Customise a page: its rows in order, each with up, down and remove; a way to add any row the TV can offer (found by
 * typing: "sports documentaries", "top 10 on netflix"), and back to the default. Every change is saved at once, to the same
 * place the TV keeps its layouts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutEditorScreen(model: Model, page: LayoutPage) {
    var list by remember { mutableStateOf(model.layoutOf(page)) }
    var adding by remember { mutableStateOf(false) }
    /** The row being built: its place in the list (-1 when it is new) and the parts chosen so far. */
    var building by remember { mutableStateOf<Pair<Int, Mix>?>(null) }
    fun commit(next: List<Section>) { list = next; model.saveLayout(page, next) }
    val details = remember { PageLayouts.options().associate { it.section.id to it.detail } }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Customise ${page.label}", listOf("Rows", "${list.size}"), RibbonShape.LOOP)
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamButton("Add a row", Icons.Filled.Add) { adding = true }
            GlassButton("Build a row", Icons.Filled.Edit) { building = -1 to Mix() }
            GlassButton("Default", Icons.Filled.Replay) { commit(PageLayouts.defaults(page)); model.toast("${page.label} is back to its default rows") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).glass(16.dp).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(PageLayouts.label(s), color = Palette.ivory, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        details[s.id]?.let { Text(it, color = Palette.dim, style = MaterialTheme.typography.bodySmall) }
                    }
                    PageLayouts.asMix(s)?.let { m -> IconButton(onClick = { building = i to m }) { Icon(Icons.Filled.Edit, "Edit ${PageLayouts.label(s)}", tint = Palette.ivory) } }
                    IconButton(onClick = { if (i > 0) commit(list.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = if (i > 0) Palette.ivory else Palette.dim)
                    }
                    IconButton(onClick = { if (i < list.lastIndex) commit(list.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < list.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = if (i < list.lastIndex) Palette.ivory else Palette.dim)
                    }
                    IconButton(onClick = { commit(list.filterNot { it.id == s.id }) }) { Icon(Icons.Filled.Close, "Remove", tint = Palette.terracotta) }
                }
            }
            if (list.isEmpty()) item { Text("No rows. Add one, or go back to the default.", color = Palette.ivoryMuted, modifier = Modifier.padding(24.dp)) }
        }
    }
    if (adding) AddRow(have = list, onPick = { opt -> commit(list + opt.section); adding = false; model.toast("Added ${opt.label}") }, onClose = { adding = false })
    building?.let { (at, start) ->
        BuildRow(start, editing = at >= 0, onSave = { mix ->
            val section = Section(SectionType.MIX, mix.encode())
            commit(if (at in list.indices) list.toMutableList().also { it[at] = section } else if (list.none { it.id == section.id }) list + section else list)
            building = null; model.toast(if (at >= 0) "Saved ${Mixes.label(mix)}" else "Added ${Mixes.label(mix)}")
        }, onClose = { building = null })
    }
}

/** The TV's "Build a row" on a phone: the row's name as it will read, then each part's choices; the same parts ([Mixes.parts]) in the same order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildRow(start: Mix, editing: Boolean, onSave: (Mix) -> Unit, onClose: () -> Unit) {
    var mix by remember { mutableStateOf(start) }
    val parts = remember { Mixes.parts() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1610)).padding(16.dp)) {
            Text("Build a row", style = MaterialTheme.typography.headlineSmall, color = Palette.ivory)
            Text(Mixes.label(mix), style = MaterialTheme.typography.titleMedium, color = Palette.butter, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
            if (Mixes.spec(mix) == null) Text("No titles for this mix. Pick another service.", style = MaterialTheme.typography.bodySmall, color = Palette.terracotta, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                parts.forEach { part ->
                    Text(part.name, style = MaterialTheme.typography.labelLarge, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        part.choices.forEach { (value, name) ->
                            val on = part.get(mix) == value
                            Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "${part.name}: $name" + if (on) ", selected" else ""; role = Role.RadioButton; selected = on }) {
                                ChoicePill(name, on) { mix = part.set(mix, value) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreamButton(if (editing) "Save this row" else "Add this row", Icons.Filled.Check) { onSave(mix) }
                GlassButton("Cancel") { onClose() }
            }
        }
    }
}

@Composable
private fun AddRow(have: List<Section>, onPick: (SectionOption) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all by produceState<List<SectionOption>?>(null) { value = withContext(Dispatchers.Default) { PageLayouts.options() } }
    val shown = remember(all, query, have) { if (all == null) emptyList() else PageLayouts.search(query, have).take(80) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1610)).padding(16.dp)) {
            Text("Add a row", style = MaterialTheme.typography.headlineSmall, color = Palette.ivory)
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search rows") }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                placeholder = { Text("top 10 on netflix, sports documentaries…") }, keyboardOptions = KeyboardOptions.Default)
            Box(Modifier.weight(1f, fill = false)) {
                if (all == null) LoadingLine("Finding rows") else LazyColumn {
                    items(shown, key = { it.section.id }) { o ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 10.dp)) {
                            Text(o.label, color = Palette.ivory, style = MaterialTheme.typography.titleSmall)
                            Text(o.detail, color = Palette.dim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (shown.isEmpty()) item { Text("Nothing matches.", color = Palette.ivoryMuted, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
    }
}
