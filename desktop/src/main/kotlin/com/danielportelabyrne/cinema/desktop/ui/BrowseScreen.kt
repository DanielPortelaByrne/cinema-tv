package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.Pick
import com.danielportelabyrne.cinema.data.movies.Picks
import com.danielportelabyrne.cinema.data.movies.ShelfSpec
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Dest
import com.danielportelabyrne.cinema.desktop.Entry
import com.danielportelabyrne.cinema.desktop.FocusMemory
import com.danielportelabyrne.cinema.desktop.Page
import com.danielportelabyrne.cinema.desktop.PageState
import com.danielportelabyrne.cinema.desktop.jellyfin.runtimeLabel
import com.danielportelabyrne.cinema.desktop.jellyfin.watchedFraction
import com.danielportelabyrne.cinema.ui.FilmAction
import com.danielportelabyrne.cinema.ui.WatchProgress
import com.danielportelabyrne.cinema.ui.WatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where page content starts: right of the rail (32 inset + 92 wide + 60). */
const val PAGE_START = 184
const val PAGE_END = 96
const val HERO_HEIGHT = 762

/** The browse section: the page for the current destination, and the floating rail over it. */
@Composable
fun BrowseScreen(model: CinemaModel, art: Art) {
    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        when (val p = model.page) {
            Page.Home, Page.Films, Page.Series, Page.Docs, is Page.OnService, is Page.InGenre, Page.Library -> CatalogPage(model, art, p)
            Page.Search -> SearchPage(model, art)
            Page.Downloads -> DownloadsPage(model)
            Page.Letterboxd -> LetterboxdHome(model, art)
            is Page.LbPerson -> LetterboxdPerson(model, art, p)
            is Page.LbList -> LetterboxdListPage(model, art, p)
            is Page.LbSearch -> LetterboxdSearch(model, art, p)
            is Page.LbFollow -> LetterboxdFollow(model, art, p)
        }
        Rail(model)
    }
}

// --- The rail -----------------------------------------------------------------------------------------

/**
 * The floating glass pill on the left (`.rail`): the logo, then one icon per destination, Settings at the foot. The
 * page you are on has a glass circle, a saffron icon and the stripe bar at the pill's edge. Focus opens the rail to
 * show the names (and how much is stored), and the focused item becomes a cream band across it.
 */
@Composable
fun Rail(model: CinemaModel) {
    val open = model.railOpen
    LaunchedEffect(model.railWanted) {
        if (model.railWanted) { focusSoon({ model.railFocus.getValue(model.page.rail).tryFocus() }); model.railWanted = false }
    }
    Box(
        Modifier.padding(start = 32.dp, top = 32.dp, bottom = 32.dp).width(if (open) 420.dp else 92.dp).fillMaxHeight()
            .clip(RoundedCornerShape(46.dp)).background(if (open) Cin.RAIL_FILL_OPEN else Cin.RAIL_FILL)
            .onFocusChanged { if (it.hasFocus && !model.railOpen) model.loadStorage(); model.railOpen = it.hasFocus }
    ) {
        Column(Modifier.fillMaxSize().padding(top = 30.dp, bottom = 26.dp)) {
            Row(Modifier.padding(start = 18.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(56.dp)
                if (open) { Spacer(Modifier.width(18.dp)); T("Cinema", ts(36, Cin.CREAM, serif = true, weight = 500, tracking = -0.02f)) }
            }
            Spacer(Modifier.height(26.dp))
            Dest.entries.filter { it != Dest.SETTINGS }.forEach { d -> RailItem(model, d) }
            Spacer(Modifier.weight(1f))
            if (open && model.storage.isNotEmpty()) T(model.storage.joinToString("\n"), ts(18, Cin.DIM, lineHeight = 1.1f), modifier = Modifier.padding(start = 30.dp, end = 20.dp, top = 8.dp))
            RailItem(model, Dest.SETTINGS)
        }
    }
}

@Composable
private fun RailItem(model: CinemaModel, d: Dest) {
    var focused by remember { mutableStateOf(false) }
    val selected = model.page.rail == d && d != Dest.SETTINGS
    val colour = when { focused -> Cin.INK; selected -> Cin.SAFFRON; else -> Cin.RAIL_ICON }
    Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(64.dp)) {
        Row(
            Modifier.fillMaxSize().background(if (focused) Cin.CREAM else Color.Transparent)
                .focusProperties { canFocus = model.railOpen }
                .onFocusChanged { focused = it.isFocused }
                .tvFocusable(model.railFocus.getValue(d)) { model.goRail(d) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(if (selected && !focused) Cin.GLASS_STRONG else Color.Transparent), contentAlignment = Alignment.Center) {
                if (d.multicolour) Icon(d.icon, 36.dp, null, Modifier.alpha(if (focused || selected) 1f else 0.62f)) else Icon(d.icon, 36.dp, colour)
            }
            if (model.railOpen) { Spacer(Modifier.width(14.dp)); T(d.label, ts(24, colour, weight = if (focused) 600 else 500), maxLines = 1) }
        }
        if (selected) Stripes(8.dp, 32.dp, horizontalBands = false, Modifier.align(Alignment.CenterStart).offset(x = (-3).dp))
    }
}

