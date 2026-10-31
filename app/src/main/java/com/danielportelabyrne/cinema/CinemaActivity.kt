package com.danielportelabyrne.cinema

import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import com.danielportelabyrne.cinema.design.cinema.OpeningState
import com.danielportelabyrne.cinema.design.cinema.SplashView
import com.danielportelabyrne.cinema.device.AndroidPlatform
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.danielportelabyrne.cinema.data.ContinueWatching
import com.danielportelabyrne.cinema.data.ContinueWatchingContract
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Preset
import com.danielportelabyrne.cinema.ui.JellyfinPlayback
import com.danielportelabyrne.cinema.ui.MoviesPanel
import com.danielportelabyrne.cinema.ui.Perf
import com.danielportelabyrne.cinema.ui.PlayerActivity
import com.danielportelabyrne.cinema.ui.phone.PhoneInput
import com.danielportelabyrne.cinema.ui.room.InfoSheet
import com.danielportelabyrne.cinema.ui.room.JellyfinConnectSheet
import com.danielportelabyrne.cinema.ui.room.SeerrSignInSheet
import com.danielportelabyrne.cinema.ui.setup.SetupFlow

/**
 * The whole app: Cinema full screen, with first-run setup before it and Settings over it. Back walks back through
 * Cinema's pages and leaves the app from its Home page.
 */
