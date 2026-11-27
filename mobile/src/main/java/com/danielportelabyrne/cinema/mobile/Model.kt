package com.danielportelabyrne.cinema.mobile

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danielportelabyrne.cinema.data.account.ProfileSync
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.CatalogRepository
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.movies.RequestOutcome
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.movies.Service
import com.danielportelabyrne.cinema.data.movies.ShelfSpec
import com.danielportelabyrne.cinema.data.movies.StreamStatus
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.mobile.data.HomeShelves
import com.danielportelabyrne.cinema.mobile.data.Shelves
import com.danielportelabyrne.cinema.mobile.device.MobilePlatform
import com.danielportelabyrne.cinema.mobile.offline.Offline
import com.danielportelabyrne.cinema.mobile.player.PlayerActivity
import com.danielportelabyrne.cinema.mobile.remote.TvRemote
import com.danielportelabyrne.cinema.mobile.update.MobileUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** The bottom bar's tabs. */
enum class Tab(val label: String) { HOME("Home"), BROWSE("Browse"), SEARCH("Search"), LIBRARY("Library"), MORE("More") }

/** A page pushed over a tab. */
sealed class Screen {
    class TitlePage(val title: Title, library: JellyfinItem?) : Screen() {
        var library by mutableStateOf(library)
        var availability by mutableStateOf<Availability?>(null)
        var episodes by mutableStateOf<List<JellyfinItem>>(emptyList())
        var season by mutableStateOf<Int?>(null)
        var version by mutableIntStateOf(0)
    }
    class ServicePage(val service: Service) : Screen()
    class ChannelPage(val channel: com.danielportelabyrne.cinema.data.movies.Channel) : Screen()
    class GenrePage(val genre: com.danielportelabyrne.cinema.data.movies.Genre) : Screen()
    class LayoutEditor(val page: com.danielportelabyrne.cinema.data.movies.LayoutPage) : Screen()
    data object Downloads : Screen()
    data object Offline : Screen()
    data object Remote : Screen()
    data object Settings : Screen()
    data object Requests : Screen()
}

/** A row of catalogue titles, loaded on demand. */
class Row(val spec: ShelfSpec) {
    var titles by mutableStateOf<List<Title>?>(null)
}

/**
 * Everything the phone's screens show and every action they can take (the TV's MoviesPanel, the desktop's
 * CinemaModel, without their focus handling). Network work runs on IO; state is changed on the main thread.
 */
