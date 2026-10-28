package com.danielportelabyrne.cinema.ui.setup

import com.danielportelabyrne.cinema.device.AndroidPlatform
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinDiscovery
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository
import com.danielportelabyrne.cinema.data.movies.ArrSetup
import com.danielportelabyrne.cinema.data.music.SignIn
import com.danielportelabyrne.cinema.data.music.SubsonicApi
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.settings.LibraryOrder
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.ui.room.DownloadClientSignInSheet
import com.danielportelabyrne.cinema.ui.room.InfoSheet
import com.danielportelabyrne.cinema.ui.room.JellyfinConnectSheet
import com.danielportelabyrne.cinema.ui.room.SheetAction
import java.util.Locale
import java.util.concurrent.Executors

/**
 * First-run setup and Settings, drawn full screen over Cinema in [host].
 *
 * First run walks through the steps in order: Jellyfin's address, signing in to it, Seerr (optional), Sonarr and Radarr,
 * the torrent client, and the country. Settings is an overview of the same things; each "Change" opens that one step
 * and comes back to the overview. Network calls run on a background thread and never block the remote.
 */
class SetupFlow(
    private val activity: Activity,
    private val host: FrameLayout,
    private val infoSheet: InfoSheet,
    private val jellyfin: JellyfinClient,
    private val seerr: SeerrClient,
    private val settings: CinemaSettings,
    /** Setup finished or Settings closed; [changed] when a server, sign-in or region changed, so Cinema rebuilds. */
    private val onClosed: (changed: Boolean) -> Unit
) {
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool()
    private val queue by lazy { QueueClient(seerr, AndroidPlatform(activity)) }
    private var settingsMode = false
    private var changed = false
    /** Bumped every time a page is drawn, so a late network answer never paints over the page that replaced it. */
    private var pageToken = 0
    /** Where Back goes from the page on screen; null means Back leaves (the overview, or Welcome on first run). */
    private var backTo: (() -> Unit)? = null
    /** Set when one step was opened from inside Cinema (a sign-in that lapsed): run once that step succeeds. */
    private var afterStep: (() -> Unit)? = null

    val isVisible: Boolean get() = host.visibility == View.VISIBLE

    fun startFirstRun() { settingsMode = false; changed = true; welcome() }

    fun openSettings() { settingsMode = true; changed = false; afterStep = null; overview() }

    /** Just the Jellyfin sign-in, from inside Cinema; [onDone] runs when it worked. */
    fun signInToJellyfin(onDone: () -> Unit) { settingsMode = true; changed = false; afterStep = onDone; jellyfinSignIn() }

    /** Just the Seerr step, from inside Cinema; [onDone] runs when Seerr is set up and signed in. */
    fun signInToSeerr(onDone: () -> Unit) { settingsMode = true; changed = false; afterStep = onDone; seerrStep() }

    /** The remote's Back. False when setup has nothing to go back to (first run's first page), so the app may close. */
    fun back(): Boolean {
        val to = backTo
        if (to != null) { to(); return true }
        if (settingsMode) { close(); return true }
        return false
    }

    fun close() {
        host.removeAllViews(); host.visibility = View.GONE; pageToken++
        if (!settingsMode) settings.setupDone = true
        onClosed(changed)
    }

    fun shutdown() = pool.shutdownNow()

    /** Debug builds only: opens one page by name, for screenshots without signing in. */
    fun debugOpen(name: String) {
        settingsMode = name != "welcome"; changed = false; afterStep = null
        when (name) {
            "welcome" -> welcome(); "jellyfin" -> jellyfinServer(); "signin" -> jellyfinSignIn(); "seerr" -> seerrStep()
            "arr" -> arrStep(); "client" -> clientStep(); "region" -> regionStep(); "advanced" -> advancedStep(); "order" -> libraryOrderStep()
            "integrations" -> integrationsStep(); "music" -> musicStep()
            else -> overview()
        }
    }

    /**
     * After a step succeeds: on first run, on to [then]; in Settings, back to the overview; and for a single step opened
     * from inside Cinema, back to Cinema to finish what it was doing.
     */
    private fun advance(then: () -> Unit) {
        val done = afterStep
        when {
            done != null -> { afterStep = null; changed = false; close(); done() }
            settingsMode -> overview()
            else -> then()
        }
    }

    // --- The steps ---------------------------------------------------------------------------------

    private fun welcome() {
        val p = page(null, "Cinema", "Your Jellyfin library, and what is streaming everywhere else, in one place. " +
            "Setting up takes a couple of minutes: you will need your Jellyfin address and sign-in, and optionally your Seerr.")
        backTo = null
        p.primary("Set up", R.drawable.ic_cin_chev) { jellyfinServer() }
    }

    private fun jellyfinServer() {
        val p = page(step(1), "Your Jellyfin server", "Cinema plays your films and series from Jellyfin. Pick your server, or type its address " +
            "(for example 192.168.1.20:8096).")
        backTo = if (settingsMode) ::overview else ::welcome
        val found = p.chips()
        val address = p.field("Server address", jellyfin.server.takeIf { it.isNotBlank() })
        val token = pageToken
        p.status("Looking for servers on your network…")
        bg({ JellyfinDiscovery.find() }) { servers ->
            if (token != pageToken) return@bg
            p.status("")
            servers.forEach { s ->
                found.chip("${s.name} · ${s.address.removePrefix("http://")}") { address.setText(s.address); address.requestFocus() }
            }
            if (address.text.isNullOrBlank()) servers.firstOrNull()?.let { address.setText(it.address) }
        }
        p.primary("Continue", R.drawable.ic_cin_chev) {
            val typed = address.text.toString().trim()
            if (typed.isEmpty()) { p.status("Enter your Jellyfin server's address."); return@primary }
            p.busy("Checking…")
            bg({ jellyfin.probe(typed) }) { ok ->
                if (token != pageToken) return@bg
                if (!ok) { p.done("Couldn't find Jellyfin at $typed. Check the address, and that this TV is on the same network."); return@bg }
                if (CinemaSettings.cleanUrl(typed) != jellyfin.server) changed = true
                jellyfin.setServer(typed)
                jellyfinSignIn()
            }
        }
        if (settingsMode) p.secondary("Cancel") { overview() }
    }

    private fun jellyfinSignIn() {
        val p = page(step(2), "Sign in to Jellyfin", "Use your Jellyfin username and password, or approve this TV from your phone with Quick Connect. " +
            "Only a sign-in token is kept on this TV, never your password.")
        backTo = if (afterStep != null) ::overview else ::jellyfinServer
        val user = p.field("Username", null)
        val password = p.field("Password", null, password = true)
        val token = pageToken
        fun signedIn() { changed = true; advance(::seerrStep) }
        p.primary("Sign in", R.drawable.ic_cin_check) {
            val u = user.text.toString().trim(); val pw = password.text.toString()
            if (u.isEmpty()) { p.status("Enter your Jellyfin username."); return@primary }
            p.busy("Signing in…")
            bg({ jellyfin.signInByName(u, pw) }) { error ->
                if (token != pageToken) return@bg
                if (error == null) signedIn() else p.done(error)
            }
        }
        p.secondary("Use Quick Connect") { JellyfinConnectSheet(activity, infoSheet).show(jellyfin) { if (token == pageToken) signedIn() } }
        password.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { p.primaryButton?.performClick(); true } else false }
    }

    private fun seerrStep() {
        val p = page(step(3), "Requests and downloads", "Seerr (Jellyseerr or Overseerr) lets Cinema ask for films and series that are not in " +
            "your library yet, and shows them downloading. Skip this if you only want to watch what you already have.")
        backTo = if (settingsMode) ::overview else ::jellyfinSignIn
        val suggested = seerr.server.takeIf { it.isNotBlank() }
            ?: jellyfin.server.takeIf { it.isNotBlank() }?.let { runCatching { "http://" + java.net.URL(it).host + ":5055" }.getOrNull() }
        val address = p.field("Seerr address", suggested)
        var local = false
        val user = p.field("Jellyfin username", null)
        val password = p.field("Password", null, password = true)
        val mode = p.chips()
        lateinit var toggle: CinemaButton
        toggle = mode.chip("Use a Seerr email account instead") {
            local = !local
            user.hint = if (local) "Seerr email" else "Jellyfin username"
            user.inputType = if (local) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS else InputType.TYPE_CLASS_TEXT
            toggle.setText(if (local) "Use your Jellyfin account instead" else "Use a Seerr email account instead")
        }
        val token = pageToken
        bg({ jellyfin.userName() }) { name -> if (token == pageToken && name != null && user.text.isNullOrBlank() && !local) user.setText(name) }
        p.primary("Connect", R.drawable.ic_cin_check) {
            val a = address.text.toString().trim(); val u = user.text.toString().trim(); val pw = password.text.toString()
            if (a.isEmpty()) { p.status("Enter Seerr's address, or skip."); return@primary }
            if (u.isEmpty() || pw.isEmpty()) { p.status(if (local) "Enter your Seerr email and password." else "Enter your Jellyfin username and password."); return@primary }
            p.busy("Connecting…")
            bg({
                if (!seerr.probe(a)) "Couldn't find Seerr at $a. Check the address."
                else { seerr.setServer(a); if (local) seerr.signInLocal(u, pw) else seerr.signIn(u, pw) }
            }) { error ->
                if (token != pageToken) return@bg
                if (error != null) { p.done(error); return@bg }
                changed = true; ArrSetup.forget()
                advance(::arrStep)
            }
        }
        if (afterStep != null) p.secondary("Cancel") { overview() }
        else p.secondary(if (settingsMode && seerr.hasServer) "Turn off Seerr" else "Skip") {
            if (seerr.hasServer) { seerr.setServer(null); changed = true }
            if (settingsMode) overview() else regionStep()
        }
    }

    private fun arrStep() {
        val p = page(step(4), "Sonarr and Radarr", "Checking what Seerr shares…")
        backTo = if (settingsMode) ::overview else ::seerrStep
        val token = pageToken
        bg({ Triple(seerr.sonarrConfig() != null, seerr.radarrConfig() != null, Unit) }) { (sonarr, radarr, _) ->
            if (token != pageToken) return@bg
            val manualS = settings.manualArr(Arr.SONARR); val manualR = settings.manualArr(Arr.RADARR)
            if (sonarr && radarr && manualS == null && manualR == null && !settingsMode) {
                p.body("Found Sonarr and Radarr through Seerr. Downloads will show their progress here.")
                p.primary("Continue", R.drawable.ic_cin_chev) { clientStep() }
                return@bg
            }
            p.body("Cinema reads Sonarr and Radarr to show downloads, and to ask for single episodes. Seerr only shares them with an " +
                "admin account, so you can enter them here instead. The API key is in each app under Settings, General. Leave both " +
                "blank to use what Seerr shares" + (if (!sonarr && !radarr) ", or skip to go without download progress." else "."))
            val seerrHost = seerr.host.takeIf { it.isNotBlank() }
            fun suggest(arr: Arr) = settings.manualArr(arr)?.baseUrl ?: seerrHost?.let { "http://$it:${arr.defaultPort}" }
            val sUrl = p.field("Sonarr address", if (sonarr && manualS == null) null else suggest(Arr.SONARR))
            val sKey = p.field("Sonarr API key", manualS?.apiKey, password = true)
            val rUrl = p.field("Radarr address", if (radarr && manualR == null) null else suggest(Arr.RADARR))
            val rKey = p.field("Radarr API key", manualR?.apiKey, password = true)
            fun found(arr: Arr, ok: Boolean, manual: ManualArr?) =
                if (!ok) null else if (manual != null) "${arr.label} connected ✓" else "${arr.label} found through Seerr ✓"
            p.status(listOfNotNull(found(Arr.SONARR, sonarr, manualS), found(Arr.RADARR, radarr, manualR)).joinToString("   "))
            p.primary("Save", R.drawable.ic_cin_check) {
                val entries = listOf(Triple(Arr.SONARR, sUrl, sKey), Triple(Arr.RADARR, rUrl, rKey)).map { (arr, url, key) ->
                    val k = key.text.toString().trim()
                    Triple(arr, CinemaSettings.cleanUrl(url.text.toString()), k)
                }
                p.busy("Checking…")
                bg({
                    entries.firstNotNullOfOrNull { (arr, url, key) ->
                        when {
                            key.isEmpty() -> null
                            url == null -> "Enter ${arr.label}'s address as well as its key."
                            else -> ArrSetup.check(arr, ManualArr(url, key))
                        }
                    }
                }) { error ->
                    if (token != pageToken) return@bg
                    if (error != null) { p.done(error); return@bg }
                    entries.forEach { (arr, url, key) -> settings.setManualArr(arr, if (key.isEmpty() || url == null) null else ManualArr(url, key)) }
                    ArrSetup.forget(); changed = true
                    advance(::clientStep)
                }
            }
            p.secondary(if (settingsMode) "Cancel" else "Skip") { advance(::clientStep) }
        }
    }

    private fun clientStep() {
        val p = page(step(5), "Your torrent client", "Looking at the download clients Sonarr and Radarr use…")
        backTo = if (settingsMode) ::overview else ::arrStep
        val token = pageToken
        bg({ queue.detectClients().map { it to (it.config?.let { c -> queue.needsLogin(c) } ?: false) } }) { found ->
            if (token != pageToken) return@bg
            if (found.isEmpty()) {
                p.body("No torrent client found. Cinema needs Sonarr or Radarr (set up through Seerr, or in the previous step) to find it. " +
                    "Requests still work without it.")
            } else {
                p.body("Download progress works with any client. Cinema can also move a download to the front of the queue or start it " +
                    "at once in qBittorrent, Transmission and Deluge.")
                found.forEach { (client, needsLogin) ->
                    val config = client.config
                    val kindName = config?.kind?.label ?: client.listed.implementation
                    val state = when {
                        config == null -> "Progress only"
                        needsLogin -> "Needs your sign-in for the download controls"
                        else -> "Ready ✓"
                    }
                    val row = p.row("${client.listed.name} · $kindName", "${client.listed.host} · $state")
                    if (config != null) row.button(if (needsLogin) "Sign in" else "Sign in again", accent = needsLogin) {
                        DownloadClientSignInSheet(activity, infoSheet).show(config.kind, { u, pw -> queue.signIn(config, u, pw) }) {
                            if (token == pageToken) clientStep()
                        }
                    }
                }
            }
            p.primary(if (settingsMode) "Done" else "Continue", R.drawable.ic_cin_chev) { advance(::regionStep) }
        }
    }

    private fun regionStep() {
        val p = page(step(6), "Where are you watching?", "Cinema shows what is streaming on Netflix, Prime Video, Disney+ and the rest in your country.")
        backTo = if (settingsMode) ::overview else if (seerr.hasServer) ::clientStep else ::seerrStep
        val field = p.field("Two-letter country code", settings.region)
        val common = p.chips()
        listOf("GB", "IE", "US", "CA", "AU", "NZ", "DE", "FR", "ES", "IT", "NL", "SE").forEach { code ->
            common.chip(Locale("", code).getDisplayCountry(Locale.ENGLISH)) { field.setText(code) }
        }
        p.primary(if (settingsMode) "Save" else "Finish", R.drawable.ic_cin_check) {
            val code = field.text.toString().trim().uppercase(Locale.ROOT)
            if (!code.matches(Regex("[A-Z]{2}"))) { p.status("Use a two-letter country code, like GB or US."); return@primary }
            if (code != settings.region) changed = true
            settings.region = code; Catalog.country = code
            if (settingsMode) overview() else close()
        }
    }

    private fun advancedStep() {
        val p = page("ADVANCED", "Helper services", "Optional services that run next to Seerr. Leave these blank unless you run them.")
        backTo = ::overview
        val helper = p.field("Request helper address", settings.requestHelperUrl)
        p.note("Watches requests on the server, retries searches, and reports why a request is stuck.")
        val stream = p.field("Stream service address", settings.streamServiceUrl)
        p.note("Plays a film before it has downloaded. Adds Stream now to title pages.")
        p.primary("Save", R.drawable.ic_cin_check) {
            settings.requestHelperUrl = helper.text.toString(); settings.streamServiceUrl = stream.text.toString()
            changed = true; overview()
        }
        p.secondary("Cancel") { overview() }
    }

    private fun musicStep() {
        val p = page("MUSIC", "Music server", "A Subsonic server (Navidrome, Gonic, Airsonic) adds Music to the menu.")
        backTo = ::integrationsStep
        val current = settings.subsonic
        val address = p.field("Music server address", current?.baseUrl)
        val user = p.field("Username", current?.username)
        val password = p.field("Password", null, password = true)
        val key = p.field("API key (instead of a password)", null, password = true)
        p.note("Leave the password blank to keep the saved one. Your password never leaves this TV.")
        val token = pageToken
        p.primary("Connect", R.drawable.ic_cin_check) {
            val a = address.text.toString().trim(); val u = user.text.toString().trim()
            val pw = password.text.toString().ifEmpty { if (key.text.isEmpty()) current?.password.orEmpty() else "" }
            val k = key.text.toString().ifEmpty { if (password.text.isEmpty()) current?.apiKey.orEmpty() else "" }
            if (a.isEmpty()) { p.status("Enter the music server's address."); return@primary }
            if (k.isEmpty() && (u.isEmpty() || pw.isEmpty())) { p.status("Enter a username and password, or an API key."); return@primary }
            val config = SubsonicConfig(SubsonicApi.normalize(a), u, pw, k)
            p.busy("Connecting…")
            bg({ SubsonicClient(config).signIn() }) { result ->
                if (token != pageToken) return@bg
                when (result) {
                    SignIn.OK -> { settings.subsonic = config; settings.setOn(Integration.MUSIC, true); changed = true; integrationsStep() }
                    SignIn.WRONG_CREDENTIALS -> p.done("The server turned down that sign-in.")
                    SignIn.NEEDS_API_KEY -> p.done("This server needs an API key. Make one in its settings.")
                    SignIn.NOT_SUBSONIC -> p.done("That address is not a Subsonic server.")
                    else -> p.done("Couldn't reach $a. Check the address.")
                }
            }
        }
        if (current != null) p.secondary("Forget server") { settings.subsonic = null; changed = true; integrationsStep() }
        p.secondary("Cancel") { integrationsStep() }
    }

    private fun libraryOrderStep() {
        val p = page("SETTINGS", "Library order", "How My library lists your films and series.")
        backTo = ::overview
        val current = settings.libraryOrder
        LibraryOrder.entries.forEach { order ->
            p.row(order.label, order.detail).button(if (order == current) "In use ✓" else "Use", accent = order != current) {
                if (order != current) { settings.libraryOrder = order; changed = true }
                overview()
            }
        }
        p.secondary("Cancel") { overview() }
    }

    private fun integrationsStep() {
        val p = page("SETTINGS", "Integrations", "Optional services Cinema can add to the menu and to title pages. Switch off any you do not use.")
        backTo = ::overview
        Integration.entries.forEach { integration ->
            val row = p.row(integration.label, integrationDetail(integration))
            if (integration == Integration.MUSIC) row.button(if (settings.subsonic == null) "Set up" else "Server") { musicStep() }
            row.button(toggleLabel(integration)) {
                settings.setOn(integration, !settings.isOn(integration)); changed = true
                row.detail.text = integrationDetail(integration)
                row.lastButton?.label?.text = toggleLabel(integration)
            }
        }
        p.primary("Done", R.drawable.ic_cin_check) { overview() }
    }

    private fun toggleLabel(integration: Integration) = if (settings.isOn(integration)) "Turn off" else "Turn on"

    private fun integrationDetail(integration: Integration): String {
        if (!settings.isOn(integration)) return "Off · ${integration.summary}"
        return when (integration) {
            Integration.LETTERBOXD -> LetterboxdRepository.followingCount(activity).let { n ->
                if (n == 0) "On · follow people from the Letterboxd page" else "On · following $n, managed from the Letterboxd page"
            }
            Integration.MUSIC -> settings.subsonic?.let { "On · ${it.baseUrl}" + if (it.username.isNotBlank()) " · ${it.username}" else "" }
                ?: "On · set up a server to add Music to the menu"
        }
    }

    // --- Settings overview ---------------------------------------------------------------------------

    private fun overview() {
        if (afterStep != null) { afterStep = null; changed = false; close(); return }
        val p = page("SETTINGS", "Settings", null)
        backTo = null
        val token = pageToken
        val jf = p.row("Jellyfin", if (jellyfin.isConnected) jellyfin.server else "Not signed in")
        jf.button("Change") { jellyfinServer() }
        bg({ if (jellyfin.isConnected) jellyfin.userName() else null }) { name -> if (token == pageToken && name != null) jf.detail.text = "${jellyfin.server} · $name" }

        val se = p.row("Seerr", when { !seerr.hasServer -> "Off"; seerr.isSignedIn -> seerr.server; else -> "${seerr.server} · not signed in" })
        se.button(if (seerr.hasServer) "Change" else "Set up") { seerrStep() }

        if (seerr.hasServer) {
            val manual = listOfNotNull(settings.manualArr(Arr.SONARR)?.let { "Sonarr" }, settings.manualArr(Arr.RADARR)?.let { "Radarr" })
            val arr = p.row("Sonarr and Radarr", if (manual.isEmpty()) "Through Seerr" else "${manual.joinToString(" and ")} entered here")
            arr.button("Change") { arrStep() }
            p.row("Torrent client", "Download controls for qBittorrent, Transmission and Deluge").button("Check") { clientStep() }
        }

        p.row("Country", Locale("", settings.region).getDisplayCountry(Locale.ENGLISH)).button("Change") { regionStep() }

        p.row("Library order", settings.libraryOrder.label).button("Change") { libraryOrderStep() }

        val on = Integration.entries.filter { settings.isOn(it) }.map { it.label }
        p.row("Integrations", if (on.isEmpty()) "None switched on" else on.joinToString(", ") + " on").button("Change") { integrationsStep() }

        val extras = listOfNotNull(settings.requestHelperUrl?.let { "request helper" }, settings.streamServiceUrl?.let { "stream service" })
        p.row("Helper services", if (extras.isEmpty()) "None" else extras.joinToString(" and ").replaceFirstChar { it.uppercase() })
            .button("Change") { advancedStep() }

        p.row("Reset", "Forget every server, sign-in and setting on this TV").button("Reset everything", danger = true) { confirmReset() }
        p.primary("Done", R.drawable.ic_cin_check) { close() }
    }

    private fun confirmReset() {
        infoSheet.show("RESET", "Reset everything?", "This TV forgets Jellyfin, Seerr, Sonarr, Radarr, download-client logins and the country. " +
            "Your servers are not changed.", emptyList(), null, listOf(
            SheetAction("Reset", "Start setup again", primary = true) {
                jellyfin.forget(); seerr.setServer(null); settings.reset(); ArrSetup.forget()
                LetterboxdRepository.forgetFollowing(activity)
                infoSheet.hide()
                startFirstRun()
            },
            SheetAction("Cancel") { infoSheet.hide() }
        ))
    }

    private fun step(n: Int) = if (settingsMode) "SETTINGS" else "STEP $n OF 6"

    // --- Building pages ------------------------------------------------------------------------------

    private fun <T> bg(work: () -> T, then: (T) -> Unit) {
        try { pool.execute { val result = runCatching(work).getOrNull(); @Suppress("UNCHECKED_CAST") ui.post { then(result as T) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    private fun px(mock: Int) = Cinema.px(activity, mock)

    private fun page(eyebrow: String?, title: String, body: String?): Page {
        pageToken++
        host.removeAllViews()
        host.visibility = View.VISIBLE
        host.setBackgroundColor(Cinema.BG)
        val scroll = ScrollView(activity).apply { isFillViewport = true; isFocusable = false; isVerticalScrollBarEnabled = false; clipChildren = false }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(px(200), px(110), px(200), px(110))
        }
        scroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        eyebrow?.let { column.addView(Cinema.text(activity, it, 22, Cinema.TERRA, weight = 600, tracking = 0.2f, caps = true)) }
        column.addView(Cinema.text(activity, title, 84, Cinema.CREAM, serif = true, tracking = -0.02f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(10) })
        val page = Page(column)
        body?.let { page.body(it) }
        // Focus the first thing that takes it once the page has laid out (fields, then buttons).
        val token = pageToken
        column.post { if (token == pageToken && host.findFocus() == null) page.firstFocus()?.requestFocus() }
        return page
    }

    private inner class Page(val column: LinearLayout) {
        private var bodyView: TextView? = null
        private var statusView: TextView? = null
        private var buttons: LinearLayout? = null
        private val focusables = ArrayList<View>()
        var primaryButton: CinemaButton? = null

        fun firstFocus(): View? = focusables.firstOrNull { it.isShown && it.isEnabled }

        fun body(text: String) {
            val view = bodyView ?: Cinema.text(activity, "", 30, Cinema.MUTED).apply { setLineSpacing(0f, 1.25f); maxWidth = px(1500) }
                .also { bodyView = it; column.addView(it, insertAt(), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22); bottomMargin = px(14) }) }
            view.text = text
        }

        fun note(text: String) {
            column.addView(Cinema.text(activity, text, 22, Cinema.DIM).apply { maxWidth = px(1500) }, insertAt(), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(6) })
        }

        fun field(hint: String, value: String?, password: Boolean = false): EditText {
            val rest = activity.getColor(R.color.btn_brown_on)
            val edit = EditText(activity).apply {
                this.hint = hint; setText(value.orEmpty())
                inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                // After inputType: a password type swaps in a monospace face, which would clash with the rest.
                Cinema.style(this, 30, Cinema.CREAM); setHintTextColor(Cinema.DIM)
                maxLines = 1; isSingleLine = true
                // isSingleLine replaces the password mask.
                if (password) transformationMethod = PasswordTransformationMethod.getInstance()
                imeOptions = (if (password) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setPadding(px(40), 0, px(40), 0)
                background = GradientDrawable().apply { cornerRadius = px(52).toFloat(); setColor(rest) }
                setOnFocusChangeListener { v, focused -> (v.background as GradientDrawable).setColor(if (focused) 0xFF7A5C42.toInt() else rest) }
            }
            column.addView(edit, insertAt(), LinearLayout.LayoutParams(px(1100), px(104)).apply { topMargin = px(20) })
            focusables += edit
            return edit
        }

        /** A horizontally scrolling line of small buttons (found servers, countries). */
        fun chips(): Chips {
            val inner = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(px(4), px(18), px(40), px(18)) }
            val scroller = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(inner) }
            column.addView(scroller, insertAt(), LinearLayout.LayoutParams(-1, -2))
            return Chips(inner)
        }

        inner class Chips(private val inner: LinearLayout) {
            fun chip(label: String, onClick: () -> Unit): CinemaButton = CinemaButton(activity, CinemaButton.Kind.GLASS, label, null, small = true).apply {
                setOnClickListener { onClick() }
                inner.addView(this, LinearLayout.LayoutParams(-2, px(64)).apply { if (inner.childCount > 0) marginStart = px(14) })
                focusables += this
            }
        }

        /** One line of the Settings overview (or a found client): a name, a detail line, and buttons on the right. */
        fun row(name: String, detail: String): Row {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false
                setPadding(px(36), px(22), px(28), px(22)); background = Cinema.glass(activity, 30f)
            }
            val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(Cinema.text(activity, name, 30, Cinema.CREAM, weight = 600))
            val detailView = Cinema.text(activity, detail, 23, Cinema.MUTED).apply { maxLines = 2 }
            texts.addView(detailView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
            line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            column.addView(line, insertAt(), LinearLayout.LayoutParams(px(1500), -2).apply { topMargin = px(16) })
            return Row(line, detailView)
        }

        inner class Row(private val line: LinearLayout, val detail: TextView) {
            /** The button added last, so a toggle can relabel itself. */
            var lastButton: CinemaButton? = null
                private set

            fun button(label: String, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit): Row {
                val kind = when { danger -> CinemaButton.Kind.DANGER; accent -> CinemaButton.Kind.TERRA; else -> CinemaButton.Kind.GLASS }
                val b = CinemaButton(activity, kind, label, null, small = true).apply { setOnClickListener { onClick() } }
                line.addView(b, LinearLayout.LayoutParams(-2, px(64)).apply { marginStart = px(16) })
                focusables += b
                lastButton = b
                return this
            }
        }

        fun status(text: String) {
            val view = statusView ?: Cinema.text(activity, "", 24, 0xFFE8C27A.toInt()).apply { maxWidth = px(1500) }
                .also { statusView = it; column.addView(it, insertAt(), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(18) }) }
            view.text = text
            view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }

        /** Shows [text] and greys the buttons out while a check runs. */
        fun busy(text: String) { status(text); setButtonsEnabled(false) }
        fun done(text: String) { status(text); setButtonsEnabled(true) }
        private fun setButtonsEnabled(on: Boolean) { buttons?.let { b -> (0 until b.childCount).forEach { b.getChildAt(it).isEnabled = on } } }

        fun primary(label: String, icon: Int?, onClick: () -> Unit) {
            primaryButton = button(CinemaButton.Kind.TERRA, label, icon, onClick)
        }

        fun secondary(label: String, onClick: () -> Unit) { button(CinemaButton.Kind.GLASS, label, null, onClick) }

        private fun button(kind: CinemaButton.Kind, label: String, icon: Int?, onClick: () -> Unit): CinemaButton {
            val row = buttons ?: LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                .also { buttons = it; column.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(40) }) }
            val b = CinemaButton(activity, kind, label, icon).apply { setOnClickListener { if (isEnabled) onClick() } }
            row.addView(b, LinearLayout.LayoutParams(-2, px(84)).apply { if (row.childCount > 0) marginStart = px(20) })
            focusables += b
            // Steps that draw after a network answer: focus their first field (or this button) once it is laid out.
            val token = pageToken
            b.post { if (token == pageToken && host.findFocus() == null) firstFocus()?.requestFocus() }
            return b
        }

        /** New content goes above the button row, so a step can add to the page after a network answer arrives. */
        private fun insertAt(): Int = buttons?.let { column.indexOfChild(it) } ?: column.childCount
    }
}
