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
    /** Cinema updating itself from its GitHub releases: offered when a newer build is out, and from Settings. */
    private lateinit var updates: com.danielportelabyrne.cinema.update.UpdatePrompt
    private var quietOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cinema)
        // A restored screen, a Continue watching launch or a debug smoke test goes straight in; otherwise the first Movies open after the app starts plays the opening.
        // Changing person reopens Cinema straight into the page, without the opening playing again.
        quietOpen = savedInstanceState != null || intent?.hasExtra(EXTRA_ASK) == true || intent?.hasExtra(EXTRA_PERSON) == true || intent?.action == ContinueWatchingContract.actionResume(packageName) ||
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent?.extras?.keySet()?.any { it.startsWith("debug_") } == true)
        // The phone typing session lives as long as this screen does; every text field in the app shares it.
        PhoneInput.attach(this)
        overlay = findViewById(R.id.moviesOverlay)
        infoSheet = InfoSheet(findViewById(R.id.infoSheet))
        updates = com.danielportelabyrne.cinema.update.UpdatePrompt(this, infoSheet)
        updates.sayIfUpdated()
        choosePerson(savedInstanceState) { startCinema() }
    }

    // --- Who is watching ---------------------------------------------------------------------------------------

    private var whoScreen: com.danielportelabyrne.cinema.ui.account.WhoScreen? = null
    private var syncer: com.danielportelabyrne.cinema.data.account.ProfileSyncer? = null

    /**
     * Settles who is watching before anything personal is opened, then runs [go]. The TV launcher's chosen account
     * decides it when Cinema knows who that is; otherwise "Who's watching?" asks. A restored screen, a launch to resume
     * something, or a TV with one person and no launcher to ask goes straight in as the person last in use.
     */
    private fun choosePerson(saved: Bundle?, go: () -> Unit) {
        val platform = AndroidPlatform(this)
        val accounts = com.danielportelabyrne.cinema.data.account.AccountStore(platform)
        val jf = JellyfinClient(platform)
        if (saved != null || !jf.isConnected) { android.util.Log.w("Accounts", "launch: straight in (restored=${saved != null})"); accounts.applyScope(); go(); return }
        Thread {
            // The person signed in before people existed becomes the first one, and keeps everything that was here.
            if (accounts.all().isEmpty()) jf.savedSession()?.let { s ->
                accounts.upsert(com.danielportelabyrne.cinema.data.account.CinemaAccount(s.userId, jf.userName(s) ?: "Me"), s.token)
                accounts.activate(s.userId)
            }
            // A one-off asked for over adb: start one person as a copy of another (see copyPerson).
            val copyFrom = intent?.getStringExtra(EXTRA_COPY_FROM); val copyTo = intent?.getStringExtra(EXTRA_COPY_TO)
            if (copyFrom != null && copyTo != null) { intent?.removeExtra(EXTRA_COPY_FROM); copyPerson(accounts, jf, platform, copyFrom, copyTo) }
            val launcher = com.danielportelabyrne.cinema.data.account.LauncherProfile.current(this)
            val asked = intent?.getStringExtra(EXTRA_PERSON)?.let { id -> accounts.all().firstOrNull { it.userId == id } }
            val matched = launcher?.let { accounts.forLauncher(it) }
            val resuming = intent?.action == ContinueWatchingContract.actionResume(packageName)
            android.util.Log.w("Accounts", "launch: ${accounts.all().size} people, launcher=${launcher?.name}, matched=${matched?.name}, asked=${asked?.name}, resuming=$resuming")
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val all = accounts.all()
                val direct = when {
                    intent?.getBooleanExtra(EXTRA_ASK, false) == true -> null
                    asked != null -> asked
                    matched != null -> matched
                    resuming -> accounts.active()
                    launcher == null && all.size == 1 -> all.first()
                    else -> null
                }
                if (direct != null) { launcher?.takeIf { asked == null }?.let { accounts.link(direct.userId, it) }; become(accounts, jf, direct, go); return@runOnUiThread }
                // Characters only for now: pictures from favourite titles (FavouriteCharacters) are left out of the picker.
                whoScreen = com.danielportelabyrne.cinema.ui.account.WhoScreen(this, findViewById(R.id.cinemaRoot), accounts, jf, infoSheet, launcher) { person ->
                    // Remembered only when it is plainly the same person; otherwise Cinema asks again next time.
                    launcher?.takeIf { com.danielportelabyrne.cinema.data.account.AccountStore.sameName(it, person) }?.let { accounts.link(person.userId, it) }
                    become(accounts, jf, person, go)
                }.also { it.show() }
            }
        }.start()
    }

    /** Signs Jellyfin in as [person], takes their saved copy from the server, then opens Cinema as them. */
    private fun become(accounts: com.danielportelabyrne.cinema.data.account.AccountStore, jf: JellyfinClient,
                       person: com.danielportelabyrne.cinema.data.account.CinemaAccount, go: () -> Unit) {
        // No greeting and no blank screen in between: "Who's watching?" stays as it is, ignoring keys, while Cinema is
        // built underneath, then fades away over it.
        whoScreen?.hold()
        Thread {
            accounts.activate(person.userId)
            val token = accounts.token(person.userId)
            if (token != null && jf.currentUserId != person.userId) jf.restoreSession(com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession(jf.server, token, person.userId))
            syncer = com.danielportelabyrne.cinema.data.account.ProfileSyncer(this, jf, person.userId).also { s -> runCatching { s.pull() } }
            // The character picked on another TV comes with the person's saved things.
            AndroidPlatform.personalPrefs(this, "cinema_personal").getString("avatar", null)?.let { a ->
                val picked = a.ifBlank { null }
                if (picked != person.avatar) accounts.setAvatar(person.userId, picked)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                go()
                whoScreen?.fadeOut(); whoScreen = null
                syncer?.start()
            }
        }.start()
    }

    /**
     * Makes [toName] start as a copy of [fromName] (people named as on "Who's watching?"): every personal file on this
     * TV, and their watched and part-watched state on Jellyfin. The copy reaches the other TV when [toName] is next in
     * use here (their saved copy is then updated). Blocks.
     */
    private fun copyPerson(accounts: com.danielportelabyrne.cinema.data.account.AccountStore, jf: JellyfinClient, platform: AndroidPlatform, fromName: String, toName: String) {
        val all = accounts.all()
        val from = all.firstOrNull { it.name.equals(fromName, ignoreCase = true) }
        val to = all.firstOrNull { it.name.equals(toName, ignoreCase = true) }
        if (from == null || to == null || from.userId == to.userId) { android.util.Log.w("Accounts", "copy: who? from=${from?.name} to=${to?.name}"); return }
        val original = accounts.originalUserId
        val values = com.danielportelabyrne.cinema.data.account.ProfileCopy.copy(
            { platform.forPerson(from.userId, from.userId == original).preferences(it) },
            { platform.forPerson(to.userId, to.userId == original).preferences(it) },
            if (from.userId == original) platform.preferences("cinema_settings") else null, from.userId, to.userId)
        // Their saved copy on Jellyfin is replaced with this one the next time they are in use.
        getSharedPreferences("accounts", MODE_PRIVATE).edit().putInt("pushed_${to.userId}", 0).apply()
        val a = accounts.token(from.userId); val b = accounts.token(to.userId)
        val items = if (a != null && b != null) jf.copyWatchState(
            com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession(jf.server, a, from.userId),
            com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession(jf.server, b, to.userId)) else -1
        android.util.Log.w("Accounts", "copy: ${from.name} to ${to.name}: $values values, $items watched items")
    }

    /** Opens Cinema again from "Who's watching?", to change who is watching. */
    fun switchPerson() {
        syncer?.pushNow()
        startActivity(android.content.Intent(this, CinemaActivity::class.java).putExtra(EXTRA_ASK, true)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        overridePendingTransition(0, 0)   // no window animation: nothing to flash between
    }

    /** Everything else, once the person is settled (their personal files are the ones opened from here on). */
    private fun startCinema() {
        val platform = AndroidPlatform(this)
        val settings = CinemaSettings(platform)
        val addonSync = com.danielportelabyrne.cinema.data.stremio.StremioAddonSync(
            com.danielportelabyrne.cinema.data.stremio.StremioClient()::addons, android.os.SystemClock::elapsedRealtime)
        val preset = Preset.load(platform)?.also { it.applySettings(platform, settings) }
        Catalog.country = settings.region
        Catalog.followed = settings.followedServices
        Catalog.followedChannels = settings.followedChannels
        jellyfin = JellyfinClient(AndroidPlatform(this))
        jellyfin.onReachabilityChanged = { reachable -> runOnUiThread {
            if (!isFinishing && !isDestroyed) CinemaToast.show(
                this,
                if (reachable) "Jellyfin server connection restored" else "Can't reach the Jellyfin server",
                Toast.LENGTH_LONG,
            )
        } }
        val seerr = SeerrClient(AndroidPlatform(this))
        setup = SetupFlow(this, findViewById(R.id.setupHost), infoSheet, jellyfin, seerr, settings, updates) { changed ->
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
            saveWatchlist = { settings.watchlist = it },
            saveFollowedServices = { settings.followedServices = it },
            saveFollowedChannels = { settings.followedChannels = it },
            featuredHistory = { settings.featuredHistory },
            saveFeaturedHistory = { settings.featuredHistory = it },
            personName = { com.danielportelabyrne.cinema.data.account.AccountStore(platform).active()?.name },
            onSwitchPerson = { switchPerson() },
            personAvatar = { com.danielportelabyrne.cinema.data.account.AccountStore(platform).active()?.avatar },
            onAvatar = { id -> val store = com.danielportelabyrne.cinema.data.account.AccountStore(platform)
                store.active()?.let { store.setAvatar(it.userId, id) }
                // Kept with the person's own things too, so their other TVs show the same character.
                AndroidPlatform.personalPrefs(this, "cinema_personal").edit().putString("avatar", id ?: "").apply() },
            pageLayouts = { settings.pageLayouts },
            savePageLayouts = { settings.pageLayouts = it }
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
            covered = { if (!isDestroyed) openingHeld() }, done = { splash = null; offerUpdate() })
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
        followLauncher()
    }

    /**
     * Back in front (the TV woke, or someone came back from another app): when the launcher now says someone else is
     * watching, Cinema opens again as them, or asks who they are when Cinema has not met them.
     */
    private fun followLauncher() {
        if (!::moviesPanel.isInitialized) return
        Thread {
            val person = com.danielportelabyrne.cinema.data.account.LauncherProfile.current(this) ?: return@Thread
            val accounts = com.danielportelabyrne.cinema.data.account.AccountStore(AndroidPlatform(this))
            val active = accounts.active() ?: return@Thread
            val match = accounts.forLauncher(person)
            if (match?.userId == active.userId) return@Thread
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                syncer?.pushNow()
                val again = android.content.Intent(this, CinemaActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (match != null) again.putExtra(EXTRA_PERSON, match.userId) else again.putExtra(EXTRA_ASK, true)
                startActivity(again); finish(); overridePendingTransition(0, 0)
            }
        }.start()
    }

    companion object {
        /** Open as this person (a Jellyfin user id), without asking. */
        const val EXTRA_PERSON = "cinema_person"
        /** Open on "Who's watching?", whatever the launcher says. */
        const val EXTRA_ASK = "cinema_ask_person"
        /** With [EXTRA_COPY_TO]: start that person as a copy of this one (names as on "Who's watching?"). */
        const val EXTRA_COPY_FROM = "cinema_copy_person_from"
        const val EXTRA_COPY_TO = "cinema_copy_person_to"
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
        if (!::moviesPanel.isInitialized) return   // still on "Who's watching?"
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
        // "debug_update_url": offers the app file at that address as build 999999, to try the whole update on a device.
        intent?.getStringExtra("debug_update_url")?.let { url ->
            val build = intent.getIntExtra("debug_update_build", 999_999)
            updates.debugArrived(com.danielportelabyrne.cinema.data.update.AvailableUpdate(build, "Test build", url, 0L, "A test of updating in the app")); return
        }
        if (setup.isVisible) return
        intent?.getStringExtra("debug_cinema_dest")?.let { moviesPanel.debugGo(it) }
    }

    override fun onResume() {
        super.onResume()
        if (::moviesPanel.isInitialized) moviesPanel.onResume()
        offerUpdate()
    }

    /** Looks for a newer build (at most every few hours) and offers it over the pages, never over setup or the opening. */
    private fun offerUpdate() {
        if (!::updates.isInitialized || !::moviesPanel.isInitialized) return
        updates.checkInBackground { !isFinishing && moviesPanel.isVisible && !setup.isVisible && !infoSheet.isVisible && splash == null }
    }

    override fun onStop() {
        super.onStop()
        syncer?.pushNow()
    }

    override fun onDestroy() {
        syncer?.stop()
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
                    } else if (target === overlay && direction == View.FOCUS_LEFT && moviesPanel.leftKey(focused, event.repeatCount == 0)) {
                        // Swallowed at the page's left edge: a held Left never spills into the rail (see NavGate).
                    } else if (target === overlay && moviesPanel.navigate(focused, direction)) {
                        // The rail and the loading screen move focus themselves.
                    } else if (target === overlay) {
                        // Cinema has its own focus rules, so ask the views themselves, staying inside the page.
                        focused.focusSearch(direction)?.takeIf { it !== focused && isInside(it, target) }?.requestFocus(direction)
                    } else {
                        val next = FocusFinder.getInstance().findNextFocus(target, focused, direction)
                        next?.requestFocus(direction)
                        // Setup pages and Settings move focus here, not through the page's own scroll view, so the rule that keeps
                        // the top reachable lives here too: Up with nothing above, or focus landing on the first item, shows the
                        // header again instead of leaving it scrolled out of reach.
                        if (direction == View.FOCUS_UP && (next == null || FocusFinder.getInstance().findNextFocus(target, next, View.FOCUS_UP) == null)) {
                            scrollPageToTop(target)
                        }
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Scrolls the first scrolling page under [root] back to its top. */
    private fun scrollPageToTop(root: ViewGroup) {
        fun find(v: View): android.widget.ScrollView? {
            if (v is android.widget.ScrollView) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        find(root)?.takeIf { it.scrollY > 0 }?.smoothScrollTo(0, 0)
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
