package com.danielportelabyrne.cinema.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danielportelabyrne.cinema.data.jellyfin.FoundServer
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinDiscovery
import com.danielportelabyrne.cinema.data.jellyfin.QuickConnectTicket
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository
import com.danielportelabyrne.cinema.data.movies.ArrSetup
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.desktop.ui.KeyboardRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Step { WELCOME, JF_SERVER, JF_SIGN_IN, SEERR, ARR, CLIENT, REGION, ADVANCED, OVERVIEW }

/** A text field on a setup page: shown as a brown pill, typed on the keyboard sheet. */
class Field(val hint: String, initial: String?, val secret: Boolean = false) {
    var value by mutableStateOf(initial.orEmpty())
}

/** A row of the Settings overview or the client list: a name, a detail line, and buttons on the right. */
class SetupRow(val name: String, detail: String, val buttons: List<RowButton>) {
    var detail by mutableStateOf(detail)
}
class RowButton(val label: String, val accent: Boolean = false, val danger: Boolean = false, val run: () -> Unit)

/**
 * First-run setup and Settings (the TV's SetupFlow): the same steps, in the same order, with the same words. Fields
 * open the keyboard sheet; network calls run in the background and a late answer never paints over a newer page.
 */
class SetupModel(private val m: CinemaModel) {
    private val jellyfin get() = m.client
    private val seerr get() = m.seerr
    private val settings get() = m.settings
    private val queue: QueueClient get() = m.queueClient

