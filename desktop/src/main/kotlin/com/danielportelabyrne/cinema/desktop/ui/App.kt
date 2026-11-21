package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Dest
import com.danielportelabyrne.cinema.desktop.Page
import com.danielportelabyrne.cinema.desktop.Route
import com.danielportelabyrne.cinema.desktop.input.KeyMap
import com.danielportelabyrne.cinema.desktop.input.NavAction
import com.danielportelabyrne.cinema.desktop.input.NavDeduper
import com.danielportelabyrne.cinema.desktop.input.NavSource
import com.danielportelabyrne.cinema.desktop.jellyfin.clock
import com.danielportelabyrne.cinema.desktop.player.PlayerControls
import com.danielportelabyrne.cinema.desktop.player.PlayerState
import kotlinx.coroutines.channels.Channel

/** The design frame: the TV mockups' 1920×1080. */
const val DESIGN_W = 1920f
const val DESIGN_H = 1080f

/**
 * Draws the whole UI in the mockups' pixels and scales it to the window, so the Deck (1280×800) shows the TV's layout
 * at two thirds, and a 4K TV at double. On a taller screen than 16:9 (the Deck is 16:10) the frame grows taller
 * rather than letterboxing.
 */
@Composable
fun DeckScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Cin.BG), contentAlignment = Alignment.Center) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val scale = minOf(wPx / DESIGN_W, hPx / DESIGN_H)
        Fonts.renderScale = scale
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Box(Modifier.requiredSize((wPx / scale).dp, (hPx / scale).dp)) { content() }
        }
    }
}

/**
 * Every press comes through here, from the keyboard or (via [actions]) from a gamepad. In order: the player when a
 * film is on, an open keyboard or sheet, the current screen's own keys, then focus movement and A/B.
 */
class Dispatcher(private val model: CinemaModel, val hub: FocusHub) {
    val actions = Channel<NavAction>(Channel.UNLIMITED)
    private val dedupe = NavDeduper()
    lateinit var focus: FocusManager

    fun fromPad(action: NavAction) {
        if (!dedupe.accept(action, NavSource.GAMEPAD)) return
        // The player runs in mpv's window, so the pad goes straight there without waiting for the UI thread.
        if (model.player.active) { model.player.handle(action); return }
        actions.trySend(action)
    }

    private val pageCanType get() = model.keyboard == null && model.sheet == null && model.route == Route.Browse &&
        !model.confirmQuit && model.streamLoading == null && model.playerState is PlayerState.Idle
    private val typingSearch get() = pageCanType && model.page == Page.Search

    fun key(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val req = model.keyboard
        // Typing on a real keyboard goes to whatever is collecting text.
        val target = if (pageCanType) model.typingTarget else null
        if ((req != null || typingSearch || target != null) && !event.isCtrlPressed && !event.isAltPressed) {
            fun put(c: Char) { when { req != null -> req.typeRaw(c); target != null -> target(c); else -> model.search = (model.search + c).take(60) } }
            when {
                event.key == Key.Backspace -> { when { req != null -> req.backspace(); target != null -> target(null); else -> model.search = model.search.dropLast(1) }; return true }
                else -> KeyMap.character(event)?.let { put(it); return true }
            }
        }
        val action = KeyMap.action(event) ?: return false
        if (!dedupe.accept(action, NavSource.KEYBOARD)) return true
        handle(action)
        return true
    }

    fun handle(action: NavAction) {
        if (model.player.active) { model.player.handle(action); return }
        model.keyboard?.let { req ->
            if (action == NavAction.BACK) { model.keyboard = null; return }
        }
        // The stream loading screen has nothing to select: only Back, which cancels.
        if (model.streamLoading != null) { if (action == NavAction.BACK) model.back(); return }
        if (model.keyboard == null && model.sheet == null && !model.filmLink.menuOpen && model.filmLink.onKey(action)) return
        if (model.keyboard == null && model.sheet == null && !model.filmLink.menuOpen && screenKey(action)) return
        when (action) {
            NavAction.UP -> move(FocusDirection.Up)
            NavAction.DOWN -> move(FocusDirection.Down)
            NavAction.LEFT -> move(FocusDirection.Left)
            NavAction.RIGHT -> move(FocusDirection.Right)
            NavAction.SELECT -> hub.select()
            NavAction.ALT -> hub.alt()
            NavAction.BACK -> if (inBrowse() && model.railOpen) leaveRail() else model.back()
            else -> {}
        }
    }

    private fun inBrowse() = model.route == Route.Browse && model.keyboard == null && model.sheet == null && !model.confirmQuit

    private fun move(direction: FocusDirection) {
        val browse = inBrowse()
        // In the rail: Right goes back to where you were on the page, Left goes nowhere (as on the TV).
        if (browse && model.railOpen) {
            when (direction) {
                FocusDirection.Right -> { leaveRail(); return }
                FocusDirection.Left -> return
            }
        }
        // The menu is only reached by Left from the page's left edge. Up and Down never enter it, or a press at the
        // bottom of a page would land on Settings, and Up from there could not get back to the page.
        if (browse && !model.railOpen && direction == FocusDirection.Left) {
            if (!focus.moveFocus(direction)) model.openRail()
            return
        }
        if (!focus.moveFocus(direction)) {
            // Nothing focused yet: start from the screen's first element. At an edge of the page the press does nothing.
            if (!hub.hasFocus) focus.moveFocus(FocusDirection.Enter)
            return
        }
        // Arriving in the rail from the page lands on the page you are on, not the nearest icon.
        if (browse && model.railOpen && direction == FocusDirection.Left) model.railFocus.getValue(model.page.rail).tryFocus()
    }