// --- Catalogue pages ----------------------------------------------------------------------------------

/** One row of a page, as the page lays it out: an id, and how to draw it. */
private class RowSpec(val key: String, val draw: @Composable (onEnter: () -> Unit) -> Unit)

/**
 * Home, Films, Series, Documentaries, a service's or a genre's page, and My library: the featured title on top, then
 * rows (`build`). Rows from the catalogue are fetched when they near the screen, as on the TV; the first one to arrive
 * with pictures becomes the featured title unless picks already have.
 */
@Composable
private fun CatalogPage(model: CinemaModel, art: Art, page: Page) {
    val state = model.state(page)
    val scope = rememberCoroutineScope()
    val heroFocus = remember(page) { FocusRequester() }
    val rows = remember(page, model.libraryVersion, model.shelves, model.client.isConnected) { rowsFor(model, art, page, state) }
    val title = pageTitle(page)

    model.pageEntry = { heroFocus.tryFocus() || state.focus.requesterOrNull() != null }
    LaunchedEffect(page, state.heroEntries.isNotEmpty()) {
        if (!model.railOpen) focusSoon({ state.focus.restore() }, { state.heroEntries.isNotEmpty() && heroFocus.tryFocus() })
    }
    // My library's featured titles come from the library itself.
    LaunchedEffect(page, model.libraryVersion) {
        if (page == Page.Library) {
            val all = model.libraryEntries(model.library)
            val f = all.filter { it.title.backdropUrl != null }.take(3)
            if (f.isNotEmpty()) setHero(state, f, all.map { it.title }, f.map { "Recently added" })
        }
    }
    val still = LocalInspectionMode.current
    var heroHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(page, state.heroEntries.size) {
        if (still) return@LaunchedEffect
        while (state.heroEntries.size > 1) {
            delay(9_000)
            if (!heroHasFocus && state.column.firstVisibleItemIndex == 0) state.heroIndex = (state.heroIndex + 1) % state.heroEntries.size
        }
    }
    val hero = state.heroEntries.getOrNull(state.heroIndex % maxOf(1, state.heroEntries.size))
    val backdropAlpha by remember(state) { derivedStateOf { if (state.column.firstVisibleItemIndex > 0) 0f else (1f - state.column.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f) } }

    Box(Modifier.fillMaxSize()) {
        Ambient()
        if (hero != null) AnimatedContent(hero, transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(350)) }, modifier = Modifier.alpha(backdropAlpha)) { e ->
            BackdropLayer(e.title.backdropUrl, 1230, detail = false, modifier = Modifier.background(Cin.BG))
        }
        LazyColumn(state = state.column, contentPadding = PaddingValues(bottom = 120.dp)) {
            if (title != null) item(key = "title") { T(title, ts(60, Cin.CREAM, serif = true), modifier = Modifier.padding(start = PAGE_START.dp, top = 52.dp, bottom = 6.dp)) }
            if (hero != null) item(key = "hero") {
                Hero(model, state, heroFocus) { inside -> heroHasFocus = inside; if (inside) scope.launch { state.column.glideTo(0, 0) } }
            } else item(key = "top") { Spacer(Modifier.height(if (title != null) 20.dp else 120.dp)) }
            val offset = (if (title != null) 1 else 0) + 1
            itemsIndexed(rows, key = { _, r -> r.key }) { i, r -> r.draw { scope.launch { state.column.glideTo(i + offset) } } }
        }
    }
}

