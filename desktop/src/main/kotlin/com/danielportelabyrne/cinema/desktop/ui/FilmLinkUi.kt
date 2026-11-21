package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.LinkFilm
import com.danielportelabyrne.cinema.data.movies.LinkPhase
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.FilmLinkSession
import com.danielportelabyrne.cinema.desktop.Route
import com.danielportelabyrne.cinema.desktop.posterUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Glass = Color(0xE6180F0B)

/** Everything Film Link draws over the app: the start menu, the choose-a-finish banner, the HUD and the summary. */
@Composable
fun FilmLinkLayer(model: CinemaModel) {
    val fl = model.filmLink
    val game = fl.game
    Box(Modifier.fillMaxSize()) {
        if (fl.pick != null) PickBanner()
        if (game != null) {
            if (!game.finished) LinkHud(game) else LinkSummary(model, game)
        }
        if (fl.menuOpen) LinkMenu(model)
    }
}

// --- Choosing the finish ---------------------------------------------------------------------------------------

@Composable
private fun PickBanner() {
    Box(Modifier.fillMaxWidth().padding(top = 26.dp), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Glass).padding(horizontal = 34.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon("shuffle", 28.dp, Cin.TEAL); Spacer(Modifier.width(14.dp))
            T("Film Link: choose your finishing film. Select a film in Search.", ts(23, Cin.CREAM, weight = 600), maxLines = 1)
        }
    }
}

// --- The game on a title page ------------------------------------------------------------------------------------

/** Replaces the page's buttons in a game: nothing to stream, download, delete or save, only the way on. */
@Composable
fun LinkButtons(model: CinemaModel, route: Route.Title, primary: FocusRequester) {
    val onFocus = { route.focus.clear() }
    Row(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CinemaButton("Find a link", ButtonKind.PRIMARY, "play", requester = primary, onFocus = onFocus) { route.focus.requester("link:0").tryFocus() }
        CinemaButton("Hint", ButtonKind.GLASS, onFocus = onFocus) { model.filmLink.hint() }
        CinemaButton("Give up", ButtonKind.GLASS, onFocus = onFocus) { model.filmLink.giveUp() }
    }
}

/** "More films like this" in a game: the page's connections, each a hop. Films already on the route are dimmed. */
@Composable
fun LinkRow(model: CinemaModel, route: Route.Title) {
    val game = model.filmLink.game ?: return
    val film = route.linkFilm ?: return
    LaunchedEffect(route, film, route.version) {
        val known = route.linkRow
        if (known != null) { game.run.offer(known); return@LaunchedEffect }
        route.linkFailed = false
        model.bg({ game.graph.neighbours(film.tmdbId) }) { list ->
            if (list == null) route.linkFailed = true else { route.linkRow = list; game.run.offer(list); game.version++ }
        }
    }
    RowHeading("More films like this", modifier = Modifier.padding(top = 70.dp, bottom = 14.dp))
    val row = route.linkRow
    when {
        route.linkFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
            T("Couldn't load the connected films.", ts(24, Cin.MUTED)); Spacer(Modifier.width(24.dp))
            CinemaButton("Try again", ButtonKind.GLASS, small = true) { route.version++ }
        }
        row == null -> T("Finding connections…", ts(22, Cin.MUTED))
        row.isEmpty() -> T("Dead end. Press B to go back, or give up.", ts(24, Cin.MUTED))
        else -> {
            val state = route.rows.getOrPut("link") { androidx.compose.foundation.lazy.LazyListState() }
            LazyRow(state = state, contentPadding = PaddingValues(top = 30.dp, bottom = 16.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                itemsIndexed(row, key = { _, f -> f.tmdbId }) { i, f ->
                    val visited = game.run.isOnRoute(f)
                    PosterCard(
                        title = f.name, imageUrl = f.posterUrl(), width = 200.dp, height = 300.dp,
                        subline = if (visited) "Already on your route" else "+1 hop", chip = if (visited) "Visited" else null,
                        modifier = Modifier.alpha(if (visited) 0.4f else 1f),
                        requester = route.focus.requester("link:$i"), onFocus = { route.focus.last = "link:$i" },
                    ) { model.filmLink.hop(f) }
                }
            }
        }
    }
}

// --- The HUD ------------------------------------------------------------------------------------------------------