class Model(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val platform = MobilePlatform(context)
    val client = JellyfinClient(platform)
    val seerr = SeerrClient(platform)
    val settings = CinemaSettings(platform)
    val catalog = CatalogRepository(platform)
    val queue by lazy { QueueClient(seerr, platform) }
    val offline = Offline(context, client)
    val remote = TvRemote(platform)
    val updater = MobileUpdater(context)
    private val shelvesApi = Shelves(platform, client)

    var needsSetup by mutableStateOf(!client.isConnected && !settings.setupDone)
    var tab by mutableStateOf(Tab.HOME)
    /** Which of Browse's pages (Films, Series, Documentaries) is showing, kept here so the page's colours can follow it. */
    var browsePage by mutableStateOf(com.danielportelabyrne.cinema.data.movies.LayoutPage.FILMS)
    /** Bumped when a page's layout is saved, so the pages redraw. */
    var layoutVersion by mutableIntStateOf(0)
    val favourites by lazy { com.danielportelabyrne.cinema.data.movies.FavouritesStore(platform) }
    val picks by lazy { com.danielportelabyrne.cinema.data.movies.PicksRepository(platform, seerr, client, com.danielportelabyrne.cinema.data.movies.WatchHistory(platform)) }

    /** The page as the owner laid it out (on the TV or here: it is the same saved layout), else its default rows. */
    fun layoutOf(page: com.danielportelabyrne.cinema.data.movies.LayoutPage): List<com.danielportelabyrne.cinema.data.movies.Section> {
        @Suppress("UNUSED_VARIABLE") val v = layoutVersion
        return com.danielportelabyrne.cinema.data.movies.PageLayouts.of(page, com.danielportelabyrne.cinema.data.movies.PageLayouts.parse(settings.pageLayouts))
    }

    /** Saves a page's rows. A page left as its default is not kept, so later improvements to the default still reach it. */
    fun saveLayout(page: com.danielportelabyrne.cinema.data.movies.LayoutPage, list: List<com.danielportelabyrne.cinema.data.movies.Section>) {
        val L = com.danielportelabyrne.cinema.data.movies.PageLayouts
        val saved = L.parse(settings.pageLayouts)
        settings.pageLayouts = L.encode(if (list == L.defaults(page)) saved - page else saved + (page to list))
        layoutVersion++
    }
    /** How My library lists films and series: the same setting as on the TV (it travels with the person's profile). */
    var libraryOrder by mutableStateOf(settings.libraryOrder)
    fun chooseLibraryOrder(order: com.danielportelabyrne.cinema.data.settings.LibraryOrder) { settings.libraryOrder = order; libraryOrder = order }
    val stack = mutableStateListOf<Screen>()

    var shelves by mutableStateOf<HomeShelves?>(null)
    var library by mutableStateOf<List<JellyfinItem>>(emptyList())
    var loading by mutableStateOf(false)
    var offlineMode by mutableStateOf(false)
    /** A request could not reach Jellyfin, and none has since. A saved library can fill the pages all the same. */
    var unreachable by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    /** Catalogue rows per page key ("home", "films", "series", "svc:nfx"...). */
    val rows = mutableStateMapOf<String, List<Row>>()

    var query by mutableStateOf("")
    var searchLibrary by mutableStateOf<List<JellyfinItem>>(emptyList())
    var searchRows by mutableStateOf<List<Row>>(emptyList())

    init {
        Catalog.country = settings.region
        Catalog.followed = settings.followedServices
        Catalog.followedChannels = settings.followedChannels
        client.onReachabilityChanged = { ok ->
            scope.launch {
                unreachable = !ok
                toast(if (ok) "Jellyfin is back" else "Can't reach the Jellyfin server")
                if (ok) keepPosters()
            }
        }
        client.onLibraryChanged = { val latest = client.loadedLibrary(); scope.launch { library = latest } }
        if (!needsSetup) { refresh(); client.startLibraryPolling(); pullProfile() }
    }

    /**
     * Takes the person's Cinema profile that their TVs keep on Jellyfin (followed services and channels, watchlist,
     * favourites, history, picks, Letterboxd follows...), when it is newer than what the phone last took. The first time,
     * it is laid over the phone's own files, as on a TV meeting a person for the first time. The phone only reads it: it
     * does not edit those lists yet, so it never saves over the TVs' copy.
     */
    fun pullProfile() = bg({
        val (snapshot, at) = client.profileData()?.let { ProfileSync.fromPrefs(it) } ?: return@bg false
        val prefs = platform.preferences("profile_sync")
        if (at <= prefs.getLong("synced_at", 0L)) return@bg false
        val applied = if (prefs.getLong("synced_at", 0L) == 0L) {
            val local = org.json.JSONObject(ProfileSync.snapshot(platform))
            val remote = org.json.JSONObject(snapshot)
            remote.keys().forEach { f ->
                val into = local.optJSONObject(f) ?: org.json.JSONObject().also { local.put(f, it) }
                val from = remote.getJSONObject(f)
                from.keys().forEach { k -> into.put(k, from.get(k)) }
            }
            ProfileSync.apply(platform, local.toString())
        } else ProfileSync.apply(platform, snapshot)
        if (applied) { prefs.edit().putLong("synced_at", at).apply(); client.adoptListsFromOtherAddresses() }
        applied
    }) { took ->
        if (took == true) {
            Catalog.followed = settings.followedServices
            Catalog.followedChannels = settings.followedChannels
            rows.clear()
            toast("Brought over your Cinema profile")
        }
    }

    fun <T> bg(work: () -> T, then: (T?) -> Unit = {}) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(work).getOrNull() }
            then(r)
        }
    }

    private var toastJob: Job? = null
    fun toast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = scope.launch { delay(3500); toast = null }
    }

    /** Everything this phone knows about the owner's servers and settings, as the TV's Reset does. Offline copies stay. */
    fun resetEverything() {
        client.forget(); seerr.setServer(null); settings.reset()
        com.danielportelabyrne.cinema.data.movies.ArrSetup.forget()
        platform.preferences("profile_sync").edit().clear().apply()
        stack.clear(); shelves = null; library = emptyList(); rows.clear(); tab = Tab.HOME
        libraryOrder = settings.libraryOrder
        needsSetup = true
    }

    fun setupFinished() {
        settings.setupDone = true
        needsSetup = false
        Catalog.country = settings.region
        rows.clear()
        refresh(force = true)
        client.startLibraryPolling()
        pullProfile()
    }

    // --- Library -------------------------------------------------------------------------------

    private var refreshing: Job? = null
    fun refresh(force: Boolean = false) {
        if (refreshing?.isActive == true) return
        if (!client.isConnected) { shelves = HomeShelves(); return }
        refreshing = scope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) { runCatching { shelvesApi.load(force) }.getOrDefault(HomeShelves()) }
            val lib = withContext(Dispatchers.IO) { client.loadedLibrary() }
            loading = false
            offlineMode = !online() || (loaded.isEmpty && lib.isEmpty())
            shelves = loaded; library = lib
            if (!offlineMode) keepPosters()
        }
    }

    /** Whether the phone has any network at all (none in flight mode). */
    private fun online(): Boolean = context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null

    // --- Offline copies ------------------------------------------------------------------------

    fun download(item: JellyfinItem) {
        toast(offline.download(item))
        keepPosters()
    }

    private fun keepPosters() = bg({ offline.savePosters() })

    fun libraryTitle(item: JellyfinItem): Title {
        val base = JellyfinApi.cleanBase(client.server)
        return Title(
            key = "jf:" + item.id, kind = if (item.isSeries) Kind.SHOW else Kind.MOVIE, name = item.name, year = item.year,
            overview = item.overview, runtimeMin = item.runtimeMin, genres = item.genres, age = item.officialRating,
            imdbScore = item.communityRating, posterUrl = JellyfinApi.posterUrl(base, item.id),
            backdropUrl = if (item.hasBackdrop) client.backdropUrl(item.id) else null, imdbId = item.imdbId, tmdbId = item.tmdbId,
        )
    }

    /** Headers for an image: Jellyfin's own pictures carry the sign-in, nothing else does. */
    fun imageHeaders(url: String?): Map<String, String> {
        if (url == null || !client.hasServer) return emptyMap()
        return if (url.startsWith(JellyfinApi.cleanBase(client.server))) client.playbackHeaders() else emptyMap()
    }

    fun posterOf(item: JellyfinItem): String = JellyfinApi.posterUrl(JellyfinApi.cleanBase(client.server), item.seriesId?.takeIf { item.isEpisode } ?: item.id)
    fun stillOf(item: JellyfinItem): String = if (item.hasPrimaryImage) client.episodeImageUrl(item.id) else posterOf(item)

    // --- Catalogue -----------------------------------------------------------------------------

    fun rowsFor(key: String, specs: () -> List<ShelfSpec>): List<Row> = rows.getOrPut(key) {
        specs().map { Row(it) }.also { list -> list.forEach { load(it) } }
    }

    private fun load(row: Row) = bg({ catalog.shelf(row.spec).titles }) { row.titles = it.orEmpty() }

    // --- Navigation ----------------------------------------------------------------------------

    fun push(s: Screen) { stack += s }
    fun selectTab(t: Tab) { if (tab == t) stack.clear() else { tab = t; stack.clear() } }
    fun back(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else if (tab != Tab.HOME) { tab = Tab.HOME; true } else false

    fun open(t: Title, library: JellyfinItem? = null) {
        val lib = library ?: if (client.isConnected) (if (t.key.startsWith("jf:")) this.library.firstOrNull { it.id == t.key.removePrefix("jf:") } else null)
            ?: client.findLoaded(t.imdbId, t.tmdbId, t.kind == Kind.SHOW) else null
        val page = Screen.TitlePage(t, lib)
        push(page)
        loadTitle(page)
    }

    fun openItem(item: JellyfinItem) {
        if (item.isEpisode) { play(item); return }
        open(libraryTitle(item), item)
    }

    fun loadTitle(page: Screen.TitlePage) {
        page.library?.let { lib ->
            bg({ client.item(lib.id) }) { fresh -> if (fresh != null) page.library = fresh }
            if (lib.isSeries) bg({ client.episodes(lib.id) }) { eps ->
                page.episodes = eps.orEmpty()
                if (page.season == null) page.season = eps.orEmpty().firstOrNull { !it.played }?.season ?: eps.orEmpty().firstOrNull()?.season
            }
        }
        val tmdb = page.title.tmdbId?.toLongOrNull()
        if (seerr.isSignedIn && tmdb != null) bg({ seerr.availability(page.title.kind, tmdb) }) { page.availability = it }
    }

    // --- Playing -------------------------------------------------------------------------------

    fun play(item: JellyfinItem, fromStart: Boolean = false) {
        offline.poll()   // it may have finished since anything last looked
        offline.find(item.id)?.let { copy -> if (copy.complete) { playOffline(copy.id, fromStart); return } }
        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PlayerActivity.ITEM, item.id).putExtra(PlayerActivity.FROM_START, fromStart))
    }

    fun playOffline(id: String, fromStart: Boolean = false) {
        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PlayerActivity.OFFLINE, id).putExtra(PlayerActivity.FROM_START, fromStart))
    }

    fun playUrl(url: String, title: String) {
        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PlayerActivity.URL, url).putExtra(PlayerActivity.TITLE, title))
    }

    /** The series' next episode to watch, or the film itself. */
    fun playTitle(page: Screen.TitlePage, fromStart: Boolean = false) {
        val lib = page.library ?: return
        if (!lib.isSeries) { play(lib, fromStart); return }
        bg({ client.episodeToPlay(lib.id) }) { ep -> if (ep != null) play(ep, fromStart) else toast("No episodes to play yet") }
    }

    var streaming by mutableStateOf<String?>(null)
    fun streamNow(t: Title, season: Int? = null, episode: Int? = null) {
        if (!seerr.hasStreamService) { toast("No stream service set up (More → Settings)"); return }
        if (!seerr.isSignedIn) { toast("Sign in to Seerr first (More → Settings)"); return }
        streaming = "Finding a copy of ${t.name}…"
        bg({ seerr.startStream(t.name, t.year, season, episode, if (episode == null) t.runtimeMin else null) }) { r ->
            streaming = null
            when (r?.status) {
                StreamStatus.READY -> r.url?.let { playUrl(it, t.name) } ?: toast("The stream service didn't send a link")
                StreamStatus.NOT_SIGNED_IN -> toast("Sign in to Seerr first")
                StreamStatus.NOT_FOUND -> toast("No seeded copy of ${t.name} was found")
                StreamStatus.CONNECTING -> toast("Still connecting to sources. Try again in a minute.")
                else -> toast("The stream service isn't reachable")
            }
        }
    }

    // --- Requests ------------------------------------------------------------------------------

    fun request(page: Screen.TitlePage, seasons: List<Int>? = null) {
        val tmdb = page.title.tmdbId?.toLongOrNull() ?: run { toast("Seerr doesn't know this title"); return }
        if (!seerr.isSignedIn) { toast("Sign in to Seerr in More → Settings to request"); return }
        bg({ seerr.request(page.title.kind, tmdb, seasons) }) { r ->
            toast(when (r) {
                RequestOutcome.QUEUED -> "Requested ${page.title.name}"
                RequestOutcome.ALREADY -> "${page.title.name} is already requested"
                RequestOutcome.NEEDS_SIGN_IN -> "Sign in to Seerr again"
                else -> "Couldn't request ${page.title.name}"
            })
            loadTitle(page)
        }
    }

    // --- Search --------------------------------------------------------------------------------

    private var searchJob: Job? = null
    fun search(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) { searchLibrary = emptyList(); searchRows = emptyList(); return }
        searchJob = scope.launch {
            delay(350)
            searchLibrary = searchIn(client.loadedLibrary(), q)
            searchRows = Catalog.searchSpecs(q).map { Row(it) }.also { list -> list.forEach { load(it) } }
        }
    }

    companion object {
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

        fun searchIn(items: List<JellyfinItem>, query: String): List<JellyfinItem> {
            val q = fold(query)
            if (q.isBlank()) return emptyList()
            val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
            return items.filter { i -> val n = fold(i.name); words.all { n.contains(it) } }
                .sortedBy { if (fold(it.name).startsWith(q)) 0 else 1 }
        }
    }
}