private fun pageTitle(page: Page): String? = when (page) {
    is Page.OnService -> "On ${page.service.label}"
    is Page.InGenre -> page.genre.label
    else -> null
}

/** Up to three featured titles, each with its eyebrow label ("#1 in films today"); picks take the place of the catalogue's. */
fun setHero(state: PageState, entries: List<Entry>, related: List<Title>, labels: List<String>, fromPicks: Boolean = false) {
    if (entries.isEmpty()) return
    if (!fromPicks && (state.heroFromPicks || state.heroEntries.isNotEmpty())) return
    state.heroEntries = entries.take(3); state.heroLabels = labels.take(3); state.heroRelated = related; state.heroIndex = 0
    state.heroFromPicks = fromPicks
}

private fun rowsFor(model: CinemaModel, art: Art, page: Page, state: PageState): List<RowSpec> {
    val out = ArrayList<RowSpec>()
    fun catalog(specs: List<ShelfSpec>) = specs.forEach { spec -> out += RowSpec("shelf:${spec.heading}") { enter -> CatalogShelf(model, art, state, spec, enter) } }
    when (page) {
        Page.Home -> {
            val resume = model.shelves?.resume.orEmpty()
            if (model.client.isConnected && resume.isNotEmpty())
                out += RowSpec("resume") { enter -> LibraryItemsRow(model, art, state, "resume", "Continue watching", "On your Jellyfin", resume, continueRow = true, onEnter = enter) }
            out += RowSpec("picks") { enter -> PicksRow(model, art, state, enter) }
            out += RowSpec("library") { enter -> LibraryRow(model, art, state, enter) }
            out += RowSpec("services") { enter -> TilesRow(state, "services", "Browse by service", Catalog.SERVICES.map { it.label }, wide = true, enter) { i -> model.go(Page.OnService(Catalog.SERVICES[i]), remember = true) } }
            out += RowSpec("genres") { enter -> TilesRow(state, "genres", "Browse by genre", Catalog.GENRES.map { it.label }, wide = false, enter, seedOffset = 2) { i -> model.go(Page.InGenre(Catalog.GENRES[i]), remember = true) } }
            catalog(Catalog.homePage())
        }
        Page.Films -> catalog(Catalog.filmsPage())
        Page.Series -> catalog(Catalog.seriesPage())
        Page.Docs -> catalog(Catalog.documentariesPage())
        is Page.OnService -> catalog(Catalog.servicePage(page.service))
        is Page.InGenre -> catalog(Catalog.genrePage(page.genre))
        Page.Library -> {
            if (!model.client.isConnected) { out += RowSpec("connect") { _ -> ActionCard("Connect Jellyfin", "Sign in to see your own films and series") { model.openSettings() } }; return out }
            val s = model.shelves
            if (s != null && s.resume.isNotEmpty()) out += RowSpec("resume") { enter -> LibraryItemsRow(model, art, state, "resume", "Continue watching", null, s.resume, continueRow = true, onEnter = enter) }
            if (s != null && s.nextUp.isNotEmpty()) out += RowSpec("next") { enter -> LibraryItemsRow(model, art, state, "next", "Next up", null, s.nextUp, onEnter = enter) }
            val all = model.library
            fun add(key: String, heading: String, list: List<JellyfinItem>) { if (list.isNotEmpty()) out += RowSpec(key) { enter -> LibraryItemsRow(model, art, state, key, heading, "${list.size}", list, onEnter = enter) } }
            if (all.isEmpty()) out += RowSpec("empty") { _ -> Note(if (model.offline) "Nothing in your library yet, or Jellyfin couldn't be reached" else "Loading your library…") }
            add("recent", "Recently added", all.take(20))
            add("films", "Films", all.filter { it.isMovie })
            add("series", "Series", all.filter { it.isSeries })
            all.flatMap { e -> e.genres.map { g -> g to e } }.groupBy({ it.first }, { it.second })
                .filterValues { it.size >= 2 }.entries.sortedByDescending { it.value.size }.take(6).forEach { (g, list) -> add("genre:$g", g, list) }
        }
        else -> {}
    }
    return out
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, start: Int = PAGE_START) {
    T(text, ts(22, Cin.MUTED), modifier = modifier.padding(start = start.dp, top = 10.dp, bottom = 10.dp))
}