    private fun leaveRail() {
        if (!model.state(model.page).focus.restore() && !model.pageEntry()) focus.moveFocus(FocusDirection.Right)
    }

    /** Keys that mean something on the current screen beyond moving and selecting. */
    private fun screenKey(action: NavAction): Boolean {
        if (model.confirmQuit || model.playerState is PlayerState.Failed || model.setup.quickConnectOpen) return false
        when (model.route) {
            Route.Browse -> {
                if (typingSearch) when (action) {
                    NavAction.ALT -> { model.search = model.search.dropLast(1); return true }
                    NavAction.SEARCH -> { model.search += " "; return true }
                    else -> {}
                }
                val order = Dest.entries.filter { it != Dest.SETTINGS }
                when (action) {
                    NavAction.PREV, NavAction.NEXT -> {
                        val i = order.indexOf(model.page.rail).coerceAtLeast(0)
                        model.goRail(order[(i + (if (action == NavAction.NEXT) 1 else order.size - 1)) % order.size]); return true
                    }
                    NavAction.SEARCH -> { model.goRail(Dest.SEARCH); return true }
                    NavAction.MENU -> { model.openSettings(); return true }
                    else -> {}
                }
            }
            is Route.Title -> when (action) {
                NavAction.MENU -> { model.openSettings(); return true }
                NavAction.SEARCH -> { model.goRail(Dest.SEARCH); return true }
                else -> {}
            }
            Route.Setup -> {}
        }
        return false
    }
}
@Composable
fun CinemaApp(model: CinemaModel, dispatcher: Dispatcher) {
    val hub = dispatcher.hub
    val art = remember { Art { model.client.server } }
    dispatcher.focus = LocalFocusManager.current
    LaunchedEffect(dispatcher) { for (a in dispatcher.actions) dispatcher.handle(a) }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.tryFocus() }

    CompositionLocalProvider(LocalFocusHub provides hub, LocalImages provides model.images) {
        DeckScale {
            Box(Modifier.fillMaxSize().focusRequester(rootFocus).focusTarget()) {
                // Under the Film Link menu or summary the page is out of reach of the D-pad.
                val overlay = model.filmLink.menuOpen || model.filmLink.game?.finished == true
                Box(Modifier.fillMaxSize().then(if (overlay) Modifier.focusProperties { canFocus = false } else Modifier)) {
                    when (val route = model.route) {
                        Route.Setup -> SetupScreen(model)
                        Route.Browse -> BrowseScreen(model, art)
                        is Route.Title -> TitleScreen(model, route, art)
                    }
                }
                FilmLinkLayer(model)
                PlayerOverlay(model)
                model.keyboard?.let { req -> KeyboardSheet(req) { req.onDone(it) } }
                if (model.confirmQuit) Dialog("Quit Cinema?", null, "Quit" to { model.quit() }, "Stay" to { model.confirmQuit = false })
                model.streamLoading?.let { StreamLoadingScreen(it) }
                model.sheet?.let { InfoSheet(model, it) }
                ToastLayer(model.toast)
            }
        }
    }
}

/** Behind mpv's window: what is playing and how to drive it, should the viewer switch back to the app. */
@Composable
private fun PlayerOverlay(model: CinemaModel) {
    when (val s = model.playerState) {
        PlayerState.Idle -> {}
        is PlayerState.Failed -> Dialog("Couldn't play that", s.message, "OK" to { model.playerState = PlayerState.Idle })
        is PlayerState.Starting, is PlayerState.Playing -> {
            val title = (s as? PlayerState.Starting)?.title ?: (s as PlayerState.Playing).title
            Box(Modifier.fillMaxSize()) {
                Ambient()
                Column(Modifier.align(Alignment.CenterStart).padding(start = 200.dp, end = 200.dp)) {
                    Eyebrow(listOf(if (s is PlayerState.Starting) "Starting" else if ((s as PlayerState.Playing).paused) "Paused" else "Now playing"))
                    T(title, titleStyle(84), maxLines = 2, modifier = Modifier.padding(top = 20.dp))
                    if (s is PlayerState.Playing && s.durationMs > 0) {
                        Box(Modifier.padding(top = 40.dp).width(1100.dp)) { ProgressLine(s.positionMs.toFloat() / s.durationMs) }
                        T("${clock(s.positionMs)} of ${clock(s.durationMs)}", ts(23, Cin.MUTED), modifier = Modifier.padding(top = 16.dp))
                    }
                    Row(Modifier.padding(top = 60.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        PlayerControls.help.chunked(5).forEach { col ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { col.forEach { KeyHints(listOf(it)) } }
                        }
                    }
                }
            }
        }
    }
}

/** A sheet asking one thing: serif title, a line of explanation, and its buttons (the first is focused). */
@Composable
fun Dialog(title: String, body: String?, vararg buttons: Pair<String, () -> Unit>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(title) { focusSoon({ first.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Cin.BG.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(min = 720.dp, max = 1120.dp).clip(RoundedCornerShape(60.dp)).background(Cin.SHEET).padding(horizontal = 70.dp, vertical = 64.dp)) {
            T(title, ts(56, Cin.CREAM, serif = true))
            body?.let { T(it, ts(28, Cin.KEY_TEXT, lineHeight = 1.3f), modifier = Modifier.padding(top = 18.dp)) }
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                buttons.forEachIndexed { i, (label, action) ->
                    CinemaButton(label, if (i == 0) ButtonKind.PRIMARY else ButtonKind.GLASS, requester = if (i == 0) first else null) { action() }
                }
            }
        }
    }
}