class CinemaActivity : AppCompatActivity() {
    private lateinit var infoSheet: InfoSheet
    private lateinit var moviesPanel: MoviesPanel
    private lateinit var overlay: ViewGroup
    private lateinit var setup: SetupFlow
    private lateinit var jellyfin: JellyfinClient
    private var splash: SplashView? = null
    private var quietOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cinema)
        // A restored screen, a Continue watching launch or a debug smoke test goes straight in; otherwise the first Movies open after the app starts plays the opening.
        quietOpen = savedInstanceState != null || intent?.action == ContinueWatchingContract.actionResume(packageName) ||
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent?.extras?.keySet()?.any { it.startsWith("debug_") } == true)
        // The phone typing session lives as long as this screen does; every text field in the app shares it.
        PhoneInput.attach(this)
        overlay = findViewById(R.id.moviesOverlay)
        infoSheet = InfoSheet(findViewById(R.id.infoSheet))
        val platform = AndroidPlatform(this)
        val settings = CinemaSettings(platform)
        val addonSync = com.danielportelabyrne.cinema.data.stremio.StremioAddonSync(
            com.danielportelabyrne.cinema.data.stremio.StremioClient()::addons, android.os.SystemClock::elapsedRealtime)
        val preset = Preset.load(platform)?.also { it.applySettings(platform, settings) }
        Catalog.country = settings.region
        jellyfin = JellyfinClient(AndroidPlatform(this))
        jellyfin.onReachabilityChanged = { reachable -> runOnUiThread {
            if (!isFinishing && !isDestroyed) CinemaToast.show(
                this,
                if (reachable) "Jellyfin server connection restored" else "Can't reach the Jellyfin server",
                Toast.LENGTH_LONG,
            )
        } }
        val seerr = SeerrClient(AndroidPlatform(this))
        setup = SetupFlow(this, findViewById(R.id.setupHost), infoSheet, jellyfin, seerr, settings) { changed ->
            if (changed) { moviesPanel.reload(); if (moviesPanel.isVisible) moviesPanel.hide() }
            if (!moviesPanel.isVisible) openMovies()
            else returnFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
            returnFocus = null
        }
        moviesPanel = MoviesPanel(
            activity = this,
            overlay = overlay,
            body = findViewById(R.id.moviesBody),
            top = findViewById(R.id.moviesTop),
            page = findViewById(R.id.moviesPage),
            content = findViewById(R.id.moviesContent),
            detail = findViewById(R.id.movieDetail),
            jellyfin = jellyfin,
            jellyfinSheet = JellyfinConnectSheet(this, infoSheet) { done -> overCinema { setup.signInToJellyfin(done) } },
            seerr = seerr,
            seerrSheet = SeerrSignInSheet(this, infoSheet) { done -> overCinema { setup.signInToSeerr(done) } },
            infoSheet = infoSheet,
            libraryOrder = { settings.libraryOrder },
            onOpenSettings = { moviesPanel.hide(); setup.openSettings() },
            integrationOn = { settings.isOn(it) },
            music = { settings.subsonic },
            // Asked off the main thread when something is streamed: follows the linked account, not only a manual "Port".
            stremioAddons = { addonSync.current(settings.stremioKey, settings.stremioAddons) { settings.stremioAddons = it } },
            streamProgress = { settings.streamProgress },
            recentSearches = { settings.recentSearches(it) },
            saveRecentSearches = { keyboard, terms -> settings.setRecentSearches(keyboard, terms) },
            watchlist = { settings.watchlist },
            saveWatchlist = { settings.watchlist = it }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    infoSheet.isVisible -> infoSheet.hide()
                    setup.isVisible -> if (!setup.back()) finish()
                    moviesPanel.isVisible -> if (!moviesPanel.back()) finish()
                    else -> finish()
                }
            }
        })

        val start = {
            // Nothing to show until there is a Jellyfin server: first run, or after Reset everything.
            when {
                !settings.setupDone || !jellyfin.hasServer -> setup.startFirstRun()
                // The server is known but the sign-in was lost (a token rejected, or the app data partly wiped): say so.
                !jellyfin.isConnected -> setup.startSignBackIn()
                else -> openMovies()
            }
            handleDebugIntent(intent)
            handleResumeIntent(intent)
        }
        val testAccount = debugTestAccount(intent)
        if (testAccount != null) {
            Thread {
                jellyfin.setServer(testAccount.server)
                if (jellyfin.signInByName(testAccount.username, testAccount.password) == null) settings.setupDone = true
                runOnUiThread { if (!isDestroyed) start() }
            }.start()
        } else if (preset?.needsSignIn == true) {
            Thread {
                preset.signIn(jellyfin, seerr)
                runOnUiThread { if (!isDestroyed) start() }
            }.start()
        } else start()
    }

    /**
     * Opens the Movies page. When the opening is due (see [OpeningState]) it opens behind it: the opening plays first,
     * holds at full strength while the page is built underneath it, then fades over the finished page (building keeps
     * the main thread busy, which stalled the animation while the sound carried on). Otherwise, a restored screen and
     * animations switched off in the system settings go straight in.
     */
    private fun openMovies() {
        if (quietOpen || !OpeningState.due(this)) { quietOpen = false; moviesPanel.show(); return }
        splash = SplashView.show(findViewById<ViewGroup>(R.id.cinemaRoot), skip = false,
            covered = { if (!isDestroyed) openingHeld() }, done = { splash = null })
        if (splash == null) moviesPanel.show()
    }

    /** The opening has reached its held frame: record it if Cinema is really on screen, and build the page under it. */
    private fun openingHeld() {
        if (hasWindowFocus()) OpeningState.markShown(this)
        if (!moviesPanel.isVisible) moviesPanel.show()
    }

    private var startedOnce = false

    /**
     * Set when Cinema comes back to the front (after the TV was switched off, the opening plays over the page it was
     * left on). The opening waits for the window to actually have focus.
     */
    private var openingWaiting = false

    override fun onStart() {
        super.onStart()
        if (!startedOnce) { startedOnce = true; return }
        openingWaiting = true
    }

    /**
     * The opening plays once Cinema's window is really in front. The launcher can bring Cinema back to the top without it
     * being on screen (as it does when the TV wakes), and playing then put the sound over the launcher.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !openingWaiting) return
        openingWaiting = false
        if (::moviesPanel.isInitialized && moviesPanel.isVisible && !setup.isVisible && !infoSheet.isVisible && splash == null && OpeningState.due(this)) {
            splash = SplashView.show(findViewById<ViewGroup>(R.id.cinemaRoot), skip = false, covered = { if (!isDestroyed) openingHeld() }, done = { splash = null })
        }
    }

    private class TestAccount(val server: String, val username: String, val password: String)

    /**
     * Debug builds only: a Jellyfin test account passed as intent extras by the device smoke test, so its password is
     * never in the APK. Release builds ignore these extras.
     */
    private fun debugTestAccount(intent: android.content.Intent?): TestAccount? {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val server = intent?.getStringExtra("debug_jellyfin_server") ?: return null
        val username = intent.getStringExtra("debug_jellyfin_username") ?: return null
        val password = intent.getStringExtra("debug_jellyfin_password") ?: return null
        return TestAccount(server, username, password)
    }

    /** Where focus was in Cinema before a sign-in step opened over it, so it comes back to the same button. */
    private var returnFocus: View? = null

    /** A single sign-in step drawn over Cinema, which stays as it was underneath (a title page stays open). */
    private fun overCinema(open: () -> Unit) {
        returnFocus = currentFocus
        open()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
        handleResumeIntent(intent)
    }

    /**
     * Another app (the launcher's Continue watching row) asked to pick up a title where it was left. Opens the Jellyfin
     * film or episode, or the stream this app saved, straight into the player; the row's key says which.
     */
    private fun handleResumeIntent(intent: android.content.Intent?) {
        if (intent?.action != ContinueWatchingContract.actionResume(packageName)) return
        val key = intent.getStringExtra(ContinueWatchingContract.EXTRA_KEY) ?: return
        intent.action = null
        ContinueWatchingContract.streamTitle(key)?.let { title ->
            val payload = ContinueWatching(this).load(50).firstOrNull { it.title == title }?.payload
            if (payload == null || !PlayerActivity.resume(this, payload)) CinemaToast.show(this, "Couldn't reopen $title", Toast.LENGTH_LONG)
            return
        }
        ContinueWatchingContract.jellyfinItemId(key)?.let { id ->
            Thread {
                val item = jellyfin.item(id)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (item == null) CinemaToast.show(this, "Couldn't reach Jellyfin for that title", Toast.LENGTH_LONG)
                    else JellyfinPlayback.play(this, jellyfin, item, listOfNotNull(item.displayTitle, item.episodeLabel()).joinToString(" · "), item.resumeKey)
                }
            }.start()
        }
    }

    /** Debug builds only: `--es debug_cinema_dest <name>` opens a page directly, for screenshots. */
    private fun handleDebugIntent(intent: android.content.Intent?) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        intent?.getStringExtra("debug_setup_step")?.let { if (moviesPanel.isVisible) moviesPanel.hide(); setup.debugOpen(it); return }
        if (setup.isVisible) return
        intent?.getStringExtra("debug_cinema_dest")?.let { moviesPanel.debugGo(it) }
    }

    override fun onResume() {
        super.onResume()
        if (::moviesPanel.isInitialized) moviesPanel.onResume()
    }

    override fun onDestroy() {
        if (::moviesPanel.isInitialized) moviesPanel.close()
        if (::setup.isInitialized) setup.shutdown()
        PhoneInput.detach(this)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While the opening plays, any key skips it (volume keys still work); nothing underneath sees the key.
        splash?.takeIf { it.isShowing }?.let {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) it.skip()
            return true
        }
        // The remote's voice key, if the TV hands it to apps at all: it opens Search and listens while held.
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH || event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || event.keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (::moviesPanel.isInitialized && moviesPanel.isVisible && !infoSheet.isVisible && !setup.isVisible &&
                moviesPanel.voiceKey(event.action == KeyEvent.ACTION_DOWN, event.repeatCount)) return true
        }
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> 0
        }
        if (event.action == KeyEvent.ACTION_DOWN && direction != 0 && ::moviesPanel.isInitialized) {
            val target: ViewGroup? = when {
                infoSheet.isVisible && !infoSheet.isClosing -> findViewById(R.id.infoSheet)
                findViewById<View>(R.id.setupHost).visibility == View.VISIBLE -> findViewById(R.id.setupHost)
                moviesPanel.isVisible -> overlay
                else -> null
            }
            if (target != null) {
                val focused = currentFocus
                val editingText = focused is EditText && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)
                val scrolling = focused is ScrollView &&
                    ((direction == View.FOCUS_DOWN && focused.canScrollVertically(1)) || (direction == View.FOCUS_UP && focused.canScrollVertically(-1)))
                if (!editingText && !scrolling) {
                    if (focused == null || focused === target || target.findFocus() == null) {
                        // Focus strayed behind, or the container itself took it (slow devices, a page rebuilt under the card that
                        // had it): hand it to a child. On a Movies page that child is one of its cards, not whatever the search
                        // finds first, which for Down was the rail.
                        if (target === overlay && moviesPanel.recoverFocus(direction)) return true
                        (FocusFinder.getInstance().findNextFocus(target, null, direction) ?: target).requestFocus()
                    } else if (target === overlay && moviesPanel.navigate(focused, direction)) {
                        // The rail and the loading screen move focus themselves.
                    } else if (target === overlay) {
                        // Cinema has its own focus rules, so ask the views themselves, staying inside the page.
                        focused.focusSearch(direction)?.takeIf { it !== focused && isInside(it, target) }?.requestFocus(direction)
                    } else {
                        FocusFinder.getInstance().findNextFocus(target, focused, direction)?.requestFocus(direction)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInside(view: View, parent: View): Boolean {
        var node: android.view.ViewParent? = view.parent
        while (node != null) { if (node === parent) return true; node = node.parent }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::moviesPanel.isInitialized) moviesPanel.onPermissionResult(requestCode, grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
}