@Composable
private fun LinkHud(game: FilmLinkSession) {
    @Suppress("UNUSED_VARIABLE") val v = game.version
    val run = game.run
    val ms by produceState(run.elapsedMs(), game, run.timed) { while (run.timed && !run.finished) { value = run.elapsedMs(); delay(500) } }
    Box(Modifier.fillMaxSize()) {
        // The teal edge: the one mark that the game is on.
        Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(Cin.TEAL, Cin.TEAL.copy(alpha = 0f)))))
        T(trail(game), ts(22, Cin.CREAM, weight = 600, tracking = 0.2f), maxLines = 1, modifier = Modifier.padding(start = 96.dp, top = 30.dp).width(900.dp))
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 20.dp).clip(RoundedCornerShape(26.dp)).background(Glass).padding(start = 14.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(run.target.posterUrl(), Modifier.size(72.dp, 108.dp).clip(RoundedCornerShape(10.dp)), fallback = run.target.name)
            Column(Modifier.padding(start = 20.dp).width(340.dp)) {
                T("TARGET", ts(18, Cin.TEAL, weight = 600, tracking = 0.2f))
                T(run.target.name, ts(32, Cin.CREAM, serif = true, weight = 600), maxLines = 1)
                T(run.target.year?.toString().orEmpty(), ts(19, Cin.MUTED))
                // So a film you don't know can still be imagined: what it is about, two lines.
                run.target.overview?.takeIf { it.isNotBlank() }?.let { T(it, ts(16, Cin.READING, weight = 300, lineHeight = 1.25f), maxLines = 3, modifier = Modifier.padding(top = 4.dp)) }
            }
            HudDivider()
            Column {
                T("HOPS", ts(18, Cin.MUTED, weight = 600, tracking = 0.2f))
                T(run.hops.toString(), ts(64, Cin.CREAM, serif = true, weight = 600))
            }
            if (run.timed) {
                HudDivider()
                Column {
                    T("TIME", ts(18, Cin.MUTED, weight = 600, tracking = 0.2f))
                    T(clock(ms), ts(36, Cin.CREAM, weight = 600))
                }
            }
        }
    }
}

@Composable
private fun HudDivider() = Box(Modifier.padding(horizontal = 26.dp).size(2.dp, 96.dp).background(Cin.CREAM.copy(alpha = 0.14f)))

