package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.EpisodeOutcome
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.QueueItem
import com.danielportelabyrne.cinema.data.movies.RequestOutcome
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Entry
import com.danielportelabyrne.cinema.desktop.Route
import com.danielportelabyrne.cinema.desktop.Sheet
import com.danielportelabyrne.cinema.desktop.SheetAction
import com.danielportelabyrne.cinema.ui.CardLook
import com.danielportelabyrne.cinema.ui.CardState
import com.danielportelabyrne.cinema.ui.EpisodeCards
import com.danielportelabyrne.cinema.ui.FilmAction
import com.danielportelabyrne.cinema.ui.WatchProgress
import com.danielportelabyrne.cinema.ui.WatchState
import com.danielportelabyrne.cinema.ui.episodeCardState
import kotlinx.coroutines.launch

/**
 * A title's page (`openDetail`, mockups 10 and 12): no Back button, B closes it. Watch now / Continue / Restart /
 * Delete for what is on disk; Queue download (or where its request has got to), Stream now and Clear stale request
 * for the rest. A series lists its seasons and episodes; a film shows what Letterboxd makes of it and More like this.
 */
@Composable
fun TitleScreen(model: CinemaModel, route: Route.Title, art: Art) {
    val e = route.entry
    val t = e.title
    val primary = remember(route) { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(route) { focusSoon({ route.focus.restore() }, { primary.tryFocus() }) }
    val backdrop = t.backdropUrl ?: e.library?.takeIf { it.hasBackdrop }?.let { model.client.backdropUrl(it.id) }

    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        BackdropLayer(backdrop, if (e.library != null) 1060 else 1230, detail = true)
        Column(Modifier.fillMaxSize().verticalScroll(route.scroll).padding(start = 96.dp, end = 96.dp, top = 70.dp, bottom = 160.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).onFocusChanged { if (it.hasFocus) scope.launch { route.scroll.animateScrollTo(0) } }) {
                    Eyebrow(listOfNotNull(if (t.kind == Kind.SHOW) "Series" else "Film", if (e.library != null) "In your library" else null))
                    T(t.name, titleStyle(when { t.name.length > 30 -> 68; t.name.length > 18 -> 84; else -> 104 }), maxLines = 2, modifier = Modifier.padding(top = 20.dp))
                    TitleMeta(t, Modifier.padding(top = 24.dp))
                    Provider(t.services, Modifier.padding(top = 22.dp))
                    T(t.overview ?: e.library?.overview ?: "No description yet.", synopsisStyle(26), maxLines = 3, modifier = Modifier.padding(top = 22.dp).width(1040.dp))
                    Buttons(model, route, primary)
                }
                StripePoster(t.posterUrl, t.name, Modifier.padding(start = 48.dp).offset(y = (-12).dp))
            }
            if (t.kind == Kind.SHOW) Seasons(model, route, art)
            else {
                LetterboxdFilmSection(model, route)
                MoreLikeThis(model, route, art)
            }
        }
    }
}

// --- The buttons (`setupPrimary`) ----------------------------------------------------------------------

