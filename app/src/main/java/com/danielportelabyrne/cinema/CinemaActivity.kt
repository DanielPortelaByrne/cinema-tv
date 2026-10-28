package com.danielportelabyrne.cinema

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cinema)
        overlay = findViewById(R.id.moviesOverlay)
        infoSheet = InfoSheet(findViewById(R.id.infoSheet))
        val platform = AndroidPlatform(this)
        val settings = CinemaSettings(platform)
        val preset = Preset.load(platform)?.also { it.applySettings(platform, settings) }
        Catalog.country = settings.region
        jellyfin = JellyfinClient(AndroidPlatform(this))
        val seerr = SeerrClient(AndroidPlatform(this))
        setup = SetupFlow(this, findViewById(R.id.setupHost), infoSheet, jellyfin, seerr, settings) { changed ->
            if (changed) { moviesPanel.reload(); if (moviesPanel.isVisible) moviesPanel.hide() }
            if (!moviesPanel.isVisible) moviesPanel.show()
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
            music = { settings.subsonic }
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
            if (!settings.setupDone || !jellyfin.hasServer) setup.startFirstRun() else moviesPanel.show()
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
            if (payload == null || !PlayerActivity.resume(this, payload)) Toast.makeText(this, "Couldn't reopen $title", Toast.LENGTH_LONG).show()
            return
        }
        ContinueWatchingContract.jellyfinItemId(key)?.let { id ->
            Thread {
                val item = jellyfin.item(id)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (item == null) Toast.makeText(this, "Couldn't reach Jellyfin for that title", Toast.LENGTH_LONG).show()
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
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
                        // Focus strayed behind, or the container itself took it (slow devices): hand it to a child.
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
