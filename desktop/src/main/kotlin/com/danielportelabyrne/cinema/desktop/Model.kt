package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.platform.Platform
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository
import com.danielportelabyrne.cinema.data.movies.ByteSize
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.CatalogRepository
import com.danielportelabyrne.cinema.data.movies.Genre
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.LibraryRemover
import com.danielportelabyrne.cinema.data.movies.LibraryRef
import com.danielportelabyrne.cinema.data.movies.PicksRepository
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.movies.Service
import com.danielportelabyrne.cinema.data.movies.SonarrClient
import com.danielportelabyrne.cinema.data.movies.StreamStatus
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.data.movies.WatchHistory
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.stream.LoadingStep
import com.danielportelabyrne.cinema.desktop.jellyfin.HomeShelves
import com.danielportelabyrne.cinema.desktop.jellyfin.Shelves
import com.danielportelabyrne.cinema.desktop.player.Player
import com.danielportelabyrne.cinema.desktop.player.PlayerState
import com.danielportelabyrne.cinema.desktop.ui.CachedImages
import com.danielportelabyrne.cinema.desktop.ui.ImageSource
import com.danielportelabyrne.cinema.desktop.ui.KeyboardRequest
import com.danielportelabyrne.cinema.desktop.ui.tryFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** The rail's destinations, as on the TV, with Settings at the foot. [multicolour] icons keep their own colours. */
enum class Dest(val label: String, val icon: String, val multicolour: Boolean = false) {
    SEARCH("Search", "search"), HOME("Home", "home"), FILMS("Films", "film"), SERIES("Series", "tv"),
    DOCS("Documentaries", "doc"), LETTERBOXD("Letterboxd", "lb", multicolour = true), LIBRARY("My library", "lib"),
    DOWNLOADS("Downloads", "dl"), SETTINGS("Settings", "ic_gear");
}

/** Where the page is: one of the rail's destinations, or a page opened from one (TV's `Dest`). */
sealed class Page(val rail: Dest) {
    data object Home : Page(Dest.HOME)
    data object Films : Page(Dest.FILMS)
    data object Series : Page(Dest.SERIES)
    data object Docs : Page(Dest.DOCS)
    data object Library : Page(Dest.LIBRARY)
    data object Downloads : Page(Dest.DOWNLOADS)
    data object Search : Page(Dest.SEARCH)
    data object Letterboxd : Page(Dest.LETTERBOXD)
    class OnService(val service: Service) : Page(Dest.HOME)
    class InGenre(val genre: Genre) : Page(Dest.HOME)
    class LbPerson(val user: String) : Page(Dest.LETTERBOXD)
    class LbList(val path: String) : Page(Dest.LETTERBOXD)
    class LbSearch : Page(Dest.LETTERBOXD)
    class LbFollow : Page(Dest.LETTERBOXD)

    companion object {
        fun of(d: Dest): Page = when (d) {
            Dest.SEARCH -> Search; Dest.HOME -> Home; Dest.FILMS -> Films; Dest.SERIES -> Series; Dest.DOCS -> Docs
            Dest.LETTERBOXD -> Letterboxd; Dest.LIBRARY -> Library; Dest.DOWNLOADS -> Downloads; Dest.SETTINGS -> Home
        }
    }
}

/** A poster on a page: the title, and the library copy of it when there is one (TV's `Entry`). */
class Entry(val title: Title, library: JellyfinItem?) {
    var library by mutableStateOf(library)
}

/** Remembers which card had focus on a page, so coming back puts the viewer where they were. */
class FocusMemory {
    private val requesters = LinkedHashMap<String, FocusRequester>()
    var last: String? = null
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    fun restore(): Boolean = last?.let { requesters[it]?.tryFocus() } ?: false
    /** The first element registered on the page (its first card), for when there is nothing to restore. */
    fun requesterOrNull(): FocusRequester? = requesters.values.firstOrNull { it.tryFocus() }
    fun clear() { last = null }
}