@Composable
private fun Buttons(model: CinemaModel, route: Route.Title, primary: FocusRequester) {
    val e = route.entry
    val t = e.title
    val onFocus = { route.focus.clear() }
    var label by remember(route, route.version) { mutableStateOf("Checking…") }
    var enabled by remember(route, route.version) { mutableStateOf(false) }
    var action by remember(route, route.version) { mutableStateOf<(() -> Unit)?>(null) }
    var showClearStale by remember(route, route.version) { mutableStateOf(false) }
    var clearing by remember(route, route.version) { mutableStateOf(false) }
    /** A part-watched episode of a series in the library: "Resume S1 E2". */
    var resumeEpisode by remember(route, route.version) { mutableStateOf<JellyfinItem?>(null) }
    val lib = e.library
    val tmdb = t.tmdbId?.toLongOrNull()

    fun set(l: String, on: Boolean, run: (() -> Unit)?) { label = l; enabled = on; action = run }
    fun queue() {
        if (!model.seerr.isSignedIn) { model.signInToSeerr { route.version++ }; return }
        set("Queuing…", false, null)
        model.bg({ model.seerr.request(t.kind, tmdb!!) }) { outcome ->
            when (outcome) {
                RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { set("Requested ✓", false, null); model.toast("Request accepted for ${t.name}. The server will search for a release.") }
                RequestOutcome.NEEDS_SIGN_IN -> { set("Queue download", true) { queue() }; model.signInToSeerr { route.version++ } }
                else -> { set("Queue download", true) { queue() }; model.toast("Couldn't queue it right now") }
            }
        }
    }
    LaunchedEffect(route, route.version, lib) {
        when {
            lib != null -> {
                if (lib.isSeries) model.bg({ model.client.episodes(lib.id) }) { owned ->
                    resumeEpisode = owned.orEmpty().filter { it.resumeMs > 0 && !it.played && it.season != null && it.episode != null }
                        .maxWithOrNull(compareBy<JellyfinItem> { it.season }.thenBy { it.episode })
                }
            }
            tmdb == null -> set("Can't be requested", false, null)
            !model.seerr.isSignedIn -> set("Queue download", true) { queue() }
            else -> model.bg({ model.seerr.titleState(t.kind, tmdb) }) { state ->
                if (state == null) { set("Retry status check", true) { route.version++ }; return@bg }
                if (state.staleCandidate && model.seerr.hasRequestHelper) { set("Tracked · no request", false, null); showClearStale = true; return@bg }
                when (state.availability) {
                    Availability.REQUESTED -> set("Requested ✓", false, null)
                    Availability.PROCESSING -> set("Requested ✓ · finding a release", false, null)
                    Availability.DOWNLOADING -> set("Downloading…", false, null)
                    Availability.PARTIAL -> set("Partly downloaded", false, null)
                    Availability.AVAILABLE -> set("Downloaded, syncing", false, null)
                    Availability.NONE -> set(if (t.kind == Kind.SHOW) "Request all seasons" else "Queue download", true) { queue() }
                }
            }
        }
    }

    Row(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (lib != null) {
            val resume = resumeEpisode
            if (resume != null) {
                CinemaButton("Resume S${resume.season} E${resume.episode}", ButtonKind.PRIMARY, "play", requester = primary, onFocus = onFocus) { model.play(resume, title = t) }
            } else {
                val state = if (lib.isSeries) WatchState.NotStarted else WatchProgress.of(lib.played, lib.resumeMs, lib.runtimeMin)
                val actions = WatchProgress.filmActions(state)
                CinemaButton(WatchProgress.label(actions.first(), state), ButtonKind.PRIMARY, "play", requester = primary, onFocus = onFocus) { model.play(lib, title = t) }
                if (FilmAction.RESTART in actions) CinemaButton("Restart", ButtonKind.GLASS, "play", onFocus = onFocus) { model.play(lib, fromStart = true, title = t) }
            }
            CinemaButton("Delete", ButtonKind.DANGER, "trash", onFocus = onFocus) { model.confirmDelete(e) { if (model.route === route) model.stack.removeAt(model.stack.lastIndex) } }
        } else {
            CinemaButton(label, if (enabled) ButtonKind.TERRA else ButtonKind.GLASS, if (enabled) "dl" else null, requester = primary, onFocus = onFocus) { if (enabled) action?.invoke() }
            if (t.kind == Kind.MOVIE && model.seerr.hasStreamService) CinemaButton("Stream now", ButtonKind.GLASS, "play", onFocus = onFocus) { model.streamNow(e) }
            if (showClearStale) CinemaButton(if (clearing) "Clearing…" else "Clear stale request", ButtonKind.GLASS, onFocus = onFocus) {
                if (clearing) return@CinemaButton
                clearing = true
                model.bg({ model.seerr.clearStaleStatus(t.kind, tmdb!!) }) { ok ->
                    clearing = false
                    if (ok == true) { model.toast("Stale request cleared"); route.version++ }
                    else model.toast("Couldn't clear it. An admin must be signed in, with no requests, downloads or files for this title.")
                }
            }
        }
    }
}

/** The poster with the stripe set stacked behind it, 10px a step (`StripePoster`). */
@Composable
fun StripePoster(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.size(340.dp, 490.dp)) {
        listOf(Cin.STRIPE_RED, Cin.STRIPE_TERRA, Cin.STRIPE_ORANGE, Cin.STRIPE_SAFFRON).forEachIndexed { i, c ->
            val step = (4 - i) * 10
            Box(Modifier.offset(step.dp, step.dp).size(300.dp, 450.dp).clip(RoundedCornerShape(22.dp)).background(c))
        }
        Box(Modifier.size(300.dp, 450.dp).bloom(Color.Black.copy(alpha = 0.6f), 22.dp, 90.dp, offsetY = 50.dp).clip(RoundedCornerShape(22.dp))) {
            RemoteImage(url, Modifier.matchParentSize(), fallback = title)
        }
    }
}