/** A glass card asking for something (connect Jellyfin, sign in to Seerr); cream when focused. */
@Composable
fun ActionCard(title: String, subtitle: String, modifier: Modifier = Modifier.padding(start = PAGE_START.dp, top = 56.dp), requester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.size(720.dp, 128.dp).clip(RoundedCornerShape(26.dp)).background(if (focused) Cin.CREAM else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() }.padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        T(title, ts(26, if (focused) Cin.INK else Cin.CREAM, weight = 500))
        T(subtitle, ts(20, if (focused) Cin.INK else Cin.MUTED), maxLines = 1)
    }
}

/** The featured title (`showHero`): eyebrow, big title, meta, synopsis, provider, buttons, then the turn dots. */
@Composable
private fun Hero(model: CinemaModel, state: PageState, first: FocusRequester, onFocusInside: (Boolean) -> Unit) {
    val index = state.heroIndex % state.heroEntries.size
    val e = state.heroEntries[index]
    val t = e.title
    Column(
        Modifier.fillMaxWidth().heightIn(min = HERO_HEIGHT.dp).padding(start = PAGE_START.dp, end = PAGE_END.dp, top = 140.dp)
            .onFocusChanged { onFocusInside(it.hasFocus) }
    ) {
        AnimatedContent(t.key, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) }) { _ ->
            Column {
                Eyebrow(listOfNotNull("Featured", state.heroLabels.getOrNull(index)))
                T(t.name, titleStyle(heroTitleSize(t.name)), maxLines = 2, modifier = Modifier.padding(top = 22.dp).width(1100.dp))
                TitleMeta(t, Modifier.padding(top = 30.dp))
                t.overview?.takeIf { it.isNotBlank() }?.let { T(it, synopsisStyle(26), maxLines = 3, modifier = Modifier.padding(top = 26.dp).width(820.dp)) }
                Provider(t.services, Modifier.padding(top = 30.dp))
            }
        }
        Row(Modifier.padding(top = 34.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val lib = e.library
            var firstSet = false
            fun req(): FocusRequester? = if (!firstSet) { firstSet = true; first } else null
            if (lib != null) {
                val ws = if (lib.isSeries) WatchState.NotStarted else WatchProgress.of(lib.played, lib.resumeMs, lib.runtimeMin)
                WatchProgress.filmActions(ws).forEach { action ->
                    val restart = action == FilmAction.RESTART
                    CinemaButton(WatchProgress.label(action, ws), if (restart) ButtonKind.GLASS else ButtonKind.PRIMARY, "play", requester = req(),
                        onFocus = { state.focus.clear() }) { model.play(lib, fromStart = restart, title = t) }
                }
            } else if (t.kind == Kind.MOVIE && model.seerr.hasStreamService) {
                CinemaButton("Stream now", ButtonKind.PRIMARY, "play", requester = req(), onFocus = { state.focus.clear() }) { model.streamNow(e) }
            }
            CinemaButton("More info", ButtonKind.GLASS, "info", requester = req(), onFocus = { state.focus.clear() }) { model.open(e, state.heroRelated) }
        }
        if (state.heroEntries.size > 1) Row(Modifier.padding(top = 82.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.heroEntries.indices.forEach { i ->
                if (i == index) Stripes(64.dp, 10.dp, horizontalBands = true)
                else Box(Modifier.size(64.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(Cin.CREAM.copy(alpha = 0.22f)))
            }
        }
    }
}

/** Long names step down from the mockup's 128px so they still fit on two lines. */
fun heroTitleSize(name: String) = when { name.length > 30 -> 76; name.length > 18 -> 96; else -> 128 }

/** The meta line for a title (`meta`): certificate, year, running time, genres, IMDb score. */
@Composable
fun TitleMeta(t: Title, modifier: Modifier = Modifier, sizePx: Int = 23) {
    Meta(
        t.age?.substringAfter("GB-"),
        listOfNotNull(t.year?.toString(), t.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }, t.genres.take(3).joinToString(" · ").ifBlank { null }),
        t.imdbScore?.let { String.format(java.util.Locale.UK, "%.1f", it) },
        sizePx, modifier,
    )
}