private fun trail(game: FilmLinkSession): String {
    val names = game.run.route.map { it.name }
    val shown = if (names.size > 3) listOf("…") + names.takeLast(3) else names
    return "FILM LINK  ·  " + shown.joinToString("  ›  ") { it.uppercase() }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

// --- The summary -------------------------------------------------------------------------------------------------

@Composable
private fun LinkSummary(model: CinemaModel, game: FilmLinkSession) {
    val run = game.run
    val won = run.phase == LinkPhase.COMPLETE
    val again = remember { FocusRequester() }
    LaunchedEffect(game) { focusSoon({ again.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        Column(Modifier.padding(start = 96.dp, top = 80.dp)) {
            Eyebrow(listOf("Film Link", if (won) "Complete" else "Gave up"), color = Cin.TEAL)
            T(if (won) "You got there." else "Not this time.", ts(120, Cin.CREAM, serif = true, weight = 600), maxLines = 1, modifier = Modifier.padding(top = 22.dp))
        }
        Row(Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 90.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat("HOPS", run.hops.toString())
            if (run.timed) Stat("TIME", clock(run.elapsedMs()))
        }
        T(if (won) "YOUR ROUTE" else "YOUR ROUTE SO FAR", ts(21, Cin.MUTED, weight = 600, tracking = 0.2f), modifier = Modifier.padding(start = 96.dp, top = 338.dp))
        val films = run.route + if (won) emptyList() else listOf(run.target)
        Row(Modifier.padding(top = 392.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 96.dp)) {
            films.forEachIndexed { i, f ->
                val isTarget = f.tmdbId == run.target.tmdbId
                if (i > 0) Box(Modifier.width(112.dp).height(330.dp), contentAlignment = Alignment.Center) { T(if (i < run.route.size) "→" else "⋯", ts(64, Cin.TEAL, serif = true)) }
                Column(Modifier.width(220.dp)) {
                    RemoteImage(f.posterUrl(), Modifier.size(220.dp, 330.dp).alpha(if (isTarget && !won) 0.6f else 1f).clip(RoundedCornerShape(16.dp)), fallback = f.name)
                    T(f.name, ts(26, Cin.CREAM, serif = true, weight = 600), maxLines = 2, modifier = Modifier.padding(top = 16.dp))
                    val sub = listOfNotNull(when { i == 0 -> "Start"; isTarget -> if (won) "Target" else "Target, not reached"; else -> null }, f.year?.toString()).joinToString(" · ")
                    T(sub, ts(20, if (isTarget) Cin.TEAL else Cin.MUTED), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 44.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CinemaButton("Play again", ButtonKind.PRIMARY, requester = again) { model.filmLink.again(same = false) }
            CinemaButton("Same pair, new try", ButtonKind.GLASS) { model.filmLink.again(same = true) }
            CinemaButton("Exit", ButtonKind.GLASS) { model.filmLink.exit() }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xD1180F0B)).padding(horizontal = 40.dp, vertical = 22.dp)) {
        T(label, ts(19, Cin.MUTED, weight = 600, tracking = 0.2f))
        T(value, ts(88, Cin.CREAM, serif = true, weight = 600))
    }
}

// --- The start menu ----------------------------------------------------------------------------------------------

private enum class Slot { START, FINISH }

/**
 * The "now showing" start menu: marquee, film strips, the title over the stripe ribbon, a Start and a Finish card that
 * follow the choices (each with a line about its film, so one you don't know can still be imagined), and the options.
 * "Random film" shuffles through posters and lands on a film; press it again to reshuffle.
 */
@Composable
private fun LinkMenu(model: CinemaModel) {
    val fl = model.filmLink
    val here = fl.here?.film
    val scope = rememberCoroutineScope()
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon({ play.tryFocus() }) }

    var startHere by remember { mutableStateOf(here != null) }
    var chooseFinish by remember { mutableStateOf(false) }
    var timed by remember { mutableStateOf(false) }
    var startLanded by remember { mutableStateOf<LinkFilm?>(null) }
    var finishLanded by remember { mutableStateOf<LinkFilm?>(null) }
    var startShown by remember { mutableStateOf<LinkFilm?>(null) }
    var finishShown by remember { mutableStateOf<LinkFilm?>(null) }
    var seeds by remember { mutableStateOf<List<LinkFilm>>(emptyList()) }
    val jobs = remember { HashMap<Slot, Job>() }
    val images = LocalImages.current

    LaunchedEffect(Unit) {
        fl.loadSeeds { seeds = it }
    }
    // Posters ready before the first spin, so the shuffle doesn't flicker through blanks.
    LaunchedEffect(seeds) { withContext(Dispatchers.IO) { seeds.take(30).forEach { f -> f.posterUrl()?.let { runCatching { images.load(it) } } } } }

    fun shuffle(slot: Slot) {
        if (seeds.isEmpty()) { model.toast("Couldn't fetch films to shuffle. Check Seerr and try again"); return }
        if (slot == Slot.START) startHere = false else chooseFinish = false
        jobs[slot]?.cancel()
        jobs[slot] = scope.launch {
            fun set(f: LinkFilm?, landed: Boolean) {
                if (slot == Slot.START) { startShown = f; if (landed) startLanded = f } else { finishShown = f; if (landed) finishLanded = f }
            }
            if (slot == Slot.START) startLanded = null else finishLanded = null
            val steps = 20
            for (i in 0 until steps) { set(seeds.random(), false); delay(50L + (i * i) / 3) }
            val avoid = setOfNotNull(here?.tmdbId, if (slot == Slot.START) finishLanded?.tmdbId else startLanded?.tmdbId)
            set((seeds.filter { it.tmdbId !in avoid }.ifEmpty { seeds }).random(), true)
            jobs.remove(slot)
        }
    }

    val startFilm: LinkFilm? = if (startHere) here else startShown
    val finishFilm: LinkFilm? = if (chooseFinish) null else finishShown

    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Cin.TEAL.copy(alpha = 0.20f), Color.Transparent), Offset(size.width * 0.12f, size.height * 0.95f), size.width * 0.5f))
            drawRect(Brush.radialGradient(listOf(Cin.TERRA.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.05f), size.width * 0.45f))
        }
        Marquee(Alignment.TopCenter); Marquee(Alignment.BottomCenter)
        FilmStrip(Alignment.CenterStart); FilmStrip(Alignment.CenterEnd)

        Row(Modifier.fillMaxSize().padding(start = 150.dp, end = 150.dp, top = 96.dp, bottom = 70.dp), horizontalArrangement = Arrangement.spacedBy(70.dp)) {
            Column(Modifier.width(720.dp)) {
                Eyebrow(listOf("Now showing"))
                T("Film Link", titleStyle(150), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp).width(520.dp).height(14.dp).clip(RoundedCornerShape(7.dp))) { Cin.STRIPES.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) } }
                T("Start anywhere. Reach the target. Fewest hops wins.", ts(27, Cin.READING, weight = 300), modifier = Modifier.padding(top = 22.dp))
                Row(Modifier.padding(top = 44.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    LinkCard("START", startFilm, shuffling = jobs[Slot.START]?.isActive == true && startLanded == null && !startHere)
                    LinkCard("FINISH", finishFilm, shuffling = jobs[Slot.FINISH]?.isActive == true && finishLanded == null && !chooseFinish, blank = if (chooseFinish) "You'll pick it in Search" else "A random film")
                }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(40.dp)).background(Cin.GLASS).padding(44.dp)) {
                T("Set up your run", ts(44, Cin.CREAM, serif = true))
                Group("START") {
                    if (here != null) Choice("This film", startHere) { startHere = true }
                    Choice("Random film", !startHere, icon = "shuffle") { shuffle(Slot.START) }
                }
                Group("FINISH") {
                    Choice("Choose a film", chooseFinish) { chooseFinish = true; jobs.remove(Slot.FINISH)?.cancel() }
                    Choice("Random film", !chooseFinish, icon = "shuffle") { shuffle(Slot.FINISH) }
                }
                Group("CLOCK") {
                    Choice("No clock", !timed) { timed = false }
                    Choice("Timed", timed) { timed = true }
                }
                Row(Modifier.padding(top = 44.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CinemaButton("Play", ButtonKind.TERRA, "play", requester = play) {
                        jobs.values.forEach { it.cancel() }
                        fl.play(startFilm.takeIf { startHere || startLanded != null }, startHere, finishFilm.takeIf { finishLanded != null }, chooseFinish, timed)
                    }
                    CinemaButton("Cancel", ButtonKind.GLASS) { fl.cancelMenu() }
                }
            }
        }
    }
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    T(label, ts(18, Cin.MUTED, weight = 600, tracking = 0.2f), modifier = Modifier.padding(top = 30.dp, bottom = 12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** An option chip: ticked when chosen, cream when focused. [icon] sits before the words. */
@Composable
private fun Choice(label: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) Cin.INK else Cin.CREAM
    Row(
        Modifier.height(56.dp).clip(RoundedCornerShape(50)).background(if (focused) Cin.CREAM else if (selected) Cin.GLASS_STRONG else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable { onClick() }.padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, 26.dp, if (focused) Cin.INK else Cin.TEAL); Spacer(Modifier.width(10.dp)) }
        else if (selected) { Icon("check", 20.dp, if (focused) Cin.INK else Cin.SAFFRON); Spacer(Modifier.width(8.dp)) }
        T(label, ts(23, ink, weight = if (selected) 700 else 600), maxLines = 1)
        if (icon != null && selected) { Spacer(Modifier.width(8.dp)); Icon("check", 20.dp, if (focused) Cin.INK else Cin.SAFFRON) }
    }
}