/** One page's own state: where focus was, its scroll, its rows' scrolls, and the featured titles it is turning through. */
class PageState {
    val focus = FocusMemory()
    val column = LazyListState()
    val rows = HashMap<String, LazyListState>()
    var heroIndex by mutableIntStateOf(0)
    var heroEntries by mutableStateOf<List<Entry>>(emptyList())
    var heroLabels by mutableStateOf<List<String>>(emptyList())
    var heroRelated: List<Title> = emptyList()
    /** Picks set the hero first; the catalogue's first row only when nothing else has. */
    var heroFromPicks = false
}

sealed class Route {
    data object Setup : Route()
    data object Browse : Route()
    class Title(val entry: Entry, val related: List<com.danielportelabyrne.cinema.data.movies.Title>) : Route() {
        val focus = FocusMemory()
        val scroll = ScrollState(0)
        val rows = HashMap<String, LazyListState>()
        /** Bumped to redraw the page's buttons and seasons (after a request, a delete, playback). */
        var version by mutableIntStateOf(0)
    }
}

/** A sheet: the TV's InfoSheet (`kicker`, title, a subtitle chip, lines, then its actions above Close). */
class SheetAction(val label: String, val sublabel: String? = null, val primary: Boolean = false, val run: () -> Unit)
class Sheet(val kicker: String, val title: String, val subtitle: String?, val lines: List<String>, val actions: List<SheetAction>)

/** The stream loading screen's state, while the stream service looks for a copy. */
class StreamLoading(val title: String, val backdrop: String?) {
    var message by mutableStateOf("Finding a copy of $title. This can take a minute.")
    var step by mutableStateOf(LoadingStep.FINDING_PEERS)
}

/**
 * Everything the screens show and every action they can take: the TV's MoviesPanel and SetupFlow without their views.
 * Network calls run on the IO dispatcher; state changes are made on the UI thread ([ui]).
 */