/** "Streaming on HBO Max": a glass pill with a small gradient play disc, the service in saffron (`provider`). */
@Composable
fun Provider(services: List<String>, modifier: Modifier = Modifier) {
    val main = services.filterNot { it.contains("channel", true) || it.contains("premium", true) || it.contains("with ads", true) }.ifEmpty { services }.take(2)
    if (main.isEmpty()) return
    Row(modifier.clip(RoundedCornerShape(30.dp)).background(Cin.GLASS).padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cin.TERRA, Cin.SAFFRON))), contentAlignment = Alignment.Center) {
            Icon("play", 14.dp, Color(0xFF1A0F08))
        }
        Spacer(Modifier.width(12.dp))
        T("Streaming on ", ts(22, Cin.CREAM))
        T(main.joinToString(" and "), ts(22, Cin.SAFFRON, weight = 500))
    }
}

/** "2026 · Animation · 2h 5m": the subline a focused card shows under its name. */
fun subline(t: Title): String = listOfNotNull(t.year?.toString(), t.genres.firstOrNull(),
    t.runtimeMin?.takeIf { it > 0 && t.kind == Kind.MOVIE }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }).joinToString(" · ")

fun subline(t: JellyfinItem): String = listOfNotNull(
    if (t.isEpisode) t.episodeLabel() else t.year?.toString(),
    if (t.isEpisode) t.name else t.genres.firstOrNull(),
    if (t.isMovie) runtimeLabel(t.runtimeMin) else null,
).joinToString(" · ")

/** A row (`row`): stripe-marked heading with its count, then the row of cards. Glides up when it takes focus. */
@Composable
fun ShelfRow(
    state: PageState?, key: String, heading: String?, caption: String?, onEnter: () -> Unit,
    gap: Int = 28, startPad: Int = PAGE_START, top: Int = 50, listState: LazyListState? = null,
    content: LazyListScope.() -> Unit,
) {
    var inside by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { if (it.hasFocus && !inside) onEnter(); inside = it.hasFocus }) {
        heading?.let { RowHeading(it, caption, Modifier.padding(start = startPad.dp, top = top.dp, bottom = 14.dp)) }
        val ls = listState ?: state?.rows?.getOrPut(key) { LazyListState() } ?: remember { LazyListState() }
        LazyRow(state = ls, contentPadding = PaddingValues(start = startPad.dp, end = PAGE_END.dp, top = 36.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap.dp), content = content)
    }
}

/** A catalogue title's poster (`card`): the IN LIBRARY badge when the library has it. */
@Composable
fun EntryCard(model: CinemaModel, art: Art, e: Entry, related: List<Title>, focus: FocusMemory, key: String,
              small: Boolean = false, badge: Boolean = true, note: String? = null, chip: String? = null, poster: String? = null, onSelect: (() -> Unit)? = null) {
    PosterCard(
        title = e.title.name, imageUrl = poster ?: e.title.posterUrl, width = if (small) 200.dp else 260.dp, height = if (small) 300.dp else 390.dp,
        subline = note ?: subline(e.title), badge = if (badge && e.library != null) "In library" else null, chip = chip,
        requester = focus.requester(key), onFocus = { focus.last = key },
    ) { onSelect?.invoke() ?: model.open(e, related) }
}

/** One catalogue row, fetched once it is about to be seen; a Top 10 row draws its big numbers. Empty rows hide. */
@Composable
private fun CatalogShelf(model: CinemaModel, art: Art, state: PageState, spec: ShelfSpec, onEnter: () -> Unit) {
    val shelf by produceState<List<Title>?>(null, spec, Catalog.country) {
        value = withContext(Dispatchers.IO) { runCatching { model.catalog.shelf(spec).titles }.getOrNull().orEmpty() }
    }
    val titles = shelf
    if (titles == null) {
        Column { RowHeading(spec.heading, null, Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp)); Spacer(Modifier.height(if (spec.ranked) 368.dp else 488.dp)) }
        return
    }
    val shown = titles.take(if (spec.ranked) 10 else 16)
    if (shown.isEmpty()) return
    val entries = remember(shown, model.libraryVersion) { shown.map { model.entry(it) } }
    LaunchedEffect(entries) {
        val featured = entries.withIndex().filter { it.value.title.backdropUrl != null }.take(3)
        setHero(state, featured.map { it.value }, shown, featured.map { rankLabel(spec.heading, spec.ranked, it.index) })
    }
    ShelfRow(state, "shelf:${spec.heading}", spec.heading, "${entries.size}", onEnter, gap = if (spec.ranked) 0 else 28) {
        itemsIndexed(entries, key = { _, e -> e.title.key }) { i, e ->
            val k = "${spec.heading}:${e.title.key}"
            if (spec.ranked) RankCard(i + 1, e.title.posterUrl, e.library != null, state.focus.requester(k), { state.focus.last = k }) { model.open(e, shown) }
            else EntryCard(model, art, e, shown, state.focus, k)
        }
    }
}