// --- Seasons and episodes (`seasonSection`, `drawSeasons`) --------------------------------------------------

private class SeasonState(val number: Int, val name: String, val episodeCount: Int, availability: Availability) {
    var availability by mutableStateOf(availability)
}
private class EpisodeRow(val number: Int, val name: String, val overview: String?, val airDate: String?, val stillUrl: String?, val runtime: Int?)
private class SeriesData(val seasons: List<SeasonState>, val owned: List<JellyfinItem>, val states: Map<Pair<Int, Int>, EpisodeState>, val downloads: List<QueueItem>)

@Composable
private fun Seasons(model: CinemaModel, route: Route.Title, art: Art) {
    val e = route.entry
    val tmdb = e.title.tmdbId?.toLongOrNull()
    var data by remember(route, route.version) { mutableStateOf<SeriesData?>(null) }
    val states = remember(route, route.version) { mutableStateMapOf<Pair<Int, Int>, EpisodeState>() }
    LaunchedEffect(route, route.version, e.library) {
        model.bg({
            val owned = e.library?.let { model.client.episodes(it.id) }.orEmpty().filter { !it.missing }
            val signedIn = tmdb != null && model.seerr.isSignedIn
            val st = if (signedIn) model.sonarr.episodeStates(tmdb!!) else emptyMap()
            val fromSeerr = if (signedIn) model.seerr.seasons(tmdb!!) else emptyList()
            val seasons = fromSeerr.map { SeasonState(it.number, it.name, it.episodeCount, it.availability) }.ifEmpty {
                owned.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted().map { n -> SeasonState(n, "Season $n", owned.count { it.season == n }, Availability.AVAILABLE) }
            }
            val busy = st.values.any { it == EpisodeState.REQUESTED } || seasons.any { it.availability in setOf(Availability.PROCESSING, Availability.DOWNLOADING, Availability.PARTIAL) }
            val downloads = if (signedIn && busy) model.queueClient.items().orEmpty().filter { it.tmdbId == tmdb } else emptyList()
            SeriesData(seasons, owned, st, downloads)
        }) { d -> data = d; states.clear(); d?.states?.let { states.putAll(it) } }
    }
    Spacer(Modifier.height(60.dp))
    val d = data
    if (d == null) { T("Loading seasons…", ts(22, Cin.MUTED)); return }
    if (d.seasons.isEmpty()) {
        T(if (!model.seerr.isSignedIn && e.library == null) "Press Queue download to sign in, then the seasons and episodes will be listed here." else "No season information available.", ts(22, Cin.MUTED))
        return
    }
    // The season shown first: the one with a half-watched episode, else the first.
    val startSeason = d.owned.filter { it.resumeMs > 0 && !it.played }.maxByOrNull { (it.season ?: 0) * 1000 + (it.episode ?: 0) }?.season
    var shown by remember(d) { mutableIntStateOf(d.seasons.indexOfFirst { it.number == startSeason }.coerceAtLeast(0)) }
    val season = d.seasons[shown]
    var listing by remember(d, shown) { mutableStateOf<List<EpisodeRow>?>(null) }
    LaunchedEffect(d, shown) {
        model.bg({ if (tmdb != null && model.seerr.isSignedIn) model.seerr.episodes(tmdb, season.number) else emptyList() }) { l ->
            val ownedHere = d.owned.filter { it.season == season.number }
            listing = l.orEmpty().map { EpisodeRow(it.number, it.name, it.overview, it.airDate, it.stillUrl, it.runtime) }.ifEmpty {
                ownedHere.filter { it.episode != null }.map { EpisodeRow(it.episode!!, it.name, it.overview, null, null, it.runtimeMin) }
            }.sortedBy { it.number }
        }
    }
    val today = remember { EpisodeCards.today() }
    val ownedHere = d.owned.filter { it.season == season.number }
    val looks = listing?.map { ep -> ep to episodeCardState(ownedHere.any { it.episode == ep.number }, states[season.number to ep.number], season.availability, ep.airDate, today, tmdb != null, model.seerr.hasStreamService, EpisodeCards.download(d.downloads, tmdb, season.number, ep.number)) }

    // The season bar (`.sbar`): stripe marker, the season tabs, the season's counts and its download button.
    Row(Modifier.height(62.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Stripes(14.dp, 30.dp, horizontalBands = false)
        Spacer(Modifier.width(24.dp))
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(d.seasons) { i, s -> SeasonTab(s.name, i == shown, route.focus.requester("season:$i"), { route.focus.last = "season:$i"; if (shown != i) shown = i }) { shown = i } }
        }
        Spacer(Modifier.width(24.dp))
        val inLibrary = looks?.count { it.second.state == CardState.LIBRARY } ?: 0
        val downloading = looks?.count { it.second.state == CardState.DOWNLOADING } ?: 0
        EpisodeCards.seasonInfo(looks?.size ?: season.episodeCount, inLibrary, downloading).forEachIndexed { i, (text, strong) ->
            if (i > 0) { Spacer(Modifier.width(14.dp)); Box(Modifier.size(4.dp).clip(CircleShape).background(Cin.DIM)); Spacer(Modifier.width(14.dp)) }
            T(text, ts(21, if (strong) Cin.CREAM else Cin.MUTED, weight = if (strong) 500 else 400), maxLines = 1)
        }
        Spacer(Modifier.width(22.dp))
        val all = looks != null && inLibrary >= looks.size
        EpisodeCards.seasonAction(season.availability, season.name, all)?.let { label ->
            val can = season.availability == Availability.NONE && tmdb != null
            CinemaButton(label, if (can) ButtonKind.TERRA else ButtonKind.GLASS, if (can) "dl" else null, small = true) {
                if (!can) return@CinemaButton
                if (!model.seerr.isSignedIn) { model.signInToSeerr { route.version++ }; return@CinemaButton }
                model.bg({ model.seerr.request(Kind.SHOW, tmdb!!, listOf(season.number)) }) { outcome ->
                    when (outcome) {
                        RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { season.availability = Availability.REQUESTED; model.toast("${e.title.name}, ${season.name} is queued for download") }
                        RequestOutcome.NEEDS_SIGN_IN -> model.signInToSeerr { route.version++ }
                        else -> model.toast("Couldn't queue ${season.name} right now")
                    }
                }
            }
        }
    }
    // The episode rail, 32px under the bar.
    if (looks == null) { T("Loading episodes…", ts(22, Cin.MUTED), modifier = Modifier.padding(top = 32.dp)); return }
    if (looks.isEmpty()) { T("No episode list available for this season.", ts(22, Cin.MUTED), modifier = Modifier.padding(top = 32.dp)); return }
    val rowState = route.rows.getOrPut("episodes:$shown") { androidx.compose.foundation.lazy.LazyListState() }
    LazyRow(state = rowState, contentPadding = PaddingValues(top = 32.dp, bottom = 16.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        itemsIndexed(looks, key = { _, p -> "${season.number}:${p.first.number}" }) { _, (ep, look) ->
            val mine = ownedHere.firstOrNull { it.episode == ep.number }
            val label = "${e.title.name} · S${season.number} E${ep.number}"
            val runtime = mine?.runtimeMin ?: ep.runtime
            val resume = mine?.takeIf { !it.played }?.let { EpisodeCards.resume(it.resumeMs, runtime) }
            val progress = mine?.let { WatchProgress.of(it.played, it.resumeMs, runtime) } ?: WatchState.NotStarted
            val still = if (mine?.hasPrimaryImage == true) model.client.episodeImageUrl(mine.id) else ep.stillUrl
            val key = "ep:${season.number}:${ep.number}"
            EpisodeTile(
                number = ep.number, name = ep.name, overview = ep.overview ?: mine?.overview, still = still, look = look,
                airs = EpisodeCards.airsLabel(ep.airDate).uppercase(), meta = EpisodeCards.metaParts(runtime, resume?.second, ep.airDate).joinToString(" · "),
                bar = WatchProgress.episodeBar(progress), watched = WatchProgress.episodeShowsTick(progress),
                requester = route.focus.requester(key),
                onFocus = {
                    route.focus.last = key
                    if (mine == null && !look.unaired && model.seerr.isSignedIn) model.bg({ model.seerr.prepareStream(e.title.name, e.title.year, season.number, ep.number) }) {}
                },
                onPlay = { if (mine != null) model.play(mine, title = e.title) else model.streamNow(e, season.number to ep.number, label) },
                onDownload = { episodeDownload(model, route, e, tmdb, season.number, ep, look, states) },
            )
        }
    }
}

/** The Download disc on one episode card: ask Sonarr for it, say where it has got to, or stop waiting for it. */
private fun episodeDownload(model: CinemaModel, route: Route.Title, e: Entry, tmdb: Long?, season: Int, ep: EpisodeRow, look: CardLook,
                            states: MutableMap<Pair<Int, Int>, EpisodeState>) {
    val label = "${e.title.name} · S$season E${ep.number}"
    when (look.state) {
        CardState.REQUESTED -> model.toast("Requested, waiting for a release")
        CardState.DOWNLOADING -> model.toast(look.progress?.let { "Downloading, ${Math.round(it * 100)}% so far" } ?: "Downloading")
        CardState.UNAIRED_REQUESTED -> if (tmdb != null) model.sheet = Sheet("REQUESTED", label, EpisodeCards.airsLabel(ep.airDate), listOf("It will download by itself once it airs."), listOf(
            SheetAction("Cancel request", "Stop waiting for this episode", primary = true) {
                model.bg({ model.sonarr.unwantEpisodes(tmdb, season, listOf(ep.number)) }) { done ->
                    if (done == true) states[season to ep.number] = EpisodeState.NONE
                    model.toast(if (done == true) "Cancelled S$season E${ep.number}" else "Couldn't cancel it right now")
                }
            },
            SheetAction("Keep it") { }))
        CardState.REQUEST, CardState.UNAIRED -> when {
            tmdb == null -> Unit
            !model.seerr.isSignedIn -> model.signInToSeerr { route.version++ }
            else -> model.bg({ model.sonarr.requestEpisodes(tmdb, season, listOf(ep.number)) }) { outcome ->
                when (outcome) {
                    EpisodeOutcome.QUEUED, EpisodeOutcome.ALREADY -> {
                        states[season to ep.number] = EpisodeState.REQUESTED
                        model.toast(if (look.unaired) EpisodeCards.preRequestDone(season, ep.number, ep.airDate) else "${e.title.name} S$season E${ep.number} is queued")
                    }
                    EpisodeOutcome.NOT_CONFIGURED -> model.toast("Sonarr's details aren't available, so only whole seasons can be requested here")
                    EpisodeOutcome.NOT_FOUND -> model.toast("Sonarr couldn't find that episode yet. Try again in a moment")
                    else -> model.toast("Couldn't queue that episode right now")
                }
            }
        }
        else -> Unit
    }
}

/** A season tab: a Fraunces word, the shown one cream with the stripe beneath, a focused one filled cream. */
@Composable
fun SeasonTab(name: String, selected: Boolean, requester: FocusRequester?, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(if (focused) Cin.CREAM else Color.Transparent)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.tvFocusable(requester) { onClick() }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        T(name, ts(36, if (focused) Cin.INK else if (selected) Cin.CREAM else Cin.DIM, serif = true, tracking = -0.01f), maxLines = 1)
        if (selected && !focused) Stripes(40.dp, 6.dp, horizontalBands = false, Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp))
    }
}