class CinemaModel(
    val platform: Platform,
    val scope: CoroutineScope,
    val ui: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
) {
    val client = JellyfinClient(platform)
    val seerr = SeerrClient(platform)
    val settings = CinemaSettings(platform)
    val catalog = CatalogRepository(platform)
    val history = WatchHistory(platform)
    val picks by lazy { PicksRepository(platform, seerr, client, history) }
    val queueClient by lazy { QueueClient(seerr, platform) }
    val sonarr by lazy { SonarrClient(seerr) }
    val remover by lazy { LibraryRemover(seerr) }
    val lb by lazy { LetterboxdRepository(platform, { block -> scope.launch(ui) { block() } }) }
    private val shelvesApi = Shelves(platform, client)

    var images: ImageSource = CachedImages({ url ->
        val base = JellyfinApi.cleanBase(client.server)
        if (client.hasServer && url.startsWith(base)) client.playbackHeaders() else emptyMap()
    })

    val stack = mutableStateListOf<Route>()
    val route: Route get() = stack.lastOrNull() ?: Route.Browse

    var page by mutableStateOf<Page>(Page.Home)
    val trail = mutableStateListOf<Page>()
    private val pageStates = HashMap<Page, PageState>()
    fun state(p: Page): PageState = pageStates.getOrPut(p) { PageState() }

    var railOpen by mutableStateOf(false)
    /** Set when the menu was opened from the page and should take focus once it is on screen. */
    var railWanted by mutableStateOf(false)
    /** Puts focus on the page's first element (set by each page); Right from the rail uses it when there is nowhere to return to. */
    var pageEntry: () -> Boolean = { false }
    val railFocus = Dest.entries.associateWith { FocusRequester() }
    var storage by mutableStateOf<List<String>>(emptyList())

    var shelves by mutableStateOf<HomeShelves?>(null)
    var library by mutableStateOf<List<JellyfinItem>>(emptyList())
    /** Bumped when the library arrives, so cards look for their library copy again (the TV's `rebadge`). */
    var libraryVersion by mutableIntStateOf(0)
    var offline by mutableStateOf(false)
    var loading by mutableStateOf(false)

    var keyboard by mutableStateOf<KeyboardRequest?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var toast by mutableStateOf<String?>(null)
    var confirmQuit by mutableStateOf(false)
    var streamLoading by mutableStateOf<StreamLoading?>(null)
    var playerState by mutableStateOf<PlayerState>(PlayerState.Idle)
    var search by mutableStateOf("")
    /** Where a real keyboard types on pages with their own text (Letterboxd search and follow): a char, or null to delete. */
    var typingTarget: ((Char?) -> Unit)? = null

    /** Names of the connected game controllers, for Settings. */
    var pads: () -> List<String> = { emptyList() }
    var onQuit: () -> Unit = {}

    val setup = SetupModel(this)

    val player = Player(client, onState = { s -> scope.launch(ui) { playerState = s } }, onClosed = { scope.launch(ui) { afterPlayback() } })

    init {
        client.onLibraryChanged = {
            val latest = client.loadedLibrary()
            scope.launch(ui) {
                library = latest
                libraryVersion++
                shelves = shelves?.copy(films = latest.filter { it.isMovie }.take(30), series = latest.filter { it.isSeries }.take(30))
            }
        }
        client.startLibraryPolling()
    }

    fun start() {
        Catalog.country = settings.region
        stack.clear()
        if (client.isConnected || settings.setupDone) { stack += Route.Browse; refresh(); loadStorage() }
        else { stack += Route.Setup; setup.startFirstRun() }
    }

    fun <T> bg(work: () -> T, then: (T) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(work).getOrNull() }
            withContext(ui) { @Suppress("UNCHECKED_CAST") then(result as T) }
        }
    }

    private var toastJob: Job? = null
    fun toast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = scope.launch(ui) { delay(3500); toast = null }
    }

    // --- Library ---------------------------------------------------------------------------------

    private var refreshing: Job? = null

    fun refresh(force: Boolean = false) {
        if (refreshing?.isActive == true) return
        if (!client.isConnected) { shelves = HomeShelves(); return }
        refreshing = scope.launch {
            withContext(ui) { loading = true }
            val loaded = withContext(Dispatchers.IO) { shelvesApi.load(force) }
            val lib = withContext(Dispatchers.IO) { client.loadedLibrary() }
            withContext(ui) {
                loading = false
                offline = client.isConnected && loaded.isEmpty && lib.isEmpty()
                shelves = loaded; library = lib; libraryVersion++
            }
        }
    }

    /** The library copy of a catalogue title, from what is already loaded (reads [libraryVersion] so cards update). */
    fun entry(t: Title): Entry {
        @Suppress("UNUSED_VARIABLE") val v = libraryVersion
        return Entry(t, if (client.isConnected) client.findLoaded(t.imdbId, t.tmdbId, t.kind == Kind.SHOW) else null)
    }

    fun libraryTitle(item: JellyfinItem): Title {
        val base = JellyfinApi.cleanBase(client.server)
        return Title(
            key = "jf:" + item.id, kind = if (item.isSeries) Kind.SHOW else Kind.MOVIE, name = item.name, year = item.year,
            overview = item.overview, runtimeMin = item.runtimeMin, genres = item.genres, age = item.officialRating,
            imdbScore = item.communityRating, posterUrl = JellyfinApi.posterUrl(base, item.id),
            backdropUrl = if (item.hasBackdrop) client.backdropUrl(item.id) else null, imdbId = item.imdbId, tmdbId = item.tmdbId,
        )
    }

    fun libraryEntries(items: List<JellyfinItem>): List<Entry> = items.map { Entry(libraryTitle(it), it) }

    /** "In your library · 1.2 TB", "1.8 TB free": the rail's foot, when it is open. */
    fun loadStorage() {
        bg({
            listOfNotNull(
                client.takeIf { it.isConnected }?.libraryBytes()?.let { "${ByteSize.format(it)} in library" },
                seerr.takeIf { it.isSignedIn }?.freeSpaceBytes()?.let { "${ByteSize.format(it)} free" },
            )
        }) { storage = it.orEmpty() }
    }

    // --- Navigation ------------------------------------------------------------------------------

    fun go(to: Page, remember: Boolean = false) {
        if (remember) trail += page else { trail.clear(); pageStates.keys.filter { it !is Page.Home && it::class != to::class }.forEach { if (it is Page.OnService || it is Page.InGenre || it is Page.LbPerson || it is Page.LbList || it is Page.LbSearch || it is Page.LbFollow) pageStates.remove(it) } }
        while (stack.size > 1 && stack.last() !is Route.Browse) stack.removeAt(stack.lastIndex)
        page = to
    }

    /** Opens the menu and moves focus into it (Left from the page's left edge). */
    fun openRail() {
        loadStorage()
        railOpen = true
        railWanted = true
    }

    fun goRail(d: Dest) {
        if (d == Dest.SETTINGS) { openSettings(); return }
        if (route == Route.Browse && page == Page.of(d) && trail.isEmpty()) return
        go(Page.of(d))
    }

    fun openSettings() { stack += Route.Setup; setup.openSettings() }

    /** Opens a title's page (`openDetail`). [related] are the titles of the row it came from, for "More like this". */
    fun open(e: Entry, related: List<Title> = emptyList()) {
        stack += Route.Title(e, related)
        // A library copy may have watch state newer than the list it came from.
        e.library?.let { lib -> bg({ client.item(lib.id) }) { fresh -> if (fresh != null) e.library = fresh } }
    }

    fun openItem(item: JellyfinItem) = open(Entry(libraryTitle(item), item), emptyList())

    /** An episode card (Continue watching, Next up) plays straight away; anything else opens its page. */
    fun select(item: JellyfinItem) { if (item.isEpisode) play(item) else openItem(item) }

    fun back(): Boolean {
        when {
            keyboard != null -> keyboard = null
            sheet != null -> sheet = null
            streamLoading != null -> closeStreamLoading()
            confirmQuit -> confirmQuit = false
            playerState is PlayerState.Failed -> playerState = PlayerState.Idle
            route is Route.Setup -> { if (!setup.back()) confirmQuit = true }
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            trail.isNotEmpty() -> page = trail.removeAt(trail.lastIndex)
            page != Page.Home -> go(Page.Home)
            else -> confirmQuit = true
        }
        return true
    }

    fun quit() { player.shutdown(); runCatching { lb.close() }; onQuit() }

    // --- Playing ---------------------------------------------------------------------------------

    fun play(item: JellyfinItem, fromStart: Boolean = false, title: Title? = null) {
        (title ?: libraryTitle(item)).let { remember(it, streamed = false) }
        player.play(item, fromStart)
    }

    private fun afterPlayback() {
        refresh()
        // The viewer's place has moved: redraw the title page they came back to.
        (route as? Route.Title)?.let { r ->
            val lib = r.entry.library ?: return@let
            bg({ Thread.sleep(1200); client.item(lib.id) }) { fresh -> if (fresh != null) r.entry.library = fresh; r.version++ }
        }
    }

    /** Notes a title as watched or streamed here, for "Picked for you". */
    fun remember(t: Title, streamed: Boolean) {
        val id = t.tmdbId?.toLongOrNull() ?: return
        bg({ history.add(id, t.kind, t.name, t.genres, streamed) }) {}
    }

    private var streamToken = 0

    /** Streams a seeded copy from the home stream service in mpv, without waiting for a download (`streamNow`). */
    fun streamNow(e: Entry, episode: Pair<Int, Int>? = null, shownTitle: String? = null) {
        if (!seerr.hasStreamService) { toast("Not in your library yet. Use the download button to get it."); return }
        if (!seerr.isSignedIn) { signInToSeerr { streamNow(e, episode, shownTitle) }; return }
        val token = ++streamToken
        val label = shownTitle ?: e.title.name
        val screen = StreamLoading(label, e.title.backdropUrl ?: e.library?.takeIf { it.hasBackdrop }?.let { client.backdropUrl(it.id) })
        streamLoading = screen
        fun failed(message: String) {
            screen.message = message
            scope.launch(ui) { delay(3500); if (token == streamToken) closeStreamLoading() }
        }
        bg({ seerr.startStream(e.title.name, e.title.year, episode?.first, episode?.second, if (episode == null) e.title.runtimeMin else null) }) { result ->
            if (token != streamToken) return@bg
            when (result?.status) {
                StreamStatus.READY -> {
                    val url = result.url ?: return@bg failed("The stream service didn't send a link. Try again in a minute.")
                    screen.message = "Found a copy. Starting the player…"; screen.step = LoadingStep.BUFFERING
                    remember(e.title, streamed = true)
                    player.playUrl(url, label)
                    scope.launch(ui) { delay(1500); if (token == streamToken) closeStreamLoading() }
                }
                StreamStatus.NOT_SIGNED_IN -> { closeStreamLoading(); signInToSeerr { streamNow(e, episode, shownTitle) } }
                StreamStatus.NOT_FOUND -> failed("No seeded copy of ${e.title.name} was found on your indexers.")
                StreamStatus.CONNECTING -> failed("Still connecting to the sources. Try again in a minute.")
                StreamStatus.UNAVAILABLE, null -> failed("The stream service isn't reachable.")
            }
        }
    }

    fun closeStreamLoading() { streamLoading = null; streamToken++ }

    // --- Requests and the library ----------------------------------------------------------------

    /** Sign in to Seerr from inside Cinema (the TV's Seerr sheet redirects to its Seerr step the same way). */
    fun signInToSeerr(onDone: () -> Unit) { stack += Route.Setup; setup.signInToSeerr(onDone) }

    /** Takes a downloaded title off disk after the owner confirms (`confirmDelete`). */
    fun confirmDelete(e: Entry, done: () -> Unit) {
        val ref = LibraryRef(e.title.name, e.title.year, e.title.tmdbId?.toLongOrNull(), e.title.imdbId)
        sheet = Sheet("DELETE", e.title.name, null, listOf("Removes every file for this title from the storage, and from Radarr or Sonarr so it is not downloaded again. Titles those do not hold are removed from Jellyfin. This cannot be undone."), listOf(
            SheetAction("Delete", "Remove it from disk", primary = true) {
                bg({
                    val managed = remover.delete(e.title.kind, ref)
                    val lib = e.library
                    val removed = managed || (lib != null && client.deleteItem(lib.id))
                    if (removed) client.refreshLibrary()
                    val stillThere = if (removed && lib != null) client.stillInLibrary(lib.id) else null
                    Triple(removed, managed, stillThere)
                }) { r ->
                    val (removed, managed, stillThere) = r ?: Triple(false, false, null)
                    toast(when {
                        !removed -> "Couldn't delete ${e.title.name} right now"
                        !managed && stillThere == true -> "Jellyfin still lists ${e.title.name}, so its files weren't removed"
                        else -> "Deleted ${e.title.name}"
                    })
                    if (removed) { refresh(force = true); done() }
                    loadStorage()
                }
            },
            SheetAction("Keep it") { },
        ))
    }

    fun searchLibraryLoaded(query: String): List<JellyfinItem> =
        if (client.isConnected) client.loadedLibrary().filter { it.name.contains(query, ignoreCase = true) } else emptyList()

    companion object {
        /** What was typed, then (when no port was given) Jellyfin's usual port 8096 on the same host. */
        fun addressCandidates(base: String): List<String> {
            val hostPart = base.substringAfter("://").substringBefore('/')
            return if (hostPart.contains(':') || base.startsWith("https://")) listOf(base) else listOf(base, "$base:8096")
        }

        /** "The Godfather" files under G, accents fold away, case does not matter. */
        fun sortKey(name: String): String = fold(name).removePrefix("the ").removePrefix("a ").removePrefix("an ")

        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

        /** Titles whose name holds every word typed, best matches (name starts with the query) first. */
        fun searchLibrary(items: List<JellyfinItem>, query: String): List<JellyfinItem> {
            val q = fold(query)
            if (q.isBlank()) return emptyList()
            val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
            return items.filter { item -> val n = fold(item.name); words.all { n.contains(it) } }
                .sortedWith(compareBy<JellyfinItem> { if (fold(it.name).startsWith(q)) 0 else 1 }.thenBy { sortKey(it.name) })
        }
    }
}