/** "Top 10 films", 0 -> "#1 in films today"; any other shelf is labelled by its own heading. */
fun rankLabel(heading: String, ranked: Boolean, position: Int): String {
    if (!ranked) return heading
    val rest = heading.removePrefix("Top 10").trim()
    return when {
        rest.isEmpty() || rest == "today" -> "#${position + 1} today"
        rest.startsWith("on ") -> "#${position + 1} $rest today"
        heading.startsWith("Trending in ") -> "#${position + 1} in ${heading.removePrefix("Trending in ")}"
        else -> "#${position + 1} in $rest today"
    }
}

/** A Top 10 item (`rankCard`): an outlined Fraunces numeral tucked behind the left of the poster. */
@Composable
private fun RankCard(rank: Int, poster: String?, inLibrary: Boolean, requester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.width(350.dp).height(368.dp)) {
        T("$rank", TextStyle(fontFamily = Fonts.family(true, 210f, 600), fontSize = 210.sp, letterSpacing = (-0.06f).em, lineHeight = 168.sp,
            color = Cin.SAFFRON.copy(alpha = 0.6f), drawStyle = Stroke(width = 2.5f)), maxLines = 1, modifier = Modifier.padding(top = 28.dp))
        PosterCard(null, poster, 224.dp, 336.dp, badge = if (inLibrary) "In library" else null, modifier = Modifier.padding(start = 118.dp),
            requester = requester, onFocus = onFocus, onClick = onClick)
    }
}

/** Painted browse tiles (`tile`): a flat Cinema colour with a pale sun or stripes, the name in Fraunces. */
@Composable
private fun TilesRow(state: PageState, key: String, heading: String, labels: List<String>, wide: Boolean, onEnter: () -> Unit, seedOffset: Int = 0, onSelect: (Int) -> Unit) {
    ShelfRow(state, key, heading, null, onEnter) {
        itemsIndexed(labels) { i, label -> Tile(label, i + seedOffset, wide, state.focus.requester("$key:$i"), { state.focus.last = "$key:$i" }) { onSelect(i) } }
    }
}

private val tileColours = listOf(Cin.STRIPE_TERRA, Color(0xFF2F6E66), Cin.STRIPE_SAFFRON, Color(0xFF4D5B2C), Cin.STRIPE_RED, Color(0xFF5D1710))

@Composable
private fun Tile(label: String, seed: Int, wide: Boolean, requester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.1f else 1f, tween(160))
    val colour = tileColours[seed % tileColours.size]
    val light = colour == Cin.STRIPE_SAFFRON
    Box(
        Modifier.size(if (wide) 352.dp else 264.dp, if (wide) 184.dp else 144.dp).scale(grow)
            .bloom(Cin.SAFFRON.copy(alpha = 0.42f), 22.dp, 68.dp, 6.dp, enabled = focused)
            .clip(RoundedCornerShape(22.dp)).background(colour)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.tvFocusable(requester) { onClick() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pale = Color(0x2EF6ECDB)
            when (seed % 3) {
                0 -> drawCircle(pale, size.height * 0.46f, Offset(size.width * 0.84f, size.height * 0.28f))
                1 -> { drawRect(pale, Offset(size.width * 0.62f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.10f, size.height)); drawRect(pale, Offset(size.width * 0.78f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.06f, size.height)) }
                else -> drawCircle(pale, size.height * 0.55f, Offset(size.width * 0.12f, size.height))
            }
        }
        T(label, ts(if (wide) 36 else 30, if (light) Cin.INK else Cin.CREAM, serif = true, weight = 500), maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, end = 20.dp, bottom = 20.dp))
    }
}

