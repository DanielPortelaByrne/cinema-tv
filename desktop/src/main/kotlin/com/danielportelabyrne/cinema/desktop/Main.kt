package com.danielportelabyrne.cinema.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.danielportelabyrne.cinema.desktop.input.Gamepads
import com.danielportelabyrne.cinema.desktop.ui.CinemaApp
import com.danielportelabyrne.cinema.desktop.ui.Dispatcher
import com.danielportelabyrne.cinema.desktop.ui.FocusHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.system.exitProcess

/**
 * Cinema on a desktop. Full screen by default (the Steam Deck, a TV); CINEMA_WINDOWED=1 opens a 1280×800 window
 * instead, the Deck's own size, which is handy on a desktop.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--self-test") exitProcess(SelfTest.run(args.getOrNull(1)))
    if (args.firstOrNull() == "--player-demo") exitProcess(PlayerDemo.run(args.getOrNull(1) ?: "av://lavfi:testsrc2=size=1280x720:rate=24:duration=3000"))
    val windowed = System.getenv("CINEMA_WINDOWED") == "1"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val model = CinemaModel(DesktopPlatform(), scope)
    val dispatcher = Dispatcher(model, FocusHub())
    val pads = Gamepads { dispatcher.fromPad(it) }

    application {
        model.onQuit = { pads.stop(); exitApplication() }
        model.pads = { pads.connected }
        val state = rememberWindowState(
            placement = if (windowed) WindowPlacement.Floating else WindowPlacement.Fullscreen,
            size = DpSize(1280.dp, 800.dp),
        )
        Window(
            onCloseRequest = { model.quit() },
            state = state,
            title = "Cinema",
            // Window-level handling also receives the first key while a new page is acquiring focus.
            onPreviewKeyEvent = { dispatcher.key(it) },
            undecorated = !windowed,
            icon = com.danielportelabyrne.cinema.desktop.ui.Icons.get("cinema_mark")?.let { androidx.compose.ui.graphics.vector.rememberVectorPainter(it) },
        ) {
            awtWindow = window
            CinemaApp(model, dispatcher)
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            model.start()
            pads.start()
            DebugHooks.start({ awtWindow }) { dispatcher.fromPad(it) }
        }
    }
    exitProcess(0)
}

@Volatile private var awtWindow: java.awt.Window? = null