    var step by mutableStateOf(Step.WELCOME)
    var settingsMode by mutableStateOf(false)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var body by mutableStateOf<String?>(null)
    var fields by mutableStateOf<List<Field>>(emptyList())
    var chips by mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList())
    var rows by mutableStateOf<List<SetupRow>>(emptyList())
    var notes by mutableStateOf<Map<Int, String>>(emptyMap())
    var primary by mutableStateOf<Pair<String, String?>?>(null)
    var secondary by mutableStateOf<String?>(null)
    private var onPrimary: () -> Unit = {}
    private var onSecondary: () -> Unit = {}
    var quickConnectCode by mutableStateOf<String?>(null)
    var quickConnectOpen by mutableStateOf(false)
    /** Bumped every time a page is drawn, so a late answer never paints over the page that replaced it. */
    var token by mutableIntStateOf(0)
        private set
    private var changed = false
    private var backTo: (() -> Unit)? = null
    private var afterStep: (() -> Unit)? = null

    fun title(): String = when (step) {
        Step.WELCOME -> "Cinema"; Step.JF_SERVER -> "Your Jellyfin server"; Step.JF_SIGN_IN -> "Sign in to Jellyfin"
        Step.SEERR -> "Requests and downloads"; Step.ARR -> "Sonarr and Radarr"; Step.CLIENT -> "Your torrent client"
        Step.REGION -> "Where are you watching?"; Step.ADVANCED -> "Helper services"; Step.OVERVIEW -> "Settings"
    }

    fun eyebrow(): String? = when (step) {
        Step.WELCOME -> null
        Step.ADVANCED -> "ADVANCED"
        Step.OVERVIEW -> "SETTINGS"
        else -> if (settingsMode) "SETTINGS" else "STEP ${step.ordinal} OF 6"
    }

    fun pressPrimary() { if (!busy) onPrimary() }
    fun pressSecondary() { if (!busy) onSecondary() }

    fun startFirstRun() { settingsMode = false; changed = true; afterStep = null; welcome() }
    fun openSettings() { settingsMode = true; changed = false; afterStep = null; overview() }
    fun signInToSeerr(onDone: () -> Unit) { settingsMode = true; changed = false; afterStep = onDone; seerrStep() }

    fun back(): Boolean {
        if (quickConnectOpen) { cancelQuickConnect(); return true }
        backTo?.let { it(); return true }
        if (settingsMode) { close(); return true }
        return false
    }

    fun close() {
        token++
        if (!settingsMode) settings.setupDone = true
        val stack = m.stack
        if (stack.size > 1 && stack.last() == Route.Setup) stack.removeAt(stack.lastIndex) else { stack.clear(); stack += Route.Browse }
        if (changed) { m.refresh(force = true); m.loadStorage() }
    }

    private fun advance(then: () -> Unit) {
        val done = afterStep
        when {
            done != null -> { afterStep = null; changed = true; close(); done() }
            settingsMode -> overview()
            else -> then()
        }
    }

    private fun page(step: Step, body: String?) {
        token++
        this.step = step; this.body = body
        status = ""; busy = false; fields = emptyList(); chips = emptyList(); rows = emptyList(); notes = emptyMap()
        primary = null; secondary = null; onPrimary = {}; onSecondary = {}
    }

    private fun primary(label: String, icon: String?, run: () -> Unit) { primary = label to icon; onPrimary = run }
    private fun secondary(label: String, run: () -> Unit) { secondary = label; onSecondary = run }

    private fun <T> bg(work: () -> T, then: (T) -> Unit) {
        val t = token
        m.bg(work) { r -> if (t == token) then(r) }
    }

    fun edit(f: Field) {
        m.keyboard = KeyboardRequest(f.hint, initial = if (f.secret) "" else f.value, secret = f.secret, doneLabel = "Done") { typed ->
            m.keyboard = null; f.value = typed
        }
    }

    // --- The steps -----------------------------------------------------------------------------------

    private fun welcome() {
        page(Step.WELCOME, "Your Jellyfin library, and what is streaming everywhere else, in one place. " +
            "Setting up takes a couple of minutes: you will need your Jellyfin address and sign-in, and optionally your Seerr.")
        backTo = null
        primary("Set up", "chev") { jellyfinServer() }
    }

    private fun jellyfinServer() {
        page(Step.JF_SERVER, "Cinema plays your films and series from Jellyfin. Pick your server, or type its address (for example 192.168.1.20:8096).")
        backTo = if (settingsMode) ::overview else ::welcome
        val address = Field("Server address", jellyfin.server.takeIf { it.isNotBlank() })
        fields = listOf(address)
        status = "Looking for servers on your network…"
        bg({ JellyfinDiscovery.find() }) { servers: List<FoundServer>? ->
            status = ""
            chips = servers.orEmpty().map { s -> "${s.name} · ${s.address.removePrefix("http://")}" to { address.value = s.address } }
            if (address.value.isBlank()) servers?.firstOrNull()?.let { address.value = it.address }
        }
        primary("Continue", "chev") {
            val typed = address.value.trim()
            if (typed.isEmpty()) { status = "Enter your Jellyfin server's address."; return@primary }
            busy = true; status = "Checking…"
            bg({ CinemaModel.addressCandidates(com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.cleanBase(typed)).firstOrNull { jellyfin.probe(it) } }) { found: String? ->
                busy = false
                if (found == null) { status = "Couldn't find Jellyfin at $typed. Check the address, and that this device is on the same network."; return@bg }
                if (CinemaSettings.cleanUrl(found) != jellyfin.server) changed = true
                jellyfin.setServer(found)
                jellyfinSignIn()
            }
        }
        if (settingsMode) secondary("Cancel") { overview() }
    }

    private fun jellyfinSignIn() {
        page(Step.JF_SIGN_IN, "Use your Jellyfin username and password, or approve this device from your phone with Quick Connect. " +
            "Only a sign-in token is kept here, never your password.")
        backTo = ::jellyfinServer
        val user = Field("Username", null); val password = Field("Password", null, secret = true)
        fields = listOf(user, password)
        fun signedIn() { changed = true; m.refresh(force = true); advance(::seerrStep) }
        primary("Sign in", "check") {
            val u = user.value.trim(); val pw = password.value
            if (u.isEmpty()) { status = "Enter your Jellyfin username."; return@primary }
            busy = true; status = "Signing in…"
            bg({ jellyfin.signInByName(u, pw) }) { error: String? ->
                busy = false
                if (error == null && jellyfin.isConnected) signedIn() else status = error ?: "Couldn't sign in"
            }
        }
        secondary("Use Quick Connect") { startQuickConnect { signedIn() } }
    }

    private var qc: Job? = null

    private fun startQuickConnect(onDone: () -> Unit) {
        val server = jellyfin.server
        quickConnectOpen = true; quickConnectCode = null
        qc?.cancel()
        qc = m.scope.launch {
            var error: String? = null
            val ticket: QuickConnectTicket? = withContext(kotlinx.coroutines.Dispatchers.IO) { jellyfin.startQuickConnect(server) { error = it } }
            if (ticket == null) { withContext(m.ui) { quickConnectOpen = false; status = error ?: "Quick Connect didn't start" }; return@launch }
            withContext(m.ui) { quickConnectCode = ticket.code }
            val deadline = System.currentTimeMillis() + 10 * 60_000
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(2000)
                if (withContext(kotlinx.coroutines.Dispatchers.IO) { jellyfin.isApproved(ticket) }) {
                    val ok = withContext(kotlinx.coroutines.Dispatchers.IO) { jellyfin.finishQuickConnect(ticket) }
                    withContext(m.ui) { quickConnectOpen = false; if (ok) onDone() else status = "Jellyfin approved the code but didn't sign in. Try again." }
                    return@launch
                }
            }
            withContext(m.ui) { quickConnectOpen = false; status = "The code expired. Try Quick Connect again for a new one." }
        }
    }

    fun cancelQuickConnect() { qc?.cancel(); qc = null; quickConnectOpen = false; quickConnectCode = null }

    private fun seerrStep() {
        page(Step.SEERR, "Seerr (Jellyseerr or Overseerr) lets Cinema ask for films and series that are not in your library yet, and shows them downloading. " +
            "Skip this if you only want to watch what you already have.")
        backTo = if (settingsMode) ::overview else ::jellyfinSignIn
        val suggested = seerr.server.takeIf { it.isNotBlank() }
            ?: jellyfin.server.takeIf { it.isNotBlank() }?.let { runCatching { "http://" + java.net.URL(it).host + ":5055" }.getOrNull() }
        val address = Field("Seerr address", suggested)
        var user = Field("Jellyfin username", null)
        val password = Field("Password", null, secret = true)
        var local = false
        fun layout() {
            fields = listOf(address, user, password)
            chips = listOf((if (local) "Use your Jellyfin account instead" else "Use a Seerr email account instead") to {
                local = !local
                user = Field(if (local) "Seerr email" else "Jellyfin username", user.value)
                layout()
            })
        }
        layout()
        bg({ jellyfin.userName() }) { name: String? -> if (name != null && user.value.isBlank() && !local) user.value = name }
        primary("Connect", "check") {
            val a = address.value.trim(); val u = user.value.trim(); val pw = password.value
            if (a.isEmpty()) { status = "Enter Seerr's address, or skip."; return@primary }
            if (u.isEmpty() || pw.isEmpty()) { status = if (local) "Enter your Seerr email and password." else "Enter your Jellyfin username and password."; return@primary }
            busy = true; status = "Connecting…"
            bg({
                if (!seerr.probe(a)) "Couldn't find Seerr at $a. Check the address."
                else { seerr.setServer(a); if (local) seerr.signInLocal(u, pw) else seerr.signIn(u, pw) }
            }) { error: String? ->
                busy = false
                if (error != null) { status = error; return@bg }
                changed = true; ArrSetup.forget()
                advance(::arrStep)
            }
        }
        if (afterStep != null) secondary("Cancel") { afterStep = null; close() }
        else secondary(if (settingsMode && seerr.hasServer) "Turn off Seerr" else "Skip") {
            if (seerr.hasServer) { seerr.setServer(null); changed = true }
            if (settingsMode) overview() else regionStep()
        }
    }

    private fun arrStep() {
        page(Step.ARR, "Checking what Seerr shares…")
        backTo = if (settingsMode) ::overview else ::seerrStep
        bg({ (seerr.sonarrConfig() != null) to (seerr.radarrConfig() != null) }) { found: Pair<Boolean, Boolean>? ->
            val (sonarr, radarr) = found ?: (false to false)
            val manualS = settings.manualArr(Arr.SONARR); val manualR = settings.manualArr(Arr.RADARR)
            if (sonarr && radarr && manualS == null && manualR == null && !settingsMode) {
                body = "Found Sonarr and Radarr through Seerr. Downloads will show their progress here."
                primary("Continue", "chev") { clientStep() }
                return@bg
            }
            body = "Cinema reads Sonarr and Radarr to show downloads, and to ask for single episodes. Seerr only shares them with an " +
                "admin account, so you can enter them here instead. The API key is in each app under Settings, General. Leave both " +
                "blank to use what Seerr shares" + (if (!sonarr && !radarr) ", or skip to go without download progress." else ".")
            val seerrHost = seerr.host.takeIf { it.isNotBlank() }
            fun suggest(arr: Arr) = settings.manualArr(arr)?.baseUrl ?: seerrHost?.let { "http://$it:${arr.defaultPort}" }
            val sUrl = Field("Sonarr address", if (sonarr && manualS == null) null else suggest(Arr.SONARR))
            val sKey = Field("Sonarr API key", manualS?.apiKey, secret = true)
            val rUrl = Field("Radarr address", if (radarr && manualR == null) null else suggest(Arr.RADARR))
            val rKey = Field("Radarr API key", manualR?.apiKey, secret = true)
            fields = listOf(sUrl, sKey, rUrl, rKey)
            status = listOfNotNull(if (sonarr) "Sonarr found through Seerr ✓" else null, if (radarr) "Radarr found through Seerr ✓" else null).joinToString("   ")
            primary("Save", "check") {
                val entries = listOf(Triple(Arr.SONARR, sUrl, sKey), Triple(Arr.RADARR, rUrl, rKey)).map { (arr, url, key) ->
                    Triple(arr, CinemaSettings.cleanUrl(url.value), key.value.trim())
                }
                busy = true; status = "Checking…"
                bg({
                    entries.firstNotNullOfOrNull { (arr, url, key) ->
                        when {
                            key.isEmpty() -> null
                            url == null -> "Enter ${arr.label}'s address as well as its key."
                            else -> ArrSetup.check(arr, ManualArr(url, key))
                        }
                    }
                }) { error: String? ->
                    busy = false
                    if (error != null) { status = error; return@bg }
                    entries.forEach { (arr, url, key) -> settings.setManualArr(arr, if (key.isEmpty() || url == null) null else ManualArr(url, key)) }
                    ArrSetup.forget(); changed = true
                    advance(::clientStep)
                }
            }
            secondary(if (settingsMode) "Cancel" else "Skip") { advance(::clientStep) }
        }
    }

    private fun clientStep() {
        page(Step.CLIENT, "Looking at the download clients Sonarr and Radarr use…")
        backTo = if (settingsMode) ::overview else ::arrStep
        bg({ queue.detectClients().map { it to (it.config?.let { c -> queue.needsLogin(c) } ?: false) } }) { found ->
            val list = found.orEmpty()
            if (list.isEmpty()) {
                body = "No torrent client found. Cinema needs Sonarr or Radarr (set up through Seerr, or in the previous step) to find it. Requests still work without it."
            } else {
                body = "Download progress works with any client. Cinema can also move a download to the front of the queue or start it at once in qBittorrent, Transmission and Deluge."
                rows = list.map { (client, needsLogin) ->
                    val config = client.config
                    val kindName = config?.kind?.label ?: client.listed.implementation
                    val state = when { config == null -> "Progress only"; needsLogin -> "Needs your sign-in for the download controls"; else -> "Ready ✓" }
                    SetupRow("${client.listed.name} · $kindName", "${client.listed.host} · $state",
                        if (config == null) emptyList() else listOf(RowButton(if (needsLogin) "Sign in" else "Sign in again", accent = needsLogin) {
                            clientSignIn(config.kind.label, config.kind.needsUsername, { u, p -> queue.signIn(config, u, p) }) { clientStep() }
                        }))
                }
            }
            primary(if (settingsMode) "Done" else "Continue", "chev") { advance(::regionStep) }
        }
    }

    /** A download client's sign-in (the TV's DownloadClientSignInSheet): username (if it has one) and password on the keyboard. */
    fun clientSignIn(name: String, needsUsername: Boolean, signIn: (String, String) -> Boolean, done: () -> Unit) {
        fun askPassword(user: String) {
            m.keyboard = KeyboardRequest("$name password", secret = true, doneLabel = "Sign in") { pw ->
                m.keyboard = null
                m.bg({ signIn(user, pw) }) { ok -> m.toast(if (ok == true) "Signed in to $name" else "Couldn't sign in to $name"); if (ok == true) done() }
            }
        }
        if (needsUsername) m.keyboard = KeyboardRequest("$name username", doneLabel = "Next") { u -> m.keyboard = null; askPassword(u) }
        else askPassword("")
    }

    private fun regionStep() {
        page(Step.REGION, "Cinema shows what is streaming on Netflix, Prime Video, Disney+ and the rest in your country.")
        backTo = if (settingsMode) ::overview else if (seerr.hasServer) ::clientStep else ::seerrStep
        val field = Field("Two-letter country code", settings.region)
        fields = listOf(field)
        chips = listOf("GB", "IE", "US", "CA", "AU", "NZ", "DE", "FR", "ES", "IT", "NL", "SE").map { code ->
            Locale("", code).getDisplayCountry(Locale.ENGLISH) to { field.value = code }
        }
        primary(if (settingsMode) "Save" else "Finish", "check") {
            val code = field.value.trim().uppercase(Locale.ROOT)
            if (!code.matches(Regex("[A-Z]{2}"))) { status = "Use a two-letter country code, like GB or US."; return@primary }
            if (code != settings.region) changed = true
            settings.region = code; Catalog.country = code
            if (settingsMode) overview() else close()
        }
    }

    private fun advancedStep() {
        page(Step.ADVANCED, "Optional services that run next to Seerr. Leave these blank unless you run them.")
        backTo = ::overview
        val helper = Field("Request helper address", settings.requestHelperUrl)
        val stream = Field("Stream service address", settings.streamServiceUrl)
        fields = listOf(helper, stream)
        notes = mapOf(0 to "Watches requests on the server, retries searches, and reports why a request is stuck.",
            1 to "Plays a film before it has downloaded. Adds Stream now to title pages.")
        primary("Save", "check") {
            settings.requestHelperUrl = helper.value; settings.streamServiceUrl = stream.value
            changed = true; overview()
        }
        secondary("Cancel") { overview() }
    }

    private fun overview() {
        if (afterStep != null) { afterStep = null; changed = false; close(); return }
        page(Step.OVERVIEW, null)
        backTo = null
        val jf = SetupRow("Jellyfin", if (jellyfin.isConnected) jellyfin.server else "Not signed in", listOf(RowButton("Change") { jellyfinServer() }))
        bg({ if (jellyfin.isConnected) jellyfin.userName() else null }) { name: String? -> if (name != null) jf.detail = "${jellyfin.server} · $name" }
        val list = mutableListOf(jf)
        list += SetupRow("Seerr", when { !seerr.hasServer -> "Off"; seerr.isSignedIn -> seerr.server; else -> "${seerr.server} · not signed in" },
            listOf(RowButton(if (seerr.hasServer) "Change" else "Set up") { seerrStep() }))
        if (seerr.hasServer) {
            val manual = listOfNotNull(settings.manualArr(Arr.SONARR)?.let { "Sonarr" }, settings.manualArr(Arr.RADARR)?.let { "Radarr" })
            list += SetupRow("Sonarr and Radarr", if (manual.isEmpty()) "Through Seerr" else "${manual.joinToString(" and ")} entered here", listOf(RowButton("Change") { arrStep() }))
            list += SetupRow("Torrent client", "Download controls for qBittorrent, Transmission and Deluge", listOf(RowButton("Check") { clientStep() }))
        }
        list += SetupRow("Country", Locale("", settings.region).getDisplayCountry(Locale.ENGLISH), listOf(RowButton("Change") { regionStep() }))
        val following = LetterboxdRepository.followingCount(m.platform)
        list += SetupRow("Letterboxd", if (following == 0) "Not following anyone. Follow people from the Letterboxd page." else "Following $following · manage them from the Letterboxd page", emptyList())
        val extras = listOfNotNull(settings.requestHelperUrl?.let { "request helper" }, settings.streamServiceUrl?.let { "stream service" })
        list += SetupRow("Helper services", if (extras.isEmpty()) "None" else extras.joinToString(" and ").replaceFirstChar { it.uppercase() }, listOf(RowButton("Change") { advancedStep() }))
        list += SetupRow("Controller", m.pads().ifEmpty { listOf("None found. The keyboard works too: arrows, Enter and Esc.") }.joinToString(" · "), emptyList())
        list += SetupRow("Reset", "Forget every server, sign-in and setting on this device", listOf(RowButton("Reset everything", danger = true) { confirmReset() }))
        val up = m.updates.updater
        fun how() = if (up.auto) "updates install by themselves" else "you're asked before each update"
        val waiting = up.waiting.takeIf { it > 0 }?.let { " · build $it starts next time" }.orEmpty()
        lateinit var updates: SetupRow
        updates = SetupRow("Updates", "Cinema build ${up.currentBuild} · ${how()}$waiting", listOf(
            RowButton("Check for updates") {
                updates.detail = "Checking for a newer build…"
                m.updates.checkNow { u, reached ->
                    updates.detail = when {
                        u != null -> listOfNotNull("Build ${u.build} is ready", com.danielportelabyrne.cinema.data.update.UpdateCheck.headline(u)).joinToString(" · ")
                        !reached -> "Couldn't reach GitHub just now · build ${up.currentBuild}"
                        else -> "Up to date · build ${up.currentBuild} · ${how()}"
                    }
                    if (u != null) m.updates.offer(u)
                }
            },
            RowButton(if (up.auto) "Turn off automatic" else "Turn on automatic") { up.auto = !up.auto; overview() },
        ))
        list += updates
        list += SetupRow("Cinema for Linux", "Build ${up.currentBuild} · video plays in mpv", listOf(RowButton("Quit Cinema", danger = true) { m.quit() }))
        rows = list
        primary("Done", "check") { close() }
    }

    private fun confirmReset() {
        m.sheet = Sheet("RESET", "Reset everything?", "This device forgets Jellyfin, Seerr, Sonarr, Radarr, download-client logins and the country. Your servers are not changed.", emptyList(), listOf(
            SheetAction("Reset", "Start setup again", primary = true) {
                jellyfin.forget(); seerr.setServer(null); settings.reset(); ArrSetup.forget()
                LetterboxdRepository.forgetFollowing(m.platform)
                m.shelves = null; m.library = emptyList()
                m.stack.clear(); m.stack += Route.Setup
                startFirstRun()
            },
            SheetAction("Cancel") { },
        ))
    }
}