/** START or FINISH: the poster (or a "?" before anything is chosen), its name and a short description of the film. */
@Composable
private fun LinkCard(label: String, film: LinkFilm?, shuffling: Boolean, blank: String = "A random film") {
    val pulse by animateFloatAsState(if (shuffling) 1.05f else 1f, tween(120))
    Column(Modifier.width(300.dp)) {
        T(label, ts(18, Cin.TEAL, weight = 600, tracking = 0.2f), modifier = Modifier.padding(bottom = 12.dp))
        Box(Modifier.size(220.dp, 330.dp).scale(pulse).clip(RoundedCornerShape(16.dp)).background(Cin.PLACEHOLDER), contentAlignment = Alignment.Center) {
            if (film != null) RemoteImage(film.posterUrl(), Modifier.matchParentSize(), fallback = film.name)
            else T("?", ts(150, Cin.DIM, serif = true))
        }
        T(if (shuffling) "Shuffling…" else film?.name ?: blank, ts(26, Cin.CREAM, serif = true, weight = 600), maxLines = 2, modifier = Modifier.padding(top = 16.dp))
        T(listOfNotNull(film?.year?.toString().takeIf { !shuffling }).joinToString(""), ts(19, Cin.MUTED), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        T(if (shuffling) "" else film?.overview.orEmpty(), ts(17, Cin.READING, weight = 300, lineHeight = 1.3f), maxLines = 4, modifier = Modifier.padding(top = 6.dp).height(96.dp))
    }
}

@Composable
private fun Marquee(at: Alignment) {
    val phase by produceState(0) { while (true) { delay(650); value = (value + 1) % 2 } }
    Box(Modifier.fillMaxSize(), contentAlignment = at) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(38) { i -> Box(Modifier.size(12.dp).clip(CircleShape).background(Cin.SAFFRON.copy(alpha = if ((i + phase) % 2 == 0) 0.95f else 0.22f))) }
        }
    }
}

@Composable
private fun FilmStrip(at: Alignment) {
    Box(Modifier.fillMaxSize(), contentAlignment = at) {
        Column(Modifier.width(64.dp).fillMaxHeight().background(Color(0xFF140E0A)).padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            repeat(30) { Box(Modifier.size(22.dp, 32.dp).clip(RoundedCornerShape(6.dp)).background(Cin.BG)) }
        }
    }
}