/** "Picked for you" (`picksRow`): titles recommended from what has been watched; the top three become the featured titles. */
@Composable
private fun PicksRow(model: CinemaModel, art: Art, state: PageState, onEnter: () -> Unit) {
    val picks by produceState<List<Pick>?>(null) {
        val saved = withContext(Dispatchers.IO) { runCatching { model.picks.saved() }.getOrNull().orEmpty() }
        if (saved.isNotEmpty()) value = saved
        val fresh = withContext(Dispatchers.IO) { runCatching { model.picks.fresh() }.getOrNull() }
        value = fresh ?: saved
        if (fresh != null) withContext(Dispatchers.IO) { runCatching { model.picks.shown(fresh) } }
    }
    val list = picks ?: return
    if (list.isEmpty()) return
    val entries = remember(list, model.libraryVersion) { list.map { model.entry(pickTitle(it)) } }
    val related = entries.map { it.title }
    LaunchedEffect(entries) {
        val featured = entries.indices.filter { entries[it].title.backdropUrl != null }.take(3)
        if (featured.isNotEmpty()) setHero(state, featured.map { entries[it] }, related, featured.map { "Because you watched ${list[it].because}" }, fromPicks = true)
    }
    ShelfRow(state, "picks", "Picked for you", "${entries.size}", onEnter) {
        itemsIndexed(entries, key = { _, e -> e.title.key }) { i, e -> EntryCard(model, art, e, related, state.focus, "picks:${e.title.key}", note = "Like ${list[i].because}") }
    }
}

/** A pick as a page title: TMDB's poster for the row, its full-size backdrop for the featured title. */
private fun pickTitle(p: Pick): Title {
    val s = p.suggestion
    return Title(
        key = "tmdb:" + Picks.key(s.kind, s.tmdbId), kind = s.kind, name = s.name, year = s.year,
        overview = s.overview, runtimeMin = null, genres = s.genres, age = null, imdbScore = null,
        posterUrl = s.rowPoster,
        backdropUrl = s.heroBackdrop,
        imdbId = null, tmdbId = s.tmdbId.toString(),
    )
}

/** "My library · On your Jellyfin" on Home (`libraryRow`), or the offer to connect Jellyfin. */
@Composable
private fun LibraryRow(model: CinemaModel, art: Art, state: PageState, onEnter: () -> Unit) {
    if (!model.client.isConnected) {
        Column {
            RowHeading("My library", "On your Jellyfin", Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp))
            ActionCard("Connect Jellyfin", "Sign in to see your own films and series", Modifier.padding(start = PAGE_START.dp, top = 36.dp)) { model.openSettings() }
        }
        return
    }
    val items = model.library.take(18)
    if (items.isEmpty()) {
        Column { RowHeading("My library", "On your Jellyfin", Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp)); Note(if (model.offline) "Nothing in your library yet, or Jellyfin couldn't be reached" else "Loading…") }
        return
    }
    LibraryItemsRow(model, art, state, "library", "My library", "On your Jellyfin", items, onEnter = onEnter)
}

/** A row of the owner's own titles. Episodes (Continue watching, Next up) play at once; the rest open their page. */
@Composable
private fun LibraryItemsRow(model: CinemaModel, art: Art, state: PageState, key: String, heading: String, caption: String?, items: List<JellyfinItem>, continueRow: Boolean = false, onEnter: () -> Unit) {
    val entries = remember(items) { model.libraryEntries(items) }
    val related = entries.map { it.title }
    ShelfRow(state, key, heading, caption ?: "${items.size}", onEnter) {
        itemsIndexed(entries, key = { _, e -> e.title.key }) { _, e ->
            val item = e.library!!
            val k = "$key:${item.id}"
            PosterCard(
                title = if (item.isEpisode) item.seriesName ?: item.name else item.name, imageUrl = art.poster(item),
                subline = subline(item), progress = if (continueRow) watchedFraction(item) else 0f,
                requester = state.focus.requester(k), onFocus = { state.focus.last = k },
            ) { if (item.isEpisode) model.play(item) else model.open(e, related) }
        }
    }
}