/**
 * An episode card (`EpisodeCard`): a 16:9 still with its E-number chip, a status badge and the Play and Download discs;
 * then the name, its meta line and, on the lit card, the overview. A plays; X is the Download disc (its own focus stop
 * on the TV). Unaired episodes are greyed with their air date and have no Play.
 */
@Composable
private fun EpisodeTile(
    number: Int, name: String, overview: String?, still: String?, look: CardLook, airs: String, meta: String,
    bar: Float?, watched: Boolean, requester: FocusRequester, onFocus: () -> Unit, onPlay: () -> Unit, onDownload: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.06f else 1f, tween(160))
    Column(Modifier.width(400.dp)) {
        Box(
            Modifier.size(400.dp, 225.dp).scale(grow)
                .bloom(Cin.SAFFRON.copy(alpha = 0.24f), 18.dp, 90.dp, spread = 6.dp, enabled = focused)
                .clip(RoundedCornerShape(18.dp))
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
                .tvFocusable(requester, onFocus = {}, onAlt = if (look.downloadFocusable) onDownload else null) { if (look.playShown) onPlay() else if (look.downloadFocusable) onDownload() }
        ) {
            Box(Modifier.matchParentSize().alpha(if (look.unaired) 0.45f else 1f)) { RemoteImage(still, Modifier.matchParentSize(), placeholder = Cin.PLACEHOLDER) }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
            Box(Modifier.padding(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xB80C0806)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                T("E$number", ts(15, Cin.CREAM, weight = 700, tracking = 0.14f))
            }
            look.badge?.let { b ->
                Row(Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xB80C0806)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (look.state == CardState.LIBRARY) { Icon("check", 14.dp, Cin.SAFFRON); Spacer(Modifier.width(6.dp)) }
                    T(b, ts(13, if (look.badgeAccent) Cin.SAFFRON else Cin.MUTED, weight = 700, tracking = 0.14f), maxLines = 1)
                }
            }
            if (look.unaired) T(airs, ts(18, Cin.CREAM, weight = 600, tracking = 0.12f), modifier = Modifier.align(Alignment.Center))
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (look.playShown) Disc(78.dp, if (watched && !focused) "check" else "play", focused, Cin.CREAM)
                if (look.downloadShown) {
                    val icon = when (look.state) { CardState.LIBRARY -> "check"; CardState.REQUESTED, CardState.UNAIRED_REQUESTED -> "clock"; else -> "dl" }
                    Disc(62.dp, icon, false, if (look.state == CardState.REQUEST || look.state == CardState.UNAIRED) Cin.CREAM else Cin.SAFFRON, ringProgress = look.progress)
                }
            }
            if (bar != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar.coerceIn(0.03f, 1f)).background(Cin.SAFFRON))
            }
        }
        Spacer(Modifier.height(18.dp))
        T(name, ts(23, if (focused) Cin.CREAM else Cin.CARD_TITLE, weight = 500), maxLines = 1)
        if (meta.isNotBlank()) T(meta, ts(18, Cin.DIM), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        T(overview.orEmpty(), ts(18, Cin.READING, weight = 300, lineHeight = 1.3f), maxLines = 2, modifier = Modifier.padding(top = 8.dp).height(48.dp).alpha(if (focused) 1f else 0f))
        if (focused && look.downloadFocusable) KeyHints(listOf("A" to if (look.playShown) "Play" else "Download", "X" to when (look.state) {
            CardState.REQUEST -> "Download"; CardState.UNAIRED -> "Get it when it airs"; CardState.UNAIRED_REQUESTED -> "Cancel request"; else -> "Status" }), Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Disc(size: androidx.compose.ui.unit.Dp, icon: String, lit: Boolean, tint: Color, ringProgress: Float? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(if (lit) Cin.CREAM else Color(0x8C0C0806)), contentAlignment = Alignment.Center) {
        if (ringProgress != null) Canvas(Modifier.matchParentSize().padding(4.dp)) {
            drawArc(Cin.SAFFRON, -90f, 360f * ringProgress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
        Icon(icon, size * 0.44f, if (lit) Cin.INK else tint, Modifier.padding(start = if (icon == "play") 3.dp else 0.dp))
    }
}

// --- More like this ------------------------------------------------------------------------------------

/** "More like this": the other titles of the row the page was opened from, as small posters 44px apart. */
@Composable
private fun MoreLikeThis(model: CinemaModel, route: Route.Title, art: Art) {
    val more = route.related.filter { it.key != route.entry.title.key }.take(14)
    if (more.isEmpty()) return
    RowHeading("More like this", modifier = Modifier.padding(top = 70.dp, bottom = 14.dp))
    val state = route.rows.getOrPut("more") { androidx.compose.foundation.lazy.LazyListState() }
    LazyRow(state = state, contentPadding = PaddingValues(top = 30.dp, bottom = 16.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        itemsIndexed(more, key = { _, t -> t.key }) { _, t ->
            val e = remember(t, model.libraryVersion) { model.entry(t) }
            EntryCard(model, art, e, more, route.focus, "more:${t.key}", small = true) {
                model.stack.removeAt(model.stack.lastIndex); model.open(e, more)
            }
        }
    }
}

