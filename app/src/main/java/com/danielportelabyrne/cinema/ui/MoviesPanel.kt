package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import com.danielportelabyrne.cinema.device.AndroidPlatform
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.ContinueWatching
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Favourite
import com.danielportelabyrne.cinema.data.movies.Favourites
import com.danielportelabyrne.cinema.data.movies.FavouritesStore
import com.danielportelabyrne.cinema.data.movies.KnownTitle
import com.danielportelabyrne.cinema.data.movies.ContinueAction
import com.danielportelabyrne.cinema.data.movies.ContinueDismissals
import com.danielportelabyrne.cinema.data.movies.PersonalFilter
import com.danielportelabyrne.cinema.data.movies.PersonalPage
import com.danielportelabyrne.cinema.data.movies.TitleFacts
import com.danielportelabyrne.cinema.data.movies.TitleFactsStore
import com.danielportelabyrne.cinema.data.movies.PlayHistoryStore
import com.danielportelabyrne.cinema.data.movies.RecentCard
import com.danielportelabyrne.cinema.data.movies.RecentTarget
import com.danielportelabyrne.cinema.data.movies.RecentlyWatched
import com.danielportelabyrne.cinema.data.letterboxd.Lb
import com.danielportelabyrne.cinema.data.letterboxd.LbFilm
import com.danielportelabyrne.cinema.data.letterboxd.LbList
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.CatalogRepository
import com.danielportelabyrne.cinema.data.movies.Genre
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.ui.EpisodeCards
import com.danielportelabyrne.cinema.data.movies.SavedStream
import com.danielportelabyrne.cinema.data.movies.ContinueSource
import com.danielportelabyrne.cinema.data.movies.ContinueOpen
import com.danielportelabyrne.cinema.data.movies.ContinueInputs
import com.danielportelabyrne.cinema.data.movies.ContinueEpisode
import com.danielportelabyrne.cinema.data.movies.ContinueEntry
import com.danielportelabyrne.cinema.data.movies.ContinueEngine
import com.danielportelabyrne.cinema.data.movies.ContinueCards
import android.util.Log
import com.danielportelabyrne.cinema.data.movies.ByteSize
import com.danielportelabyrne.cinema.data.movies.EpisodeOutcome
import com.danielportelabyrne.cinema.data.movies.EpisodeWatch
import com.danielportelabyrne.cinema.data.movies.NextEpisode
import com.danielportelabyrne.cinema.data.movies.NextUp
import com.danielportelabyrne.cinema.data.movies.EpisodeState
import com.danielportelabyrne.cinema.data.movies.LibraryRef
import com.danielportelabyrne.cinema.data.movies.LibraryRemover
import com.danielportelabyrne.cinema.data.movies.DownloadAction
import com.danielportelabyrne.cinema.data.movies.DownloadActionResult
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.movies.WantedEpisode
import com.danielportelabyrne.cinema.data.movies.QueueItem
import com.danielportelabyrne.cinema.data.movies.QueueSource
import com.danielportelabyrne.cinema.data.movies.RequestDiagnostic
import com.danielportelabyrne.cinema.data.movies.RequestPipelineApi
import com.danielportelabyrne.cinema.data.movies.RequestOutcome
import com.danielportelabyrne.cinema.ui.room.SheetAction
import com.danielportelabyrne.cinema.data.movies.SonarrClient
import com.danielportelabyrne.cinema.data.movies.SeerrRequest
import com.danielportelabyrne.cinema.data.movies.Stage
import com.danielportelabyrne.cinema.data.movies.StreamStart
import com.danielportelabyrne.cinema.data.movies.StreamStatus
import com.danielportelabyrne.cinema.data.stream.RankedStream
import com.danielportelabyrne.cinema.data.stream.StreamCandidate
import com.danielportelabyrne.cinema.data.stream.StreamMetadata
import com.danielportelabyrne.cinema.data.stream.Verdict
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.movies.Service
import com.danielportelabyrne.cinema.data.movies.ShelfSpec
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.AppTheme
import com.danielportelabyrne.cinema.design.SectionHeader
import com.danielportelabyrne.cinema.design.cinema.AmbientBackground
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.CinemaConfirm
import com.danielportelabyrne.cinema.data.search.RecentSearches
import com.danielportelabyrne.cinema.data.movies.FollowedServices
import com.danielportelabyrne.cinema.data.movies.ServiceDirectory
import com.danielportelabyrne.cinema.data.movies.ServiceDirectoryRepository
import com.danielportelabyrne.cinema.data.movies.Watchlist
import com.danielportelabyrne.cinema.data.movies.WatchlistFilm
import com.danielportelabyrne.cinema.data.search.SearchKeyboard
import com.danielportelabyrne.cinema.design.cinema.FrostView
import com.danielportelabyrne.cinema.design.cinema.HeroBackdrop
import com.danielportelabyrne.cinema.design.cinema.PosterCard
import com.danielportelabyrne.cinema.design.cinema.Scrim
import com.danielportelabyrne.cinema.design.cinema.StripePoster
import com.danielportelabyrne.cinema.design.cinema.StreamLoadingView
import com.danielportelabyrne.cinema.design.cinema.CinemaBar
import com.danielportelabyrne.cinema.design.cinema.GlowCard
import com.danielportelabyrne.cinema.design.cinema.MemberCard
import com.danielportelabyrne.cinema.design.cinema.FollowCard
import com.danielportelabyrne.cinema.design.cinema.ListCard
import com.danielportelabyrne.cinema.design.cinema.ReviewCard
import com.danielportelabyrne.cinema.design.cinema.QwertyKeyboard
import com.danielportelabyrne.cinema.design.cinema.VoiceListeningView
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.data.search.VoiceState
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.design.cinema.MicButton
import com.danielportelabyrne.cinema.design.cinema.SearchField
import com.danielportelabyrne.cinema.ui.phone.PhoneInput
import com.danielportelabyrne.cinema.ui.phone.PhoneTextTarget
import com.danielportelabyrne.cinema.data.settings.LibraryOrder
import com.danielportelabyrne.cinema.design.cinema.RibbonView
import com.danielportelabyrne.cinema.design.cinema.StripeDrawable
import com.danielportelabyrne.cinema.design.Type
import com.danielportelabyrne.cinema.ui.room.JellyfinConnectSheet
import com.danielportelabyrne.cinema.ui.room.SeerrSignInSheet
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Cinema: the whole app, laid out like a streaming service but dressed like the rest of the room. An icon rail on the
 * left opens Home, Films, Series, Documentaries, My library, Downloads and Search; each destination is a page of poster
 * rows under a featured title. Services and genres have pages of their own. The first row of Home is the owner's
 * Jellyfin library; everything else is what is trending and popular across the big UK services. A poster opens its
 * title page: Watch now when it is in the library, Queue download when it is not.
 */
class MoviesPanel(
    private val activity: Activity,
    private val overlay: View,
    private val body: ViewGroup,
    private val top: LinearLayout,
    private val page: android.widget.ScrollView,
    private val content: LinearLayout,
    private val detail: FrameLayout,
    private val jellyfin: JellyfinClient,
    private val jellyfinSheet: JellyfinConnectSheet,
    private val seerr: SeerrClient,
    private val seerrSheet: SeerrSignInSheet,
    private val infoSheet: com.danielportelabyrne.cinema.ui.room.InfoSheet,
    private val libraryOrder: () -> LibraryOrder = { LibraryOrder.RECENT },
    /** Opens Settings (over Cinema) from the gear at the foot of the rail. */
    private val onOpenSettings: () -> Unit = {},
    /** Whether an integration is switched on in Settings; one that is off has no rail item or title page section. */
    private val integrationOn: (Integration) -> Boolean = { true },
    /** The music server, when one is set up. */
    private val music: () -> com.danielportelabyrne.cinema.data.music.SubsonicConfig? = { null },
    /** The Stremio add-ons the owner ported; the ones that stream are asked for copies when something is played. */
    private val stremioAddons: () -> List<com.danielportelabyrne.cinema.data.stremio.StremioAddon> = { emptyList() },
    /** How far each episode got in a stream played here (see StreamProgress), as saved. */
    private val streamProgress: () -> String? = { null },
    /** The searches run lately on each search keyboard, most recent first, and the way to keep them (see RecentSearches). */
    private val recentSearches: (SearchKeyboard) -> List<String> = { emptyList() },
    private val saveRecentSearches: (SearchKeyboard, List<String>) -> Unit = { _, _ -> },
    /** Films the owner means to watch: shown as the Watchlist row in My library. */
    private val watchlist: () -> List<com.danielportelabyrne.cinema.data.movies.WatchlistFilm> = { emptyList() },
    /** Keeps the watchlist after a title is added to it or taken off it. */
    private val saveWatchlist: (List<com.danielportelabyrne.cinema.data.movies.WatchlistFilm>) -> Unit = {},
    /** Keeps the services followed on Home ("Browse by service") after they change. */
    private val saveFollowedServices: (List<Service>) -> Unit = {}
) {
    /** Where the page is: one of the tabs, or a service or genre opened from Home, or search. */
    private sealed class Dest {
        object Home : Dest()
        object Films : Dest()
        object Series : Dest()
        object Docs : Dest()
        object Library : Dest()
        object Watchlist : Dest()
        object Downloads : Dest()
        object Search : Dest()
        class OnService(val service: Service) : Dest()
        class InGenre(val genre: Genre) : Dest()
        /** Letterboxd: the followed members' page, one member, one list, list search, and following someone new. */
        object Letterboxd : Dest()
        class LbPerson(val user: String) : Dest()
        class LbListPage(val path: String) : Dest()
        object LbSearch : Dest()
        object LbFollow : Dest()
        /** The services followed on Home, and the search for more to follow. */
        object ServiceManage : Dest()
        object ServiceSearch : Dest()
        /** Music: the server's album lists, one album, one artist. */
        object Music : Dest()
        class MusicAlbum(val id: String) : Dest()
        class MusicArtist(val id: String, val name: String) : Dest()
        /** Not a page: the rail's gear opens Settings over Cinema. */
        object Settings : Dest()
    }

    /** The rail item a page belongs to: a service or genre page is part of Home, every Letterboxd page of Letterboxd. */
    private fun railOf(d: Dest): Dest = when (d) {
        is Dest.OnService, is Dest.InGenre, Dest.ServiceManage, Dest.ServiceSearch -> Dest.Home
        is Dest.LbPerson, is Dest.LbListPage, Dest.LbSearch, Dest.LbFollow -> Dest.Letterboxd
        is Dest.MusicAlbum, is Dest.MusicArtist -> Dest.Music
        Dest.Watchlist -> Dest.Library
        else -> d
    }

    /** A poster on the page: the title, and the library copy of it when there is one. */
    private class Entry(val title: Title, var library: JellyfinItem?, val posterHeaders: Map<String, String>)

    /** A heading with its scrolling row. Hidden when the row turns out to be empty. */
    private class Row(val heading: View?, val scroller: View, val inner: LinearLayout) {
        /** Posters not loaded yet because they sit far along the row; loaded as the row scrolls towards them. */
        val deferred = ArrayList<Pair<View, () -> Unit>>()

        /** Loads the deferred posters within one and a half screens of what the row shows. */
        fun loadNear() {
            if (deferred.isEmpty()) return
            val limit = scroller.scrollX + scroller.width * 3 / 2
            val due = deferred.filter { (v, _) -> v.left < limit || scroller.width == 0 }
            deferred.removeAll(due.toSet()); due.forEach { it.second() }
        }

        fun hide() { heading?.visibility = View.GONE; scroller.visibility = View.GONE }
        /** Shows how many titles the row holds beside its heading ("History films  9"). */
        fun setCount(n: Int) { (heading?.findViewWithTag<TextView>(Cinema.COUNT_TAG))?.let { it.text = "$n"; it.visibility = View.VISIBLE } }
    }

    /** Short calls: library, storage, title states, requests. */
    private val worker = Executors.newFixedThreadPool(3)
    /** Catalogue shelves and search: never queued behind a slow Jellyfin or a stream start. */
    private val shelfPool = Executors.newFixedThreadPool(3)
    /** Calls that can take a minute or more (a stream start, the Downloads refresh, deletes): each gets its own thread. */
    private val slowPool = Executors.newCachedThreadPool()
    private val storagePool = Executors.newSingleThreadExecutor { r -> Thread(r, "storage").apply { priority = Thread.MIN_PRIORITY; isDaemon = true } }
    private val images = RemoteImages.shared(activity)
    /** Groups this page's picture requests, so a rebuilt page drops the ones the old page still had queued. */
    private var pageTag = Any()
    private val catalog = CatalogRepository(AndroidPlatform(activity))
    private val favourites by lazy { FavouritesStore(AndroidPlatform(activity)) }
    private val picks by lazy { com.danielportelabyrne.cinema.data.movies.PicksRepository(AndroidPlatform(activity), seerr, jellyfin, com.danielportelabyrne.cinema.data.movies.WatchHistory(AndroidPlatform(activity))) }
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    private var current: Dest = Dest.Home
    private val trail = ArrayDeque<Dest>()
    private var returnFocus: View? = null
    private val tabs = LinkedHashMap<LinearLayout, Dest>()
    private val navigationLabels = LinkedHashMap<LinearLayout, Pair<ImageView, TextView>>()
    private var navigationExpanded = false
    private var lastContentFocus: View? = null
    private var navigationFocusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener? = null
    private var topBuilt = false
    private var searchPending: Runnable? = null

    init {
        jellyfin.onLibraryChanged = { ui.post { rebadge(generation); refreshPersonal(generation) } }
        jellyfin.startLibraryPolling()
        // A few seconds after the app starts, read Home's first shelves into memory (from disk; the network only
        // when there is no copy yet), so the first open of Movies does not pay for reading and parsing them.
        ui.postDelayed({
            shelfPool.execute { Catalog.homePage().take(PREWARM_SHELVES).forEach { catalog.shelf(it) }; titleFacts.all() }
            if (jellyfin.isConnected) worker.execute { jellyfin.library() }
        }, PREWARM_DELAY_MS)
    }

    private lateinit var storageView: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroBody: TextView
    private lateinit var heroButtons: LinearLayout
    private var heroSet = false

    val isVisible: Boolean get() = overlay.visibility == View.VISIBLE
    private val detailOpen: Boolean get() = detail.visibility == View.VISIBLE

    /** The Cinema ground behind every page: espresso with warm and teal glows and the faint arch columns. Painted once. */
    private var ambientView: AmbientBackground? = null
    private var frost: FrostView? = null
    private var edgeFade: View? = null

    /** The right-edge fade shows over rows, not over the featured title's picture. */
    private fun updateEdgeFade() {
        val f = edgeFade ?: return
        val overHero = heroSet && ::heroFrame.isInitialized && heroFrame.isAttachedToWindow && page.scrollY < heroFrame.height / 2
        val target = if (overHero) 0f else 1f
        if (f.alpha != target && f.tag != target) { f.tag = target; f.animate().alpha(target).setDuration(200).start() }
    }

    private fun showAmbient() {
        if (ambientView != null) return
        val host = overlay as? ViewGroup ?: return
        // The ground is opaque and fills the screen, so the overlay needs no fill of its own beneath it.
        overlay.background = null
        ambientView = AmbientBackground(activity).also { host.addView(it, 0, FrameLayout.LayoutParams(-1, -1)) }
    }

    /**
     * While the pages are up, nothing beneath their ground is ever seen: the window's and the root's own fills are taken
     * away, as every full-screen layer is painted on every frame (three of them were most of a Chromecast HD's work
     * while a page scrolled). They come back when the pages close, for the screens that show on them.
     */
    private fun underlayers(on: Boolean) {
        val ink = Cinema.BG
        activity.window.setBackgroundDrawable(if (on) android.graphics.drawable.ColorDrawable(ink) else null)
        activity.findViewById<View>(R.id.cinemaRoot)?.let { if (on) it.setBackgroundColor(ink) else it.background = null }
    }

    /** The page moves up and down only through its own glide (see CinemaContentScrollView), so no two moves compete. */
    private fun pageGlideTo(y: Int) { (page as? CinemaContentScrollView)?.glideTo(y) ?: page.smoothScrollTo(0, y) }

    /** A new or reopened page starts at [y] at once: any glide still running for the old one is stopped first. */
    private fun pageJumpTo(y: Int) {
        (page as? CinemaContentScrollView)?.let { it.stopGlide(); it.forgetFocus() }
        page.scrollTo(0, y)
    }

    /** Where page content starts: clear of the floating rail (184 mockup px), and its right edge (96). */
    private val pageStart: Int get() = Cinema.px(activity, 184)
    private val pageEnd: Int get() = Cinema.px(activity, 96)

    fun show() {
        // The player asks, a few minutes before a streamed episode ends, for the next one to be searched for (see warmUpcoming).
        PlayerActivity.onUpcomingEpisode = { r -> ui.post { warmUpcoming(r) } }
        Perf.mark("movies.show")
        Perf.begin("movies.open->firstRow"); Perf.begin("movies.open->firstPoster"); Perf.begin("movies.open->hero")
        overlay.visibility = View.VISIBLE
        showAmbient()
        underlayers(on = false)
        com.danielportelabyrne.cinema.design.Motion.enter(overlay, null, 0f)
        if (!topBuilt) { buildTop(); topBuilt = true }
        syncIntegrations()
        val reuse = current === Dest.Home && trail.isEmpty() && content.childCount > 0 && heroSet &&
            Perf.now() - builtAt < REUSE_MS && jellyfin.isConnected == builtConnected && !servicesDirty
        servicesDirty = false
        trail.clear(); current = Dest.Home
        styleTabs()
        if (reuse) reopen() else build()
        loadStorage()
    }

    /** The Home page as it was left, back at the top with the featured title focused; nothing is rebuilt or refetched. */
    private fun reopen() {
        pageJumpTo(0)
        (0 until content.childCount).map { content.getChildAt(it) }.forEach { row -> (row as? android.widget.HorizontalScrollView)?.scrollTo(0, 0) }
        lastContentFocus = null
        heroButtons.getChildAt(0)?.let { requestFocusRobust(overlay as ViewGroup, it) }
        ui.removeCallbacks(heroTick)
        if (heroEntries.size > 1) ui.postDelayed(heroTick, HERO_TURN_MS)
        frost?.post { frost?.refresh(500); updateEdgeFade() }
        page.post { fillNear() }
        Perf.end("movies.open->firstRow", "reused"); Perf.end("movies.open->firstPoster", "reused"); Perf.end("movies.open->hero", "reused")
        // Badges may have changed while the page was away (a download finished).
        if (jellyfin.isConnected) worker.execute { jellyfin.library(); ui.post { rebadge(generation) } }
    }

    /** When the current page was built, and whether Jellyfin was signed in then; a reopen within [REUSE_MS] keeps it. */
    private var builtAt = 0L
    private var builtConnected = false

    /** After Settings changed a server or sign-in: the next [show] builds everything afresh. */
    fun reload() {
        generation++; builtAt = 0L; trail.clear(); current = Dest.Home
        content.removeAllViews()
        jellyfin.invalidateLibrary()
    }

    fun hide() {
        closePhoneTyping()
        if (voiceActive) voice?.cancel(); closeStreamLoading(); closeDetail(restoreFocus = false); overlay.visibility = View.GONE; ui.removeCallbacks(heroTick)
        underlayers(on = true)
        ui.removeCallbacks(flushFacts); flushFacts.run()
        // Nothing of the rail's motion survives the panel going away: it comes back closed, with no push, dim or blur.
        if (topBuilt) resetRail()
        (page as? CinemaContentScrollView)?.stopGlide()
        // Home is kept for a quick reopen, so its rows keep loading; any other page is dropped.
        if (current !== Dest.Home || trail.isNotEmpty()) { generation++; builtAt = 0L }
    }
    fun close() {
        PlayerActivity.onUpcomingEpisode = null
        jellyfin.stopLibraryPolling()
        navigationFocusListener?.let { if (top.viewTreeObserver.isAlive) top.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        cancelDwellStart(); aheadStreams.clear(); preStartPool.shutdownNow()
        ui.removeCallbacks(flushFacts); onFactsPool { titleFacts.flush() }; factsPool.shutdown()
        worker.shutdownNow(); shelfPool.shutdownNow(); slowPool.shutdownNow(); infoPool.shutdownNow(); images.close(); if (lbStarted) lb.close()
    }

    /**
     * Cinema is back in front. After playing something from the library, ask Jellyfin where the viewer got to (a
     * moment later, so the player's last report has landed) and redo the buttons that depend on it: the title page's
     * Continue / Restart, the featured title's, and a series' episode cards (by reopening its page).
     */
    fun onResume() {
        // The player may have added to the history: bring Recently watched up to date, in place.
        if (isVisible) refreshRecent()
        // Continue watching reads what is saved now: the player may have moved a position or finished an episode.
        if (isVisible && personalPage() != null) { refreshFavourites(); continueRow?.let { fillContinue(it, generation) } }
        // An episode picked on the player's paused screen: find a copy of it, as the series page would.
        PlayerActivity.takeEpisodeRequest()?.let { r -> if (isVisible) ui.post { streamPickedEpisode(r) } }
        // A Continue watching stream whose saved link had expired: find a fresh copy, as Stream now would.
        if (isVisible) PlayerActivity.takeStreamAgain()?.let { r -> ui.post { streamAgain(r) } }
        if (!playbackStarted || !isVisible || !jellyfin.isConnected) return
        playbackStarted = false
        val gen = generation
        ui.postDelayed({
            worker.execute {
                val items = jellyfin.library(force = true)
                ui.post { if (isVisible) { refreshWatchState(gen, items); continueRow?.let { fillContinue(it, gen) } } }
            }
        }, RETURN_REFRESH_DELAY_MS)
    }

    private fun refreshWatchState(gen: Int, items: List<JellyfinItem>) {
        val byId = items.associateBy { it.id }
        fun renew(e: Entry) { e.library?.let { old -> byId[old.id]?.let { e.library = it } } }
        heroEntries.forEach(::renew)
        detailEntry?.let { e -> renew(e); detailButtons?.invoke() }
        if (gen == generation && heroSet) showHero(heroIndex)
    }

    /**
     * Back closes a title's page; with the rail open it only closes the rail, returning to the card that had focus (never
     * also a page back). Otherwise it steps back through where the viewer has been, then to Home, then to the top of Home,
     * then lets the panel close. Back never opens the rail: only a fresh Left does.
     */
    fun back(): Boolean {
        if (PhoneInput.hideQr()) return true
        if (voiceActive) { voice?.cancel(); return true }
        if (streamLoading != null) { closeStreamLoading(); return true }
        if (detailOpen) { closeDetail(restoreFocus = true); return true }
        if (top.hasFocus()) { focusContent(); return true }
        if (trail.isNotEmpty()) { current = trail.removeLast(); styleTabs(); build(); return true }
        if (current != Dest.Home) { current = Dest.Home; styleTabs(); build(); return true }
        // Home, somewhere down the page: back to its featured title first, so one Back too many never leaves Cinema.
        val first = if (heroSet && ::heroButtons.isInitialized) heroButtons.getChildAt(0) else null
        if (first != null && first.isShown && !first.hasFocus()) { pageGlideTo(0); first.requestFocus(); return true }
        return false
    }

    /** Debug builds only (see MainActivity): opens a destination by name for UI-review screenshots. */
    fun debugGo(name: String) {
        // "letterboxd", "downloads", "library": open that rail page.
        when (name) { "letterboxd" -> { go(Dest.Letterboxd); return }; "downloads" -> { go(Dest.Downloads); return }; "library" -> { go(Dest.Library); return }; "watchlist" -> { go(Dest.Watchlist); return } }
        // "lbtest:/path/": fetch one Letterboxd page and log what came back (checks the browser fallback on a device).
        if (name.startsWith("lbtest:")) {
            val web = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdWeb(activity)
            slowPool.execute {
                val t = System.currentTimeMillis()
                val html = web.page(name.removePrefix("lbtest:"))
                android.util.Log.i("Letterboxd", "TEST ${name.removePrefix("lbtest:")}: ${html?.length ?: -1} chars, posters=${html?.let { Regex("data-item-slug=").findAll(it).count() } ?: 0}, ${System.currentTimeMillis() - t} ms")
                html?.let { h -> java.io.File(activity.cacheDir, "lbdump").apply { mkdirs() }.resolve(name.removePrefix("lbtest:").trim('/').replace(Regex("[^a-zA-Z0-9]+"), "_") + ".html").writeText(h) }
            }
            return
        }
        // "sheet": a download's sheet as it appears, to see its look.
        if (name == "sheet") {
            infoSheet.show("DOWNLOAD", "The Substance", "2.1 GB of 6.4 GB · 3 seeds", listOf("Downloading at 4.2 MB/s, about 17 minutes left."), null, listOf(
                SheetAction("Download next", "Move to the front of the queue", primary = true) { },
                SheetAction("Download now", "Bypass the queue; other downloads continue") { },
                SheetAction("Cancel download", "Stop it and remove its partial files") { }))
            return
        }
        // "sample:<imdb>:<season>:<episode>": a public sample video in the player as that episode, to see its paused screen.
        if (name.startsWith("sample:")) {
            val p = name.removePrefix("sample:").split(":")
            val episode = p.getOrNull(1)?.toIntOrNull()?.let { s -> p.getOrNull(2)?.toIntOrNull()?.let { s to it } }
            val candidate = StreamCandidate("sample", "Sample", "Sample", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_30MB.mp4", null, StreamMetadata())
            PlayerActivity.launch(activity, listOf(RankedStream(candidate, 100, 0L, false, Verdict.RECOMMENDED, null, emptyList())),
                "Sample · S${episode?.first} E${episode?.second}", p.getOrNull(0), meta = PlayerMeta("Sample episode"), episode = episode)
            return
        }
        // "stream:<imdb>:<name>[:<season>:<episode>]": press Stream now on that title, for timing startup on a device.
        if (name.startsWith("stream:")) {
            val p = name.removePrefix("stream:").split(":")
            val imdb = p.getOrNull(0) ?: return
            val episode = p.getOrNull(2)?.toIntOrNull()?.let { s -> p.getOrNull(3)?.toIntOrNull()?.let { s to it } }
            val t = Title(imdb, if (episode == null) Kind.MOVIE else Kind.SHOW, p.getOrNull(1) ?: imdb, null, null, null, emptyList(),
                null, null, null, null, imdb, null)
            if (episode == null) streamWithoutEpisode(entry(t), page, null) else streamNow(entry(t), page, episode)
            return
        }
        // "loading": the stream loading screen as it first appears, without asking the server for anything.
        if (name == "loading") {
            go(Dest.Films)
            worker.execute {
                val e = catalog.shelf(Catalog.filmsPage().first()).titles.firstOrNull()?.let { entry(it) } ?: return@execute
                activity.runOnUiThread {
                    val screen = StreamLoadingView(activity, e.title.name)
                    (overlay as ViewGroup).addView(screen, FrameLayout.LayoutParams(-1, -1)); streamLoading = screen
                    backdropFor(e)?.let { u -> images.loadBackdrop(u, maxWidth = HERO_BACKDROP_PX) { b -> screen.backdrop(b) } }
                }
            }
            return
        }
        // "detail-series": the first series on the Series page, for checking its seasons and episodes.
        if (name == "detail-series") {
            go(Dest.Series)
            worker.execute {
                val e = catalog.shelf(Catalog.seriesPage().first()).titles.firstOrNull()?.let { entry(it) } ?: return@execute
                activity.runOnUiThread { openDetail(e, listOf(e.title)) }
            }
            return
        }
        // "detail-library" / "detail-film": a title page for a library film, or a film not in the library;
        // "detail-film-3" skips to the fourth such film (a stream test that should not reuse a warm torrent).
        if (name.startsWith("detail-")) {
            go(Dest.Films)
            worker.execute {
                if (jellyfin.isConnected) jellyfin.library()   // so "not in the library" is really known
                val skip = name.removePrefix("detail-film-").toIntOrNull() ?: 0
                // "detail-library-series": the newest series in the library (its episode cards).
                val related: List<Entry> = if (name.startsWith("detail-library")) libraryEntries(jellyfin.library().filter { it.isSeries == name.startsWith("detail-library-series") })
                    .drop(name.removePrefix("detail-library-series-").toIntOrNull() ?: 0)
                    else catalog.shelf(Catalog.filmsPage().first()).titles.filter { it.kind == Kind.MOVIE }.map { entry(it) }.filter { it.library == null }.drop(skip)
                // What the player would get for the first few library films (chapters, trickplay), without playing anything.
                if (name == "detail-library") related.take(5).mapNotNull { it.library }.forEach { lib ->
                    val full = jellyfin.item(lib.id)
                    android.util.Log.i("CinemaPlayer", "${lib.name}: ${full?.chapters?.size ?: "fetch failed"} chapters (${full?.chapters?.count { it.imageTag != null } ?: 0} with images), trickplay=${full?.trickplay?.width ?: "none"}; raw ${jellyfin.itemFieldReport(lib.id)}")
                }
                activity.runOnUiThread { related.firstOrNull()?.let { openDetail(it, related.map { r -> r.title }) } }
            }
            return
        }
        val dest = when (name) {
            "search" -> Dest.Search; "films" -> Dest.Films; "series" -> Dest.Series; "docs" -> Dest.Docs
            "library" -> Dest.Library; "downloads" -> Dest.Downloads
            "letterboxd" -> Dest.Letterboxd; "lb-search" -> Dest.LbSearch; "lb-follow" -> Dest.LbFollow
            "services" -> Dest.ServiceManage; "service-search" -> Dest.ServiceSearch
            "music" -> Dest.Music
            else -> when {
                name.startsWith("music-album:") -> Dest.MusicAlbum(name.removePrefix("music-album:"))
                name.startsWith("lb-person:") -> Dest.LbPerson(name.removePrefix("lb-person:"))
                name.startsWith("lb-list:") -> Dest.LbListPage(name.removePrefix("lb-list:"))
                else -> Dest.Home
            }
        }
        overlay.findFocus()?.clearFocus()
        go(dest)
        // With nothing focused, the page puts focus on its featured title's first button once it arrives.
        if (dest === Dest.Downloads || dest === Dest.Search) ui.postDelayed({ if (overlay.findFocus() == null || top.hasFocus()) focusContent() }, 2500)
    }

    private fun go(dest: Dest, remember: Boolean = false) {
        // Leaving a page ends its phone typing: the search that asked for it is no longer on screen.
        closePhoneTyping()
        if (remember) trail.addLast(current) else trail.clear()
        current = dest; styleTabs(); build()
    }

    // --- Left navigation ---------------------------------------------------------------------------

    private lateinit var wordmark: TextView

    private fun buildTop() {
        // A floating glass pill (`.rail`): 92 mockup px wide, inset 32 from the screen edges, over the page.
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.TOP
        top.setPadding(0, Cinema.px(activity, 31), 0, Cinema.px(activity, 26))
        top.background = null
        top.elevation = 0f
        // The rail is laid out once at its open width, labels and all; opening and closing only reveal it (see applyRail).
        (top.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.width = Cinema.px(activity, RAIL_WIDTH_OPEN); lp.height = -1
            lp.setMargins(Cinema.px(activity, 32), Cinema.px(activity, 32), 0, Cinema.px(activity, 32))
            top.layoutParams = lp
        }
        val scene = buildScene()
        // Rows fade into the ground at the right edge (`.fade-r`), once the featured title has scrolled away.
        edgeFade = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Cinema.alpha(Cinema.BG, 0f), Cinema.alpha(Cinema.BG, 0.75f), Cinema.BG))
            isFocusable = false; alpha = 0f
        }
        scene.addView(edgeFade, scene.indexOfChild(page) + 1, FrameLayout.LayoutParams(Cinema.px(activity, 220), -1, Gravity.END))
        // Frosted glass behind the rail: a blur of the page and ground beneath, in a sibling so the rail's stripe bar
        // can still draw past the pill's edge. Laid out open, like the rail, and revealed to the rail's width.
        frost = FrostView(activity, listOfNotNull(ambientView, scene), 46f).also { f ->
            body.addView(f, body.indexOfChild(top), FrameLayout.LayoutParams(Cinema.px(activity, RAIL_WIDTH_OPEN), -1).apply {
                setMargins(Cinema.px(activity, 32), Cinema.px(activity, 32), 0, Cinema.px(activity, 32))
            })
        }
        // The active item's stripe bar sits just outside the pill's left edge.
        body.clipChildren = false; body.clipToPadding = false
        (page.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.marginStart = 0; page.layoutParams = it }
        page.setOnScrollChangeListener { _, _, _, _, _ -> fillNear(); refreshGlass(); updateEdgeFade() }
        (page as? CinemaContentScrollView)?.navigationTarget = { activeNavigationItem() }
        val brand = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(activity, 18), 0, 0, 0); contentDescription = "Cinema"
        }
        brand.addView(ImageView(activity).apply { setImageResource(R.drawable.cinema_mark) }, LinearLayout.LayoutParams(Cinema.px(activity, 56), Cinema.px(activity, 56)))
        wordmark = Cinema.text(activity, "Cinema", 36, Cinema.CREAM, serif = true, weight = 500, tracking = -0.02f).apply { visibility = View.GONE; maxLines = 1; forceHasOverlappingRendering(false) }
        brand.addView(wordmark, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 18) })
        top.addView(brand, LinearLayout.LayoutParams(-1, Cinema.px(activity, 56)).apply { bottomMargin = Cinema.px(activity, 26) })

        val destinations = listOf(
            Triple("Search", R.drawable.ic_cin_search, Dest.Search),
            Triple("Home", R.drawable.ic_cin_home, Dest.Home),
            Triple("Films", R.drawable.ic_cin_film, Dest.Films),
            Triple("Series", R.drawable.ic_cin_tv, Dest.Series),
            Triple("Documentaries", R.drawable.ic_cin_doc, Dest.Docs),
            Triple("Letterboxd", R.drawable.ic_cin_lb, Dest.Letterboxd),
            Triple("Music", R.drawable.ic_cin_music, Dest.Music),
            Triple("My library", R.drawable.ic_cin_lib, Dest.Library),
            Triple("Downloads", R.drawable.ic_cin_dl, Dest.Downloads)
        )
        destinations.forEach { (label, icon, dest) ->
            top.addView(navigationItem(label, icon, dest), LinearLayout.LayoutParams(-1, Cinema.px(activity, 64)).apply { topMargin = Cinema.px(activity, 12) })
        }
        top.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        storageView = Cinema.text(activity, "", 18, Cinema.DIM).apply {
            maxLines = 2; setLineSpacing(0f, 1.1f); visibility = View.GONE
            setPadding(Cinema.px(activity, 30), Cinema.px(activity, 8), Cinema.px(activity, 20), 0)
        }
        top.addView(storageView, LinearLayout.LayoutParams(-1, -2))
        top.addView(navigationItem("Settings", R.drawable.ic_gear, Dest.Settings), LinearLayout.LayoutParams(-1, Cinema.px(activity, 64)).apply { topMargin = Cinema.px(activity, 12) })
        navigationFocusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            if (focused != null && isWithin(focused, page)) { lastContentFocus = focused; lastContentAt = pageRect(focused) }
            setNavigationExpanded(isWithin(focused, top))
            styleTabs()
            refreshGlass(450)   // a card scaling or a row gliding beneath the rail
        }.also { top.viewTreeObserver.addOnGlobalFocusChangeListener(it) }
        // Every label is shown and measured now, at the open size; the rail's clip and their alpha hide them while closed.
        navigationLabels.values.forEach { (_, name) -> name.visibility = View.VISIBLE }
        wordmark.visibility = View.VISIBLE
        // The rail's clip follows its height once it is laid out.
        top.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) applyRail(railP) }
        applyRail(0f)
        styleTabs()
    }

    /** The integration a rail item belongs to; null for the items that are always there. */
    private fun integrationOf(dest: Dest): Integration? = when (dest) {
        Dest.Letterboxd -> Integration.LETTERBOXD
        Dest.Music -> Integration.MUSIC
        else -> null
    }

    /** Shows the rail items of integrations that are on (Music only once a server is set up) and hides the rest. */
    private fun syncIntegrations() {
        musicClient = com.danielportelabyrne.cinema.ui.music.Music.use(music().takeIf { integrationOn(Integration.MUSIC) })
        tabs.forEach { (item, dest) ->
            val integration = integrationOf(dest) ?: return@forEach
            val shown = integrationOn(integration) && (integration != Integration.MUSIC || musicClient != null)
            item.visibility = if (shown) View.VISIBLE else View.GONE
        }
    }

    /** The rail items on screen, top to bottom: Up and Down step through these. */
    private fun shownTabs(): List<LinearLayout> = tabs.keys.filter { it.visibility == View.VISIBLE }

    private fun navigationItem(label: String, icon: Int, dest: Dest): LinearLayout {
        val row = RailItem(activity).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; contentDescription = label
            setPadding(Cinema.px(activity, 14), 0, Cinema.px(activity, 14), 0)
        }
        // The icon sits in a 64px circle, which lights glass when this is the page you are on.
        val circle = FrameLayout(activity)
        val image = Cinema.icon(activity, icon, Cinema.RAIL_ICON)
        circle.addView(image, FrameLayout.LayoutParams(Cinema.px(activity, 36), Cinema.px(activity, 36), Gravity.CENTER))
        val name = Cinema.text(activity, label, 24, Cinema.RAIL_ICON, weight = 500).apply {
            forceHasOverlappingRendering(false)   // fading it needs no offscreen copy
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.circle = circle
        row.addView(circle, LinearLayout.LayoutParams(Cinema.px(activity, 64), Cinema.px(activity, 64)))
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 14) })
        tabs[row] = dest
        navigationLabels[row] = image to name
        row.setOnFocusChangeListener { _, _ -> styleTabs() }
        row.setOnClickListener {
            when {
                dest === Dest.Settings -> onOpenSettings()
                current !== dest -> { go(dest); enterChosenPage() }
                else -> focusContent()   // the page already shown: just back to it
            }
        }
        row.setOnKeyListener { _, code, event ->
            when {
                code == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> true
                code == android.view.KeyEvent.KEYCODE_DPAD_UP && row === shownTabs().first() -> true
                code == android.view.KeyEvent.KEYCODE_DPAD_DOWN && row === shownTabs().last() -> true
                code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) focusContent()
                    true
                }
                else -> false
            }
        }
        return row
    }

    /**
     * D-pad moves the Cinema page handles itself, ahead of ordinary focus search (MainActivity asks first). The
     * loading screen swallows them; in the rail, Up and Down step through the items (stopping at the ends), Right
     * returns to where you were on the page, and Left goes nowhere. Returns false to let the views decide.
     */
    fun navigate(focused: View, direction: Int): Boolean {
        if (streamLoading != null || voiceActive) return true
        if (!isWithin(focused, top)) return false
        val items = shownTabs()
        val at = items.indexOfFirst { isWithin(focused, it) }
        when (direction) {
            View.FOCUS_RIGHT -> focusContent()
            View.FOCUS_UP -> items.getOrNull(at - 1)?.requestFocus()
            View.FOCUS_DOWN -> items.getOrNull(at + 1)?.requestFocus()
        }
        return true
    }

    /**
     * Left on the page, before focus moves (see NavGate): along a row it is an ordinary move; at the page's left edge only
     * a fresh press enters the rail, and a row still scrolled away from its start glides back to it first. True when the
     * key is swallowed here. Title pages and anything over the page are left alone.
     */
    fun leftKey(focused: View, fresh: Boolean): Boolean {
        if (detailOpen || streamLoading != null || voiceActive || !isWithin(focused, page)) return false
        val next = focused.focusSearch(View.FOCUS_LEFT)
        val toRail = next != null && isWithin(next, top)
        var row: android.widget.HorizontalScrollView? = null
        var p = focused.parent
        while (p != null && p !== page) { if (p is android.widget.HorizontalScrollView) { row = p; break }; p = p.parent }
        return when (com.danielportelabyrne.cinema.ui.NavGate.left(toRail, row?.scrollX, fresh)) {
            com.danielportelabyrne.cinema.ui.LeftAction.MOVE, com.danielportelabyrne.cinema.ui.LeftAction.ENTER_RAIL -> false
            com.danielportelabyrne.cinema.ui.LeftAction.HOLD -> true
            com.danielportelabyrne.cinema.ui.LeftAction.SETTLE_ROW -> { row?.smoothScrollTo(0, 0); true }
        }
    }

    /**
     * Focus is on no card at all (a page was just rebuilt under the card that had it, or its rows are still loading): Down,
     * Up and Right pick up the page's content instead of letting the search run over the whole screen, whose nearest
     * candidate is the rail. Left still goes to the rail. False when this does not apply.
     */
    fun recoverFocus(direction: Int): Boolean {
        if (!isVisible || streamLoading != null) return false
        // A title page is open: its first control, never the page hidden beneath it.
        if (detailOpen) {
            ArrayList<View>().also { detail.addFocusables(it, View.FOCUS_FORWARD) }.firstOrNull { it.isShown && it.isEnabled }?.requestFocus()
            return true
        }
        if (direction == View.FOCUS_LEFT) return false
        focusContent()
        return true
    }

    /**
     * Back to the page from the rail: the card that had focus, else (it went, say a shelf changed meanwhile) the one now
     * nearest where it was, else the page's first.
     */
    private fun focusContent() {
        val previous = lastContentFocus?.takeIf { it.isAttachedToWindow && it.isShown && it.isEnabled && it.isFocusable && isWithin(it, page) }
        if (previous?.requestFocus() == true) return
        val candidates = ArrayList<View>()
        content.addFocusables(candidates, View.FOCUS_FORWARD)
        val live = candidates.filter { it.isShown && it.isEnabled }
        val was = lastContentAt
        val nearest = if (was == null) null else live.minByOrNull { v ->
            val r = pageRect(v); val dx = (r.centerX() - was.centerX()).toLong(); val dy = (r.centerY() - was.centerY()).toLong()
            dx * dx + dy * dy
        }
        (nearest ?: live.firstOrNull())?.requestFocus()
    }

    /** Where the page's content focus last was, in the page's content coordinates: [focusContent]'s guide when that view is gone. */
    private var lastContentAt: android.graphics.Rect? = null

    private fun pageRect(v: View) = android.graphics.Rect(0, 0, v.width, v.height).also { if (isWithin(v, page)) page.offsetDescendantRectToMyCoords(v, it) }

    /**
     * A rail item was chosen: its page is built, and focus goes to that page's first card or button as soon as it has one,
     * which closes the rail. Never while a title page or another layer has taken focus, or once the viewer has moved on.
     */
    private fun enterChosenPage() {
        val gen = generation
        var tries = 0
        val attempt = object : Runnable {
            override fun run() {
                if (gen != generation || !isVisible || detailOpen || streamLoading != null || !top.hasFocus()) return
                val first = ArrayList<View>().also { content.addFocusables(it, View.FOCUS_FORWARD) }.firstOrNull { it.isShown && it.isEnabled }
                if (first != null) { lastContentFocus = null; lastContentAt = null; first.requestFocus(); return }
                if (++tries < ENTER_PAGE_TRIES) ui.postDelayed(this, ENTER_PAGE_RETRY_MS)
            }
        }
        ui.post(attempt)
    }

    private fun activeNavigationItem(): View? = tabs.entries.firstOrNull { it.value === railOf(current) }?.key

    private fun isWithin(view: View?, parent: View): Boolean {
        var node = view
        while (node != null) {
            if (node === parent) return true
            node = node.parent as? View
        }
        return false
    }
    // --- The rail opening: the page pushed aside -----------------------------------------------------

    /**
     * Everything that moves aside as one when the rail opens: the ground, the page (featured title, rows) and their
     * decorations, with a dim and the rail's edge shadow laid over them. The rail and its frost stay outside it, sharp; so
     * do a title's page, sheets, Settings and the player.
     */
    private lateinit var scene: FrameLayout
    private var railEdge: View? = null
    /** The rail's one progress, 0 closed to 1 open; every property is read from it (see RailMotion). */
    private var railP = 0f
    private var railAnimator: android.animation.ValueAnimator? = null
    private var railTarget = 0f
    private val railTint = android.animation.ArgbEvaluator()
    /** True while the rail is moving: the glass is not re-photographed each frame (it is taken again when the move ends). */
    private val railMoving get() = railAnimator?.isRunning == true
    private fun refreshGlass(forMs: Long = 0L) { if (!railMoving) frost?.refresh(forMs) }

    private fun buildScene(): FrameLayout {
        val s = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        body.addView(s, 0, FrameLayout.LayoutParams(-1, -1))
        ambientView?.let { a ->
            (a.parent as? ViewGroup)?.removeView(a)
            // The ground stays still, full screen, behind everything: only the page on top moves.
            body.addView(a, 0, FrameLayout.LayoutParams(-1, -1))
        }
        (page.parent as? ViewGroup)?.removeView(page)
        s.addView(page, FrameLayout.LayoutParams(-1, -1))
        // Where the closed rail ends, a soft shadow falls on the page: black at 10 % to nothing, 24 mockup px wide.
        railEdge = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Cinema.alpha(android.graphics.Color.BLACK, 0.10f), Cinema.alpha(android.graphics.Color.BLACK, 0f)))
            isFocusable = false; alpha = 0f; visibility = View.INVISIBLE
        }.also { body.addView(it, body.indexOfChild(s) + 2, FrameLayout.LayoutParams(Cinema.px(activity, RAIL_EDGE), -1).apply { marginStart = Cinema.px(activity, 32 + RAIL_WIDTH); topMargin = Cinema.px(activity, 52); bottomMargin = Cinema.px(activity, 52) }) }
        scene = s
        return s
    }

    /** Opens or closes the rail. Only focus entering or leaving the rail calls this (see the focus listener in [buildTop]). */
    private fun setNavigationExpanded(expanded: Boolean) {
        if (navigationExpanded == expanded) return
        navigationExpanded = expanded
        animateRail(if (expanded) 1f else 0f)
    }

    /**
     * Moves the rail's progress to [target] on Motion.GLIDE, from wherever it is drawn now: an interrupted move turns
     * round from there, taking only the time its remaining distance needs. With animations off it lands at once.
     */
    private fun animateRail(target: Float) {
        railTarget = target
        railAnimator?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }; railAnimator = null
        val from = railP
        val ms = RailMotion.duration(from, target)
        if (!com.danielportelabyrne.cinema.design.Motion.animationsOn(top) || ms <= 0L) { applyRail(target); return }
        // One cached picture of the page slides while the rail moves, rather than every row being drawn again each frame.
        // No layer: on a slow TV caching the page costs more than drawing it.
        // The glass is photographed with the page already where it will be, so it never reflects the old layout while it grows.
        frost?.let { f -> f.shiftSource = if (::scene.isInitialized) scene else null; f.forcedShift = RailMotion.push(target, Cinema.pxf(activity, RAIL_WIDTH), Cinema.pxf(activity, RAIL_WIDTH_OPEN)); f.refreshNow() }
        railAnimator = android.animation.ValueAnimator.ofFloat(from, target).apply {
            duration = ms
            interpolator = com.danielportelabyrne.cinema.design.Motion.GLIDE
            addUpdateListener { applyRail(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (railAnimator === animation) { railAnimator = null; applyRail(target); if (::scene.isInitialized) scene.setLayerType(View.LAYER_TYPE_NONE, null); frost?.forcedShift = null; frost?.refreshNow() }   // lands exactly on its end, then the glass is taken again
                }
            })
            start()
        }
    }

    /** Lands the rail closed at once, with nothing left moving: the panel hiding, or the page replaced while it shut. */
    private fun resetRail() {
        railAnimator?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }; railAnimator = null
        navigationExpanded = false; railTarget = 0f
        frost?.forcedShift = null
        applyRail(0f)
        if (::scene.isInitialized) scene.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /** Sets everything the rail's progress decides, from [p] alone. At 0 nothing is left on the page: no push, dim, shadow or blur. */
    private fun applyRail(p: Float) {
        val q = RailMotion.settle(p)
        railP = q
        val closed = Cinema.pxf(activity, RAIL_WIDTH); val open = Cinema.pxf(activity, RAIL_WIDTH_OPEN)
        val push = RailMotion.push(q, closed, open)
        val shown = q > 0f
        // Only a move: no blur, no scale. The ground behind stays still.
        if (::scene.isInitialized) scene.translationX = push
        railEdge?.let { e -> e.alpha = q; e.translationX = push; e.visibility = if (shown) View.VISIBLE else View.INVISIBLE }
        // The rail and its glass: laid out open, shown to the width the progress gives. The stripe bar draws a little left of the pill.
        val width = Math.round(closed + push)
        val bleed = Cinema.px(activity, 40)
        top.clipBounds = android.graphics.Rect(-bleed, -bleed, width, (if (top.height > 0) top.height else 100_000) + bleed)
        frost?.let { f ->
            f.visibleWidth = width
            // Open, the pill lies over the page's text, so the frost is tinted a little deeper to keep the labels clear.
            f.tint = railTint.evaluate(q, Cinema.RAIL_FILL, 0xB8120D0A.toInt()) as Int
        }
        navigationLabels.values.forEach { (_, name) -> name.alpha = q }
        if (::wordmark.isInitialized) wordmark.alpha = q
        if (::storageView.isInitialized) {
            storageView.visibility = if (storageView.text.isNotBlank()) View.VISIBLE else View.GONE
            storageView.alpha = q
        }
    }

    /**
     * The rail's three looks: the focused item is a full-width cream band with dark icon and label; the page you are on has a
     * glass circle, a saffron icon and the stripe bar at the pill's edge; the rest are quiet.
     */
    private fun styleTabs() = tabs.forEach { (view, dest) ->
        val selected = dest === railOf(current)
        view.isSelected = selected
        val focused = view.hasFocus()
        val item = view as RailItem
        item.marked = selected
        // Focus fills the rail's whole width as a cream band, edge to edge, rather than a pill inside it.
        view.background = if (focused) android.graphics.drawable.ColorDrawable(Cinema.CREAM) else null
        item.circle?.background = if (selected && !focused) android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Cinema.GLASS_STRONG)
        } else null
        val colour = when { focused -> Cinema.INK; selected -> Cinema.SAFFRON; else -> Cinema.RAIL_ICON }
        navigationLabels[view]?.let { (image, name) ->
            // Letterboxd keeps its own three colours; quieter at rest, like the other icons.
            if (dest === Dest.Letterboxd) { image.clearColorFilter(); image.alpha = if (focused || selected) 1f else 0.62f }
            else image.setColorFilter(colour)
            name.setTextColor(colour)
            Cinema.weight(name, 24, serif = false, weight = if (focused) 600 else 500)
        }
    }

    /** A rail item that can draw the stripe-set bar just outside the rail's left edge (`.nv.on::before`). */
    private class RailItem(context: Context) : LinearLayout(context) {
        var circle: FrameLayout? = null
        var marked = false
            set(value) { if (field != value) { field = value; invalidate() } }
        private val bar = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(context, 4))
        init { setWillNotDraw(false); clipChildren = false }
        override fun draw(canvas: Canvas) {
            if (marked) {
                val left = paddingLeft - Cinema.px(context, 17)
                val top = (height - Cinema.px(context, 32)) / 2
                bar.setBounds(left, top, left + Cinema.px(context, 8), top + Cinema.px(context, 32))
                bar.draw(canvas)
            }
            super.draw(canvas)
        }
    }

    // --- Building a page ---------------------------------------------------------------------------

    /** The backdrop to show for [e]: the catalogue's, else the library's own, at hero size. Null when it has none. */
    private fun backdropFor(e: Entry): String? =
        (e.title.backdropUrl ?: e.library?.takeIf { it.hasBackdrop }?.let { jellyfin.backdropUrl(it.id) })?.let { heroBackdropUrl(it) }

    /** Loads [e]'s backdrop into memory now, so its page or turn as the featured title shows it at once. */
    private fun prefetchBackdrop(e: Entry) { backdropFor(e)?.let { u -> images.loadBackdrop(u, maxWidth = HERO_BACKDROP_PX) { } } }

    private var restPrefetch: Runnable? = null

    /**
     * Resting on a poster for a moment fetches its backdrop, so opening it shows the picture at once. Skimming along a
     * row does not: each move cancels the wait.
     */
    private fun prefetchOnRest(card: View, e: Entry) {
        restPrefetch?.let { ui.removeCallbacks(it) }
        restPrefetch = Runnable { if (card.hasFocus() || card.isFocused) { prefetchBackdrop(e); prefetchPage(e) } }.also { ui.postDelayed(it, REST_PREFETCH_MS) }
    }

    private fun prefetchPage(e: Entry) {
        val tmdb = e.title.tmdbId?.toLongOrNull() ?: return
        if (!seerr.isSignedIn) return
        slowPool.execute {
            seerr.warm(e.title.kind, tmdb)
            if (e.title.kind == Kind.SHOW) sonarr.warm(tmdb)
        }
    }

    /**
     * JustWatch's largest backdrop (1920 x ~900). The featured title, title pages and the stream loading screen fill
     * most of a 1080p screen at full resolution, so any smaller copy is visibly stretched.
     */
    private fun heroBackdropUrl(url: String) = url.replace("/s1440/", "/s1920/")

    private fun build() {
        val buildStart = Perf.now()
        images.cancel(pageTag); pageTag = Any()
        val gen = ++generation
        searchPending?.let { ui.removeCallbacks(it) }
        ui.removeCallbacks(heroTick)
        content.removeAllViews(); pageJumpTo(0); heroSet = false; pending.clear(); lastContentFocus = null; lastContentAt = null
        // A page replaced while the rail is not in use starts unmoved; one chosen from the open rail stays pushed until focus leaves it.
        if (topBuilt && !top.hasFocus()) resetRail()
        // The rail floats over the page, so nothing is reserved for a top bar. A page that opens on a featured title
        // starts at the very top, so its picture runs up to the screen edge; the others start a little way down.
        val opensOnHero = current === Dest.Home || current === Dest.Films || current === Dest.Series || current === Dest.Docs || current === Dest.Library ||
            current is Dest.OnService || current is Dest.InGenre
        // Search lays itself out in screen coordinates, straight from the mockup.
        content.setPadding(0, if (opensOnHero || current === Dest.Search) 0 else Cinema.px(activity, 52), 0, dp(48))
        if (current !== Dest.Search) { if (voiceActive) voice?.cancel(); searchUi = null }
        frost?.post { frost?.refresh(500); updateEdgeFade() }
        builtAt = Perf.now(); builtConnected = jellyfin.isConnected; awaitingLibrary.clear()
        starred.clear(); favShelfCards.clear(); favRow = null; recentViews.clear(); recentRow = null; refreshFavSnapshot()
        continueViews.clear(); continueRow = null; continueBusy = false; continueAgain = false; continueEpoch++
        ambientView?.palette = paletteFor(current)
        // Rows never wait for the library: they draw from the catalogue at once, and this one fetch badges them when it lands.
        jellyfin.takeIf { it.isConnected }?.let { j -> worker.execute { j.library(); ui.post { rebadge(gen); refreshPersonal(gen) } } }
        // Every page with personal shelves puts them where Home does: right under the spotlight, ahead of the other rows.
        val personal = mapOf(1 to { personalRows(gen) })
        when (val d = current) {
            // Home threads its own rows between the catalogue's: the spotlight right under the featured title, then what
            // is part-watched, then Top 10, with picks, services, the library and genres spread through the rest.
            Dest.Home -> { buildHero(); shelves(Catalog.homePage(), gen, mapOf(
                1 to { personalRows(gen) }, 2 to { picksRow(gen) }, 3 to { serviceTiles(gen) },
                5 to { libraryRow(gen, "My library", 18) }, 7 to { genreTiles() })) }
            Dest.Films -> { buildHero(); shelves(Catalog.filmsPage(), gen, personal) }
            Dest.Series -> { buildHero(); shelves(Catalog.seriesPage(), gen, personal) }
            Dest.Docs -> { buildHero(); shelves(Catalog.documentariesPage(), gen, personal) }
            Dest.Library -> buildLibrary(gen)
            Dest.Watchlist -> buildWatchlist(gen)
            Dest.Downloads -> buildDownloads(gen)
            Dest.Search -> buildSearch()
            // A service with few titles listed fills its page with its country's own films and series, so it is not all "streaming on" it.
            is Dest.OnService -> { buildHero(); heroTag(if (d.service.sparse) "${d.service.homeName} films & series" else "Streaming on", d.service.label, Catalog.logoUrl(d.service)); shelves(Catalog.servicePage(d.service), gen, personal) }
            is Dest.InGenre -> { buildHero(); heroTag("Genre", d.genre.label); shelves(Catalog.genrePage(d.genre), gen, personal) }
            Dest.Letterboxd -> buildLetterboxd(gen)
            is Dest.LbPerson -> buildLbPerson(d.user, gen)
            is Dest.LbListPage -> buildLbList(d.path, gen)
            Dest.LbSearch -> buildLbSearch(gen)
            Dest.ServiceManage -> buildServiceManage(gen)
            Dest.ServiceSearch -> buildServiceSearch(gen)
            Dest.LbFollow -> buildLbFollow(gen)
            Dest.Music -> buildMusic(gen)
            is Dest.MusicAlbum -> buildMusicAlbum(d.id, gen)
            is Dest.MusicArtist -> buildMusicArtist(d.id, d.name, gen)
            Dest.Settings -> Unit
        }
        Perf.took("movies.build (main thread)", Perf.now() - buildStart)
    }

    /** Each section of the app has the same ground in its own colours (see AmbientBackground.Palette). */
    private fun paletteFor(d: Dest): AmbientBackground.Palette = when (d) {
        Dest.Home, Dest.ServiceManage, Dest.ServiceSearch -> AmbientBackground.HOME
        Dest.Films -> AmbientBackground.FILMS
        Dest.Series -> AmbientBackground.SERIES
        Dest.Docs -> AmbientBackground.DOCS
        Dest.Library, Dest.Watchlist -> AmbientBackground.LIBRARY
        Dest.Downloads -> AmbientBackground.DOWNLOADS
        Dest.Search -> AmbientBackground.SEARCH
        Dest.Settings -> AmbientBackground.SETTINGS
        Dest.Letterboxd, Dest.LbSearch, Dest.LbFollow, is Dest.LbPerson, is Dest.LbListPage -> AmbientBackground.LETTERBOXD
        Dest.Music, is Dest.MusicAlbum, is Dest.MusicArtist -> AmbientBackground.MUSIC
        // A service's page takes the service's own colour; a genre's, the colour of its tile.
        is Dest.OnService -> AmbientBackground.from(ServiceCard.coloursFor(d.service.code).first)
        is Dest.InGenre -> GenreArt.coloursFor(d.genre.code).let { AmbientBackground.from(it.base, it.light) }
    }

    /**
     * Names the service or genre a page is about, laid over the top corner of the hero. It used to be a title row above the
     * hero, which pushed the featured picture down the screen and left these pages starting lower than Films and Series.
     */
    private fun heroTag(kicker: String, name: String, logo: String? = null) {
        val tag = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        // A service page leads with the service's own app icon, rounded like a launcher tile.
        logo?.let { url ->
            val icon = android.widget.ImageView(activity).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; clipToOutline = true; contentDescription = name
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 18)) } }
                setBackgroundColor(Cinema.PLACEHOLDER)
            }
            tag.addView(icon, LinearLayout.LayoutParams(Cinema.px(activity, 76), Cinema.px(activity, 76)).apply { marginEnd = Cinema.px(activity, 22) })
            images.load(url, emptyMap(), maxWidth = Cinema.px(activity, 76), tag = pageTag) { b -> icon.setImageBitmap(b) }
        }
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        // Its own width: left to match the column, a kicker wider than a short name ("NOW") wrapped and lost its last word.
        words.addView(Cinema.text(activity, kicker, 17, Cinema.MUTED, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2))
        words.addView(Cinema.text(activity, name, 38, Cinema.CREAM, serif = true, weight = 500), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 2) })
        tag.addView(words)
        (heroFrame as FrameLayout).addView(tag, FrameLayout.LayoutParams(-2, -2).apply { marginStart = pageStart; topMargin = Cinema.px(activity, 20) })
    }

    private fun pageTitle(text: String) {
        content.addView(Type.text(activity, text, Type.Style.TITLE).apply { textSize = 30f; setPadding(pageStart, dp(4), 0, dp(6)) })
    }

    /** A row whose posters are fetched and drawn only when it nears the screen. */
    private class Lazy(val row: Row, val load: () -> Unit)
    private val pending = ArrayList<Lazy>()

    /** Starts every waiting row that is within a screen of the visible part of the page. */
    private fun fillNear() {
        if (pending.isEmpty()) return
        // A refreshed Letterboxd row may have replaced its old views before their deferred load ran.
        pending.removeAll { lazy ->
            var parent: android.view.ViewParent? = lazy.row.scroller.parent
            while (parent != null && parent !== content) parent = parent.parent
            parent == null
        }
        val limit = page.scrollY + page.height * 2
        val due = pending.filter {
            val target = it.row.heading ?: it.row.scroller
            val bounds = android.graphics.Rect(0, 0, target.width, target.height)
            content.offsetDescendantRectToMyCoords(target, bounds)
            bounds.top < limit
        }
        if (due.isEmpty()) return
        pending.removeAll(due.toSet())
        due.forEach { it.load() }
    }

    /**
     * Rows of posters, loaded three at a time and filled as each arrives; an empty one hides itself. The page's first
     * ordinary shelf leads as the spotlight (see ShelfLooks.plan). [between] adds the page's own rows: the one at key i
     * goes in just before the i-th shelf shown, or at the end when there are fewer shelves.
     */
    private fun shelves(specs: List<ShelfSpec>, gen: Int, between: Map<Int, () -> Unit> = emptyMap()) {
        // Every row gets its place on the page now, with room reserved so the page does not jump; its titles are fetched
        // and drawn only when it is about to be seen, which is what keeps opening the page quick.
        // Rows take turns between a spotlight, wide stills, posters, a grid and smaller stills, so neighbours never look alike.
        heroFromSpotlight = false
        val plan = com.danielportelabyrne.cinema.data.movies.ShelfLooks.plan(specs)
        plan.forEachIndexed { i, (spec, look) ->
            between[i]?.invoke()
            val row = row(spec.heading, null)
            // Room for the row before it loads, so the page does not jump.
            row.inner.minimumHeight = Cinema.px(activity, rowHeight(look))
            // The first rows are always near the top: fetch them now rather than after the page's first layout (a second
            // or more on a cold start), so they are ready the moment the main thread is free to draw them.
            if (i < EAGER_SHELVES) loadShelf(spec, row, gen, look) else pending += Lazy(row) { loadShelf(spec, row, gen, look) }
        }
        between.filterKeys { it >= plan.size }.toSortedMap().values.forEach { it() }
        page.post { fillNear() }
    }

    /** The featured titles came from the spotlight row, which gives way to the first other row that loads. */
    private var heroFromSpotlight = false

    /** Mockup pixels a row of each look needs, card and name included. */
    private fun rowHeight(look: com.danielportelabyrne.cinema.data.movies.ShelfLook): Int = when (look) {
        com.danielportelabyrne.cinema.data.movies.ShelfLook.RANKED -> 368
        com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS -> STILL_H + 8
        com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS -> SMALL_STILL_H + 8
        com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID -> 2 * SMALL_STILL_H + GRID_GAP + 8
        com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT -> SPOTLIGHT_H + 8
        com.danielportelabyrne.cinema.data.movies.ShelfLook.POSTERS -> 488
    }

    private fun loadShelf(spec: ShelfSpec, row: Row, gen: Int, planned: com.danielportelabyrne.cinema.data.movies.ShelfLook) {
        val queuedAt = Perf.now()
        Perf.mark("shelf '${spec.heading}' queued")
        shelfPool.execute {
            val startAt = Perf.now()
            val libraryAt = Perf.now()
            // A Top 10 row is exactly that: ten numbered posters, nothing after them.
            val fetched = catalog.shelf(spec).titles.take(if (spec.ranked) 10 else SHELF_LIMIT).map { entry(it) }
            // Each title's own genres and services, kept so the personal shelves can tell which pages it belongs on.
            // A broadcaster's shelf asked for abroad lists that country's services: they add to what is known here.
            learnFacts(fetched.map { TitleFacts.of(it.title, System.currentTimeMillis()) }, addServices = spec.country != null && spec.country != Catalog.country)
            val look =com.danielportelabyrne.cinema.data.movies.ShelfLooks.resolve(planned, fetched.map { it.title })
            val lead = if (look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT)
                com.danielportelabyrne.cinema.data.movies.ShelfLooks.spotlightIndex(fetched.map { it.title }) ?: 0 else 0
            val entries = if (lead > 0) listOf(fetched[lead]) + fetched.filterIndexed { i, _ -> i != lead } else fetched
            val catalogAt = Perf.now()
            activity.runOnUiThread {
                if (gen != generation) return@runOnUiThread
                val uiAt = Perf.now()
                Perf.took("shelf '${spec.heading}' queued", startAt - queuedAt); Perf.took("shelf '${spec.heading}' library", libraryAt - startAt)
                Perf.took("shelf '${spec.heading}' catalog", catalogAt - libraryAt)
                if (entries.isNotEmpty()) Perf.end("movies.open->firstRow", spec.heading)
                if (entries.isEmpty()) { row.hide(); return@runOnUiThread }
                val related = entries.map { it.title }
                row.setCount(entries.size)
                row.inner.minimumHeight = Cinema.px(activity, rowHeight(look))
                // The featured titles come from the first row to load, but never from the spotlight while another row can
                // give them: the same picture twice, one above the other, would waste the top of the page.
                val spotlit = look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT
                if (!heroSet || (heroFromSpotlight && !spotlit)) {
                    val featured = entries.withIndex().filter { it.value.title.backdropUrl != null && !(spotlit && it.index == 0) }.take(3)
                    if (featured.isNotEmpty()) {
                        setHero(featured.map { it.value }, related, featured.map { rankLabel(spec.heading, spec.ranked, it.index) })
                        heroFromSpotlight = spotlit
                    }
                }
                // A grid row holds its titles in columns of two, one above the other.
                val grid = look == com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID
                var column: LinearLayout? = null
                // A few cards a frame, not sixteen in one go (each is a small tree of views): the page keeps moving while a
                // row fills. Posters further along than the screen wait until the row scrolls near them.
                fun addFrom(from: Int) {
                    if (gen != generation) return
                    val to = minOf(from + CARDS_PER_FRAME, entries.size)
                    for (i in from until to) {
                        val e = entries[i]
                        val ranked = spec.ranked && i < 10
                        val defer = if (i >= EAGER_POSTERS) row else null
                        if (grid) {
                            val col = column?.takeIf { it.childCount < 2 } ?: LinearLayout(activity).apply {
                                orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
                            }.also { c -> row.inner.addView(c, cardParams(i / 2)); column = c }
                            col.addView(stillCard(e, related, SMALL_STILL_W, SMALL_STILL_H, defer, holder = col),
                                LinearLayout.LayoutParams(-2, -2).apply { if (col.childCount > 0) topMargin = Cinema.px(activity, GRID_GAP) })
                            continue
                        }
                        val view = when {
                            ranked -> rankCard(i + 1, e, related, defer)
                            look == com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS -> stillCard(e, related, STILL_W, STILL_H, defer)
                            look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS -> stillCard(e, related, SMALL_STILL_W, SMALL_STILL_H, defer)
                            look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT && i == 0 -> stillCard(e, related, SPOTLIGHT_W, SPOTLIGHT_H, null, spotlight = true)
                            else -> card(e, related, defer = defer)
                        }
                        row.inner.addView(view, cardParams(i, wide = ranked))
                    }
                    if (to < entries.size) android.view.Choreographer.getInstance().postFrameCallback { addFrom(to) }
                    else {
                        // A genre shelf ends with the way into that genre's own page.
                        val moreSize = when (look) {
                            com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS -> STILL_W to STILL_H
                            com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS -> SMALL_STILL_W to SMALL_STILL_H
                            com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID -> SMALL_STILL_W to 2 * SMALL_STILL_H + GRID_GAP
                            else -> 260 to 390
                        }
                        // Its still is one from further along the row, so it never repeats a picture still on screen.
                        val art = entries.drop(4).firstOrNull { it.title.backdropUrl != null }?.title?.backdropUrl
                        moreCard(spec, moreSize.first, moreSize.second, art)?.let { row.inner.addView(it, cardParams(entries.size)) }
                        Perf.took("shelf '${spec.heading}' ui add ${entries.size} (spread)", Perf.now() - uiAt); row.scroller.post { row.loadNear() }; page.post { fillNear() }
                    }
                }
                addFrom(0)
            }
        }
    }

    /**
     * Never waits for the network: it only looks at the library already loaded, so it is safe to call while drawing.
     * A title the library has not been checked for yet is badged later by [rebadge].
     */
    private fun entry(t: Title): Entry =
        Entry(t, if (jellyfin.isConnected) jellyfin.findLoaded(t.imdbId, t.tmdbId, t.kind == Kind.SHOW) else null, emptyMap())

    private fun libraryTitle(item: JellyfinItem): Title {
        val base = JellyfinApi.cleanBase(jellyfin.server)
        return Title(
            key = "jf:" + item.id, kind = if (item.isSeries) Kind.SHOW else Kind.MOVIE, name = item.name, year = item.year,
            overview = item.overview, runtimeMin = item.runtimeMin, genres = item.genres, age = item.officialRating, imdbScore = null,
            posterUrl = JellyfinApi.posterUrl(base, item.id), backdropUrl = if (item.hasBackdrop) jellyfin.backdropUrl(item.id) else null,
            imdbId = item.imdbId, tmdbId = item.tmdbId
        )
    }

    private fun recencyLabel(e: Entry): String {
        val item = e.library
        val watched = when (libraryOrder()) {
            LibraryOrder.RECENT -> item != null && (item.playedMs ?: 0L) > (item.addedMs ?: 0L)
            LibraryOrder.WATCHED -> item?.playedMs != null
            LibraryOrder.ADDED -> false
            LibraryOrder.NAME -> return "In your library"
        }
        return if (watched) "Recently watched" else "Recently added"
    }

    private fun libraryEntries(items: List<JellyfinItem>): List<Entry> = items.map { Entry(libraryTitle(it), it, jellyfin.playbackHeaders()) }

    /** A row of the owner's own titles, or the offer to connect Jellyfin. */
    // --- Watchlist ----------------------------------------------------------------------------------

    private val stremio = com.danielportelabyrne.cinema.data.stremio.StremioClient()
    /** Most torrents from the add-ons passed on to the stream service, best seeded first. */
    private val STREAM_CANDIDATES = 60
    private val ADDON_ANSWER_MS = 8 * 60_000L
    private val ADDON_PREFETCH_DELAY_MS = 700L

    /** What the add-ons offered lately for a title or episode, so Play does not wait for them again. */
    private val addonAnswerCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, java.util.concurrent.Future<com.danielportelabyrne.cinema.data.stremio.AddonAnswers>>>()
    private val addonPool = java.util.concurrent.Executors.newFixedThreadPool(2)
    private var addonPrefetch = Runnable {}

    /** The add-ons' answer for [imdb] (and [episode]): the one asked in the last few minutes, or a new request. */
    private fun addonAnswersFor(imdb: String, episode: Pair<Int, Int>?): java.util.concurrent.Future<com.danielportelabyrne.cinema.data.stremio.AddonAnswers> {
        val key = imdb + (episode?.let { ":${it.first}:${it.second}" } ?: "")
        val now = android.os.SystemClock.elapsedRealtime()
        addonAnswerCache[key]?.let { (at, f) ->
            val empty = f.isDone && runCatching { f.get().let { it.torrents.isEmpty() && it.links.isEmpty() } }.getOrDefault(true)
            if (now - at < ADDON_ANSWER_MS && !empty) return f
        }
        addonAnswerCache.entries.removeIf { now - it.value.first >= ADDON_ANSWER_MS }
        val f = addonPool.submit(java.util.concurrent.Callable { stremio.addonAnswers(stremioAddons(), imdb, episode) })
        addonAnswerCache[key] = now to f
        return f
    }

    /**
     * Whether a direct link really serves the film: it answers a one-byte request, and is not a short placeholder
     * (a debrid service's "still downloading" clip). Off the main thread; gives up after a few seconds.
     */
    private fun linkAnswers(url: String, headers: Map<String, String>): Boolean = try {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 4000; c.instanceFollowRedirects = true
        c.setRequestProperty("Range", "bytes=0-0")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: c.contentLengthLong.takeIf { c.responseCode == 200 && it > 0 }
            val ok = c.responseCode in 200..299 && (total == null || total >= 50L * 1024 * 1024)
            android.util.Log.i("StreamNow", "link check: HTTP ${c.responseCode}, ${total?.let { it / (1024 * 1024) }} MiB -> $ok")
            ok
        } finally { c.disconnect() }
    } catch (ex: Exception) { android.util.Log.w("StreamNow", "link check failed: ${ex.javaClass.simpleName}"); false }

    /** Results in the search preview, beside the keyboard: films and series in turn. */
    private val PREVIEW_ROWS = 6

    /** The Watchlist row at the top of My library: films the owner meant to watch, imported from Stremio. */
    private fun watchlistRow() {
        val films = watchlist()
        if (films.isEmpty()) return
        val titles = films.map { watchlistTitle(it) }
        val r = row("Watchlist", "${films.size}")
        films.take(WATCHLIST_ROW).forEachIndexed { i, f ->
            r.inner.addView(card(Entry(titles[i], null, emptyMap()), titles, defer = r, onSelect = { openWatchlistFilm(f) }), cardParams(i))
        }
        r.inner.addView(watchlistDoor(films.size), cardParams(1))
        r.scroller.post { r.loadNear() }
    }

    /** The watchlist row's last card: the way to the whole watchlist, with its sorting and grouping. */
    private fun watchlistDoor(count: Int): View {
        val card = PosterCard(activity, 260, 390, titled = false, radiusPx = 22f)
        card.contentDescription = "Open your watchlist"
        card.poster.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF4A2A1C.toInt(), 0xFF1A0F0A.toInt())).apply { cornerRadius = Cinema.pxf(activity, 22) }
        card.poster.foreground = null
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.START
            setPadding(Cinema.px(activity, 26), 0, Cinema.px(activity, 22), Cinema.px(activity, 28))
        }
        column.addView(View(activity).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(activity, 5)) },
            LinearLayout.LayoutParams(Cinema.px(activity, 10), Cinema.px(activity, 34)))
        column.addView(Cinema.text(activity, "All $count", 18, Cinema.SAFFRON, weight = 700, tracking = 0.18f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
        column.addView(Cinema.text(activity, "Your watchlist", 38, Cinema.CREAM, serif = true, weight = 400).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 6) })
        column.addView(Cinema.text(activity, "Sort, group by genre or decade", 18, Cinema.MUTED).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10) })
        card.poster.addView(column, FrameLayout.LayoutParams(-1, -1))
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { go(Dest.Watchlist, remember = true) }
        return card
    }

    // --- The watchlist page ---------------------------------------------------------------------------

    private val watchlistPrefs by lazy { activity.getSharedPreferences("watchlist_view", Context.MODE_PRIVATE) }
    private var watchSort: com.danielportelabyrne.cinema.data.movies.WatchlistSort
        get() = runCatching { com.danielportelabyrne.cinema.data.movies.WatchlistSort.valueOf(watchlistPrefs.getString("sort", null)!!) }.getOrDefault(com.danielportelabyrne.cinema.data.movies.WatchlistSort.ADDED)
        set(v) { watchlistPrefs.edit().putString("sort", v.name).apply() }
    private var watchGroup: com.danielportelabyrne.cinema.data.movies.WatchlistGroup
        get() = runCatching { com.danielportelabyrne.cinema.data.movies.WatchlistGroup.valueOf(watchlistPrefs.getString("group", null)!!) }.getOrDefault(com.danielportelabyrne.cinema.data.movies.WatchlistGroup.NONE)
        set(v) { watchlistPrefs.edit().putString("group", v.name).apply() }
    private var watchShow: com.danielportelabyrne.cinema.data.movies.WatchlistShow
        get() = runCatching { com.danielportelabyrne.cinema.data.movies.WatchlistShow.valueOf(watchlistPrefs.getString("show", null)!!) }.getOrDefault(com.danielportelabyrne.cinema.data.movies.WatchlistShow.ALL)
        set(v) { watchlistPrefs.edit().putString("show", v.name).apply() }

    /**
     * The whole watchlist on a page of its own: how to show it (one grid, or a row per genre or per decade), how to
     * order it, and whether to show only what is (or is not) in the library. Each film's genres, length and rating come
     * from Cinemeta once and are kept, so the page can sort and group by them; until they arrive a film waits under
     * "Details on the way" and the page redraws itself, keeping focus where it was, as they come in.
     */
    private fun buildWatchlist(gen: Int) {
        val films = watchlist()
        val header = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; setPadding(pageStart, 0, pageEnd, 0) }
        header.addView(Cinema.eyebrow(activity, listOf("Your watchlist", "${films.size} films")))
        header.addView(Cinema.title(activity, "Watchlist", 96, maxLines = 1), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 16) })
        content.addView(header)
        if (films.isEmpty()) {
            content.addView(note("Nothing on your watchlist yet. Add a film from its page with the watchlist button.").apply { setPadding(pageStart, Cinema.px(activity, 30), 0, 0) })
            return
        }
        val controls = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(pageStart, Cinema.px(activity, 10), pageEnd, 0) }
        content.addView(controls)
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(rows, LinearLayout.LayoutParams(-1, -2))
        var facts: Map<String, com.danielportelabyrne.cinema.data.movies.WatchlistFacts> =
            com.danielportelabyrne.cinema.data.movies.WatchlistView.parseFacts(watchlistPrefs.getString("facts", null))
        val refreshers = ArrayList<() -> Unit>()
        val titles = films.associate { it.imdbId to watchlistTitle(it) }
        fun inLibrary(id: String) = jellyfin.isConnected && jellyfin.findLoaded(id, null, false) != null

        fun render() {
            if (gen != generation) return
            val focusedId = (rows.findFocus()?.let { f -> generateSequence(f) { it.parent as? View }.firstOrNull { it.tag is String && (it.tag as String).startsWith("tt") } })?.tag
            rows.removeAllViews(); pending.removeAll { isWithin(it.row.scroller, rows) }
            val sections = com.danielportelabyrne.cinema.data.movies.WatchlistView.arrange(films, facts, ::inLibrary, watchSort, watchGroup, watchShow)
            if (sections.isEmpty()) { rows.addView(note("Nothing here with these choices.").apply { setPadding(pageStart, Cinema.px(activity, 30), 0, 0) }); return }
            fun cardFor(f: com.danielportelabyrne.cinema.data.movies.WatchlistFilm, related: List<Title>, defer: Row): View {
                val x = facts[f.imdbId]
                val sub = listOfNotNull(f.year?.toString(), x?.genres?.firstOrNull(), x?.runtimeMin?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" },
                    x?.rating?.let { "★ " + String.format(java.util.Locale.UK, "%.1f", it) }).joinToString(" · ")
                return card(Entry(titles.getValue(f.imdbId), null, emptyMap()), related, defer = defer, note = sub.ifBlank { null }, onSelect = { openWatchlistFilm(f) }).apply { tag = f.imdbId }
            }
            sections.forEach { section ->
                val related = section.films.map { titles.getValue(it.imdbId) }
                if (section.heading == null) {
                    // One grid: lines of five posters, each line loading its posters as it nears the screen.
                    section.films.chunked(WATCHLIST_PER_LINE).forEachIndexed { li, line ->
                        val strip = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
                            setPadding(pageStart, Cinema.px(activity, if (li == 0) 40 else 20), pageEnd, Cinema.px(activity, 20))
                        }
                        val r = Row(null, strip, strip)
                        line.forEachIndexed { i, f -> strip.addView(cardFor(f, related, r), cardParams(i)) }
                        rows.addView(strip, LinearLayout.LayoutParams(-1, -2))
                        pending += Lazy(r) { r.loadNear() }
                    }
                } else {
                    val r = row(section.heading, "${section.films.size}", into = rows)
                    section.films.forEachIndexed { i, f -> r.inner.addView(cardFor(f, related, r), cardParams(i)) }
                    pending += Lazy(r) { r.loadNear() }
                }
            }
            page.post { fillNear() }
            focusedId?.let { id -> rows.findViewWithTag<View>(id)?.requestFocus() }
        }

        fun chipRow(label: String, options: List<Pair<String, () -> Unit>>, isOn: (Int) -> Boolean) {
            val strip = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
            options.forEachIndexed { i, (text, pick) ->
                val chip = lbChip(text).apply { setOnClickListener { pick(); refreshers.forEach { it() }; render() } }
                refreshers += { chip.isSelected = isOn(i) }
                strip.addView(chip, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { if (i > 0) marginStart = Cinema.px(activity, 14) })
            }
            val line = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            line.addView(Cinema.text(activity, label, 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 4) })
            line.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(strip) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10) })
            controls.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 22) })
        }
        val groups = com.danielportelabyrne.cinema.data.movies.WatchlistGroup.values()
        val sorts = com.danielportelabyrne.cinema.data.movies.WatchlistSort.values()
        val shows = com.danielportelabyrne.cinema.data.movies.WatchlistShow.values()
        chipRow("Show as", groups.map { g -> g.label to { watchGroup = g } }) { groups[it] == watchGroup }
        chipRow("Sort", sorts.map { o -> o.label to { watchSort = o } }) { sorts[it] == watchSort }
        if (jellyfin.isConnected) chipRow("Films", shows.map { o -> o.label to { watchShow = o } }) { shows[it] == watchShow }
        refreshers.forEach { it() }
        render()

        // The details still missing, four at a time; the page redraws once a batch has come in.
        val missing = films.filter { it.imdbId !in facts }
        if (missing.isEmpty()) return
        val got = java.util.concurrent.ConcurrentHashMap<String, com.danielportelabyrne.cinema.data.movies.WatchlistFacts>()
        val left = java.util.concurrent.atomic.AtomicInteger(missing.size)
        var redraw: Runnable? = null
        fun landed() {
            redraw?.let { ui.removeCallbacks(it) }
            redraw = Runnable {
                if (gen != generation) return@Runnable
                facts = facts + got
                watchlistPrefs.edit().putString("facts", com.danielportelabyrne.cinema.data.movies.WatchlistView.factsJson(facts)).apply()
                if (watchGroup != com.danielportelabyrne.cinema.data.movies.WatchlistGroup.NONE || watchSort == com.danielportelabyrne.cinema.data.movies.WatchlistSort.RATING ||
                    watchSort == com.danielportelabyrne.cinema.data.movies.WatchlistSort.SHORTEST || left.get() == 0) render()
            }.also { ui.postDelayed(it, if (left.get() == 0) 0L else 1200L) }
        }
        missing.forEach { f ->
            infoPool.execute {
                if (gen != generation) return@execute
                val t = stremio.filmPage(com.danielportelabyrne.cinema.data.stremio.StremioFilm(f.imdbId, f.name, f.poster, f.year, false, ""))
                got[f.imdbId] = com.danielportelabyrne.cinema.data.movies.WatchlistFacts(t.genres, t.runtimeMin, t.imdbScore, t.backdropUrl)
                left.decrementAndGet()
                ui.post { landed() }
            }
        }
    }


    private fun watchlistTitle(f: com.danielportelabyrne.cinema.data.movies.WatchlistFilm) =
        com.danielportelabyrne.cinema.data.stremio.StremioApi.bare(com.danielportelabyrne.cinema.data.stremio.StremioFilm(f.imdbId, f.name, f.poster, f.year, false, ""))

    /** Fetches the film's full details, finds its copy in Jellyfin if there is one, and opens its page. */
    private fun openWatchlistFilm(f: com.danielportelabyrne.cinema.data.movies.WatchlistFilm) {
        slowPool.execute {
            val saved = com.danielportelabyrne.cinema.data.stremio.StremioFilm(f.imdbId, f.name, f.poster, f.year, false, "")
            // The watchlist holds series too: asked as a series first, so a series' page lists its seasons and episodes.
            val title = stremio.seriesPage(saved) ?: stremio.filmPage(saved)
            val copy = jellyfin.takeIf { it.isConnected }?.find(title.imdbId, title.tmdbId, title.kind == Kind.SHOW)
            ui.post { openDetail(Entry(title, copy, if (copy != null) jellyfin.playbackHeaders() else emptyMap()), listOf(title)) }
        }
    }

    // --- Music --------------------------------------------------------------------------------------

    private var musicClient: com.danielportelabyrne.cinema.data.music.SubsonicClient? = null

    /** One track row and the gap after it. */
    private val trackStep: Int get() = Cinema.px(activity, TRACK_HEIGHT + TRACK_GAP)

    /** Runs [work] against the music server off the main thread; [then] gets null when it failed. */
    private fun <T> musicLoad(gen: Int, work: (com.danielportelabyrne.cinema.data.music.SubsonicClient) -> T?, then: (T?) -> Unit) {
        val c = musicClient ?: return then(null)
        try { shelfPool.execute { val r = runCatching { work(c) }.getOrNull(); ui.post { if (gen == generation) then(r) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    private fun playMusic(tracks: List<com.danielportelabyrne.cinema.data.music.MusicTrack>, start: Int = 0) {
        if (tracks.isEmpty()) { CinemaToast.show(activity, "Nothing to play", Toast.LENGTH_SHORT); return }
        com.danielportelabyrne.cinema.ui.music.MusicPlayerActivity.launch(activity, tracks, start)
    }

    /** Focus [view] unless the viewer is in the rail; the card that opened the page is gone. */
    private fun musicFocus(view: View) { if (!detailOpen && overlay.findFocus()?.let { isWithin(it, top) } != true) requestFocusRobust(overlay as ViewGroup, view) }

    private fun musicHeader(eyebrow: List<String>, title: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(Cinema.eyebrow(activity, eyebrow))
            addView(Cinema.title(activity, title, 76, maxLines = 1), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
        }

    private fun musicButtons(vararg buttons: CinemaButton): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { if (i > 0) marginStart = Cinema.px(activity, 20) }) }
    }

    /** Shuffle all, then the server's album lists and its artists. */
    private fun buildMusic(gen: Int) {
        val host = musicClient?.config?.baseUrl?.substringAfter("://") ?: return
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pageStart, 0, pageEnd, 0) }
        head.addView(musicHeader(listOf("Music", host), "Your music"))
        val shuffle = CinemaButton(activity, CinemaButton.Kind.TERRA, "Shuffle all", R.drawable.ic_cin_play, small = true)
        shuffle.setOnClickListener { musicLoad(gen, { it.randomSongs(100) }) { songs -> if (songs == null) CinemaToast.show(activity, "Couldn't reach the music server", Toast.LENGTH_SHORT) else playMusic(songs) } }
        head.addView(musicButtons(shuffle), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        content.addView(head, LinearLayout.LayoutParams(-1, -2))
        musicFocus(shuffle)
        val lists = listOf(
            "Recently added" to com.danielportelabyrne.cinema.data.music.AlbumList.NEWEST,
            "Played lately" to com.danielportelabyrne.cinema.data.music.AlbumList.RECENT,
            "Most played" to com.danielportelabyrne.cinema.data.music.AlbumList.FREQUENT,
            "Starred" to com.danielportelabyrne.cinema.data.music.AlbumList.STARRED,
            "Something different" to com.danielportelabyrne.cinema.data.music.AlbumList.RANDOM
        )
        lists.forEach { (heading, list) -> musicAlbumRow(heading, gen) { it.albums(list) } }
        musicArtistRow(gen)
    }

    /** A row of square album covers, fetched when the row nears the screen; hidden when empty. */
    private fun musicAlbumRow(heading: String, gen: Int, fetch: (com.danielportelabyrne.cinema.data.music.SubsonicClient) -> List<com.danielportelabyrne.cinema.data.music.MusicAlbum>?) {
        val row = row(heading, null)
        row.inner.minimumHeight = Cinema.px(activity, 360)
        pending += Lazy(row) {
            musicLoad(gen, fetch) { albums ->
                row.inner.minimumHeight = 0
                if (albums.isNullOrEmpty()) { row.hide(); return@musicLoad }
                row.setCount(albums.size)
                val tag = pageTag
                albums.forEachIndexed { i, a ->
                    val card = PosterCard(activity, 260, 260)
                    card.title?.text = a.name
                    card.subline?.text = listOfNotNull(a.artist, a.year?.toString()).joinToString(" · ")
                    card.placeholder(a.name)
                    card.onFocus = { lit -> if (lit) reveal(card) }
                    card.setOnClickListener { go(Dest.MusicAlbum(a.id), remember = true) }
                    row.inner.addView(card, cardParams(i))
                    val load: () -> Unit = { com.danielportelabyrne.cinema.ui.music.Music.cover(a.coverArt, 300)?.let { key ->
                        images.loadShadedPoster(key, emptyMap(), Cinema.px(activity, 260), tag) { b -> if (gen == generation) card.setShadedPoster(b) } } }
                    if (i < EAGER_POSTERS) load() else row.deferred += card to load
                }
                row.scroller.post { if (gen == generation) row.loadNear() }
            }
        }
        page.post { if (gen == generation) fillNear() }
    }

    private fun musicArtistRow(gen: Int) {
        val row = row("Artists", null)
        row.inner.minimumHeight = Cinema.px(activity, 128)
        pending += Lazy(row) {
            musicLoad(gen, { it.artists() }) { artists ->
                row.inner.minimumHeight = 0
                if (artists.isNullOrEmpty()) { row.hide(); return@musicLoad }
                row.setCount(artists.size)
                artists.forEachIndexed { i, a ->
                    val tile = actionCard(a.name, a.albumCount?.let { if (it == 1) "1 album" else "$it albums" } ?: "Artist") { go(Dest.MusicArtist(a.id, a.name), remember = true) }
                    tile.layoutParams = LinearLayout.LayoutParams(Cinema.px(activity, 420), Cinema.px(activity, 128)).apply { if (i > 0) marginStart = Cinema.px(activity, 28) }
                    row.inner.addView(tile)
                }
            }
        }
        page.post { if (gen == generation) fillNear() }
    }

    /** One album: its cover, Play and Shuffle, and the tracks in order. */
    private fun buildMusicAlbum(id: String, gen: Int) {
        val wait = loading("Loading the album…").apply { setPadding(pageStart, 0, 0, 0) }
        content.addView(wait)
        musicLoad(gen, { it.album(id) }) { found ->
            content.removeView(wait)
            if (found == null) { content.addView(note("Couldn't reach the music server just now").apply { setPadding(pageStart, 0, 0, 0) }); return@musicLoad }
            val (album, tracks) = found
            val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(pageStart, 0, pageEnd, 0) }
            val cover = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.06f))
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 18)) } }
                clipToOutline = true
            }
            head.addView(cover, LinearLayout.LayoutParams(Cinema.px(activity, 360), Cinema.px(activity, 360)))
            com.danielportelabyrne.cinema.ui.music.Music.cover(album.coverArt, 600)?.let { key -> images.load(key, maxWidth = Cinema.px(activity, 360), tag = pageTag) { b -> if (gen == generation) cover.setImageBitmap(b) } }
            val side = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val minutes = album.durationSec?.let { "${(it + 30) / 60} min" }
            side.addView(musicHeader(listOfNotNull("Album", album.year?.toString(), "${tracks.size} tracks", minutes), album.name))
            album.artist?.let { side.addView(Cinema.text(activity, it, 30, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 12) }) }
            val play = CinemaButton(activity, CinemaButton.Kind.TERRA, "Play", R.drawable.ic_cin_play, small = true).apply { setOnClickListener { playMusic(tracks) } }
            val shuffle = CinemaButton(activity, CinemaButton.Kind.GLASS, "Shuffle", null, small = true).apply { setOnClickListener { playMusic(tracks.shuffled()) } }
            val more = album.artistId?.let { artistId -> CinemaButton(activity, CinemaButton.Kind.GLASS, "More by ${album.artist ?: "this artist"}", null, small = true).apply {
                setOnClickListener { go(Dest.MusicArtist(artistId, album.artist ?: album.name), remember = true) } } }
            side.addView(musicButtons(*listOfNotNull(play, shuffle, more).toTypedArray()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
            head.addView(side, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 56) })
            content.addView(head, LinearLayout.LayoutParams(-1, -2))
            musicFocus(play)

            val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pageStart, Cinema.px(activity, 50), pageEnd, 0) }
            val discs = tracks.mapNotNull { it.disc }.toSet().size > 1
            tracks.forEachIndexed { i, t ->
                val number = listOfNotNull(t.disc?.takeIf { discs }, t.number).joinToString("-").ifEmpty { "${i + 1}" }
                list.addView(musicTrackLine(number, t.title, t.artist?.takeIf { it != album.artist }, t.durationSec) { playMusic(tracks, i) },
                    LinearLayout.LayoutParams(-1, Cinema.px(activity, TRACK_HEIGHT)).apply { if (i > 0) topMargin = Cinema.px(activity, TRACK_GAP) })
            }
            content.addView(list, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** "3   Song title   Guest artist   4:05": a track that plays from here when chosen. */
    private fun musicTrackLine(number: String, title: String, artist: String?, seconds: Int?, onSelect: () -> Unit): View {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(Cinema.px(activity, 30), 0, Cinema.px(activity, 30), 0)
        }
        val num = Cinema.text(activity, number, 22, Cinema.DIM).apply { maxLines = 1 }
        val name = Cinema.text(activity, title, 26, Cinema.CREAM, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val by = Cinema.text(activity, artist.orEmpty(), 22, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val time = Cinema.text(activity, seconds?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty(), 22, Cinema.DIM).apply { maxLines = 1 }
        line.addView(num, LinearLayout.LayoutParams(Cinema.px(activity, 70), -2))
        line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        if (artist != null) line.addView(by, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 24) })
        line.addView(time, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 24) })
        val rest = Cinema.glass(activity, 20f, Cinema.alpha(Cinema.CREAM, 0.03f)); val lit = Cinema.glass(activity, 20f, Cinema.CREAM)
        line.background = rest
        line.setOnFocusChangeListener { _, f ->
            line.background = if (f) lit else rest
            name.setTextColor(if (f) Cinema.INK else Cinema.CREAM)
            listOf(num, by, time).forEach { it.setTextColor(if (f) Cinema.INK else if (it === by) Cinema.MUTED else Cinema.DIM) }
            // Keep three tracks in view past the focused one, so the list moves before focus reaches the edge.
            if (f) FastFocus.reveal(line, page, trackStep * 3, trackStep * 3, pageStart)
        }
        line.setOnClickListener { onSelect() }
        return line
    }

    /** One artist: their albums. */
    private fun buildMusicArtist(id: String, name: String, gen: Int) {
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pageStart, 0, pageEnd, 0) }
        head.addView(musicHeader(listOf("Artist"), name))
        content.addView(head, LinearLayout.LayoutParams(-1, -2))
        val row = row("Albums", null)
        row.inner.minimumHeight = Cinema.px(activity, 360)
        musicLoad(gen, { it.artistAlbums(id) }) { albums ->
            row.inner.minimumHeight = 0
            if (albums.isNullOrEmpty()) { row.inner.addView(note(if (albums == null) "Couldn't reach the music server just now" else "No albums")); return@musicLoad }
            row.setCount(albums.size)
            albums.sortedByDescending { it.year ?: 0 }.forEachIndexed { i, a ->
                val card = PosterCard(activity, 260, 260)
                card.title?.text = a.name
                card.subline?.text = a.year?.toString().orEmpty()
                card.placeholder(a.name)
                card.onFocus = { lit -> if (lit) reveal(card) }
                card.setOnClickListener { go(Dest.MusicAlbum(a.id), remember = true) }
                row.inner.addView(card, cardParams(i))
                com.danielportelabyrne.cinema.ui.music.Music.cover(a.coverArt, 300)?.let { key ->
                    images.loadShadedPoster(key, emptyMap(), Cinema.px(activity, 260), pageTag) { b -> if (gen == generation) card.setShadedPoster(b) } }
                if (i == 0) musicFocus(card)
            }
        }
    }

    // --- Letterboxd -----------------------------------------------------------------------------

    @Volatile private var lbStarted = false
    private val lb by lazy { lbStarted = true; com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository(activity) }
    private val lbPosterPool = Executors.newFixedThreadPool(4)

    /** Display names and pictures of followed members, once their profiles have loaded. */
    private val lbNames = HashMap<String, String>()

    private fun lbName(user: String) = lbNames[user.lowercase()] ?: user

    /** "2026-10-16" -> "16 Oct". */
    private fun lbDay(iso: String?): String? {
        if (iso == null || iso.length < 10) return null
        val m = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return "${iso.substring(8, 10).trimStart('0')} ${arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m - 1]}"
    }

    /** Blocking: poster addresses for [films], looked up four at a time (each is remembered for good). */
    private fun lbPosters(films: List<com.danielportelabyrne.cinema.data.letterboxd.LbFilm>): Map<String, String> {
        val out = java.util.concurrent.ConcurrentHashMap<String, String>()
        films.map { f -> lbPosterPool.submit { lb.posterUrl(f.slug)?.let { out[f.slug] = it } } }.forEach { runCatching { it.get(20, java.util.concurrent.TimeUnit.SECONDS) } }
        return out
    }

    /** A Letterboxd film as a page title, until its own page says more (TMDB id, synopsis, backdrop). */
    private fun lbTitle(f: com.danielportelabyrne.cinema.data.letterboxd.LbFilm, poster: String?) = Title(
        key = "lb:" + f.slug, kind = Kind.MOVIE, name = f.name, year = f.year, overview = null, runtimeMin = null, genres = emptyList(),
        age = null, imdbScore = null, posterUrl = poster, backdropUrl = null, imdbId = null, tmdbId = null)

    /**
     * Opens a Letterboxd film as an ordinary title page (Watch now, Stream now, the library badge): its Letterboxd page
     * gives the TMDB id, synopsis, running time and backdrop.
     */
    private fun lbOpen(f: com.danielportelabyrne.cinema.data.letterboxd.LbFilm, poster: String?) {
        val gen = generation
        val key = "lb:" + f.slug
        CinemaToast.show(activity, "Opening ${f.name}…", Toast.LENGTH_SHORT)
        // The title opens at once from what the list gives. The film's own page (synopsis, running time, IMDb and TMDB ids)
        // fills it in when it arrives, from the saved copy if there is one, else from Letterboxd, and only if the title is still open.
        fun show(info: com.danielportelabyrne.cinema.data.letterboxd.LbFilmInfo?) {
            if (gen != generation || !isVisible) return
            val p = info?.page
            val t = lbTitle(f, poster).copy(
                kind = if (info?.isTv == true) Kind.SHOW else Kind.MOVIE, tmdbId = info?.tmdbId?.toString(), imdbId = info?.imdb,
                overview = p?.synopsis, runtimeMin = p?.runtimeMin, genres = p?.genres.orEmpty().take(3), year = f.year ?: p?.year,
                // Letterboxd resizes its backdrops on request: ask for the full 1920 rather than its 1200 default.
                backdropUrl = info?.backdrop?.replace("-1200-1200-675-675-crop", "-1920-1920-1080-1080-crop"))
            if (info == null) { openDetail(entry(t), emptyList()); return }
            // A title already on screen is redrawn with its details; focus stays where it was on the list.
            if (!detailOpen || detailEntry?.title?.key != key) return
            val keep = returnFocus
            openDetail(entry(t), emptyList())
            returnFocus = keep
        }
        show(null)
        lb.load("/film/${f.slug}/", 7 * 24 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR,
            { com.danielportelabyrne.cinema.data.letterboxd.Lb.film(it) }, { gen == generation }) { info ->
            activity.runOnUiThread { if (info != null) show(info) }
        }
        // The library is only used for the "in library" badge: refresh it in the background, never on the way to the title.
        if (jellyfin.isConnected) slowPool.execute { jellyfin.library() }
    }

    /** A row of Letterboxd posters under [heading]: [note] is the line a focused poster shows, [chip] its corner tag. */
    private fun lbFilmRow(
        heading: String, caption: String?, films: List<com.danielportelabyrne.cinema.data.letterboxd.LbFilm>, gen: Int,
        into: LinearLayout = content, line: (com.danielportelabyrne.cinema.data.letterboxd.LbFilm) -> String? = { it.stars },
        chip: (com.danielportelabyrne.cinema.data.letterboxd.LbFilm) -> String? = { null }, empty: String = "Nothing here yet",
        badges: (com.danielportelabyrne.cinema.data.letterboxd.LbFilm) -> List<Pair<String, Bitmap?>> = { emptyList() },
        onShown: (List<com.danielportelabyrne.cinema.data.letterboxd.LbFilm>) -> Unit = {}
    ): Row {
        Perf.mark("lb row \"$heading\" films=${films.size}")
        val row = row(heading, caption, into = into)
        row.inner.minimumHeight = Cinema.px(activity, 488)
        if (films.isEmpty()) { row.inner.addView(note(empty)); return row }
        // Reserve the row now; build and fetch only near the viewport. A slow poster never holds up its neighbours.
        val shown = films.take(LB_ROW_LIMIT)
        val posters = java.util.concurrent.ConcurrentHashMap<String, String>()
        row.setCount(films.size)
        val tag = pageTag
        fun addFrom(from: Int) {
            if (gen != generation) return
            val to = minOf(from + CARDS_PER_FRAME, shown.size)
            for (i in from until to) {
                val f = shown[i]
                val v = card(Entry(lbTitle(f, null), null, emptyMap()), emptyList(), badge = false, note = line(f), chip = chip(f)) { lbOpen(f, posters[f.slug]) } as PosterCard
                row.inner.addView(v, cardParams(i))
                v.tag = f.slug
                v.watchers(badges(f))
                val load = {
                    lbPosterPool.execute {
                        if (gen == generation) lb.posterUrl(f.slug)?.let { url ->
                            posters[f.slug] = url
                            activity.runOnUiThread {
                                if (gen == generation && row.scroller.isAttachedToWindow) {
                                    val bounds = android.graphics.Rect(0, 0, row.scroller.width, row.scroller.height)
                                    content.offsetDescendantRectToMyCoords(row.scroller, bounds)
                                    // Cached URLs can return before the newly added card has had its first layout.
                                    val left = if (v.width > 0) v.left else i * Cinema.px(activity, 288)
                                    val right = left + Cinema.px(activity, 260)
                                    val bottom = maxOf(bounds.bottom, bounds.top + Cinema.px(activity, 488))
                                    val visible = bounds.top < page.scrollY + page.height && bottom > page.scrollY &&
                                        left < row.scroller.scrollX + row.scroller.width - row.scroller.paddingRight && right > row.scroller.scrollX
                                    val done: (Bitmap) -> Unit = { b -> if (gen == generation) { v.setShadedPoster(b); Perf.mark("lb poster drawn ${f.slug}") } }
                                    if (visible) images.loadShadedPosterPriority(url, Cinema.px(activity, 260), tag, done)
                                    else images.loadShadedPoster(url, emptyMap(), Cinema.px(activity, 260), tag, done)
                                }
                            }
                        }
                    }
                }
                if (i < EAGER_POSTERS) load() else row.deferred += v to load
            }
            if (from == 0) { lbFocusIfLost(row); onShown(shown) }
            if (to < shown.size) android.view.Choreographer.getInstance().postFrameCallback { addFrom(to) }
            else row.scroller.post { if (gen == generation) row.loadNear() }
        }
        pending += Lazy(row) { addFrom(0) }
        page.post { if (gen == generation) fillNear() }
        return row
    }

    /** A row of list cards; each opens its list. */
    private fun lbListRow(heading: String, caption: String?, lists: List<com.danielportelabyrne.cinema.data.letterboxd.LbList>, gen: Int, into: LinearLayout = content): Row {
        val row = row(heading, caption, into = into)
        row.inner.minimumHeight = Cinema.px(activity, 300)
        if (lists.isEmpty()) { row.inner.addView(note("No lists yet")); return row }
        val tag = pageTag
        lists.forEachIndexed { i, l ->
            val card = ListCard(activity, posterW = 120, step = 76, widthMock = 500)
            card.name.text = l.name
            card.by.text = listOfNotNull(l.ownerName ?: lbName(l.owner), l.count?.let { "$it films" }, l.likes?.let { "♥ $it" }).joinToString("  ·  ")
            card.setOnClickListener { go(Dest.LbListPage(l.path), remember = true) }
            card.setOnFocusChangeListener { v, f -> if (f) reveal(v) }
            row.inner.addView(card, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(activity, 56) })
            row.deferred += card to {
                l.previews.take(5).forEachIndexed { k, f -> lbPosterPool.execute {
                    if (gen == generation) lb.posterUrl(f.slug)?.let { url -> activity.runOnUiThread {
                        if (gen == generation) images.load(url, maxWidth = 240, tag = tag) { b ->
                            if (gen == generation) card.posters[k].setImageBitmap(b)
                        }
                    } }
                } }
            }
        }
        row.setCount(lists.size)
        lbFocusIfLost(row)
        pending += Lazy(row) { row.loadNear() }
        page.post { if (gen == generation) fillNear() }
        return row
    }

    /** The page heading the Letterboxd pages share: three dots, the eyebrow words, and a big title. */
    private fun lbHeader(eyebrow: List<String>, title: String, titleSize: Int = 84): LinearLayout {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(com.danielportelabyrne.cinema.design.cinema.LbDots.view(activity, 12), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Cinema.px(activity, 16) })
        line.addView(Cinema.eyebrow(activity, eyebrow, mark = false))
        box.addView(line, LinearLayout.LayoutParams(-2, -2))
        box.addView(Cinema.title(activity, title, titleSize, maxLines = 1), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
        return box
    }

    /** Letterboxd home: the followed members as cards, what they watched lately, and their watchlists and likes. */
    private fun buildLetterboxd(gen: Int) {
        val users = lb.following
        Perf.mark("lb home open members=${users.size}")
        val head = FrameLayout(activity).apply { clipChildren = false }
        head.addView(lbHeader(listOf("Letterboxd", "Following ${users.size}"), "Your film diary", 76), FrameLayout.LayoutParams(-2, -2).apply { leftMargin = pageStart })
        val search = CinemaButton(activity, CinemaButton.Kind.GLASS, "Search lists", R.drawable.ic_cin_search, small = true).apply { setOnClickListener { go(Dest.LbSearch, remember = true) } }
        head.addView(search, FrameLayout.LayoutParams(-2, Cinema.px(activity, 60), Gravity.END).apply { rightMargin = pageEnd; topMargin = Cinema.px(activity, 26) })
        content.addView(head, LinearLayout.LayoutParams(-1, -2))

        // The members, side by side, then "Follow someone".
        val members = row(null, null)
        members.inner.setPadding(0, Cinema.px(activity, 34), 0, Cinema.px(activity, 20))
        val cards = users.map { u ->
            MemberCard(activity).also { c ->
                c.bind(lbName(u), u, null, null, null, emptyList())
                c.setOnClickListener { go(Dest.LbPerson(u), remember = true) }
                c.setOnFocusChangeListener { v, f -> if (f) reveal(v) }
                members.inner.addView(c, LinearLayout.LayoutParams(Cinema.px(activity, 600), Cinema.px(activity, 300)).apply { marginEnd = Cinema.px(activity, 28) })
            }
        }
        val follow = FollowCard(activity).apply {
            setOnClickListener { go(Dest.LbFollow, remember = true) }
            setOnFocusChangeListener { v, f -> if (f) reveal(v) }
        }
        members.inner.addView(follow, LinearLayout.LayoutParams(Cinema.px(activity, 384), Cinema.px(activity, 300)))
        (cards.firstOrNull() ?: follow).let { first -> if (!detailOpen && overlay.findFocus() == null) requestFocusRobust(overlay as ViewGroup, first) }
        if (users.isEmpty()) {
            content.addView(note("Follow yourself and your friends to see what they have watched lately, their watchlists, and their stars on every film page. " +
                "Nothing is signed in: Cinema only reads public Letterboxd profiles.").apply { isSingleLine = false; maxLines = 4; setLineSpacing(0f, 1.2f) },
                LinearLayout.LayoutParams(Cinema.px(activity, 1400), -2).apply { leftMargin = pageStart })
            return
        }

        val recentHolder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(recentHolder, LinearLayout.LayoutParams(-1, -2))
        val perPerson = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(perPerson, LinearLayout.LayoutParams(-1, -2))
        lbFilmRow("Recently watched", if (users.size > 1) "by the people you follow" else null, emptyList(), gen, recentHolder, empty = "Loading…")

        val diaries = java.util.concurrent.ConcurrentHashMap<String, List<com.danielportelabyrne.cinema.data.letterboxd.LbDiaryEntry>>()
        var recentScheduled = false
        var lastRecent: List<Pair<String, com.danielportelabyrne.cinema.data.letterboxd.LbDiaryEntry>>? = null
        fun drawRecent() {
            val merged = users.flatMap { u -> diaries[u].orEmpty().map { u to it } }.sortedByDescending { it.second.date ?: "" }.take(18)
            if (merged == lastRecent) return
            if (merged.isEmpty() && diaries.size < users.size) return
            lastRecent = merged
            recentHolder.removeAllViews()
            lbFilmRow("Recently watched", if (users.size > 1) "by the people you follow" else null, merged.map { it.second.film.copy(rating = it.second.rating) }, gen, into = recentHolder,
                line = { f -> listOfNotNull(f.stars, merged.firstOrNull { it.second.film.slug == f.slug }?.second?.date?.let { lbDay(it) }).joinToString("  ·  ").ifBlank { null } },
                chip = { f -> merged.firstOrNull { it.second.film.slug == f.slug }?.first?.let { lbName(it).substringBefore(' ') } })
        }
        users.forEachIndexed { i, u ->
            lb.load("/$u/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, u) }, { gen == generation }) { p ->
                if (gen != generation || p == null) return@load
                Perf.mark("lb profile $u")
                lbNames[u.lowercase()] = p.name.substringBefore(' ').ifBlank { u }
                lbStats[u] = p.stats.filter { it.second.lowercase() in setOf("films", "this year", "lists", "following") }.take(3)
                val card = cards[i]
                val last = diaries[u]?.firstOrNull()
                card.bind(lbName(u), u, last?.film?.name ?: p.recent.firstOrNull()?.name, (last?.rating ?: p.recent.firstOrNull()?.rating)?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it) },
                    lbDay(last?.date), p.stats.filter { it.second.lowercase() in setOf("films", "this year", "lists", "following") }.take(3).map { it.first to it.second })
                card.avatar.set(lbName(u), null)
                val tag = pageTag
                p.avatar?.let { a -> images.loadPriority(a, maxWidth = Cinema.px(activity, 96), tag = tag) { b -> if (gen == generation) card.avatar.set(lbName(u), b) } }
                p.favourites.take(4).forEachIndexed { k, f ->
                    lbPosterPool.execute {
                        if (gen == generation) lb.posterUrl(f.slug)?.let { url -> activity.runOnUiThread {
                            if (gen == generation) images.loadPriority(url, maxWidth = Cinema.px(activity, 110), tag = tag) { b ->
                                if (gen == generation) { card.favs[k].setImageBitmap(b); if (k == 1) card.washWith(b) }
                            }
                        } }
                    }
                }
            }
            lb.load("/$u/films/diary/", 30 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.MINUTE, { com.danielportelabyrne.cinema.data.letterboxd.Lb.diary(it) }, { gen == generation }) { d ->
                if (gen != generation) return@load
                Perf.mark("lb diary $u entries=${d?.size}")
                diaries[u] = d.orEmpty()
                if (!recentScheduled) {
                    recentScheduled = true
                    android.view.Choreographer.getInstance().postFrameCallback {
                        recentScheduled = false
                        if (gen == generation) drawRecent()
                    }
                }
                d?.firstOrNull()?.let { last -> cards[i].bind(lbName(u), u, last.film.name, last.rating?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it) }, lbDay(last.date), lbStats[u].orEmpty()) }
            }
        }
        // Each member's watchlist and likes, in turn.
        users.forEach { u ->
            val watch = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            val likes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            perPerson.addView(watch); perPerson.addView(likes)
            lbFilmRow("${lbName(u)}’s watchlist", null, emptyList(), gen, watch, empty = "Loading…")
            lbFilmRow("${lbName(u)} liked", null, emptyList(), gen, likes, empty = "Loading…")
            lb.load("/$u/watchlist/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }, { gen == generation }) { f ->
                if (gen != generation) return@load
                watch.removeAllViews(); lbFilmRow("${lbName(u)}’s watchlist", null, f.orEmpty().take(20), gen, into = watch, line = { null }, empty = if (f == null) "Couldn't load this watchlist just now" else "Nothing here yet")
            }
            lb.load("/$u/likes/films/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }, { gen == generation }) { f ->
                if (gen != generation) return@load
                likes.removeAllViews(); lbFilmRow("${lbName(u)} liked", null, f.orEmpty().take(20), gen, into = likes, line = { it.stars?.let { s -> "$s  ♥" } ?: "♥" }, empty = if (f == null) "Couldn't load liked films just now" else "Nothing here yet")
            }
        }
    }

    /** Numbers shown on a member's card, kept from their profile so a diary refresh does not lose them. */
    private val lbStats = HashMap<String, List<Pair<String, String>>>()

    /** One member: picture, name, numbers and favourites, then tabs for their watchlist, likes, diary, reviews and lists. */
    private fun buildLbPerson(user: String, gen: Int) {
        val followed = lb.following.any { it.equals(user, true) }
        val head = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val left = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        val avatar = com.danielportelabyrne.cinema.design.cinema.AvatarView(activity, 180).apply { set(lbName(user), null) }
        left.addView(avatar, LinearLayout.LayoutParams(Cinema.px(activity, 180), Cinema.px(activity, 180)))
        val names = lbHeader(listOf(if (followed) "Following" else "Letterboxd", "@$user"), lbName(user), 104)
        left.addView(names, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 40) })
        head.addView(left, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = pageStart })
        val favs = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        favs.addView(Cinema.text(activity, "Favourites", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true))
        val favRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        favs.addView(favRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 16) })
        head.addView(favs, FrameLayout.LayoutParams(-2, -2, Gravity.END).apply { rightMargin = pageEnd })
        content.addView(head, LinearLayout.LayoutParams(-1, -2))

        // The numbers as glass tiles, and Follow / Unfollow beside them.
        val tiles = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        content.addView(tiles, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pageStart; topMargin = Cinema.px(activity, 34) })
        val followButton = CinemaButton(activity, if (followed) CinemaButton.Kind.GLASS else CinemaButton.Kind.PRIMARY, if (followed) "Unfollow" else "Follow", null, small = true).apply {
            setOnClickListener {
                if (lb.following.any { it.equals(user, true) }) { lb.unfollow(user); CinemaToast.show(activity, "Stopped following ${lbName(user)}", Toast.LENGTH_SHORT); current = Dest.Letterboxd; trail.clear(); styleTabs(); build() }
                else { lb.follow(user); CinemaToast.show(activity, "Following ${lbName(user)}", Toast.LENGTH_SHORT); build() }
            }
        }

        // Tabs: Fraunces words, the shown one with a stripe beneath, a focused one filled cream.
        val tabRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(tabRow, LinearLayout.LayoutParams(-2, Cinema.px(activity, 62)).apply { marginStart = pageStart - Cinema.px(activity, 26); topMargin = Cinema.px(activity, 44) })
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(holder, LinearLayout.LayoutParams(-1, -2))
        val tabNames = listOf("Watchlist", "Liked", "Diary", "Reviews", "Lists", "Liked lists")
        var shown = -1
        val paints = ArrayList<() -> Unit>()
        fun show(i: Int) {
            if (shown == i) return
            shown = i; paints.forEach { it() }
            holder.removeAllViews()
            val loading = loading("Loading…").apply { setPadding(pageStart, Cinema.px(activity, 30), 0, 0) }
            holder.addView(loading)
            val H = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR
            fun ready(): Boolean = gen == generation && shown == i
            when (i) {
                0 -> lb.load("/$user/watchlist/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }, { ready() }) { f -> if (!ready()) return@load; holder.removeAllViews(); lbFilmRow("Watchlist", null, f.orEmpty(), gen, holder, line = { null }) }
                1 -> lb.load("/$user/likes/films/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }, { ready() }) { f -> if (!ready()) return@load; holder.removeAllViews(); lbFilmRow("Liked films", null, f.orEmpty(), gen, holder, line = { it.stars?.let { s -> "$s  ♥" } ?: "♥" }) }
                2 -> lb.load("/$user/films/diary/", H / 2, { com.danielportelabyrne.cinema.data.letterboxd.Lb.diary(it) }, { ready() }) { d -> if (!ready()) return@load; holder.removeAllViews()
                    lbFilmRow("Diary", null, d.orEmpty().map { it.film.copy(rating = it.rating) }, gen, holder,
                        line = { f -> listOfNotNull(f.stars, d.orEmpty().firstOrNull { it.film.slug == f.slug }?.date?.let { lbDay(it) }).joinToString("  ·  ").ifBlank { null } }) }
                3 -> lb.load("/$user/films/reviews/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.reviews(it) }, { ready() }) { r -> if (!ready()) return@load; holder.removeAllViews(); lbReviewRow("Reviews", r.orEmpty(), gen, holder, byFilm = true) }
                4 -> lb.load("/$user/lists/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }, { ready() }) { l -> if (!ready()) return@load; holder.removeAllViews(); lbListRow("Lists", null, l.orEmpty(), gen, holder) }
                5 -> lb.load("/$user/likes/lists/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }, { ready() }) { l -> if (!ready()) return@load; holder.removeAllViews(); lbListRow("Liked lists", null, l.orEmpty(), gen, holder) }
            }
        }
        tabNames.forEachIndexed { i, name ->
            val label = Cinema.text(activity, name, 34, Cinema.DIM, serif = true, tracking = -0.01f).apply { maxLines = 1 }
            val underline = View(activity).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(activity, 3)) }
            val tab = FrameLayout(activity).apply {
                isFocusable = true; isClickable = true; contentDescription = name
                setPadding(Cinema.px(activity, 26), 0, Cinema.px(activity, 26), 0)
                addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL))
                addView(underline, FrameLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 6), Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(activity, 26); bottomMargin = Cinema.px(activity, 2) })
            }
            val lit = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 31); setColor(Cinema.CREAM) }
            paints += {
                val focused = tab.isFocused; val on = shown == i
                tab.background = if (focused) lit else null
                label.setTextColor(if (focused) Cinema.INK else if (on) Cinema.CREAM else Cinema.DIM)
                underline.visibility = if (on && !focused) View.VISIBLE else View.GONE
            }
            tab.setOnFocusChangeListener { v, f -> if (f) { show(i); reveal(v) }; paints[i]() }
            tab.setOnClickListener { show(i) }
            tabRow.addView(tab, LinearLayout.LayoutParams(-2, -1).apply { if (i > 0) marginStart = Cinema.px(activity, 10) })
        }
        show(0)

        lb.load("/$user/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, user) }, { gen == generation }) { p ->
            if (gen != generation) return@load
            if (p == null) { names.addView(note("Couldn't reach Letterboxd just now")); return@load }
            lbNames[user.lowercase()] = p.name.substringBefore(' ').ifBlank { user }
            ((names.getChildAt(1) as? TextView))?.text = p.name
            avatar.set(p.name, null)
            p.avatar?.let { a -> images.load(a, maxWidth = 360, tag = pageTag) { b -> avatar.set(p.name, b) } }
            tiles.removeAllViews()
            p.stats.take(4).forEachIndexed { i, (value, label) ->
                val tile = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(Cinema.px(activity, 22), 0, Cinema.px(activity, 16), 0); background = Cinema.glass(activity, 22f)
                }
                tile.addView(Cinema.text(activity, value, 40, Cinema.CREAM, serif = true))
                tile.addView(Cinema.text(activity, label, 14, Cinema.DIM, weight = 600, tracking = 0.14f, caps = true).apply { maxLines = 1 })
                tiles.addView(tile, LinearLayout.LayoutParams(Cinema.px(activity, 200), Cinema.px(activity, 96)).apply { if (i > 0) marginStart = Cinema.px(activity, 18) })
            }
            tiles.addView(followButton, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { marginStart = Cinema.px(activity, 30) })
            shelfPool.execute {
                val posters = lbPosters(p.favourites)
                activity.runOnUiThread {
                    if (gen != generation) return@runOnUiThread
                    favRow.removeAllViews()
                    p.favourites.forEachIndexed { k, f ->
                        val v = ImageView(activity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; elevation = Cinema.pxf(activity, 12)
                            background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 12); setColor(Cinema.PLACEHOLDER) }
                            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 12)) } }
                        }
                        posters[f.slug]?.let { u -> images.load(u, maxWidth = 300, tag = pageTag) { b -> v.setImageBitmap(b) } }
                        favRow.addView(v, LinearLayout.LayoutParams(Cinema.px(activity, 150), Cinema.px(activity, 225)).apply { if (k > 0) marginStart = Cinema.px(activity, 20) })
                    }
                }
            }
        }
        tabRow.getChildAt(0)?.let { if (overlay.findFocus() == null) requestFocusRobust(overlay as ViewGroup, it) }
    }

    /** A row of review cards. [byFilm] names each card by its film (a member's reviews) rather than its author. */
    private fun lbReviewRow(heading: String?, reviews: List<com.danielportelabyrne.cinema.data.letterboxd.LbReview>, gen: Int, into: LinearLayout, byFilm: Boolean = false, inset: Boolean = true): Row {
        val row = row(heading, null, into = into, inset = inset)
        if (reviews.isEmpty()) { row.inner.addView(note("No reviews yet")); return row }
        reviews.take(16).forEachIndexed { i, r ->
            val card = ReviewCard(activity)
            val footer = listOfNotNull(lbDay(r.date), r.likes?.let { n -> if (n >= 1000) String.format(Locale.UK, "%.1fK likes", n / 1000.0).replace(".0K", "K") else if (n == 1) "1 like" else "$n likes" }).joinToString(" · ")
            card.bind(if (byFilm) r.film?.name ?: r.authorName else r.authorName, r.rating?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it) }, r.liked, r.text, footer)
            if (byFilm) card.avatar.visibility = View.GONE
            else { card.avatar.set(r.authorName, null); r.avatar?.let { a -> images.load(a, maxWidth = 104, tag = pageTag) { b -> card.avatar.set(r.authorName, b) } } }
            card.setOnFocusChangeListener { v, f -> if (f) reveal(v) }
            card.setOnClickListener {
                val reviewed = r.film
                if (byFilm && reviewed != null) lbOpen(reviewed, null)
                else infoSheet.show("REVIEW", r.authorName, listOfNotNull(r.rating?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it) }, lbDay(r.date)).joinToString(" · ").ifBlank { null }, listOf(r.text), null, listOf(SheetAction("Close") { }))
            }
            row.inner.addView(card, LinearLayout.LayoutParams(Cinema.px(activity, 540), Cinema.px(activity, 300)).apply { if (i > 0) marginStart = Cinema.px(activity, 32) })
        }
        row.setCount(reviews.size)
        lbFocusIfLost(row)
        return row
    }

    /** A Letterboxd page opened from a card that is gone now leaves nothing focused: give focus to [row]'s first item. */
    private fun lbFocusIfLost(row: Row) {
        val f = overlay.findFocus()
        if (!detailOpen && streamLoading == null && (f == null || !f.isAttachedToWindow) && row.inner.childCount > 0)
            row.inner.getChildAt(0)?.takeIf { it.isFocusable }?.let { requestFocusRobust(overlay as ViewGroup, it) }
    }

    /** How a list's films are put in order: as the list has them, or by their average rating on Letterboxd, highest first. */
    private enum class LbOrder { LIST, RATED }

    /** What a list hides: nothing, the films any picked member has watched, or only those they have all watched. */
    private enum class LbHide { NONE, ANY, ALL }

    /** A choice on a Letterboxd list page: glass, saffron when it is the one in force, cream when focused. */
    private fun lbChip(label: String): TextView {
        val r = Cinema.pxf(activity, 200)
        fun pillOf(color: Int) = GradientDrawable().apply { cornerRadius = r; setColor(color) }
        return Cinema.text(activity, label, 23, Cinema.CREAM, weight = 600).apply {
            gravity = Gravity.CENTER; isFocusable = true; isClickable = true; maxLines = 1
            setPadding(Cinema.px(activity, 30), 0, Cinema.px(activity, 30), 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), pillOf(Cinema.CREAM))
                addState(intArrayOf(android.R.attr.state_selected), pillOf(Cinema.SAFFRON))
                addState(intArrayOf(), pillOf(Cinema.GLASS_STRONG))
            }
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(Cinema.INK, Cinema.INK, Cinema.CREAM)))
        }
    }

    /**
     * A list: its name, owner and description, a fan of its first posters, then its films. Above the films: an order
     * (as listed, or highest average rating first) and a filter on what the followed members have watched. Each film
     * shows a badge for every followed member who has watched it.
     */
    private fun buildLbList(path: String, gen: Int) {
        val head = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val owner = path.split('/').getOrNull(1).orEmpty()
        val header = lbHeader(listOf("List", "by ${lbName(owner)}"), "…", 104)
        text.addView(header)
        val meta = Cinema.text(activity, "", 23, Cinema.MUTED)
        text.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 26) })
        val desc = Cinema.synopsis(activity, "", 24, maxLines = 3).apply { visibility = View.GONE }
        text.addView(desc, LinearLayout.LayoutParams(Cinema.px(activity, 960), -2).apply { topMargin = Cinema.px(activity, 18) })
        head.addView(text, FrameLayout.LayoutParams(Cinema.px(activity, 1080), -2).apply { leftMargin = pageStart })
        val fan = ListCard(activity, posterW = 200, step = 96, widthMock = 600).apply { isFocusable = false; name.visibility = View.GONE; by.visibility = View.GONE }
        head.addView(fan, FrameLayout.LayoutParams(-2, -2, Gravity.END).apply { rightMargin = pageEnd })
        content.addView(head, LinearLayout.LayoutParams(-1, -2))
        val H = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR
        val stillHere = { gen == generation }
        val members = lb.following

        // What the page shows: the films as the list has them, and how they are ordered and filtered.
        var films = emptyList<LbFilm>()
        var listCount: Int? = null
        var loaded = false
        var order = LbOrder.LIST
        var hide = LbHide.NONE
        // The accounts a filter counts: all the followed ones to begin with.
        val picked = LinkedHashSet(members)
        // What has arrived so far: each film's average rating, and which members have watched which films.
        val ratings = HashMap<String, Double>()
        var ratingsStarted = false
        var ratingsDone = 0
        val watched = HashMap<String, HashMap<String, Boolean>>()
        var watchesDone = 0
        // Watch status asked for so far: "member/slug" keys, and how many asks (a row asks for its films as it is built).
        val watchAsked = HashSet<String>()
        var watchesAsked = 0
        // Set once renderSoon exists below: a watch that arrives asks for a redraw.
        var onWatchArrived: () -> Unit = {}
        val pictures = HashMap<String, Bitmap>()
        val accountChips = HashMap<String, TextView>()
        val refreshers = ArrayList<() -> Unit>()
        var renderQueued = false

        val controls = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10); marginStart = pageStart })
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(rows, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10) })
        rows.addView(loading("Loading the list…").apply { setPadding(pageStart, Cinema.px(activity, 40), 0, 0) })

        fun membersWhoWatched(slug: String) = members.filter { watched[it]?.get(slug) == true }

        /** Whether the filter hides [slug]: any picked member has watched it, or (ALL) every picked member has. */
        fun hidden(slug: String): Boolean {
            if (hide == LbHide.NONE || picked.isEmpty()) return false
            val seen = picked.map { watched[it]?.get(slug) == true }
            return if (hide == LbHide.ANY) seen.any { it } else seen.all { it }
        }

        /** The films to show, in the order chosen. A film whose ratings or watchers have not arrived yet stays in. */
        fun shown(): List<LbFilm> {
            val kept = films.filterNot { hidden(it.slug) }
            return if (order == LbOrder.RATED) kept.sortedByDescending { ratings[it.slug] ?: -1.0 } else kept
        }

        fun noteFor(f: LbFilm): String? {
            val who = membersWhoWatched(f.slug).map { lbName(it) }
            return listOfNotNull(
                ratings[f.slug]?.let { String.format(Locale.UK, "★ %.1f", it) },
                who.takeIf { it.isNotEmpty() }?.let { "watched by ${it.joinToString(", ")}" }
            ).joinToString("  ·  ").ifBlank { null }
        }

        fun badgesFor(f: LbFilm): List<Pair<String, Bitmap?>> = membersWhoWatched(f.slug).map { lbName(it) to pictures[it] }

        /** Puts focus back on [slug] once it is drawn again: a row's cards are built a few at a time, so this tries a while. */
        fun refocus(slug: String, attempt: Int) {
            if (gen != generation) return
            rows.findViewWithTag<View>(slug)?.let { requestFocusRobust(overlay as ViewGroup, it); return }
            if (attempt < 20) ui.postDelayed({ refocus(slug, attempt + 1) }, 100)
        }

        /**
         * Whether each followed member has watched each of [wanted]'s films, from their own page for it (Not Found means not).
         * A row asks for its films as it is built, so a film is looked up when it is about to be seen; a film is asked once.
         */
        fun loadWatchesFor(wanted: List<LbFilm>) {
            members.forEach { u ->
                wanted.forEach { f ->
                    if (!watchAsked.add("$u/${f.slug}")) return@forEach
                    watchesAsked++
                    lb.load("/$u/film/${f.slug}/", 6 * H, { Lb.member(it, u) }, stillHere, background = true) { m ->
                        if (gen != generation) return@load
                        watchesDone++
                        if (m != null) watched.getOrPut(u) { HashMap() }[f.slug] = m.watched
                        onWatchArrived()
                    }
                }
            }
        }

        /** Draws the list, twelve films to a row, under the order and filter chosen. */
        /** The line under the title: how many films, and what is still arriving. */
        fun renderMeta(visibleCount: Int) {
            meta.text = listOfNotNull(
                listCount?.let { "$it films" },
                if (films.size < (listCount ?: 0)) "first ${films.size} shown" else null,
                if (visibleCount != films.size) "$visibleCount after the filter" else null,
                if (order == LbOrder.RATED && ratingsDone < films.size) "ratings $ratingsDone of ${films.size}" else null,
                if (watchesDone < watchesAsked) "checking who watched" else null
            ).joinToString("  ·  ")
        }

        // The films the rows were last drawn for, in their order: the same again, the cards are only given their new notes and badges.
        var drawnFor: String? = null

        fun render() {
            if (!loaded || gen != generation) return
            val visible = shown()
            Perf.mark("lb list render visible=${visible.size} watches=$watchesDone/${members.size * films.size}")
            val drawing = "$order|" + visible.joinToString(",") { it.slug }
            if (visible.isNotEmpty() && drawing == drawnFor) {
                visible.forEach { f ->
                    rows.findViewWithTag<PosterCard>(f.slug)?.let { card ->
                        card.watchers(badgesFor(f))
                        card.subline?.text = noteFor(f) ?: subline(lbTitle(f, null))
                    }
                }
                renderMeta(visible.size)
                return
            }
            drawnFor = drawing
            val keepFocus = overlay.findFocus()?.takeIf { rows.hasFocus() }?.tag as? String
            rows.removeAllViews()
            renderMeta(visible.size)
            if (visible.isEmpty()) { rows.addView(note("Every film here is hidden by the filter.").apply { setPadding(pageStart, Cinema.px(activity, 40), 0, 0) }); return }
            // Each film's place in the order shown, so a sorted list counts from one as it is seen.
            val place = visible.withIndex().associate { it.value.slug to it.index + 1 }
            // Twelve to a row, each row headed by its places ("1–12"), so a long list stays easy to move through.
            visible.chunked(12).forEachIndexed { r, chunk ->
                val from = r * 12 + 1
                lbFilmRow(if (visible.size > 12) "$from–${from + chunk.size - 1}" else "In order", null, chunk, gen, into = rows,
                    line = { f -> noteFor(f) }, chip = { f -> place[f.slug]?.toString() }, badges = { f -> badgesFor(f) }, onShown = { loadWatchesFor(it) })
            }
            keepFocus?.let { refocus(it, 0) }
        }

        /** Redraws soon, so a burst of results that arrive together makes one redraw, not one each. */
        fun renderSoon() {
            if (renderQueued) return
            renderQueued = true
            // While ratings are still arriving in "Highest rated" order, each one can move films: redraw less often, so the list holds still.
            val settling = order == LbOrder.RATED && ratingsDone < films.size
            ui.postDelayed({ renderQueued = false; render() }, if (settling) 2500L else 800L)
        }

        onWatchArrived = { renderSoon() }

        /** A control has changed: every control shows its state again, and the list is drawn to match. */
        fun change() {
            refreshers.forEach { it() }
            // A filter on watched films needs every film's watch status, not only the rows that have been built.
            if (hide != LbHide.NONE) loadWatchesFor(films)
            render()
        }

        /** Names and pictures of the followed members, for the account chips and the badges. */
        fun loadProfiles() {
            members.forEach { u ->
                lb.load("/$u/", 2 * H, { Lb.profile(it, u) }, stillHere) { p ->
                    if (gen != generation || p == null) return@load
                    lbNames[u.lowercase()] = p.name.substringBefore(' ').ifBlank { u }
                    accountChips[u]?.text = lbName(u)
                    p.avatar?.let { a -> images.loadPriority(a, maxWidth = Cinema.px(activity, 96), tag = pageTag) { b -> if (gen == generation) { pictures[u] = b; renderSoon() } } }
                }
            }
        }

        /** Each film's average rating, from its rating chart (a small page): what the list is put in order by. */
        fun loadRatings() {
            if (ratingsStarted || !loaded) return
            ratingsStarted = true
            films.forEach { f ->
                lb.load("/csi/film/${f.slug}/rating-histogram/", 7 * 24 * H, { Lb.histogram(it) }, stillHere, background = true) { counts ->
                    if (gen != generation) return@load
                    ratingsDone++
                    val average = counts?.let { Lb.average(it) }
                    if (average != null) ratings[f.slug] = average
                    renderSoon()
                }
            }
        }

        fun chipStrip() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        fun chipAt(strip: LinearLayout) = LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)).apply { if (strip.childCount > 0) marginStart = Cinema.px(activity, 14) }

        /** A labelled row of controls: [label] at its left, then the chips in [strip], which scrolls if they run out. */
        fun addControlRow(label: String, strip: LinearLayout): LinearLayout {
            // The label sits over its choices, which run along the full width: no gap between a short label and its chips.
            val line = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            line.addView(Cinema.text(activity, label, 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 4) })
            line.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(strip) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10) })
            controls.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 22) })
            return line
        }

        /** A row of choices, one in force at a time: [current] says which, and [pick] takes a choice (then the list is drawn). */
        fun <T> choiceRow(label: String, options: List<Pair<String, T>>, current: () -> T, pick: (T) -> Unit) {
            val strip = chipStrip()
            options.forEach { (text, value) ->
                val chip = lbChip(text).apply { setOnClickListener { pick(value); change() } }
                refreshers += { chip.isSelected = current() == value }
                strip.addView(chip, chipAt(strip))
            }
            addControlRow(label, strip)
        }

        choiceRow("Order", listOf("List order" to LbOrder.LIST, "Highest rated" to LbOrder.RATED), { order }) { order = it; loadRatings() }
        if (members.isNotEmpty()) {
            choiceRow("Hide films watched by", listOf("Nobody" to LbHide.NONE, "Any of these" to LbHide.ANY, "All of these" to LbHide.ALL), { hide }) { hide = it }
            // The accounts a filter counts, shown only while a filter is on.
            val strip = chipStrip()
            members.forEach { u ->
                val chip = lbChip(lbName(u)).apply { setOnClickListener { if (!picked.add(u)) picked.remove(u); change() } }
                accountChips[u] = chip
                refreshers += { chip.isSelected = u in picked }
                strip.addView(chip, chipAt(strip))
            }
            val accounts = addControlRow("Accounts", strip)
            refreshers += { accounts.visibility = if (hide == LbHide.NONE) View.GONE else View.VISIBLE }
        }
        refreshers.forEach { it() }

        loadProfiles()
        lb.load(path, H, { Lb.listPage(it, path) }, stillHere) { l ->
            if (gen != generation) return@load
            if (l == null) { rows.removeAllViews(); rows.addView(note("Couldn't load this list just now").apply { setPadding(pageStart, Cinema.px(activity, 40), 0, 0) }); return@load }
            ((header.getChildAt(1) as? TextView))?.let { t -> t.text = l.name; Cinema.style(t, when { l.name.length > 30 -> 64; l.name.length > 16 -> 84; else -> 104 }, Cinema.CREAM, serif = true, tracking = -0.02f) }
            listCount = l.count
            l.description?.let { desc.text = it; desc.visibility = View.VISIBLE }
            films = l.films()
            loaded = true
            render()
            loadWatchesFor(films.take(12))
            if (order == LbOrder.RATED) loadRatings()
            // The fan's five first, ahead of the rows' posters, which queue on the shelf workers.
            val first = films.take(5)
            slowPool.execute {
                val posters = lbPosters(first)
                activity.runOnUiThread { if (gen == generation) first.forEachIndexed { k, f -> posters[f.slug]?.let { u -> images.load(u, maxWidth = 400, tag = pageTag) { b -> fan.posters[k].setImageBitmap(b) } } } }
            }
        }
    }

    private fun com.danielportelabyrne.cinema.data.letterboxd.LbList.films() = previews

    /** Words too common to find a list by: a phrase is searched by the rest of its words. */
    private val lbStopWords = setOf("the", "and", "for", "you", "your", "want", "when", "what", "that", "this", "with", "from", "are", "was", "its", "all", "any", "some", "just", "like", "have", "not", "but", "out", "into")

    /** The words of a list-search phrase that are worth searching for on their own (three letters or more, not common). */
    private fun keywordsOf(q: String): List<String> =
        q.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in lbStopWords }.distinct()

    /** Typed text on the Letterboxd search and follow pages. */
    private var lbTyped = ""

    /**
     * List search (`C`): the keyboard on the left, the best matches on the right as they arrive, every match in a row
     * beneath, and with nothing typed, the lists of the people followed here.
     */
    /**
     * The live parts of a page with the search keyboard, which [keyboardSearchPage] builds: the Letterboxd lists search and
     * the services search are both this page with different results. [typed] is the query. [side] is beside the keyboard,
     * [below] under it. Set [onIdle] (nothing typed yet: recent searches, suggestions) and [onQuery] (a query to run),
     * then call [start].
     */
    private class KbSearch(val field: SearchField, val side: LinearLayout, val below: LinearLayout) {
        var typed = ""
        var onIdle: () -> Unit = {}
        var onQuery: (String) -> Unit = {}
        var focusFirst: () -> Unit = {}

        /** Runs [q]: too short a query shows the idle side. */
        fun run(q: String) { if (q.length < 2) onIdle() else onQuery(q) }

        /** Searches [term] as if it had been typed (a recent search, a suggestion). */
        fun search(term: String) { typed = term; field.render(term, null); run(term) }

        fun clear() { side.removeAllViews(); below.removeAllViews() }

        fun start() { onIdle(); focusFirst() }
    }

    /**
     * A search page with the in-app QWERTY keyboard, the same search bar as the global search (SearchField: [lead] is its
     * icon, then the query, a phone to type from and the microphone), a phone that types into it and voice that fills it.
     * Typing runs [KbSearch.onQuery] once it pauses. The caller fills [KbSearch.side] and [KbSearch.below].
     */
    private fun keyboardSearchPage(lead: View, prompt: String, phoneId: String, phoneLabel: String): KbSearch {
        val stage = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        content.addView(stage, LinearLayout.LayoutParams(-1, Cinema.px(activity, 900)))
        val left = Cinema.px(activity, 176)
        fun place(v: View, x: Int, y: Int, w: Int = -2, h: Int = -2) = stage.addView(v, FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
        val field = SearchField(activity, lead, prompt = prompt, hintText = "")
        place(field, left, Cinema.px(activity, 12), activity.resources.displayMetrics.widthPixels - left - pageEnd, Cinema.px(activity, 112))
        val typeToSearch = Cinema.eyebrow(activity, listOf("Type to search"), color = Cinema.DIM, markAlpha = 0.5f)
        place(typeToSearch, left, Cinema.px(activity, 170))
        val side = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        place(side, Cinema.px(activity, 1232), Cinema.px(activity, 170), Cinema.px(activity, 592))
        val below = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(below, LinearLayout.LayoutParams(-1, -2))
        val kb = KbSearch(field, side, below)
        var pending: Runnable? = null
        fun runSoon() {
            pending?.let { ui.removeCallbacks(it) }
            pending = Runnable { kb.run(kb.typed.trim()) }.also { ui.postDelayed(it, 450) }
        }
        val keyboard = QwertyKeyboard(activity) { key ->
            kb.typed = Qwerty.type(kb.typed, key)
            field.render(kb.typed, null)
            runSoon()
        }
        keyboard.onLeave = { leaveKeyboard(field) }
        keyboard.upTarget = { searchBarControl(field) }
        place(keyboard, left, Cinema.px(activity, 240), Cinema.px(activity, 980), Cinema.px(activity, 476))
        // Typing from a phone: the full text it sends is the query, live as it is typed.
        val phone = phoneTargetFor(phoneId, phoneLabel, Qwerty.MAX_LENGTH, { kb.typed }, { text ->
            kb.typed = text.take(Qwerty.MAX_LENGTH)
            field.render(kb.typed, null)
            runSoon()
        }, { pending?.let { ui.removeCallbacks(it) }; kb.run(kb.typed.trim()) })
        // Speaking the query: the words said replace it, and the search runs when they are done.
        // The same listening screen as the global search: the keyboard gives way to it while the microphone listens.
        val listening = VoiceListeningView(activity).apply { visibility = View.GONE }
        stage.addView(listening, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = left; rightMargin = pageEnd; topMargin = Cinema.px(activity, 230) })
        fun showListening(partial: String, level: Float, hint: String) {
            keyboard.visibility = View.INVISIBLE; side.visibility = View.INVISIBLE; below.visibility = View.INVISIBLE; typeToSearch.visibility = View.INVISIBLE; listening.visibility = View.VISIBLE
            field.render(kb.typed, partial)
            listening.update(partial, level, hint)
        }
        fun showKeyboard() {
            keyboard.visibility = View.VISIBLE; side.visibility = View.VISIBLE; below.visibility = View.VISIBLE; typeToSearch.visibility = View.VISIBLE; listening.visibility = View.GONE
            field.render(kb.typed, null)
        }
        fun startSpeech() {
            voiceSink = { state ->
                when (state) {
                    is VoiceState.Listening -> showListening(state.partial, state.level, "Speak now · Back to cancel")
                    VoiceState.Requesting -> showListening("", 0f, "Allow the microphone to search by voice")
                    is VoiceState.Done -> {
                        voiceSink = null
                        showKeyboard()
                        kb.typed = state.text.take(Qwerty.MAX_LENGTH)
                        field.render(kb.typed, null)
                        kb.run(kb.typed.trim())
                    }
                    is VoiceState.Unavailable -> {
                        voiceSink = null
                        showKeyboard()
                        field.query.text = state.reason; field.query.setTextColor(Cinema.MUTED)
                    }
                    VoiceState.Idle -> { voiceSink = null; showKeyboard() }
                    else -> Unit
                }
            }
            ensureVoice().start()
        }
        // The field's icons, as on the global search: a phone to type from, and the microphone to speak with.
        field.onPhone = { PhoneInput.toggleQr() }
        usePhoneTarget(field, phone)
        field.onMic = { startSpeech() }
        kb.focusFirst = { keyboard.keys.firstOrNull { it.contentDescription == "q" }?.let { requestFocusRobust(overlay as ViewGroup, it) } }
        return kb
    }

    private fun buildLbSearch(gen: Int) {
        // The same search bar as the global search (SearchField), led by the Letterboxd mark and the word "Lists".
        val lead = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lead.addView(Cinema.icon(activity, R.drawable.ic_cin_search, Cinema.SAFFRON), LinearLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 40)))
        lead.addView(com.danielportelabyrne.cinema.design.cinema.LbDots.view(activity, 12), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 22) })
        lead.addView(Cinema.text(activity, "Lists", 20, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 16) })
        val kb = keyboardSearchPage(lead, "Search Letterboxd lists", "lb-search", "Search lists")
        val side = kb.side; val below = kb.below
        fun showResults(q: String, lists: List<com.danielportelabyrne.cinema.data.letterboxd.LbList>?) {
            kb.clear()
            side.addView(Cinema.text(activity, if (lists == null) "Searching for “$q”…" else "Lists matching “$q”", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (lists == null) return
            if (lists.isEmpty()) { side.addView(note("No lists found"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) }); return }
            saveRecentSearches(SearchKeyboard.LISTS, RecentSearches.add(recentSearches(SearchKeyboard.LISTS), q))
            lists.take(3).forEach { l ->
                val card = GlowCard(activity, 26f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 22), 0, Cinema.px(activity, 22), 0); contentDescription = l.name }
                val mini = ListCard(activity, posterW = 66, step = 30, widthMock = 186).apply { isFocusable = false; name.visibility = View.GONE; by.visibility = View.GONE }
                card.addView(mini, LinearLayout.LayoutParams(Cinema.px(activity, 186), Cinema.px(activity, 99)))
                val t = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                t.addView(Cinema.text(activity, l.name, 28, Cinema.CREAM, serif = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                t.addView(Cinema.text(activity, listOfNotNull(l.ownerName ?: l.owner, l.count?.let { "$it films" }, l.likes?.let { "♥ $it" }).joinToString("  ·  "), 18, Cinema.MUTED).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 6) })
                card.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 20) })
                card.setOnClickListener { go(Dest.LbListPage(l.path), remember = true) }
                side.addView(card, LinearLayout.LayoutParams(-1, Cinema.px(activity, 140)).apply { topMargin = Cinema.px(activity, 18) })
                shelfPool.execute { val p = lbPosters(l.previews.take(5)); activity.runOnUiThread { l.previews.take(5).forEachIndexed { k, f -> p[f.slug]?.let { u -> images.load(u, maxWidth = 140, tag = pageTag) { b -> mini.posters[k].setImageBitmap(b) } } } } }
            }
            lbListRow("All matching lists", "${lists.size}", lists, gen, into = below)
        }
        fun showIdle() {
            kb.clear()
            addRecent(SearchKeyboard.LISTS, side, search = { term -> kb.search(term) }, refresh = { showIdle() })
            side.addView(Cinema.text(activity, "Try a theme, a director or a mood", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true))
            listOf("a24", "comfort", "horror", "ghibli", "noir").forEachIndexed { i, s ->
                side.addView(pill(s, accent = false).apply { setOnClickListener { kb.search(s) } },
                    LinearLayout.LayoutParams(-2, Cinema.px(activity, 54)).apply { topMargin = Cinema.px(activity, if (i == 0) 22 else 12) })
            }
            // Lists by the people followed here.
            val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            below.addView(holder)
            val all = java.util.concurrent.ConcurrentHashMap<String, List<com.danielportelabyrne.cinema.data.letterboxd.LbList>>()
            lb.following.forEach { u ->
                lb.load("/$u/lists/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }) { l ->
                    if (gen != generation || kb.typed.isNotBlank() || l == null) return@load
                    all[u] = l.map { it.copy(ownerName = it.ownerName ?: lbName(u)) }
                    holder.removeAllViews(); lbListRow("Lists by the people you follow", null, all.values.flatten(), gen, into = holder)
                }
            }
        }
        fun listSearch(term: String, current: () -> Boolean, done: (List<com.danielportelabyrne.cinema.data.letterboxd.LbList>?) -> Unit) {
            // A query the owner has typed past is dropped: its page is not fetched, or its browser load stops, so the latest one is not held up.
            // Letterboxd's search page arrives with an empty results table and fills it by script from /s/search/..., after the page
            // has loaded, so reading the page gave "No lists found". The /s/ address returns the results themselves.
            lb.load("/s/search/lists/${java.net.URLEncoder.encode(term, "UTF-8").replace("+", "%20")}/", 24 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR,
                { Lb.lists(it) }, stillWanted = current) { l -> if (current()) done(l) }
        }
        // A phrase that matches no list at once: each of its words is searched, and the lists that match the most of them come first.
        fun searchKeywords(q: String, words: List<String>, current: () -> Boolean) {
            val hits = LinkedHashMap<String, Pair<com.danielportelabyrne.cinema.data.letterboxd.LbList, MutableSet<String>>>()
            var waiting = words.size
            words.forEach { word ->
                listSearch(word, current) { found ->
                    found?.forEach { list -> hits.getOrPut(list.path) { list to mutableSetOf() }.second.add(word) }
                    if (--waiting == 0 && current()) showResults(q, hits.values.sortedByDescending { it.second.size }.map { it.first })
                }
            }
        }
        fun runQuery(q: String) {
            showResults(q, null)
            val current = { gen == generation && kb.typed.trim() == q }
            listSearch(q, current) { l ->
                val words = keywordsOf(q)
                if (l.isNullOrEmpty() && words.size > 1) searchKeywords(q, words, current) else showResults(q, l.orEmpty())
            }
        }
        kb.onIdle = { showIdle() }
        kb.onQuery = { q -> runQuery(q) }
        kb.start()
    }

    /** The same round phone button as the search bar and every field, so they look and light up alike. Types from a phone. */
    private fun phoneIconButton(onClick: () -> Unit): View =
        SearchField.phoneButton(activity).apply { setOnClickListener { onClick() } }

    /** A phone target whose behaviour is given as closures, for the search screens. */
    private fun phoneTargetFor(
        id: String, name: String, max: Int, current: () -> String, apply: (String) -> Unit, done: () -> Unit,
    ): PhoneTextTarget = object : PhoneTextTarget {
        override val fieldId = id
        override val label = name
        override val maxLength = max
        override fun currentText() = current()
        override fun applyFromPhone(text: String) = apply(text)
        override fun phoneDone() = done()
    }

    private var phoneTarget: PhoneTextTarget? = null

    /**
     * Makes [target] the field a connected phone types into, now that this search is on screen. [field] (when the screen
     * has a search bar) passes on text the TV's keyboard or microphone types, so the phone's box follows.
     */
    private fun usePhoneTarget(field: SearchField?, target: PhoneTextTarget) {
        closePhoneTyping()
        phoneTarget = target
        field?.phoneTarget = target
        PhoneInput.focus(target)
    }

    /** The search that was using the phone is leaving: let the phone go, and take its code card away. */
    private fun closePhoneTyping() {
        phoneTarget?.let { PhoneInput.blur(it) }
        phoneTarget = null
        PhoneInput.hideQr()
    }

    /**
     * Follow someone (`F`): type a username and their profile appears beside the keyboard, favourites and all, with
     * Follow. Beneath, the people the followed members follow, to pick from without typing.
     */
    private fun buildLbFollow(gen: Int) {
        lbTyped = ""
        val stage = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        content.addView(stage, LinearLayout.LayoutParams(-1, Cinema.px(activity, 800)))
        val left = Cinema.px(activity, 176)
        fun place(v: View, x: Int, y: Int, w: Int = -2, h: Int = -2) = stage.addView(v, FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
        place(lbHeader(listOf("Follow someone"), "Who should we follow?", 76), left, 0)
        val field = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(activity, 42), 0, Cinema.px(activity, 42), 0); background = Cinema.glass(activity, 56f, 0x17FFF2DE)
        }
        field.addView(Cinema.text(activity, "letterboxd.com/", 30, Cinema.DIM))
        val typed = Cinema.text(activity, "", 36, Cinema.CREAM).apply { maxLines = 1 }
        field.addView(typed, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 4) })
        place(field, left, Cinema.px(activity, 170), Cinema.px(activity, 980), Cinema.px(activity, 112))
        val preview = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 40), Cinema.px(activity, 36), Cinema.px(activity, 40), Cinema.px(activity, 36))
            background = Cinema.glass(activity, 34f); clipChildren = false; clipToPadding = false; visibility = View.INVISIBLE
        }
        place(preview, Cinema.px(activity, 1240), Cinema.px(activity, 170), Cinema.px(activity, 584))
        var pending: Runnable? = null
        fun showPreview(user: String) {
            preview.visibility = View.VISIBLE; preview.removeAllViews()
            preview.addView(Cinema.text(activity, "Looking for @$user…", 17, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true))
            lb.load("/$user/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { html -> if (lb.missing(html)) null else com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(html, user) },
                stillWanted = { gen == generation && lbTyped == user }) { p ->
                if (gen != generation || lbTyped != user) return@load
                preview.removeAllViews()
                if (p == null) { preview.addView(Cinema.text(activity, "No member called @$user", 24, Cinema.MUTED)); return@load }
                preview.addView(Cinema.text(activity, "Found on Letterboxd", 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true))
                val who = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
                val av = com.danielportelabyrne.cinema.design.cinema.AvatarView(activity, 120).apply { set(p.name, null) }
                p.avatar?.let { a -> images.load(a, maxWidth = 240, tag = pageTag) { b -> av.set(p.name, b) } }
                who.addView(av, LinearLayout.LayoutParams(Cinema.px(activity, 120), Cinema.px(activity, 120)))
                val nm = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                nm.addView(Cinema.title(activity, p.name, 52, maxLines = 1))
                nm.addView(Cinema.text(activity, listOfNotNull("@$user", p.stat("Films")?.let { "$it films" }).joinToString(" · "), 20, Cinema.DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 6) })
                who.addView(nm, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 26) })
                preview.addView(who, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 24) })
                if (p.favourites.isNotEmpty()) {
                    preview.addView(Cinema.text(activity, "Favourites", 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
                    val favs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    preview.addView(favs, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
                    shelfPool.execute {
                        val posters = lbPosters(p.favourites)
                        activity.runOnUiThread {
                            p.favourites.forEachIndexed { k, f ->
                                val v = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                                    background = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 10); setColor(Cinema.PLACEHOLDER) }
                                    outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 10)) } } }
                                posters[f.slug]?.let { u -> images.load(u, maxWidth = 240, tag = pageTag) { b -> v.setImageBitmap(b) } }
                                favs.addView(v, LinearLayout.LayoutParams(Cinema.px(activity, 118), Cinema.px(activity, 177)).apply { if (k > 0) marginStart = Cinema.px(activity, 14) })
                            }
                        }
                    }
                }
                val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                val already = lb.following.any { it.equals(user, true) }
                buttons.addView(CinemaButton(activity, CinemaButton.Kind.PRIMARY, if (already) "Already following" else "Follow", null).apply {
                    isEnabled = !already
                    setOnClickListener { lb.follow(user); lbNames[user.lowercase()] = p.name.substringBefore(' '); CinemaToast.show(activity, "Following ${p.name}", Toast.LENGTH_SHORT); current = Dest.Letterboxd; trail.clear(); styleTabs(); build() }
                }, LinearLayout.LayoutParams(-2, Cinema.px(activity, 76)))
                preview.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 34) })
            }
        }
        val keyboard = QwertyKeyboard(activity) { key ->
            // Usernames have no spaces: Space types an underscore, which they may contain.
            lbTyped = (if (key == Qwerty.Key.Space) Qwerty.type(lbTyped, Qwerty.Key.Char('_')) else Qwerty.type(lbTyped, key)).take(30)
            typed.text = lbTyped
            phoneTarget?.let { PhoneInput.textChanged(it) }
            pending?.let { ui.removeCallbacks(it) }
            if (lbTyped.length >= 2) pending = Runnable { showPreview(lbTyped) }.also { ui.postDelayed(it, 900) } else preview.visibility = View.INVISIBLE
        }
        place(keyboard, left, Cinema.px(activity, 320), Cinema.px(activity, 980), Cinema.px(activity, 476))
        // Typing from a phone: the username typed there is the one looked up here, live. Usernames have no spaces, so a
        // space becomes an underscore as on the keyboard; Done turns a pasted profile link or @name into the username.
        val followPhoneTarget = phoneTargetFor("follow-username", "Letterboxd username", 30, { lbTyped }, { text ->
            lbTyped = text.lowercase().replace(' ', '_').take(30)
            typed.text = lbTyped
            pending?.let { ui.removeCallbacks(it) }
            if (lbTyped.length >= 2) pending = Runnable { showPreview(lbTyped) }.also { ui.postDelayed(it, 500) } else preview.visibility = View.INVISIBLE
        }, {
            com.danielportelabyrne.cinema.data.letterboxd.FollowPage.normalize(lbTyped)?.let { lbTyped = it.take(30); typed.text = lbTyped }
            pending?.let { ui.removeCallbacks(it) }
            if (lbTyped.length >= 2) showPreview(lbTyped)
        })
        usePhoneTarget(null, followPhoneTarget)
        val followPhone = phoneIconButton { PhoneInput.toggleQr() }
        keyboard.upTarget = { followPhone }
        place(followPhone, Cinema.px(activity, 1240), Cinema.px(activity, 0), Cinema.px(activity, 84), Cinema.px(activity, 84))
        // People the followed members follow, to choose without typing.
        val suggestions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(suggestions, LinearLayout.LayoutParams(-1, -2))
        lb.following.firstOrNull()?.let { u ->
            lb.load("/$u/following/", 12 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.members(it) }) { m ->
                if (gen != generation || m.isNullOrEmpty()) return@load
                suggestions.removeAllViews()
                val r = row("People ${lbName(u)} follows", null, into = suggestions)
                // The heading names them properly once their profile (usually saved already) is read.
                if (lbNames[u.lowercase()] == null) lb.load("/$u/", 24 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, u) }) { p ->
                    p ?: return@load; lbNames[u.lowercase()] = p.name.substringBefore(' ')
                    (r.heading?.let { h -> (h as? ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) }.filterIsInstance<TextView>().firstOrNull() } })?.text = "People ${lbName(u)} follows"
                }
                m.filterNot { x -> lb.following.any { it.equals(x.user, true) } }.take(20).forEachIndexed { i, x ->
                    val chip = GlowCard(activity, 40f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 14), 0, Cinema.px(activity, 28), 0); contentDescription = x.name }
                    val av = com.danielportelabyrne.cinema.design.cinema.AvatarView(activity, 56).apply { set(x.name, null) }
                    x.avatar?.let { a -> images.load(a, maxWidth = 112, tag = pageTag) { b -> av.set(x.name, b) } }
                    chip.addView(av, LinearLayout.LayoutParams(Cinema.px(activity, 56), Cinema.px(activity, 56)))
                    chip.addView(Cinema.text(activity, x.name, 22, Cinema.CREAM, weight = 500).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 14) })
                    chip.setOnClickListener { lbTyped = x.user; typed.text = x.user; showPreview(x.user) }
                    chip.onFocus = { lit -> if (lit) reveal(chip) }
                    r.inner.addView(chip, LinearLayout.LayoutParams(-2, Cinema.px(activity, 80)).apply { if (i > 0) marginStart = Cinema.px(activity, 18) })
                }
            }
        }
        keyboard.keys.firstOrNull { it.contentDescription == "q" }?.let { requestFocusRobust(overlay as ViewGroup, it) }
    }

    /** "2906266" -> "2.9M", "412000" -> "412K". */
    private fun lbCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.UK, "%.1fM", n / 1e6).replace(".0M", "M")
        n >= 1_000 -> "${n / 1000}K"
        else -> "$n"
    }

    /**
     * "On Letterboxd" on a film's page (`E`): the average with its chart, what the followed members made of it, and
     * the reviews people are liking this week. It appears once its page has been read; nothing shows for a title
     * Letterboxd does not know.
     */
    private fun lbFilmSection(column: LinearLayout, e: Entry) {
        if (!integrationOn(Integration.LETTERBOXD)) return
        val t = e.title
        val slugHint = t.key.removePrefix("lb:").takeIf { t.key.startsWith("lb:") }
        val tmdb = t.tmdbId?.toLongOrNull()
        if (slugHint == null && (tmdb == null || t.kind == Kind.SHOW)) return   // Letterboxd is about films
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; visibility = View.GONE }
        column.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 64) })
        val H = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR
        slowPool.execute {
            val info = lb.pageNow(slugHint?.let { "/film/$it/" } ?: "/tmdb/$tmdb/", 72 * H)?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.film(it) } ?: return@execute
            activity.runOnUiThread {
                if (!detailOpen || !box.isAttachedToWindow) return@runOnUiThread
                box.visibility = View.VISIBLE
                val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                head.addView(Cinema.rowHeading(activity, "On Letterboxd", sizePx = 46))
                head.addView(com.danielportelabyrne.cinema.design.cinema.LbDots.view(activity, 12), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 18) })
                box.addView(head)
                val panels = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                box.addView(panels, LinearLayout.LayoutParams(-1, Cinema.px(activity, 250)).apply { topMargin = Cinema.px(activity, 28) })

                // The average, its chart, and how many have rated and reviewed it.
                val avg = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 34), Cinema.px(activity, 30), Cinema.px(activity, 34), 0); background = Cinema.glass(activity, 30f) }
                avg.addView(Cinema.text(activity, "Average rating", 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true))
                val figures = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
                // The film's page does not always give its average; then it is worked out from the chart once that arrives.
                val figure = Cinema.text(activity, info.average?.let { String.format(Locale.UK, "%.1f", it) } ?: "–", 96, Cinema.CREAM, serif = true)
                figures.addView(figure)
                val chart = com.danielportelabyrne.cinema.design.cinema.LbHistogram(activity)
                figures.addView(chart, LinearLayout.LayoutParams(Cinema.px(activity, 260), Cinema.px(activity, 90)).apply { marginStart = Cinema.px(activity, 26); bottomMargin = Cinema.px(activity, 14) })
                avg.addView(figures, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 6) })
                avg.addView(Cinema.text(activity, listOfNotNull(info.ratings?.let { "from ${lbCount(it)} ratings" }, info.reviews?.let { "${lbCount(it)} reviews" }).joinToString(" · "), 19, Cinema.MUTED),
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 8) })
                panels.addView(avg, LinearLayout.LayoutParams(Cinema.px(activity, 520), -1))
                lb.load("/csi/film/${info.slug}/rating-histogram/", 24 * H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.histogram(it) }) { c ->
                    c?.let {
                        chart.counts = it
                        if (info.average == null) com.danielportelabyrne.cinema.data.letterboxd.Lb.average(it)?.let { a -> figure.text = String.format(Locale.UK, "%.1f", a) }
                    }
                }

                // The followed members: their stars, a like, and the start of their review, or that they have not logged it.
                val circle = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(Cinema.px(activity, 34), Cinema.px(activity, 30), Cinema.px(activity, 34), 0); background = Cinema.glass(activity, 30f); clipChildren = false }
                circle.addView(Cinema.text(activity, "Your circle", 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true))
                val people = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                circle.addView(people, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 22) })
                if (lb.following.isNotEmpty()) panels.addView(circle, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = Cinema.px(activity, 40) })
                lb.following.forEachIndexed { i, u ->
                    val person = GlowCard(activity, 24f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 14), Cinema.px(activity, 10), Cinema.px(activity, 24), Cinema.px(activity, 10)); background = null }
                    val av = com.danielportelabyrne.cinema.design.cinema.AvatarView(activity, 84).apply { set(lbName(u), null) }
                    person.addView(av, LinearLayout.LayoutParams(Cinema.px(activity, 84), Cinema.px(activity, 84)))
                    val lines = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    val nameView = Cinema.text(activity, lbName(u), 26, Cinema.CREAM, weight = 600)
                    val starsView = Cinema.text(activity, "…", 24, Cinema.SAFFRON, tracking = 0.06f)
                    val said = Cinema.text(activity, "", 18, Cinema.DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
                    lines.addView(nameView); lines.addView(starsView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 2) }); lines.addView(said, LinearLayout.LayoutParams(Cinema.px(activity, 330), -2))
                    person.addView(lines, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 20) })
                    person.setOnClickListener { closeDetail(restoreFocus = false); go(Dest.LbPerson(u), remember = true) }
                    people.addView(person, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(activity, 30) })
                    lb.load("/$u/", 2 * H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, u) }) { p ->
                        p ?: return@load
                        lbNames[u.lowercase()] = p.name.substringBefore(' ').ifBlank { u }; nameView.text = lbName(u); av.set(lbName(u), null)
                        p.avatar?.let { a -> images.load(a, maxWidth = 168, tag = pageTag) { b -> av.set(lbName(u), b) } }
                    }
                    lb.load("/$u/film/${info.slug}/", 6 * H, { h -> Lb.member(h, u) }) { m ->
                        if (m == null || (m.rating == null && m.review == null && !m.liked)) { starsView.text = "Not logged yet"; starsView.setTextColor(Cinema.MUTED); Cinema.style(starsView, 22, Cinema.MUTED); return@load }
                        starsView.text = android.text.SpannableStringBuilder(m.rating?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it) } ?: "Watched").apply {
                            if (m.liked) { val at = length; append("  ♥"); setSpan(android.text.style.ForegroundColorSpan(0xFFFF6F61.toInt()), at, length, 0) }
                        }
                        said.text = m.review?.let { "“$it”" }.orEmpty()
                    }
                }

                // What people are reading about it this week.
                val reviews = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
                box.addView(reviews, LinearLayout.LayoutParams(-1, -2))
                lb.load("/film/${info.slug}/reviews/by/activity/", 24 * H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.reviews(it) }) { r ->
                    if (!detailOpen || r.isNullOrEmpty()) return@load
                    reviews.removeAllViews(); lbReviewRow("Popular reviews", r.take(12), generation, into = reviews, inset = false)
                }
            }
        }
    }

    // --- Picked for you ----------------------------------------------------------------------------

    /** A pick as a page title: TMDB's poster for the row, its full-size backdrop for the featured title. */
    private fun pickTitle(p: com.danielportelabyrne.cinema.data.movies.Pick): Title {
        val s = p.suggestion
        return Title(
            key = "tmdb:" + com.danielportelabyrne.cinema.data.movies.Picks.key(s.kind, s.tmdbId), kind = s.kind, name = s.name, year = s.year,
            overview = s.overview, runtimeMin = null, genres = s.genres, age = null, imdbScore = null,
            posterUrl = s.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
            backdropUrl = s.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
            imdbId = null, tmdbId = s.tmdbId.toString()
        )
    }

    /**
     * "Picked for you" on Home: titles recommended from what has been downloaded, watched and streamed, each saying
     * why. The last picks show at once; fresh ones replace them when they are due. The top three also become the
     * featured titles, in place of what is trending (which stays until there are picks).
     */
    // --- Personal shelves on every page ---------------------------------------------------------------

    /** Genres and services learned for titles, so a page can tell which of the viewer's own titles belong on it. */
    private val titleFacts by lazy { TitleFactsStore(AndroidPlatform(activity)) }
    /** When each title's details were last asked for, so a page never asks for the same title again for a while. */
    private val factsTried = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** One lookup at a time, at low priority: a page's unknown titles never crowd out its own rows. */
    private val factsPool = Executors.newSingleThreadExecutor { r -> Thread(r, "title-facts").apply { priority = Thread.MIN_PRIORITY; isDaemon = true } }
    private val flushFacts = Runnable { onFactsPool { titleFacts.flush() } }

    /** Runs [work] on the details thread; nothing once the panel has closed it. */
    private fun onFactsPool(work: () -> Unit) {
        try { factsPool.execute(work) } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    /** Continue watching, Favourites and Recently watched, in that order. Each hides itself when this page has none. */
    private fun personalRows(gen: Int) { continueRow(gen); favouritesRow(gen); recentRow(gen) }

    /** Which of the viewer's titles a page shows; null for a page without the personal shelves. */
    private fun personalPage(d: Dest = current): PersonalPage? = when (d) {
        Dest.Home -> PersonalPage.Home
        Dest.Films -> PersonalPage.Films
        Dest.Series -> PersonalPage.Series
        Dest.Docs -> PersonalPage.Documentaries
        is Dest.InGenre -> PersonalPage.InGenre(d.genre.code)
        is Dest.OnService -> PersonalPage.OnService(d.service.label)
        Dest.Library -> PersonalPage.Library
        else -> null
    }

    /** The filter for this page from what is known now. Never fetches; safe on any thread. */
    private fun personalFilter(page: PersonalPage? = personalPage()): PersonalFilter {
        if (page == null || page == PersonalPage.Home) return PersonalFilter.HOME
        // The library counts only once this user's library is actually held: until then, nothing is known to be in it.
        val library = if (jellyfin.isConnected && (jellyfin.libraryLoadedAt != 0L || jellyfin.loadedLibrary.isNotEmpty())) jellyfin.loadedLibrary else null
        return PersonalFilter(page, titleFacts.all(), library)
    }

    /** Keeps what a catalogue row said about its titles; written to disk a few seconds later, once for many rows. */
    private fun learnFacts(learned: List<TitleFacts>, addServices: Boolean = false) {
        if (!titleFacts.learn(learned, addServices)) return
        ui.removeCallbacks(flushFacts); ui.postDelayed(flushFacts, FACTS_FLUSH_MS)
    }

    /**
     * Asks, in the background, about titles a page could not place (their genres or services are not known yet): Seerr
     * for a title with a TMDB id, Cinemeta for a film known by IMDb id. Each title at most once every [FACTS_RETRY_MS].
     * When anything was learned, the page's personal shelves are brought up to date in place.
     */
    private fun warmFacts(filter: PersonalFilter, gen: Int) {
        if (filter.page == PersonalPage.Home || filter.page == PersonalPage.Library) return   // the library page waits for the library instead
        val now = System.currentTimeMillis()
        val due = filter.unknown.filter { (kind, ref) ->
            (ref.tmdbId != null && seerr.isSignedIn) || (ref.imdbId != null && kind == Kind.MOVIE)
        }.filter { (kind, ref) -> factsTried.put("$kind|${ref.tmdbId}|${ref.imdbId}", now)?.let { now - it < FACTS_RETRY_MS } != true }
            .take(FACTS_PER_PAGE)
        if (due.isEmpty()) return
        onFactsPool {
            var learned = false
            for ((kind, ref) in due) {
                if (gen != generation) return@onFactsPool
                val f = try {
                    val tmdb = ref.tmdbId?.toLongOrNull()
                    if (tmdb != null && seerr.isSignedIn) seerr.titleFacts(kind, tmdb)
                    else ref.imdbId?.let { imdb ->
                        // Cinemeta knows a film's genres, not where it streams: services stay unknown.
                        val t = stremio.filmPage(com.danielportelabyrne.cinema.data.stremio.StremioFilm(imdb, "", null, null, false, ""))
                        TitleFacts(kind, ref.tmdbId ?: t.tmdbId, imdb, null, t.genres.ifEmpty { null }, null, System.currentTimeMillis())
                    }
                } catch (e: Exception) { Log.w("MoviesPanel", "title details failed: ${e.javaClass.simpleName}"); null }
                if (f != null && (f.genres != null || f.services != null) && titleFacts.learn(listOf(f))) learned = true
            }
            if (!learned) return@onFactsPool
            titleFacts.flush()
            ui.post { refreshPersonal(gen) }
        }
    }

    /**
     * The page's personal shelves again, from what is known now (details arrived, the library loaded): each shelf is redone
     * in place only when its cards changed, keeping focus, and the page around them is left alone.
     */
    private fun refreshPersonal(gen: Int) {
        if (gen != generation || !isVisible || personalPage() == null) return
        refreshFavourites()
        refreshRecent()
        continueRow?.let { fillContinue(it, gen) }
    }

    // --- Favourites ---------------------------------------------------------------------------------

    private var favScope = ""
    /** The owner's favourites, read once per page and after each change, so cards never parse the store themselves. */
    private var favSnapshot: List<Favourite> = emptyList()
    private var favRow: Row? = null
    private class FavCard(val card: PosterCard, val fav: Favourite)
    /** The Home shelf's cards, newest first. */
    private val favShelfCards = ArrayList<FavCard>()
    /** Poster cards on this page that can carry the star. */
    private val starred = ArrayList<Pair<PosterCard, Entry>>()
    /** A favourite whose card was removed while its title page was open: if it comes back, focus returns to it. */
    private var favFocusPending: Favourite? = null

    private fun refreshFavSnapshot() { favScope = jellyfin.ownerScope; favSnapshot = favourites.list(favScope) }

    private fun favOf(e: Entry): Favourite {
        val t = e.title
        return Favourite(t.kind, t.tmdbId, t.imdbId ?: e.library?.imdbId, e.library?.id, t.name, t.year, t.posterUrl, t.backdropUrl, System.currentTimeMillis())
    }

    private fun trackFavourite(card: PosterCard, e: Entry) {
        val f = favOf(e)
        if (!f.hasId) return
        starred += card to e
        card.favourite(Favourites.contains(favSnapshot, f))
    }

    /** A card from what was saved, so the shelf draws with no network. The library copy, if already loaded, only adds the Jellyfin picture headers. */
    private fun favEntry(f: Favourite): Entry {
        val t = Title(key = "fav:" + (f.tmdbId ?: f.imdbId ?: f.jellyfinId), kind = f.kind, name = f.name, year = f.year, overview = null, runtimeMin = null,
            genres = emptyList(), age = null, imdbScore = null, posterUrl = f.poster, backdropUrl = f.backdrop, imdbId = f.imdbId, tmdbId = f.tmdbId)
        val library = entry(t).library
        return Entry(t, library, if (library != null) jellyfin.playbackHeaders() else emptyMap())
    }

    private fun favCardView(row: Row, f: Favourite, index: Int, related: List<Title>): PosterCard =
        card(favEntry(f), related, defer = if (index >= EAGER_POSTERS) row else null, star = false) as PosterCard

    private fun restyleFavCards(row: Row) {
        for (i in 0 until row.inner.childCount) row.inner.getChildAt(i).layoutParams = cardParams(i)
    }

    /** Favourites, straight under Continue watching. Hidden whole (heading and spacing) while there are none. */
    private fun favouritesRow(gen: Int) {
        val row = row("Favourites", null)
        favRow = row
        row.hide()   // shown by refreshFavourites once its cards are ready
        refreshFavourites()
    }

    private fun fillFavourites(row: Row, items: List<Favourite>) {
        favShelfCards.clear(); row.deferred.clear(); row.inner.removeAllViews()
        row.inner.minimumHeight = Cinema.px(activity, 488)
        val related = items.map { favEntry(it).title }
        items.forEachIndexed { i, f ->
            val v = favCardView(row, f, i, related)
            row.inner.addView(v, cardParams(i)); favShelfCards += FavCard(v, f)
        }
        row.setCount(items.size)
        row.scroller.post { row.loadNear() }
    }

    /**
     * The Favourites shelf again for this page, from what is known now (a title's details arrived). Redone in place only
     * when its cards changed; the focused card stays focused wherever it went, else the nearest one left, else a
     * neighbouring shelf.
     */
    private fun refreshFavourites() {
        val row = favRow ?: return
        val page = personalPage() ?: return
        if (!row.scroller.isAttachedToWindow) return
        refreshFavSnapshot()
        val gen = generation; val seq = ++favSeq
        val snapshot = favSnapshot
        worker.execute {
            val filter = personalFilter(page)
            val now = filter.favourites(snapshot)
            activity.runOnUiThread {
                if (gen != generation || seq != favSeq || row !== favRow) return@runOnUiThread
                warmFacts(filter, gen)
                applyFavourites(row, now)
            }
        }
    }

    private var favSeq = 0

    private fun applyFavourites(row: Row, now: List<Favourite>) {
        val old = favShelfCards.map { it.fav }
        if (now.size == old.size && now.indices.all { now[it] == old[it] }) return
        val focusedAt = favShelfCards.indexOfFirst { it.card.hasFocus() }
        val returnAt = favShelfCards.indexOfFirst { it.card === returnFocus }
        val lastAt = favShelfCards.indexOfFirst { it.card === lastContentFocus }
        val scrollX = row.scroller.scrollX
        val wasEmpty = old.isEmpty()
        favShelfCards.forEach { c -> c.card.onFocus = null; c.card.setOnClickListener(null); starred.removeAll { it.first === c.card } }
        restPrefetch?.let { ui.removeCallbacks(it) }
        if (now.isEmpty()) {
            favShelfCards.clear(); row.inner.removeAllViews(); row.deferred.clear()
            row.hide(); row.inner.minimumHeight = 0
            val next = neighbouringFocus(row)
            if (focusedAt >= 0) { if (next != null) next.requestFocus() else focusContent() }
            if (returnAt >= 0) returnFocus = next
            if (lastAt >= 0) lastContentFocus = next
            return
        }
        fillFavourites(row, now)
        if (wasEmpty) { row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE }
        fun carried(at: Int): View? = if (at < 0) null else Favourites.focusAfter(old, at, now).let { favShelfCards.getOrNull(it)?.card } ?: neighbouringFocus(row)
        if (focusedAt >= 0) carried(focusedAt)?.requestFocus()
        if (returnAt >= 0) returnFocus = carried(returnAt)
        if (lastAt >= 0) lastContentFocus = carried(lastAt)
        if (!wasEmpty && scrollX > 0) row.scroller.post { row.scroller.scrollTo(scrollX, 0) }
    }

    /** A favourite was added or removed on a title page: the page underneath follows at once, without being rebuilt. */
    private fun favouritesChanged(changed: Favourite, on: Boolean) {
        refreshFavSnapshot()
        starred.forEach { (c, e) -> c.favourite(Favourites.contains(favSnapshot, favOf(e))) }
        val row = favRow ?: return
        val at = favShelfCards.indexOfFirst { it.fav.sameAs(changed) }
        if (!on) { if (at >= 0) removeFavCard(row, at, changed); return }
        if (at >= 0) return
        val f = favSnapshot.firstOrNull { it.sameAs(changed) } ?: return
        // Only a title that belongs on this page joins its shelf; one not known yet is asked about, and joins when it turns out to.
        val filter = personalFilter()
        if (!filter.keeps(f)) { warmFacts(filter, generation); return }
        val wasEmpty = favShelfCards.isEmpty()
        val v = favCardView(row, f, 0, emptyList())
        row.inner.addView(v, 0); favShelfCards.add(0, FavCard(v, f)); restyleFavCards(row)
        if (wasEmpty) { row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE; row.inner.minimumHeight = Cinema.px(activity, 488) }
        else row.scroller.post { if (row.scroller.scrollX > 0) row.scroller.scrollBy(v.width + Cinema.px(activity, 28), 0) }  // keep what the row shows where it was
        row.setCount(favShelfCards.size)
        if (favFocusPending?.sameAs(f) == true) { favFocusPending = null; returnFocus = v }
    }

    private fun firstFocusable(v: View): View? {
        val l = ArrayList<View>(); v.addFocusables(l, View.FOCUS_FORWARD)
        return l.firstOrNull { it.isShown && it.isEnabled }
    }

    private fun removeFavCard(row: Row, at: Int, changed: Favourite) {
        val gone = favShelfCards.removeAt(at).card
        val focusedNow = gone.hasFocus()
        val held = focusedNow || returnFocus === gone || lastContentFocus === gone
        // Quiet the card first, so nothing it scheduled can act on a view that is no longer on the page.
        gone.onFocus = null; gone.setOnClickListener(null)
        starred.removeAll { it.first === gone }; row.deferred.removeAll { it.first === gone }
        restPrefetch?.let { ui.removeCallbacks(it) }
        var target: View? = Favourites.focusAfterRemoval(at, favShelfCards.size).takeIf { it >= 0 }?.let { favShelfCards[it].card }
        if (focusedNow) target?.requestFocus()
        row.inner.removeView(gone)
        restyleFavCards(row)
        if (favShelfCards.isEmpty()) {
            row.hide(); row.inner.minimumHeight = 0
            if (held) {
                val kids = (0 until content.childCount).map { content.getChildAt(it) }
                val k = Favourites.nearestVisible(kids.map { it.visibility == View.VISIBLE && firstFocusable(it) != null }, kids.indexOf(row.scroller))
                target = if (k >= 0) firstFocusable(kids[k]) else null
                if (focusedNow) { if (target != null) target.requestFocus() else focusContent() }
            }
        } else row.setCount(favShelfCards.size)
        if (!held) return
        if (returnFocus === gone) { returnFocus = target; favFocusPending = changed }
        if (lastContentFocus === gone) lastContentFocus = target
    }

    // --- Recently watched -------------------------------------------------------------------------

    private val playHistory by lazy { PlayHistoryStore(AndroidPlatform(activity)) }
    private var recentRow: Row? = null
    private class RecentView(val view: ResumeCard, val card: RecentCard)
    /** The Home shelf's cards, newest first. */
    private val recentViews = ArrayList<RecentView>()
    /** An episode chosen on the shelf: the series page it opens selects it. Taken by [seasonSection]. */
    private var pendingEpisodePick: Pair<Int, Int>? = null
    /** The page [pendingEpisodePick] was made for (its title's key): any other series page opened ignores it. */
    private var pendingPickFor: String? = null

    /** What is already known about titles (favourites, the loaded library), so a record with no name can still be named. Never fetches. */
    private fun knownTitles(): List<KnownTitle> {
        val out = ArrayList<KnownTitle>()
        favSnapshot.forEach { out += KnownTitle(it.kind, it.name, it.tmdbId, it.imdbId, it.jellyfinId, it.poster, it.backdrop) }
        if (jellyfin.isConnected) {
            val base = JellyfinApi.cleanBase(jellyfin.server)
            jellyfin.loadedLibrary.forEach { i ->
                if (i.isEpisode || i.name.isBlank()) return@forEach
                out += KnownTitle(if (i.isSeries) Kind.SHOW else Kind.MOVIE, i.name, i.tmdbId, i.imdbId, i.id,
                    JellyfinApi.posterUrl(base, i.id), if (i.hasBackdrop) JellyfinApi.backdropUrl(base, i.id) else null)
            }
        }
        return out
    }

    /** The shelf for this page; titles [filter] could not place are left for it to report. */
    private fun recentCards(filter: PersonalFilter = personalFilter()): List<RecentCard> =
        RecentlyWatched.shelf(watchedRecords(), knownTitles(), keep = filter::keeps)

    /**
     * What was watched: the saved history, plus what streams and the library already say was played (read, never written),
     * so the shelf is useful from the first open and not only after the next viewing.
     */
    private fun watchedRecords(): List<com.danielportelabyrne.cinema.data.movies.PlayedEntry> {
        var all = playHistory.list(jellyfin.ownerScope)
        for (e in com.danielportelabyrne.cinema.data.movies.PlayHistory.fromStreamProgress(streamProgress())) if (all.none { it.sameAs(e) }) all = all + e
        if (jellyfin.isConnected) {
            val base = JellyfinApi.cleanBase(jellyfin.server)
            for (i in jellyfin.loadedLibrary) {
                val at = i.playedMs ?: continue
                if (!i.isMovie || at <= 0) continue
                val e = com.danielportelabyrne.cinema.data.movies.PlayedEntry(Kind.MOVIE, tmdbId = i.tmdbId, imdbId = i.imdbId, jellyfinId = i.id, title = i.name,
                    poster = JellyfinApi.posterUrl(base, i.id), backdrop = if (i.hasBackdrop) JellyfinApi.backdropUrl(base, i.id) else null, playedAt = at,
                    positionMs = i.resumeMs, durationMs = (i.runtimeMin ?: 0) * 60_000L, completed = i.played, source = com.danielportelabyrne.cinema.data.movies.PlayedEntry.SOURCE_JELLYFIN)
                if (all.none { it.sameAs(e) }) all = all + e
            }
        }
        return all
    }

    /** Recently watched, after Favourites. Drawn from the saved history at once; hidden whole while there is nothing to show. */
    private fun recentRow(gen: Int) {
        val row = row("Recently watched", null)
        recentRow = row
        row.hide()   // shown by refreshRecent once its cards are ready
        refreshRecent()
    }

    private fun fillRecent(row: Row, cards: List<RecentCard>, gen: Int) {
        recentViews.clear(); row.deferred.clear(); row.inner.removeAllViews()
        cards.forEachIndexed { i, c ->
            val v = recentCardView(row, c, i, gen)
            row.inner.addView(v, cardParams(i)); recentViews += RecentView(v, c)
        }
        row.setCount(cards.size)
        row.scroller.post { row.loadNear() }
    }

    private fun recentCardView(row: Row, c: RecentCard, index: Int, gen: Int): ResumeCard {
        val card = ResumeCard(activity, featured = false)
        card.contentDescription = listOf(c.title, c.meta).filter { it.isNotBlank() }.joinToString(", ")
        card.title.text = c.title
        card.meta.text = c.meta
        card.showBar(c.progress != null)
        card.setProgress(c.progress ?: 0f)
        c.artUrl?.let { url ->
            val headers = if (c.entry.source == com.danielportelabyrne.cinema.data.movies.PlayedEntry.SOURCE_JELLYFIN && jellyfin.isConnected) jellyfin.playbackHeaders() else emptyMap()
            val load = { images.load(url, headers, maxWidth = Cinema.px(activity, 480), tag = pageTag) { b -> if (gen == generation) card.setStill(b) } }
            if (index >= EAGER_POSTERS) row.deferred += card to { load() } else load()
        }
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { openRecent(c) }
        return card
    }

    /** OK opens the film's or series' page (never the player). An episode's page opens on its season, with that episode picked. */
    private fun openRecent(c: RecentCard) {
        val e = c.entry
        val target = RecentlyWatched.target(e) ?: return
        val isShow = e.isEpisode
        val imdb = if (isShow) e.seriesImdbId else e.imdbId
        val tmdb = if (isShow) e.seriesTmdbId else e.tmdbId
        val t = Title(key = "hist:" + (tmdb ?: imdb ?: e.seriesJellyfinId ?: e.jellyfinId), kind = e.kind, name = c.title, year = null, overview = null, runtimeMin = null,
            genres = emptyList(), age = null, imdbScore = null, posterUrl = e.poster, backdropUrl = e.backdrop, imdbId = imdb, tmdbId = tmdb)
        val own = if (isShow) e.seriesJellyfinId else e.jellyfinId
        val library = if (!jellyfin.isConnected) null
            else jellyfin.findLoaded(imdb, tmdb, isShow) ?: own?.let { id -> jellyfin.loadedLibrary.firstOrNull { it.id == id } }
        val entry = Entry(t, library, if (library != null) jellyfin.playbackHeaders() else emptyMap())
        pendingEpisodePick = (target as? RecentTarget.Episode)?.let { it.season to it.episode }; pendingPickFor = t.key
        openDetail(entry, emptyList())
    }

    /**
     * Brings the shelf up to date after playback, or on returning to the app. Rebuilds only this shelf, and only when the
     * history actually changed; the card that was focused stays focused wherever it moved to (else the nearest one left,
     * else a neighbouring shelf), and the shelf keeps its horizontal position.
     */
    private fun refreshRecent() {
        val row = recentRow ?: return
        val page = personalPage() ?: return
        if (!row.scroller.isAttachedToWindow) return
        refreshFavSnapshot()
        // Reading the saved history and sorting it is not main-thread work: the cards are made here and drawn there.
        val gen = generation; val seq = ++recentSeq
        worker.execute {
            val filter = personalFilter(page)
            val now = recentCards(filter)
            activity.runOnUiThread {
                if (gen != generation || seq != recentSeq || row !== recentRow) return@runOnUiThread
                warmFacts(filter, gen)
                applyRecent(row, now)
            }
        }
    }

    private var recentSeq = 0

    private fun applyRecent(row: Row, now: List<RecentCard>) {
        if (now == recentViews.map { it.card }) return
        val old = recentViews.map { it.card.entry }
        val focusedAt = recentViews.indexOfFirst { it.view.hasFocus() }
        val returnAt = recentViews.indexOfFirst { it.view === returnFocus }
        val lastAt = recentViews.indexOfFirst { it.view === lastContentFocus }
        val scrollX = row.scroller.scrollX
        val wasEmpty = recentViews.isEmpty()
        recentViews.forEach { it.view.onFocus = null; it.view.setOnClickListener(null) }
        restPrefetch?.let { ui.removeCallbacks(it) }
        fillRecent(row, now, generation)
        fun carried(at: Int): View? = if (at < 0) null else RecentlyWatched.focusAfter(old, at, now.map { it.entry }).let { recentViews.getOrNull(it)?.view }
        if (now.isEmpty()) {
            row.hide(); row.inner.minimumHeight = 0
            val kids = (0 until content.childCount).map { content.getChildAt(it) }
            val k = Favourites.nearestVisible(kids.map { it.visibility == View.VISIBLE && firstFocusable(it) != null }, kids.indexOf(row.scroller))
            val next = if (k >= 0) firstFocusable(kids[k]) else null
            if (focusedAt >= 0) { if (next != null) next.requestFocus() else focusContent() }
            if (returnAt >= 0) returnFocus = next
            if (lastAt >= 0) lastContentFocus = next
            return
        }
        if (wasEmpty) { row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE; row.inner.minimumHeight = Cinema.px(activity, ResumeCard.heightOf(false) + 24) }
        if (focusedAt >= 0) carried(focusedAt)?.requestFocus()
        if (returnAt >= 0) returnFocus = carried(returnAt)
        if (lastAt >= 0) lastContentFocus = carried(lastAt)
        if (scrollX > 0) row.scroller.post { row.scroller.scrollTo(scrollX, 0) }
    }

    // --- Continue watching ------------------------------------------------------------------------

    private val continueStore by lazy { ContinueWatching(activity) }
    private var continueRow: Row? = null

    /** One Continue watching card on the shelf: the engine's choice and the view drawn for it. */
    private class ContinueView(val view: ResumeCard, val entry: ContinueEntry)
    /** What the worker settled for the shelf: the cards, their saved-stream pictures, and the Jellyfin items they may play. */
    private class ContinueResult(val entries: List<ContinueEntry>, val stills: Map<String, Bitmap>, val items: List<JellyfinItem>)

    private val continueViews = ArrayList<ContinueView>()
    private var continueItems: List<JellyfinItem> = emptyList()
    /** One refresh at a time; a request made meanwhile is answered by one more run after it, never queued twice. */
    private var continueBusy = false
    private var continueAgain = false
    /** Bumped when the page is rebuilt, so a refresh begun for the old page finds out and does nothing. */
    private var continueEpoch = 0
    /** Series whose episode listing was fetched lately (by TMDB id), so each is asked for once in a while. */
    private val catalogueTried = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Continue watching, right under the spotlight: a section of its own on a warm band, with a kicker over a large
     * heading. The title watched last leads as a large card; the rest sit beside it two to a column. Hidden when empty.
     */
    private fun continueRow(gen: Int) {
        val section = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false; visibility = View.GONE }
        section.addView(View(activity).apply { background = StatementBand(Cinema.pxf(activity, 380)) }, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = -Cinema.px(activity, RAIL_WIDTH_OPEN - RAIL_WIDTH) })   // reaches behind the rail, so moving the page aside never shows its edge
        // The Downloads ribbon, looping in from the right edge, quieter here: the section's signature.
        section.addView(RibbonView.downloads(activity, opacity = 0.47f),
            FrameLayout.LayoutParams(-1, Cinema.px(activity, 300)))
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(0, Cinema.px(activity, 72), 0, Cinema.px(activity, 64))
        }
        column.addView(Cinema.eyebrow(activity, listOf("Pick up where you left off"), 17).apply { setPadding(pageStart, 0, pageEnd, 0) })
        column.addView(Cinema.rowHeading(activity, "Continue watching", sizePx = 60).apply { setPadding(pageStart, Cinema.px(activity, 14), pageEnd, Cinema.px(activity, 18)) })
        section.addView(column, FrameLayout.LayoutParams(-1, -2))
        content.addView(section, LinearLayout.LayoutParams(-1, -2))
        val inner = row(null, null, into = column)
        // The section is the row's "heading": hiding the row hides all of it, and the count sits in its heading.
        val row = Row(section, inner.scroller, inner.inner).also { rowOf[inner.scroller] = it }
        row.inner.minimumHeight = Cinema.px(activity, ResumeCard.heightOf(featured = true))
        continueRow = row
        fillContinue(row, gen)
    }

    /** Starts a refresh of the shelf from what is saved now. Quiet while one is running; asks for one more after it. */
    private fun fillContinue(row: Row, gen: Int) {
        if (continueBusy) { continueAgain = true; return }
        continueBusy = true
        val epoch = continueEpoch
        val page = personalPage()   // read here: the worker must not read the page as it changes
        worker.execute {
            val result = try { continueResult(row, gen, epoch, page) } catch (e: Exception) { Log.w("MoviesPanel", "continue watching failed: ${e.javaClass.simpleName}"); null }
            activity.runOnUiThread {
                // Replaced meanwhile (page rebuilt): the new page has its own refresh state.
                if (epoch != continueEpoch) return@runOnUiThread
                continueBusy = false
                if (gen == generation && continueRow === row && result != null) applyContinue(row, result, gen)
                if (continueAgain) { continueAgain = false; if (gen == generation && continueRow === row) fillContinue(row, gen) }
            }
        }
    }

    /** Off the main thread. Everything here is read from this TV's own saved state and caches; episode listings are only peeked at. */
    private fun continueResult(row: Row, gen: Int, epoch: Int, page: PersonalPage?): ContinueResult {
        val scope = jellyfin.ownerScope
        val savedItems = continueStore.load(50).filter { it.payload != null }
        val saved = savedItems.map { SavedStream(it.payload!!, it.positionMs, it.durationMs, it.lastEngagedMs) }
        val resumable = if (jellyfin.isConnected) jellyfin.continueWatching() else emptyList()
        val loaded = if (jellyfin.isConnected) jellyfin.loadedLibrary else emptyList()
        // The server's say: what it lists as part-watched now, and what it holds as played (which lets a finished episode move on).
        // Part-watched library entries missing from the fresh list were cleared since, so only their played state is kept.
        val resumeIds = resumable.map { it.id }.toSet()
        val server = resumable + loaded.filter { it.played && it.id !in resumeIds }
        val inputs = ContinueInputs(scope, EpisodeCards.today(), playHistory.list(scope), streamProgress(), saved, server, knownTitles())
        val gone = continueDismissals.all(scope)
        val wanted = LinkedHashMap<String, Long>()
        // Only what belongs on this page, chosen before the shelf's limit; titles not placed yet are asked about.
        val filter = personalFilter(page)
        val entries = ContinueEngine.select(inputs, keep = { filter.keeps(it) && !ContinueDismissals.hidden(gone, it) }, limit = CONTINUE_LIMIT, catalogue = { ids ->
            val tmdb = ids.tmdbId?.toLongOrNull()
                ?: ContinueEngine.jellyfinKey(ids.jellyfinId)?.let { jf -> loaded.firstOrNull { it.isSeries && ContinueEngine.jellyfinKey(it.id) == jf } }?.tmdbId?.toLongOrNull()
            val cat = ContinueCards.catalogue(ids, tmdb?.let { seerr.cachedCatalogue(it) }, loaded)
            if (tmdb != null && cat?.complete != true) wanted[tmdb.toString()] = tmdb
            cat
        })
        activity.runOnUiThread { if (epoch == continueEpoch) warmFacts(filter, gen) }
        val stills = HashMap<String, Bitmap>()
        entries.forEach { e ->
            val payload = (e.source as? ContinueSource.Stream)?.payload ?: return@forEach
            savedItems.firstOrNull { it.payload == payload }?.imageUri?.let { u -> continueStore.image(u)?.let { stills[e.key] = it } }
        }
        warmCatalogues(row, gen, epoch, wanted.values.toList())
        return ContinueResult(entries, stills, server)
    }

    /** Fetches the listings of series the shelf could not place, once each in a while, then recomputes the shelf. Never on the main thread. */
    private fun warmCatalogues(row: Row, gen: Int, epoch: Int, series: List<Long>) {
        if (!seerr.isSignedIn) return
        val now = System.currentTimeMillis()
        val due = series.filter { tmdb -> catalogueTried.put(tmdb.toString(), now)?.let { now - it < CATALOGUE_RETRY_MS } != true }
        if (due.isEmpty()) return
        slowPool.execute {
            due.forEach { tmdb -> try { seerr.warmCatalogue(tmdb) } catch (_: Exception) { } }
            activity.runOnUiThread { if (epoch == continueEpoch && gen == generation && continueRow === row) fillContinue(row, gen) }
        }
    }

    /**
     * Brings the shelf up to date in place: only this shelf is rebuilt, and only when the cards changed. The focused card
     * stays focused wherever it moved to (a Resume that became a Next episode is still the same series); if it went, the
     * nearest card left, else a neighbouring shelf. The shelf keeps its horizontal position.
     */
    private fun applyContinue(row: Row, result: ContinueResult, gen: Int) {
        continueItems = result.items
        val entries = result.entries
        if (entries == continueViews.map { it.entry } && entries.isNotEmpty() == (row.scroller.visibility == View.VISIBLE)) return
        val oldKeys = continueViews.map { it.entry.key }
        val focusedAt = continueViews.indexOfFirst { it.view.hasFocus() }
        val returnAt = continueViews.indexOfFirst { it.view === returnFocus }
        val lastAt = continueViews.indexOfFirst { it.view === lastContentFocus }
        val scrollX = row.scroller.scrollX
        val wasEmpty = continueViews.isEmpty()
        continueViews.forEach { it.view.onFocus = null; it.view.setOnClickListener(null) }
        restPrefetch?.let { ui.removeCallbacks(it) }
        continueViews.clear(); row.inner.removeAllViews()
        if (entries.isEmpty()) {
            // Heading, band and spacing all go with the row.
            row.hide(); row.inner.minimumHeight = 0
            val next = neighbouringFocus(row)
            if (focusedAt >= 0) { if (next != null) next.requestFocus() else focusContent() }
            if (returnAt >= 0) returnFocus = next
            if (lastAt >= 0) lastContentFocus = next
            return
        }
        row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE
        row.inner.minimumHeight = Cinema.px(activity, ResumeCard.heightOf(featured = true))
        row.inner.addView(continueCard(entries[0], result, gen, featured = true))
        // The rest two to a column, beside the lead card and exactly as tall as it.
        val gap = Cinema.px(activity, ResumeCard.heightOf(true) - 2 * ResumeCard.heightOf(false))
        entries.drop(1).chunked(2).forEachIndexed { c, pair ->
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            pair.forEachIndexed { i, e -> col.addView(continueCard(e, result, gen, featured = false), LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) topMargin = gap }) }
            row.inner.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, if (c == 0) 40 else 32) })
        }
        row.setCount(entries.size)
        row.scroller.post { row.loadNear() }
        val newKeys = continueViews.map { it.entry.key }
        fun carried(at: Int): View? {
            if (at < 0) return null
            val k = ContinueCards.focusAfter(oldKeys, at, newKeys)
            return if (k >= 0) continueViews[k].view else neighbouringFocus(row)
        }
        if (focusedAt >= 0) carried(focusedAt)?.requestFocus()
        if (returnAt >= 0) returnFocus = carried(returnAt)
        if (lastAt >= 0) lastContentFocus = carried(lastAt)
        if (!wasEmpty && scrollX > 0) row.scroller.post { row.scroller.scrollTo(scrollX, 0) }
    }

    /** The first focusable thing on the next shelf showing (else the one before); null on a page with nothing else. */
    private fun neighbouringFocus(row: Row): View? {
        val kids = (0 until content.childCount).map { content.getChildAt(it) }
        val self = kids.indexOf(row.heading ?: row.scroller)
        val k = Favourites.nearestVisible(kids.mapIndexed { i, v -> i != self && v.visibility == View.VISIBLE && firstFocusable(v) != null }, self)
        return if (k >= 0) firstFocusable(kids[k]) else null
    }

    /** The picture a card wears (after a saved stream's own frame): the library's artwork for what it plays, else a still, backdrop or poster the engine knows. */
    private fun continueArt(e: ContinueEntry, result: ContinueResult): String? {
        ContinueCards.libraryItem(e, result.items + jellyfin.loadedLibrary)?.let { item ->
            if (e.kind == Kind.MOVIE && item.hasBackdrop) return jellyfin.backdropUrl(item.id)
            if (e.still == null) return JellyfinApi.episodeImageUrl(JellyfinApi.cleanBase(jellyfin.server), item.id, 640)
        }
        return e.still ?: e.backdrop ?: e.poster
    }

    private fun continueCard(e: ContinueEntry, result: ContinueResult, gen: Int, featured: Boolean): View {
        val m = ContinueCards.model(e)
        val card = ResumeCard(activity, featured)
        card.contentDescription = listOfNotNull(m.title, m.label, m.detail, m.remaining).joinToString(", ")
        card.title.text = m.title
        if (featured) { card.kicker.text = m.featuredKicker; card.meta.text = m.featuredMeta } else card.meta.text = m.smallMeta
        // A next episode has no progress to show: its bar's room stays, so cards keep one height.
        card.showBar(m.progress != null)
        card.setProgress(m.progress ?: 0f)
        val frame = result.stills[e.key]
        if (frame != null) card.setStill(frame) else continueArt(e, result)?.let { url ->
            // Jellyfin's sign-in goes only to Jellyfin.
            val headers = if (jellyfin.isConnected && url.startsWith(JellyfinApi.cleanBase(jellyfin.server))) jellyfin.playbackHeaders() else emptyMap()
            images.load(url, headers, maxWidth = Cinema.px(activity, if (featured) 960 else 480), tag = pageTag) { b -> if (gen == generation) card.setStill(b) }
        }
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { openContinue(e) }
        // Holding OK on a card opens its menu: resume, from the start, next episode, details, remove.
        card.setOnLongClickListener { continueMenu(e); true }
        continueViews += ContinueView(card, e)
        return card
    }

    private val continueDismissals by lazy { ContinueDismissals(AndroidPlatform(activity)) }

    /**
     * The menu on a Continue watching card (hold OK): carry on, play from the start, the next episode, the series page at the
     * viewer's place, and taking the card away (it returns when it is played again).
     */
    private fun continueMenu(e: ContinueEntry) {
        val m = ContinueCards.model(e)
        val actions = ArrayList<SheetAction>()
        val lead = when (e.action) { ContinueAction.RESUME -> "Resume"; ContinueAction.NEXT_EPISODE -> "Play next episode"; ContinueAction.START_OVER -> "Start over" }
        if (m.open != ContinueOpen.PAGE) actions += SheetAction(lead, m.detail, primary = true, icon = R.drawable.ic_cin_play) { openContinue(e) }
        // From the start: a library item, or a stream the player saved.
        val fromStartItem = ContinueCards.libraryItem(e, continueItems + jellyfin.loadedLibrary)
        val payload = (e.source as? ContinueSource.Stream)?.payload
        if (e.action == ContinueAction.RESUME && (fromStartItem != null || payload != null)) actions += SheetAction("Play from start", "From the very beginning", icon = R.drawable.ic_cin_prev) {
            playbackStarted = true
            if (fromStartItem != null) {
                val name = listOfNotNull(m.title, e.episode?.code?.takeIf { e.kind == Kind.SHOW }).joinToString(" · ")
                JellyfinPlayback.play(activity, jellyfin, fromStartItem, name, fromStartItem.resumeKey, fromStart = true, series = if (e.kind == Kind.SHOW) e.ids.imdbId to e.ids.tmdbId else null)
            } else if (!PlayerActivity.resume(activity, payload!!, fromStart = true)) { playbackStarted = false; CinemaToast.show(activity, "Couldn't reopen ${m.title}", Toast.LENGTH_LONG) }
        }
        // The next episode, once the listing says which it is.
        val atSeason = e.episode?.season; val atEpisode = e.episode?.episode
        if (e.kind == Kind.SHOW && e.action != ContinueAction.NEXT_EPISODE && atSeason != null && atEpisode != null) {
            val tmdb = e.ids.tmdbId?.toLongOrNull()
            fun open(next: com.danielportelabyrne.cinema.data.movies.CatalogueEpisode) =
                openContinuePage(e, pick = ContinueEpisode(next.season, next.episode, next.title, next.still, ContinueEngine.jellyfinKey(next.jellyfinId)))
            val next = ContinueCards.nextEpisode(ContinueCards.catalogue(e.ids, tmdb?.let { seerr.cachedCatalogue(it) }, jellyfin.loadedLibrary), atSeason, atEpisode)
            if (next != null) actions += SheetAction("Next episode", "S${next.season} E${next.episode}" + (next.title?.let { " · $it" } ?: ""), icon = R.drawable.ic_cin_next) { open(next) }
            // Its listing is not held yet: ask for it when chosen, then open the page on that episode.
            else if (tmdb != null && seerr.isSignedIn) actions += SheetAction("Next episode", "Find it in the series' listing", icon = R.drawable.ic_cin_next) {
                worker.execute {
                    runCatching { seerr.warmCatalogue(tmdb) }
                    val found = ContinueCards.nextEpisode(ContinueCards.catalogue(e.ids, seerr.cachedCatalogue(tmdb), jellyfin.loadedLibrary), atSeason, atEpisode)
                    activity.runOnUiThread { if (found != null) open(found) else CinemaToast.show(activity, "No next episode found for ${m.title}", Toast.LENGTH_LONG) }
                }
            }
        }
        actions += SheetAction("Show details", if (e.kind == Kind.SHOW) "The series, at your place in its seasons" else "About this film", icon = R.drawable.ic_cin_info) { openContinuePage(e, pick = e.episode) }
        actions += SheetAction("Remove from Continue watching", "It comes back when you watch it again", icon = R.drawable.ic_cin_trash) {
            continueDismissals.dismiss(jellyfin.ownerScope, e.key, e.lastPlayedAt)
            continueRow?.let { fillContinue(it, generation) }
        }
        // The card's own picture heads the menu, flying in from the card.
        val card = continueViews.firstOrNull { it.entry.key == e.key }?.view
        val still = (card?.image?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        infoSheet.show("CONTINUE WATCHING", m.title, m.detail, listOfNotNull(m.remaining), still, actions, imageSizeDp = 300 to 169, from = card?.image.takeIf { still != null })
    }

    /** OK on a card: play what the engine picked, or open its page when it has no source yet. Never guesses an episode. */
    private fun openContinue(e: ContinueEntry) {
        val m = ContinueCards.model(e)
        when (m.open) {
            ContinueOpen.PLAY_STREAM -> {
                val payload = (e.source as? ContinueSource.Stream)?.payload
                playbackStarted = true
                if (payload == null || !PlayerActivity.resume(activity, payload)) { playbackStarted = false; CinemaToast.show(activity, "Couldn't reopen ${m.title}", Toast.LENGTH_LONG) }
            }
            ContinueOpen.PLAY_LIBRARY -> {
                val item = ContinueCards.libraryItem(e, continueItems + jellyfin.loadedLibrary)
                if (item == null) { openContinuePage(e, pick = e.episode); return }
                playbackStarted = true
                val name = listOfNotNull(m.title, e.episode?.code?.takeIf { e.kind == Kind.SHOW }).joinToString(" · ")
                val series = if (e.kind == Kind.SHOW) e.ids.imdbId to e.ids.tmdbId else null
                JellyfinPlayback.play(activity, jellyfin, item, name, item.resumeKey, fromStart = m.fromStart, series = series, startMs = m.resumeMs,
                    settledAtMs = if (m.resumeMs != null) e.lastPlayedAt else 0L)
            }
            ContinueOpen.EPISODE_PAGE -> openContinuePage(e, pick = e.episode)
            ContinueOpen.PAGE -> openContinuePage(e, pick = null)
        }
    }

    /** The film's or series' page, with the episode picked when it is known, so a source is found the usual way. */
    private fun openContinuePage(e: ContinueEntry, pick: ContinueEpisode?) {
        val isShow = e.kind == Kind.SHOW
        val t = Title(key = "hist:" + (e.ids.tmdbId ?: e.ids.imdbId ?: e.ids.jellyfinId), kind = e.kind, name = e.title, year = null, overview = null, runtimeMin = null,
            genres = emptyList(), age = null, imdbScore = null, posterUrl = e.poster, backdropUrl = e.backdrop, imdbId = e.ids.imdbId, tmdbId = e.ids.tmdbId)
        val own = ContinueEngine.jellyfinKey(e.ids.jellyfinId)
        val library = if (!jellyfin.isConnected) null
            else jellyfin.findLoaded(e.ids.imdbId, e.ids.tmdbId, isShow) ?: own?.let { id -> jellyfin.loadedLibrary.firstOrNull { ContinueEngine.jellyfinKey(it.id) == id && it.isSeries == isShow } }
        val entry = Entry(t, library, if (library != null) jellyfin.playbackHeaders() else emptyMap())
        val season = pick?.season; val episode = pick?.episode
        pendingEpisodePick = if (isShow && season != null && episode != null) season to episode else null; pendingPickFor = t.key
        openDetail(entry, emptyList())
    }

    private fun picksRow(gen: Int) {
        val row = row("Picked for you", null)
        row.inner.minimumHeight = Cinema.px(activity, 488)
        var shown: List<String> = emptyList()
        fun show(list: List<com.danielportelabyrne.cinema.data.movies.Pick>) {
            if (gen != generation) return
            val keys = list.map { it.suggestion.key }
            if (keys == shown) return
            shown = keys
            row.inner.removeAllViews(); row.deferred.clear()
            if (list.isEmpty()) { row.hide(); return }
            row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE
            val entries = list.map { entry(pickTitle(it)) }
            val related = entries.map { it.title }
            val because = list.map { "Because you watched ${it.because}" }
            // Under a poster there is room for little: "Like Rick and Morty". The featured title says it in full.
            entries.forEachIndexed { i, e -> row.inner.addView(card(e, related, note = "Like ${list[i].because}", defer = if (i >= EAGER_POSTERS) row else null), cardParams(i)) }
            row.setCount(entries.size)
            row.scroller.post { row.loadNear() }
            val featured = entries.indices.filter { entries[it].title.backdropUrl != null }.take(3)
            if (featured.isNotEmpty()) setHero(featured.map { entries[it] }, related, featured.map { because[it] })
        }
        worker.execute {
            val saved = picks.saved()
            activity.runOnUiThread { if (saved.isNotEmpty()) show(saved) }
            val fresh = picks.fresh()
            activity.runOnUiThread { if (fresh != null) show(fresh) else if (saved.isEmpty()) row.hide() }
        }
    }

    private fun libraryRow(gen: Int, heading: String, limit: Int) {
        val row = row(heading, "On your Jellyfin")
        if (!jellyfin.isConnected) {
            row.inner.addView(actionCard("Connect Jellyfin", "Sign in to see your own films and series") { jellyfinSheet.show(jellyfin) { if (isVisible) build() } })
            return
        }
        row.inner.addView(loading("Loading…"))
        worker.execute {
            val items = libraryOrder().sort(jellyfin.library()).take(limit)
            activity.runOnUiThread {
                if (gen != generation) return@runOnUiThread
                row.inner.removeAllViews()
                if (items.isEmpty()) { row.inner.addView(note("Nothing in your library yet, or Jellyfin couldn't be reached")); return@runOnUiThread }
                val entries = libraryEntries(items)
                entries.forEachIndexed { i, e -> row.inner.addView(card(e, entries.map { it.title }, badge = false), cardParams(i)) }
                row.setCount(entries.size)
            }
        }
    }

    // --- My library --------------------------------------------------------------------------------

    private fun buildLibrary(gen: Int) {
        val margin = pageStart
        if (!jellyfin.isConnected) {
            pageTitle("My library")
            content.addView(actionCard("Connect Jellyfin", "Sign in to see your own films and series") { jellyfinSheet.show(jellyfin) { if (isVisible) build() } }, LinearLayout.LayoutParams(dp(380), dp(64)).apply { marginStart = margin; topMargin = dp(10) })
            watchlistRow()
            return
        }
        buildHero()
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        // The viewer's own titles that the library holds, ahead of the watchlist and the library's rows.
        personalRows(gen)
        watchlistRow()
        content.addView(holder, LinearLayout.LayoutParams(-1, -2))
        holder.addView(loading("Loading your library…").apply { setPadding(margin, dp(10), 0, dp(10)) })
        worker.execute {
            val items = libraryOrder().sort(jellyfin.library())
            activity.runOnUiThread {
                if (gen != generation) return@runOnUiThread
                holder.removeAllViews()
                if (items.isEmpty()) { holder.addView(note("Nothing in your library yet, or Jellyfin couldn't be reached").apply { setPadding(margin, dp(10), 0, dp(10)) }); return@runOnUiThread }
                val all = libraryEntries(items)
                all.filter { it.title.backdropUrl != null }.take(3).let { f -> setHero(f, all.map { e -> e.title }, f.map { recencyLabel(it) }) }
                // The same row looks as the catalogue: the latest first as a spotlight, then stills, a grid, posters…
                val looks = listOf(com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT, com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS,
                    com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID, com.danielportelabyrne.cinema.data.movies.ShelfLook.POSTERS,
                    com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS)
                var next = 0
                fun add(heading: String, list: List<Entry>) {
                    if (list.isEmpty()) return
                    val r = row(heading, "${list.size}", into = holder)
                    val look = com.danielportelabyrne.cinema.data.movies.ShelfLooks.resolve(looks[next++ % looks.size], list.map { it.title })
                    drawEntries(r, list, look, libraryChip = false)
                }
                add(libraryOrder().label, all.take(20))
                add("Films", all.filter { it.title.kind == Kind.MOVIE })
                add("Series", all.filter { it.title.kind == Kind.SHOW })
                all.flatMap { e -> e.title.genres.map { g -> g to e } }.groupBy({ it.first }, { it.second })
                    .filterValues { it.size >= 2 }.entries.sortedByDescending { it.value.size }.take(6).forEach { (g, list) -> add(g, list) }
            }
        }
    }

    /**
     * Fills [r] with [entries] drawn in [look], as the catalogue's shelves are: a spotlight leads with its first title
     * that has a wide picture; a grid stacks stills two to a column. Pictures far along the row wait until it nears them.
     */
    private fun drawEntries(r: Row, entries: List<Entry>, look: com.danielportelabyrne.cinema.data.movies.ShelfLook, libraryChip: Boolean = true) {
        val lead = if (look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT) com.danielportelabyrne.cinema.data.movies.ShelfLooks.spotlightIndex(entries.map { it.title }) ?: 0 else 0
        val list = if (lead > 0) listOf(entries[lead]) + entries.filterIndexed { i, _ -> i != lead } else entries
        val related = list.map { it.title }
        r.inner.minimumHeight = Cinema.px(activity, rowHeight(look))
        var column: LinearLayout? = null
        list.forEachIndexed { i, e ->
            val defer = if (i >= EAGER_POSTERS) r else null
            when {
                look == com.danielportelabyrne.cinema.data.movies.ShelfLook.GRID -> {
                    val col = column?.takeIf { it.childCount < 2 } ?: LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
                    }.also { c -> r.inner.addView(c, cardParams(i / 2)); column = c }
                    col.addView(stillCard(e, related, SMALL_STILL_W, SMALL_STILL_H, defer, holder = col, libraryChip = libraryChip),
                        LinearLayout.LayoutParams(-2, -2).apply { if (col.childCount > 0) topMargin = Cinema.px(activity, GRID_GAP) })
                }
                look == com.danielportelabyrne.cinema.data.movies.ShelfLook.STILLS -> r.inner.addView(stillCard(e, related, STILL_W, STILL_H, defer, libraryChip = libraryChip), cardParams(i))
                look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SMALL_STILLS -> r.inner.addView(stillCard(e, related, SMALL_STILL_W, SMALL_STILL_H, defer, libraryChip = libraryChip), cardParams(i))
                look == com.danielportelabyrne.cinema.data.movies.ShelfLook.SPOTLIGHT && i == 0 -> r.inner.addView(stillCard(e, related, SPOTLIGHT_W, SPOTLIGHT_H, null, spotlight = true, libraryChip = libraryChip), cardParams(i))
                else -> r.inner.addView(card(e, related, badge = libraryChip, defer = defer), cardParams(i))
            }
        }
        r.scroller.post { r.loadNear() }
    }

    // --- Downloads ---------------------------------------------------------------------------------

    private var requestDiagnostics: Map<Int, RequestDiagnostic> = emptyMap()
    /** Name and poster per title, keyed by TMDB id, doubled, plus one for a series. Kept on disk between sessions. */
    private val knownTitles: java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String?>> by lazy {
        java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String?>>().also { map ->
            try {
                val saved = org.json.JSONObject(activity.getSharedPreferences("downloads", Context.MODE_PRIVATE).getString("titles", "{}") ?: "{}")
                saved.keys().forEach { k -> saved.optJSONArray(k)?.let { a -> map[k.toLong()] = a.optString(0) to a.optString(1).ifBlank { null } } }
            } catch (_: Exception) { }
        }
    }
    private val infoPool = Executors.newFixedThreadPool(4)   // threads start only when first used

    /** Looks up every title not yet known, four at a time instead of one after another, and keeps them for next time. */
    private fun learnTitles(wanted: List<Pair<Kind, Long>>) {
        val missing = wanted.filter { (kind, tmdb) -> tmdb > 0 && !knownTitles.containsKey(tmdb * 2 + if (kind == Kind.SHOW) 1 else 0) }.distinct()
        if (missing.isEmpty()) return
        val found = try {
            infoPool.invokeAll(missing.map { (kind, tmdb) -> java.util.concurrent.Callable { seerr.titleInfo(kind, tmdb)?.let { (tmdb * 2 + if (kind == Kind.SHOW) 1 else 0) to it } } }, 30, java.util.concurrent.TimeUnit.SECONDS)
                .mapNotNull { f -> try { f.get() } catch (_: Exception) { null } }
        } catch (_: InterruptedException) { emptyList() }
        if (found.isEmpty()) return
        found.forEach { (k, v) -> knownTitles[k] = v }
        val json = org.json.JSONObject()
        knownTitles.forEach { (k, v) -> json.put(k.toString(), org.json.JSONArray().put(v.first).put(v.second ?: "")) }
        activity.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putString("titles", json.toString()).apply()
    }

    private fun titleKey(r: SeerrRequest) = r.tmdbId * 2 + if (r.kind == Kind.SHOW) 1 else 0

    /** Requests still on their way to a finished file: waiting to be approved, looking for a release, or downloading. */
    private val pendingStages = setOf(Stage.WAITING_APPROVAL, Stage.SEARCHING, Stage.DOWNLOADING)

    /** The four counts at the top of Downloads; the busiest one is lit saffron. */
    private class StatTiles(val row: LinearLayout, val values: List<TextView>, val tiles: List<View>)

    private fun statTiles(): StatTiles {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val values = ArrayList<TextView>(); val tiles = ArrayList<View>()
        listOf("Downloading", "Queued", "Finding sources", "Awaiting release").forEachIndexed { i, label ->
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(Cinema.px(activity, 24), 0, Cinema.px(activity, 16), 0)
                background = Cinema.glass(activity, 22f)
            }
            val value = Cinema.text(activity, "–", 42, Cinema.CREAM, serif = true)
            tile.addView(value)
            tile.addView(Cinema.text(activity, label, 15, Cinema.DIM, weight = 500, tracking = 0.12f, caps = true).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 6) })
            row.addView(tile, LinearLayout.LayoutParams(Cinema.px(activity, 276), Cinema.px(activity, 96)).apply { if (i > 0) marginStart = Cinema.px(activity, 20) })
            values += value; tiles += tile
        }
        return StatTiles(row, values, tiles)
    }

    private fun updateStats(stats: StatTiles, counts: List<Int>) {
        val busiest = counts.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
        counts.forEachIndexed { i, n ->
            stats.values[i].text = "$n"
            stats.values[i].setTextColor(if (i == busiest) Cinema.SAFFRON else Cinema.CREAM)
            stats.tiles[i].background = Cinema.glass(activity, 22f, if (i == busiest) Cinema.alpha(Cinema.SAFFRON, 0.1f) else Cinema.GLASS)
        }
    }

    private fun buildDownloads(gen: Int) {
        val margin = pageStart
        // Header (`21-downloads`): the ribbon looping in from the top right, eyebrow, big title and a line beneath.
        val header = FrameLayout(activity).apply { clipChildren = false }
        header.addView(RibbonView.downloads(activity), FrameLayout.LayoutParams(-1, Cinema.px(activity, 330)).apply { topMargin = -Cinema.px(activity, 52) })
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(margin, 0, pageEnd, 0) }
        heading.addView(Cinema.eyebrow(activity, listOf("Request queue")))
        heading.addView(Cinema.title(activity, "Downloads", 84, maxLines = 1), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
        heading.addView(Cinema.text(activity, "What you’ve asked for, and how far along it is", 24, Cinema.MUTED, weight = 300), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
        header.addView(heading, FrameLayout.LayoutParams(-1, -2))
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        if (!seerr.isSignedIn) {
            content.addView(actionCard(if (seerr.hasServer) "Sign in to see your downloads" else "Set up Seerr to request films", if (seerr.hasServer) "Sign in to Seerr again" else "Then your downloads show here") { seerrSheet.show(seerr, jellyfin) { if (isVisible) build() } },
                LinearLayout.LayoutParams(Cinema.px(activity, 720), Cinema.px(activity, 128)).apply { marginStart = margin; topMargin = Cinema.px(activity, 56) })
            return
        }
        val stats = statTiles()
        content.addView(stats.row, LinearLayout.LayoutParams(-2, -2).apply { marginStart = margin; topMargin = Cinema.px(activity, 56) })
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(holder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 14) })
        holder.addView(loading("Loading…").apply { setPadding(margin, dp(10), 0, 0) })
        var firstLoad = true
        var loading = false
        var lastSignature: String? = null
        var scheduled: Runnable? = null
        fun refresh() {
            scheduled?.let { ui.removeCallbacks(it) }
            if (loading || gen != generation || !isVisible) return
            loading = true
            slowPool.execute {
                val queue = queueClient.items()
                val inQueue = queue.orEmpty().map { it.tmdbId * 2 + if (it.source == QueueSource.SONARR) 1 else 0 }.toSet()
                val snapshot = seerr.pipelineStatus()
                val diagnostics = snapshot?.takeIf { it.healthy }?.requests.orEmpty()
                val rawRequests = seerr.requestsOrNull()
                // One queued season must not hide another season of the same show.
                val requests = rawRequests.orEmpty().filter { !RequestPipelineApi.covered(it, queue.orEmpty()) }
                // Single episodes asked for one at a time: monitored in Sonarr with nothing grabbed yet. A series already listed above is not repeated.
                val listed = inQueue + requests.map { titleKey(it) }
                val wanted = sonarr.wanted().orEmpty().groupBy { it.seriesTmdbId }.filterKeys { it * 2 + 1 !in listed }
                // Episodes asked for before they air: Sonarr's wanted list leaves them out, its calendar has them.
                val upcoming = sonarr.upcoming().orEmpty().groupBy { it.seriesTmdbId }.filterKeys { it * 2 + 1 !in inQueue }
                learnTitles(queue.orEmpty().map { q -> (if (q.source == QueueSource.SONARR) Kind.SHOW else Kind.MOVIE) to q.tmdbId } +
                    requests.map { it.kind to it.tmdbId } + wanted.keys.map { Kind.SHOW to it } + upcoming.keys.map { Kind.SHOW to it })
                val wantedEpisodes = wanted.values.flatten() + upcoming.values.flatten()
                // Nothing moved since the last look: leave the rows as they are (no rebuild, focus untouched).
                val signature = listOf(queue, rawRequests == null, snapshot?.healthy, requests, diagnostics, wanted, upcoming, knownTitles.size,
                    if (diagnostics.isNotEmpty()) System.currentTimeMillis() / 60_000 else 0).toString()
                activity.runOnUiThread {
                    loading = false
                    if (gen != generation) return@runOnUiThread
                    if (signature == lastSignature) {
                        scheduled = Runnable { if (gen == generation && current == Dest.Downloads && !detailOpen) refresh() }
                        ui.postDelayed(scheduled!!, 15_000)
                        return@runOnUiThread
                    }
                    lastSignature = signature
                    val focused = (0 until holder.childCount).firstOrNull { holder.getChildAt(it).hasFocus() }
                    holder.removeAllViews()
                    requestDiagnostics = diagnostics
                    if (rawRequests == null) holder.addView(note("Couldn't read your requests. Showing the download queue only.").apply { setPadding(margin, dp(4), margin, dp(4)) })
                    if (seerr.hasRequestHelper && snapshot?.healthy != true) holder.addView(note("Automatic search status is unavailable. Download-client progress is shown below.").apply { setPadding(margin, dp(4), margin, dp(4)) })
                    val rows = queue.orEmpty().map { q -> queueRow(q) { refresh() } } + requests.map { downloadRow(it) { refresh() } } +
                        wanted.map { (tmdb, eps) -> wantedRow(tmdb, eps) { refresh() } } + upcoming.map { (tmdb, eps) -> upcomingRow(tmdb, eps) { refresh() } }
                    if (rows.isEmpty()) holder.addView(note(if (queue == null) "Couldn't reach the download queue just now." else "Nothing requested yet. Open any title and press Queue download.").apply { setPadding(margin, dp(10), 0, 0) })
                    val q = queue.orEmpty()
                    updateStats(stats, listOf(q.count { it.isDownloading }, q.count { it.isWaiting }, q.count { it.status in setOf("metadata", "stalled") },
                        requests.count { it.stage == Stage.SEARCHING && diagnostics[it.id]?.stopped != true } + upcoming.values.sumOf { it.size }))
                    rows.forEachIndexed { i, r -> holder.addView(r, LinearLayout.LayoutParams(-1, Cinema.px(activity, 128)).apply { setMargins(margin, Cinema.px(activity, if (i > 0) 14 else 28), pageEnd, 0) }) }
                    // Everything not yet finished, including requests no release has been found for. Kept last so first focus stays on the top row.
                    val pendingQueue = queue.orEmpty().filter { it.canCancel && (it.isActive || it.isWaiting) }
                    val pendingRequests = requests.filter { it.stage in pendingStages && diagnostics[it.id]?.stopped != true }
                    if (pendingQueue.isNotEmpty() || pendingRequests.isNotEmpty() || wantedEpisodes.isNotEmpty()) {
                        val total = pendingQueue.size + pendingRequests.size + wantedEpisodes.size
                        holder.addView(pill("Cancel all pending ($total)", accent = false).apply { setOnClickListener { cancelAllPending(pendingQueue, pendingRequests, wantedEpisodes) { refresh() } } },
                            LinearLayout.LayoutParams(-2, Cinema.px(activity, 76)).apply { setMargins(margin, Cinema.px(activity, 36), 0, 0) })
                    }
                    if (firstLoad) { firstLoad = false; (0 until holder.childCount).map { holder.getChildAt(it) }.firstOrNull { it.isFocusable }?.let { requestFocusRobust(overlay as ViewGroup, it) } }
                    else focused?.let { holder.getChildAt(it)?.requestFocus() }
                    loadStorage(force = true)
                    // Progress moves, so look again every little while for as long as this page is the one on screen.
                    scheduled = Runnable { if (gen == generation && current == Dest.Downloads && !detailOpen) refresh() }
                    ui.postDelayed(scheduled!!, 15_000)
                }
            }
        }
        refresh()
    }

    private val queueClient by lazy { QueueClient(seerr, AndroidPlatform(activity)) }

    private val libraryRemover by lazy { LibraryRemover(seerr) }

    /** Takes a downloaded title off disk after the owner confirms. Radarr or Sonarr is told too, so it is not fetched again. */
    /** The episodes of a series the viewer finished in a stream played here. */
    private fun streamedEpisodes(e: Entry): List<com.danielportelabyrne.cinema.data.movies.StreamedEpisode> =
        e.title.imdbId?.let { com.danielportelabyrne.cinema.data.movies.StreamProgress.forSeries(streamProgress(), it) }.orEmpty()

    /** Deletes one episode's file: through Sonarr when it holds the series, else from Jellyfin. [done] runs on the UI thread. */
    private fun deleteEpisodeFiles(tmdb: Long?, season: Int, number: Int, mine: JellyfinItem?, done: (Boolean) -> Unit) {
        slowPool.execute {
            val managed = tmdb != null && sonarr.managesSeries(tmdb) && sonarr.deleteEpisodeFiles(tmdb, season, listOf(number))
            val removed = managed || (mine != null && jellyfin.deleteItem(mine.id))
            if (removed) jellyfin.refreshLibrary()
            activity.runOnUiThread { done(removed) }
        }
    }

    private fun confirmDeleteEpisode(e: Entry, tmdb: Long?, season: Int, number: Int, mine: JellyfinItem?, after: (Boolean) -> Unit) {
        CinemaConfirm.show(infoSheet, activity, "DELETE", "${e.title.name} · S$season E$number",
            "Removes this episode's file from disk. The rest of the season stays. This cannot be undone.", "Delete this episode") {
            deleteEpisodeFiles(tmdb, season, number, mine) { removed ->
                CinemaToast.show(activity, if (removed) "Deleted S$season E$number" else "Couldn't delete it right now", Toast.LENGTH_LONG)
                after(removed)
            }
        }
    }

    /** Deletes a season's files and its Seerr request, so the season can be requested and downloaded again. */
    private fun confirmDeleteSeason(e: Entry, tmdb: Long?, season: SeasonState, owned: List<JellyfinItem>, known: Set<Int>, after: () -> Unit) {
        CinemaConfirm.show(infoSheet, activity, "DELETE", "${e.title.name} · ${season.name}",
            "Removes every episode of ${season.name} from disk, and its request, so it can be downloaded again. This cannot be undone.", "Delete the season") {
            slowPool.execute {
                val here = owned.filter { it.season == season.number }
                val numbers = (here.mapNotNull { it.episode } + known).distinct()
                if (tmdb != null && sonarr.managesSeries(tmdb)) sonarr.deleteEpisodeFiles(tmdb, season.number, numbers)
                here.forEach { jellyfin.deleteItem(it.id) }
                val request = if (tmdb != null && seerr.isSignedIn) seerr.requests().firstOrNull { it.kind == Kind.SHOW && it.tmdbId == tmdb && it.seasons == listOf(season.number) } else null
                val cancelled = request?.let { seerr.cancelRequest(it.id) } ?: true
                jellyfin.refreshLibrary()
                seerr.forgetTitles()
                activity.runOnUiThread {
                    season.availability = Availability.NONE
                    if (tmdb != null) deletedSeasons += tmdb to season.number
                    CinemaToast.show(activity, if (cancelled) "Deleted ${season.name}" else "Deleted ${season.name}, but its request is still there", Toast.LENGTH_LONG)
                    after()
                }
            }
        }
    }

    private fun confirmDelete(e: Entry) {
        val ref = LibraryRef(e.title.name, e.title.year, e.title.tmdbId?.toLongOrNull(), e.title.imdbId)
        CinemaConfirm.show(infoSheet, activity, "DELETE", e.title.name,
            "Removes every file for this title from the storage, and from Radarr or Sonarr so it is not downloaded again. Titles those do not hold are removed from Jellyfin. This cannot be undone.", "Delete it") {
            slowPool.execute {
                // Radarr or Sonarr first, so nothing fetches it again. A title they do not hold (added some other way, such as
                // public-domain films) is deleted from Jellyfin, which removes its files.
                val managed = libraryRemover.delete(e.title.kind, ref)
                val lib = e.library
                val removed = managed || (lib != null && jellyfin.deleteItem(lib.id))
                if (removed) jellyfin.refreshLibrary()
                // Fetch the library now, so the page does not keep showing what has gone. Only a title Jellyfin removed itself can
                // be checked straight away; one Radarr or Sonarr removed reappears in Jellyfin only after its scan.
                val stillThere = if (removed && lib != null) jellyfin.stillInLibrary(lib.id) else null
                activity.runOnUiThread {
                    val message = when {
                        !removed -> "Couldn't delete ${e.title.name} right now"
                        !managed && stillThere == true -> "Jellyfin still lists ${e.title.name}, so its files weren't removed"
                        else -> "Deleted ${e.title.name}"
                    }
                    CinemaToast.show(activity, message, Toast.LENGTH_LONG)
                    if (removed && detailOpen) { closeDetail(restoreFocus = true); build() }
                    loadStorage(force = true)
                }
            }
        }
    }

    /** Cancels every pending download and withdraws every pending request, after the owner confirms. Anything already finished is left alone. */
    private fun cancelAllPending(items: List<QueueItem>, requests: List<SeerrRequest>, wanted: List<WantedEpisode>, refresh: () -> Unit) {
        infoSheet.show("DOWNLOADS", "Cancel all pending", "${items.size + requests.size + wanted.size} waiting or downloading", listOf(
            "Stops everything in the queue, takes it out of the download client and deletes anything already downloaded for it. " +
                "Requests and episodes that have no release yet are withdrawn. Finished titles are not touched."), null, listOf(
            SheetAction("Cancel all", "Stop every pending download", primary = true) {
                slowPool.execute {
                    val failed = items.count { !queueClient.cancel(it) } + requests.count { !queueClient.cancelRequest(it) } +
                        if (sonarr.unwant(wanted.map { it.id })) 0 else 1
                    activity.runOnUiThread {
                        CinemaToast.show(activity, if (failed == 0) "Cancelled everything pending" else "$failed couldn't be cancelled right now", Toast.LENGTH_LONG)
                        refresh()
                    }
                }
            },
            SheetAction("Keep them") { }
        ))
    }

    /**
     * One request on the Downloads page (`.dl`): a glass card with the poster, "Title · S1 E7 · Name", a status dot and
     * line, a striped progress bar (sweeping while sources are being found) and the percentage in Fraunces ("—" when
     * nothing is known).
     */
    private fun requestCard(posterUrl: String?, name: String, detail: String?, status: String, active: Boolean, progress: Float?, sweeping: Boolean, failed: Boolean = false): GlowCard {
        val card = GlowCard(activity, 26f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 20), 0, Cinema.px(activity, 36), 0); contentDescription = name }
        val thumb = FrameLayout(activity).apply {
            setBackgroundColor(Cinema.PLACEHOLDER); clipToOutline = true; elevation = Cinema.pxf(activity, 8)
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 10)) } }
        }
        val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumb.addView(image, FrameLayout.LayoutParams(-1, -1))
        posterUrl?.let { u -> images.load(u, maxWidth = 120) { b -> image.setImageBitmap(b) } }
        card.addView(thumb, LinearLayout.LayoutParams(Cinema.px(activity, 68), Cinema.px(activity, 96)))

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val line = android.text.SpannableStringBuilder(name).apply {
            detail?.takeIf { it.isNotBlank() }?.let { d ->
                val start = length; append(" ·  $d")
                setSpan(android.text.style.ForegroundColorSpan(Cinema.MUTED), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        column.addView(Cinema.text(activity, line, 27, Cinema.CREAM, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        val statusRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusRow.addView(Cinema.dot(activity, when { failed -> Cinema.TERRA; active -> Cinema.SAFFRON; else -> Cinema.DIM }, 9), LinearLayout.LayoutParams(Cinema.px(activity, 9), Cinema.px(activity, 9)).apply { marginEnd = Cinema.px(activity, 10) })
        statusRow.addView(Cinema.text(activity, status, 19, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        column.addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 8) })
        column.addView(CinemaBar(activity).apply { this.progress = progress; indeterminate = progress == null && sweeping },
            LinearLayout.LayoutParams(-1, Cinema.px(activity, 14)).apply { topMargin = Cinema.px(activity, 14) })
        card.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 28) })
        val percent = if (progress != null) Cinema.text(activity, "${Math.round(progress * 100)}%", 44, Cinema.CREAM, serif = true)
                      else Cinema.text(activity, "—", 30, Cinema.DIM)
        card.addView(percent.apply { gravity = Gravity.END }, LinearLayout.LayoutParams(Cinema.px(activity, 120), -2).apply { marginStart = Cinema.px(activity, 28) })
        card.onFocus = { lit -> if (lit) reveal(card) }
        return card
    }

    /** A queued torrent: open its controls to change priority, force it to start, or cancel it. */
    private fun queueRow(item: QueueItem, refresh: () -> Unit): View {
        val kind = if (item.source == QueueSource.SONARR) Kind.SHOW else Kind.MOVIE
        val info = knownTitles[item.tmdbId * 2 + if (kind == Kind.SHOW) 1 else 0]
        val status = queueStatus(item)
        val row = requestCard(info?.second, item.title, item.detail, status, active = item.isDownloading || item.status == "metadata",
            progress = item.progress(), sweeping = item.status == "metadata" || (item.status == "queued" && !item.clientVerified), failed = item.status == "failed")
        row.setOnClickListener {
            val actions = mutableListOf<SheetAction>()
            if (item.canPrioritize) {
                actions += SheetAction("Download next", "Move to the front of the queue", primary = true) { prioritizeDownload(item, DownloadAction.NEXT, refresh) }
                actions += SheetAction("Download now", "Bypass the queue; other downloads continue") { prioritizeDownload(item, DownloadAction.NOW, refresh) }
            }
            if (item.canCancel) actions += SheetAction("Cancel download", "Stop it and remove its partial files") {
                worker.execute {
                    val done = queueClient.cancel(item)
                    activity.runOnUiThread { CinemaToast.show(activity, if (done) "Cancelled ${item.title}" else "Couldn't cancel it right now", Toast.LENGTH_LONG); if (done) refresh() }
                }
            }
            infoSheet.show("DOWNLOAD", item.title, item.detail, listOf(status), null, actions)
        }
        return row
    }

    private fun prioritizeDownload(item: QueueItem, action: DownloadAction, refresh: () -> Unit) {
        worker.execute {
            val result = queueClient.prioritize(item, action)
            val kind = queueClient.clientFor(item)?.config?.kind
            val clientName = kind?.label ?: "the download client"
            activity.runOnUiThread {
                if (result == DownloadActionResult.NEEDS_SIGN_IN && kind != null) {
                    com.danielportelabyrne.cinema.ui.room.DownloadClientSignInSheet(activity, infoSheet)
                        .show(kind, { u, p -> queueClient.signIn(item, u, p) }) { prioritizeDownload(item, action, refresh) }
                    return@runOnUiThread
                }
                val message = when (result) {
                    DownloadActionResult.DONE -> if (action == DownloadAction.NEXT) "Moved ${item.title} to the front" else "Starting ${item.title} now"
                    DownloadActionResult.QUEUE_DISABLED -> "Queueing is switched off in $clientName. Use Download now instead."
                    DownloadActionResult.NOT_FOUND -> "This download has finished or left the queue"
                    DownloadActionResult.NOT_CONFIGURED -> "Couldn't connect to this download's client"
                    DownloadActionResult.NEEDS_SIGN_IN -> "Sign in to $clientName"
                    DownloadActionResult.FAILED -> "Couldn't change the download right now"
                }
                CinemaToast.show(activity, message, Toast.LENGTH_LONG)
                refresh()
            }
        }
    }

    /** Episodes of one series asked for one at a time, with no release found or grabbed yet. Pressing it offers to stop looking for them. */
    private fun wantedRow(tmdb: Long, episodes: List<WantedEpisode>, refresh: () -> Unit): View {
        val info = knownTitles[tmdb * 2 + 1]
        val title = info?.first ?: episodes.first().seriesTitle.ifBlank { "Title $tmdb" }
        val sorted = episodes.sortedWith(compareBy({ it.season }, { it.number }))
        val listed = sorted.take(3).joinToString(", ") { "S${it.season} E${it.number}" } + if (sorted.size > 3) " +${sorted.size - 3}" else ""
        val status = "Waiting for a release · $listed"
        val row = requestCard(info?.second, title, listed, "Waiting for a release", active = false, progress = null, sweeping = false)
        row.setOnClickListener {
            infoSheet.show("WANTED", title, null, listOf(status, "Cancelling stops looking for these episodes. Anything already downloaded is kept."), null, listOf(
                SheetAction("Cancel", "Stop looking for them", primary = true) {
                    worker.execute {
                        val done = sonarr.unwant(episodes.map { it.id })
                        activity.runOnUiThread { CinemaToast.show(activity, if (done) "Stopped looking for $title" else "Couldn't cancel it right now", Toast.LENGTH_LONG); if (done) refresh() }
                    }
                },
                SheetAction("Keep them") { }
            ))
        }
        return row
    }

    /** Episodes of one series asked for before they air. Pressing it offers to stop waiting for them. */
    private fun upcomingRow(tmdb: Long, episodes: List<WantedEpisode>, refresh: () -> Unit): View {
        val info = knownTitles[tmdb * 2 + 1]
        val title = info?.first ?: episodes.first().seriesTitle.ifBlank { "Title $tmdb" }
        val sorted = episodes.sortedWith(compareBy({ it.season }, { it.number }))
        val first = sorted.first()
        val listed = sorted.take(3).joinToString(", ") { "S${it.season} E${it.number}" } + if (sorted.size > 3) " +${sorted.size - 3}" else ""
        val status = "Waiting to air · S${first.season} E${first.number} · " + (EpisodeCards.formatDate(first.airDate)?.substringBeforeLast(' ') ?: "TBA")
        val row = requestCard(info?.second, title, listed, status, active = false, progress = null, sweeping = false)
        row.setOnClickListener {
            infoSheet.show("UPCOMING", title, null, listOf(status, "These download by themselves once they air. Cancelling stops waiting for them."), null, listOf(
                SheetAction("Cancel", "Stop waiting for them", primary = true) {
                    slowPool.execute {
                        val done = sonarr.unwant(episodes.map { it.id })
                        activity.runOnUiThread { CinemaToast.show(activity, if (done) "Stopped waiting for $title" else "Couldn't cancel it right now", Toast.LENGTH_LONG); if (done) refresh() }
                    }
                },
                SheetAction("Keep them") { }
            ))
        }
        return row
    }

    /** Where this item stands, in words: how it is going, or its place in line. */
    private fun queueStatus(item: QueueItem): String = when {
        item.status == "metadata" -> "Finding torrent details" + if (item.forced) " · starting immediately" else ""
        item.status == "stalled" -> "Waiting for sources" + if (item.forced) " · starting immediately" else ""
        item.status == "checking" -> "Checking downloaded files"
        item.status == "downloading" -> {
            val left = ByteSize.format(item.sizeLeftBytes) + " left"
            val eta = item.etaSeconds?.takeIf { it < 604_800 && item.seeds != 0 }?.let { s -> if (s < 90) " · almost done" else if (s < 7200) " · about ${s / 60} min left" else " · about ${s / 3600} h left" }.orEmpty()
            val rate = item.speedBytes?.takeIf { it > 0 }?.let { bytes ->
                if (bytes >= 1_000_000) String.format(Locale.UK, " · %.1f MB/s", bytes / 1e6)
                else if (bytes >= 1000) " · ${bytes / 1000} KB/s" else " · $bytes B/s"
            }.orEmpty()
            val seeds = item.seeds?.let { " · $it " + if (it == 1) "seeder" else "seeders" }.orEmpty()
            (if (item.forced) "Downloading now" else "Downloading") + " · $left$rate$seeds$eta"
        }
        item.status == "paused" -> "Paused"
        item.status == "delayed" -> "Waiting for a better release"
        item.position == 1 -> "Next in line · waiting for a free download slot"
        item.position > 1 -> "In line · #${item.position} · waiting for a free download slot"
        item.status == "queued" && !item.clientVerified && item.sizeBytes == 0L -> "Sent to the download client · waiting for torrent details"
        item.status == "queued" && !item.clientVerified -> "Queued in the download client"
        item.status == "queued" -> "Queued · waiting for a free download slot"
        item.status == "completed" -> "Finished · importing"
        item.status == "failed" -> "Failed"
        item.status == "warning" -> "Needs attention"
        else -> item.status.replaceFirstChar { it.uppercase() }
    }

    /** One request: poster, title, what is happening, and a bar for how far it has got. */
    private fun downloadRow(r: SeerrRequest, refresh: () -> Unit): View {
        val info = knownTitles[titleKey(r)]
        val row = requestCard(info?.second, info?.first ?: "Title ${r.tmdbId}", null, statusLine(r), active = r.stage == Stage.DOWNLOADING || r.stage == Stage.SEARCHING,
            progress = r.progress(), sweeping = r.stage == Stage.SEARCHING && requestDiagnostics[r.id]?.stopped != true, failed = r.stage == Stage.FAILED || r.stage == Stage.DECLINED)
        if (r.canCancel) row.setOnClickListener {
            val name = info?.first ?: "Title ${r.tmdbId}"
            val actions = mutableListOf<SheetAction>()
            val diagnostic = requestDiagnostics[r.id]
            val canSearch = diagnostic?.canRetry == true || (!seerr.hasRequestHelper && r.stage == Stage.SEARCHING)
            if (canSearch) actions += SheetAction("Search again", "Look for a release now", primary = true) {
                worker.execute {
                    val accepted = if (seerr.hasRequestHelper) seerr.retrySearch(r.id) else queueClient.searchAgain(r)
                    activity.runOnUiThread {
                        CinemaToast.show(activity, if (accepted) "Search requested" else "Search couldn't start yet. Try again shortly.", Toast.LENGTH_LONG)
                        refresh()
                    }
                }
            }
            actions += SheetAction("Cancel request", "Stop looking for a release", primary = true) {
                    worker.execute {
                        val done = queueClient.cancelRequest(r)
                        activity.runOnUiThread {
                            CinemaToast.show(activity, if (done) "Cancelled $name" else "Couldn't cancel this request right now", Toast.LENGTH_LONG)
                            if (done) refresh()
                        }
                    }
                }
            infoSheet.show("REQUEST", name, null, listOf(statusLine(r), "Cancelling stops looking for this request. Completed files and other seasons are kept."), null, actions)
        }
        return row
    }

    private fun statusLine(r: SeerrRequest): String {
        requestDiagnostics[r.id]?.let { diagnostic ->
            val retry = diagnostic.nextSearchAtMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000 }
            val last = diagnostic.lastSearchAtMs?.let { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(it)) }
            return diagnostic.message + if (diagnostic.canRetry) {
                (last?.let { " · checked $it" }.orEmpty()) + (retry?.let { if (it < 1) " · retry shortly" else " · retry in $it min" }.orEmpty())
            } else ""
        }
        return when (r.stage) {
        Stage.WAITING_APPROVAL -> "Waiting for approval"
        Stage.SEARCHING -> "Looking for a release · nothing downloading yet"
        Stage.DOWNLOADING -> {
            val done = ByteSize.format(r.totalBytes - r.leftBytes) + " of " + ByteSize.format(r.totalBytes)
            val left = r.etaMs()?.let { (it - System.currentTimeMillis()) / 60_000L }
            "Downloading · $done" + when { left == null -> ""; left < 1 -> " · almost done"; left < 120 -> " · about $left min left"; else -> " · about ${left / 60} h left" }
        }
        Stage.READY -> "Ready to watch in your library"
        Stage.DECLINED -> "Declined"
        Stage.FAILED -> "Failed · the download could not be completed"
        }
    }

    // --- Storage -----------------------------------------------------------------------------------

    /** Storage information is visible in the expanded menu, without crowding the collapsed icon rail. */
    private val storagePrefs by lazy { activity.getSharedPreferences("storage", Context.MODE_PRIVATE) }

    private fun showStorage(text: String) {
        storageView.text = text
        // Laid out whether the rail is open or not, so opening it never moves the items; the rail's progress fades it.
        storageView.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
        storageView.alpha = railP
    }

    private fun loadStorage(force: Boolean = false) {
        val saved = storagePrefs.getString("text", null)
        saved?.let { showStorage(it) }
        if (!force && saved != null && System.currentTimeMillis() - storagePrefs.getLong("at", 0L) < STORAGE_TTL_MS) return
        storagePool.execute {
            val used = jellyfin.takeIf { it.isConnected }?.libraryBytes()
            val free = seerr.takeIf { it.isSignedIn }?.freeSpaceBytes()
            val text = listOfNotNull(used?.let { "${ByteSize.format(it)} in library" }, free?.let { "${ByteSize.format(it)} free" }).joinToString("\n")
            if (text.isNotEmpty()) storagePrefs.edit().putString("text", text).putLong("at", System.currentTimeMillis()).apply()
            activity.runOnUiThread { if (text.isNotEmpty() || saved == null) showStorage(text) }
        }
    }

    // --- Search ------------------------------------------------------------------------------------

    /** The search page's live parts, so voice and typing can update them. */
    private class SearchUi(
        val field: LinearLayout, val icon: ImageView, val caret: View, val query: TextView, val hint: TextView,
        val mic: View, val keyboardArea: List<View>, val voiceView: VoiceListeningView, val side: LinearLayout, val results: LinearLayout
    )
    private var searchUi: SearchUi? = null
    private var searchText = ""
    private var voice: VoiceSearch? = null
    private var voiceHeld = false
    private var voiceKeyDownAt = 0L

    /**
     * Search (`22-search`): a field with a microphone, the in-app QWERTY keyboard on the left (no system keyboard),
     * trending titles and genres on the right, and the remote's hints at the bottom. Results arrive live as you type,
     * as list cards on the right and poster rows below. Voice (`23`) replaces the keyboard while it listens.
     */
    private fun buildSearch() {
        val left = Cinema.px(activity, 176)
        val stage = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        content.addView(stage, LinearLayout.LayoutParams(-1, Cinema.px(activity, 1080)))
        fun place(v: View, x: Int, y: Int, w: Int = -2, h: Int = -2) = stage.addView(v, FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y })

        // The field: search icon, caret, the query (or its hint), "Say a title, actor or theme" and the mic button.
        // The same search bar as the Letterboxd lists search (see SearchField): the icon, the query, the hint, phone and mic.
        val icon = Cinema.icon(activity, R.drawable.ic_cin_search, Cinema.SAFFRON)
        val field = SearchField(activity, icon, prompt = "Search films, series and documentaries", hintText = "Say a title, actor or theme")
        val caret = field.caret; val query = field.query; val hint = field.hint; val mic = field.mic
        field.onMic = { startVoice(held = false) }
        field.onPhone = { PhoneInput.toggleQr() }
        usePhoneTarget(field, phoneTargetFor("search", "Search", Qwerty.MAX_LENGTH, { searchText }, ::phoneTextIntoSearch, ::phoneSearchNow))
        place(field, left, Cinema.px(activity, 64), activity.resources.displayMetrics.widthPixels - left - pageEnd, Cinema.px(activity, 112))
        caret.animate().alpha(0f).setDuration(530).withEndAction(object : Runnable {
            override fun run() { if (caret.isAttachedToWindow) caret.animate().alpha(if (caret.alpha < 0.5f) 1f else 0f).setDuration(530).withEndAction(this).start() }
        }).start()

        // Keyboard side.
        val eyebrow = Cinema.eyebrow(activity, listOf("Type to search"), color = Cinema.DIM, markAlpha = 0.5f)
        place(eyebrow, left, Cinema.px(activity, 222))
        val keyboard = QwertyKeyboard(activity) { key -> typeKey(key) }
        keyboard.onLeave = { leaveKeyboard(field) }
        keyboard.upTarget = { searchBarControl(field) }
        place(keyboard, left, Cinema.px(activity, 300), Cinema.px(activity, 980), Cinema.px(activity, 476))
        val hints = Cinema.keyHints(activity, listOf("MIC" to "Hold on your remote to speak", "OK" to "Select", "▲" to "Voice search"))
        stage.addView(hints, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = left; bottomMargin = Cinema.px(activity, 48) })

        // Right side: trending titles and genres, or the results once there is a query.
        val side = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        place(side, Cinema.px(activity, 1232), Cinema.px(activity, 230), Cinema.px(activity, 592))
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(results, LinearLayout.LayoutParams(-1, -2))

        // Voice, listening: takes the place of the keyboard and the right side.
        val voiceView = VoiceListeningView(activity).apply { visibility = View.GONE }
        stage.addView(voiceView, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = left; rightMargin = pageEnd; topMargin = Cinema.px(activity, 230) })

        val ui = SearchUi(field, icon, caret, query, hint, mic, listOf(eyebrow, keyboard, hints, side), voiceView, side, results)
        searchUi = ui
        searchText = ""
        showQuery()
        showTrending(ui)
        keyboard.keys.firstOrNull { it.contentDescription == "q" }?.let { requestFocusRobust(overlay as ViewGroup, it) }
    }

    /** A key from the on-screen keyboard: edit the query, show it, and search once typing pauses. */
    private fun typeKey(key: Qwerty.Key) {
        searchText = Qwerty.type(searchText, key)
        showQuery()
        searchPending?.let { ui.removeCallbacks(it) }
        val q = searchText.trim()
        searchPending = Runnable { runSearch(q) }.also { ui.postDelayed(it, 500) }
    }

    /** Puts the caret just after the typed text ([after]), or before the grey prompt when nothing is typed yet. */
    /** Back on the keyboard leaves it: focus goes to the search field's last control (the microphone), and the page stays open. */
    /** Where Up from the top of a keyboard lands: the search bar's first button (the phone, or the microphone). */
    private fun searchBarControl(field: com.danielportelabyrne.cinema.design.cinema.SearchField): View? =
        listOf(field.phone, field.mic).firstOrNull { it.isShown && it.isFocusable }

    private fun leaveKeyboard(field: View) {
        val group = field as? ViewGroup ?: return
        (group.childCount - 1 downTo 0).map { group.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus()
    }

    private fun placeCaret(s: SearchUi, after: Boolean) {
        val line = s.caret.parent as? LinearLayout ?: return
        val caretFirst = line.indexOfChild(s.caret) == 0
        if (caretFirst == !after) return
        line.removeView(s.caret)
        line.addView(s.caret, if (after) 1 else 0)
        (s.query.layoutParams as LinearLayout.LayoutParams).marginStart = if (after) 0 else Cinema.px(activity, 22)
        (s.caret.layoutParams as LinearLayout.LayoutParams).marginStart = if (after) Cinema.px(activity, 6) else 0
        line.requestLayout()
    }

    private fun showQuery(partial: String? = null) {
        val s = searchUi ?: return
        (s.field as? SearchField)?.render(searchText, partial)
    }

    /** The text a phone is typing for the global search box: it is the query, live, and the search runs once it settles. */
    private fun phoneTextIntoSearch(text: String) {
        searchText = text.take(Qwerty.MAX_LENGTH)
        showQuery()
        val ui = searchUi?.field ?: return
        searchPending?.let { ui.removeCallbacks(it) }
        val q = searchText.trim()
        searchPending = Runnable { runSearch(q) }.also { ui.postDelayed(it, 500) }
    }

    /** The phone's Done on the global search: search now, without waiting for the pause. */
    private fun phoneSearchNow() {
        val ui = searchUi?.field ?: return
        searchPending?.let { ui.removeCallbacks(it) }
        runSearch(searchText.trim())
    }

    /** One list card on the search page's right side: poster, name, "Film · 2026", chevron. */
    private fun searchRow(e: Entry, related: List<Title>): View {
        val card = GlowCard(activity, 24f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 20), 0, Cinema.px(activity, 20), 0); contentDescription = e.title.name }
        val thumb = FrameLayout(activity).apply {
            setBackgroundColor(Cinema.PLACEHOLDER); clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 9)) } }
        }
        val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumb.addView(image, FrameLayout.LayoutParams(-1, -1))
        e.title.posterUrl?.let { u -> images.load(u, e.posterHeaders, maxWidth = 160) { b -> image.setImageBitmap(b) } }
        card.addView(thumb, LinearLayout.LayoutParams(Cinema.px(activity, 58), Cinema.px(activity, 78)))
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        text.addView(Cinema.text(activity, e.title.name, 27, Cinema.CREAM, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        text.addView(Cinema.text(activity, listOfNotNull(if (e.title.kind == Kind.SHOW) "Series" else "Film", e.title.year?.toString(), if (e.library != null) "In your library" else null).joinToString(" · "), 18, Cinema.DIM),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 5) })
        card.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 22) })
        card.addView(Cinema.icon(activity, R.drawable.ic_cin_chev, Cinema.DIM), LinearLayout.LayoutParams(Cinema.px(activity, 26), Cinema.px(activity, 26)))
        card.setOnClickListener { openDetail(e, related) }
        return card
    }

    /** The right side with no query: trending searches (today's top titles) and genres to browse. */
    private fun showTrending(s: SearchUi) {
        s.side.removeAllViews()
        addRecent(SearchKeyboard.GLOBAL, s.side, search = { term ->
            searchText = term; showQuery()
            searchPending?.let { s.field.removeCallbacks(it) }
            runSearch(term)
        }, refresh = { showTrending(s) })
        val head =LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Cinema.icon(activity, R.drawable.ic_cin_trend, Cinema.DIM), LinearLayout.LayoutParams(Cinema.px(activity, 22), Cinema.px(activity, 22)).apply { marginEnd = Cinema.px(activity, 14) })
        head.addView(Cinema.text(activity, "Trending searches", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true))
        s.side.addView(head)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        s.side.addView(list)
        val gen = generation
        shelfPool.execute {
            val top = catalog.shelf(Catalog.homePage().first()).titles.take(3).map { entry(it) }
            activity.runOnUiThread {
                if (gen != generation || searchText.isNotBlank()) return@runOnUiThread
                top.forEach { e -> list.addView(searchRow(e, top.map { it.title }), LinearLayout.LayoutParams(-1, Cinema.px(activity, 104)).apply { topMargin = Cinema.px(activity, 18) }) }
            }
        }
        s.side.addView(Cinema.text(activity, "Browse by theme", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 40) })
        // Real genres, two rows of three, each opening its own page.
        val themes = listOf("drm", "trl", "scf", "cmy", "hst", "ani").mapNotNull { code -> Catalog.GENRES.firstOrNull { it.code == code } }
        themes.chunked(3).forEachIndexed { r, chunk ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { i, g ->
                row.addView(pill(g.label, accent = false).apply { setOnClickListener { go(Dest.InGenre(g), remember = true) } },
                    LinearLayout.LayoutParams(-2, Cinema.px(activity, 50)).apply { if (i > 0) marginStart = Cinema.px(activity, 12) })
            }
            s.side.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, if (r == 0) 18 else 12) })
        }
    }

    /**
     * The searches run lately on [keyboard], as pills at the top of [into]: a press searches that again. Nothing is drawn
     * when there are none. [search] runs a term; [refresh] redraws the side after the history is cleared.
     */
    private fun addRecent(keyboard: SearchKeyboard, into: LinearLayout, search: (String) -> Unit, refresh: () -> Unit) {
        val terms = recentSearches(keyboard).take(RecentSearches.SHOWN)
        if (terms.isEmpty()) return
        into.addView(Cinema.text(activity, "Recent searches", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true))
        terms.forEachIndexed { i, term ->
            into.addView(pill(term, accent = false).apply { ellipsize = TextUtils.TruncateAt.END; setOnClickListener { search(term) } },
                LinearLayout.LayoutParams(-2, Cinema.px(activity, 54)).apply { topMargin = Cinema.px(activity, if (i == 0) 18 else 12) })
        }
        into.addView(pill("Clear recent", accent = false).apply { setOnClickListener { saveRecentSearches(keyboard, emptyList()); refresh() } },
            LinearLayout.LayoutParams(-2, Cinema.px(activity, 54)).apply { topMargin = Cinema.px(activity, 12); bottomMargin = Cinema.px(activity, 40) })
    }

    /** [a] and [b] taken in turn, [max] in all, so both kinds show; when one runs out the other fills the rest. */
    private fun <T> interleave(a: List<T>, b: List<T>, max: Int): List<T> {
        val out = ArrayList<T>()
        var i = 0
        var j = 0
        while (out.size < max && (i < a.size || j < b.size)) {
            if (i < a.size) out.add(a[i++])
            if (out.size < max && j < b.size) out.add(b[j++])
        }
        return out
    }

    /** Searches the library and the catalogue for [query]; at least two letters. Results replace the right side. */
    private fun runSearch(query: String) {
        val s = searchUi ?: return
        val g = ++generation
        s.results.removeAllViews()
        if (query.length < 2) { showTrending(s); return }
        shelfPool.execute {
            // Only what is already loaded: a slow or sleeping Jellyfin must not hold up the catalogue's results.
            val mine = if (jellyfin.isConnected) jellyfin.loadedLibrary().filter { it.name.contains(query, ignoreCase = true) } else emptyList()
            val found = Catalog.searchSpecs(query).map { catalog.shelf(it) }
            activity.runOnUiThread {
                if (g != generation || searchUi !== s) return@runOnUiThread
                val library = libraryEntries(mine)
                val shelves = found.map { it.heading to it.titles.map { t -> entry(t) } }
                s.side.removeAllViews()
                s.side.addView(Cinema.text(activity, "Results for “$query”", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                val all = (library + shelves.flatMap { it.second }).distinctBy { it.title.name.lowercase() + it.title.year }
                // Films and series in turn, so the preview shows both; everything else is in the results below the keyboard.
                val top = interleave(all.filter { it.title.kind != Kind.SHOW }, all.filter { it.title.kind == Kind.SHOW }, PREVIEW_ROWS)
                if (all.isNotEmpty()) saveRecentSearches(SearchKeyboard.GLOBAL, RecentSearches.add(recentSearches(SearchKeyboard.GLOBAL), query))
                if (top.isEmpty()) s.side.addView(note("Nothing found for “$query”"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
                top.forEach { e -> s.side.addView(searchRow(e, top.map { it.title }), LinearLayout.LayoutParams(-1, Cinema.px(activity, 104)).apply { topMargin = Cinema.px(activity, 18) }) }
                if (all.size > top.size) s.side.addView(Cinema.text(activity, "Scroll down for all ${all.size} results", 19, Cinema.DIM).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) })
                // Everything found, as poster rows below the keyboard.
                fun add(heading: String, entries: List<Entry>) {
                    if (entries.isEmpty()) return
                    val r = row(heading, null, into = s.results)
                    r.setCount(entries.size)
                    val related = entries.map { it.title }
                    entries.forEachIndexed { i, e -> r.inner.addView(card(e, related), cardParams(i)) }
                }
                add("In your library", library)
                shelves.forEach { (h, list) -> add(h, list) }
            }
        }
    }

    // --- Voice ---

    /** Starts listening: [held] when the remote's mic button is being held (letting go finishes). */
    private fun startVoice(held: Boolean) {
        if (current != Dest.Search) go(Dest.Search)
        voiceHeld = held
        voiceSink = null
        ensureVoice().start()
    }

    /** The one recogniser; its states go to whichever screen started it (the global search, or a list search). */
    private fun ensureVoice(): VoiceSearch = voice ?: VoiceSearch(activity) { state -> (voiceSink ?: ::onVoice).invoke(state) }.also { voice = it }

    /** Set by a screen that started listening itself (the list search); null hands states to the global search. */
    private var voiceSink: ((VoiceState) -> Unit)? = null

    private fun onVoice(state: VoiceState) {
        val s = searchUi ?: return
        val listening = state is VoiceState.Listening || state is VoiceState.Requesting
        s.keyboardArea.forEach { it.visibility = if (listening) View.INVISIBLE else View.VISIBLE }
        s.voiceView.visibility = if (listening) View.VISIBLE else View.GONE
        when (state) {
            is VoiceState.Listening -> {
                showQuery(state.partial)
                s.voiceView.update(state.partial, state.level, if (voiceHeld) "Release the mic button when you’re done · Back to cancel" else "Speak now · Back to cancel")
            }
            VoiceState.Requesting -> { showQuery(""); s.voiceView.update("", 0f, "Allow the microphone to search by voice") }
            is VoiceState.Done -> {
                searchText = state.text.take(Qwerty.MAX_LENGTH); showQuery()
                searchPending?.let { ui.removeCallbacks(it) }
                runSearch(searchText.trim())
                s.mic.requestFocus()
            }
            is VoiceState.Unavailable -> {
                showQuery()
                s.hint.text = state.reason
                s.mic.requestFocus()
            }
            VoiceState.Idle -> { showQuery(); s.mic.requestFocus() }
        }
    }

    /** The voice key on the remote (search / assistant). Down starts holding-to-talk; up finishes. */
    fun voiceKey(down: Boolean, repeat: Int): Boolean {
        if (!isVisible) return false
        if (down && repeat == 0) { voiceKeyDownAt = android.os.SystemClock.uptimeMillis(); startVoice(held = true) }
        else if (!down && voiceHeld && android.os.SystemClock.uptimeMillis() - voiceKeyDownAt > 600) voice?.stop()
        return true
    }

    /** Forwarded from MainActivity's permission result. */
    fun onPermissionResult(requestCode: Int, granted: Boolean) { if (requestCode == VoiceSearch.PERMISSION_REQUEST) voice?.onPermission(granted) }

    private val voiceActive: Boolean get() = voice?.state.let { it is VoiceState.Listening || it is VoiceState.Requesting }

    /** The search field's microphone: a saffron-to-terracotta disc with soft halo rings; grows and glows on focus. */
    // --- Featured title ----------------------------------------------------------------------------

    /**
     * The featured title (`01-movies-top`): the backdrop on the right, faded in from the left and down into the page,
     * with the eyebrow, a big Fraunces title, the meta line, synopsis, provider and buttons on the left, and the
     * stripe indicators beneath. The picture runs past the frame's bottom, under the first row, as in the mockup.
     */
    private fun buildHero() {
        ui.removeCallbacks(heroTick)
        val hero = HeroFrame(activity, Cinema.px(activity, HERO_HEIGHT)).apply { clipChildren = false; clipToPadding = false }
        heroBackdrop = HeroBackdrop(activity, focusX = 0.5f, fadeTo = 0.38f)
        hero.addView(heroBackdrop, FrameLayout.LayoutParams(Cinema.px(activity, 1230), Cinema.px(activity, 1080), Gravity.END or Gravity.TOP))
        hero.addView(View(activity).apply { background = Scrim.hero() }, FrameLayout.LayoutParams(-1, Cinema.px(activity, 1080)))
        // The fades' left edge carried on behind the rail, so pushing the page aside never shows a different ground there.
        hero.addView(View(activity).apply { background = Scrim(listOf(0f to 0.98f, 1f to 0.98f), listOf(0f to 0.55f, 0.22f to 0f, 0.5f to 0f, 1f to 0.96f)) },
            FrameLayout.LayoutParams(Cinema.px(activity, RAIL_WIDTH_OPEN - RAIL_WIDTH) + 2, Cinema.px(activity, 1080)).apply { leftMargin = -Cinema.px(activity, RAIL_WIDTH_OPEN - RAIL_WIDTH) })
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(pageStart, Cinema.px(activity, 140), pageEnd, 0)
        }
        heroEyebrow = FrameLayout(activity)
        heroTitle = Cinema.title(activity, "", 128)
        heroMeta = FrameLayout(activity)
        heroBody = Cinema.synopsis(activity, "", 26, maxLines = 3)
        heroProvider = FrameLayout(activity)
        heroButtons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; visibility = View.INVISIBLE }
        heroDots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        column.addView(heroEyebrow)
        column.addView(heroTitle, LinearLayout.LayoutParams(Cinema.px(activity, 1100), -2).apply { topMargin = Cinema.px(activity, 22) })
        column.addView(heroMeta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        column.addView(heroBody, LinearLayout.LayoutParams(Cinema.px(activity, 820), -2).apply { topMargin = Cinema.px(activity, 26) })
        column.addView(heroProvider, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        column.addView(heroButtons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 34) })
        column.addView(heroDots, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 82) })
        hero.addView(column, FrameLayout.LayoutParams(-1, -2))
        hero.content = column
        heroFrame = hero
        content.addView(hero, LinearLayout.LayoutParams(-1, -2))
    }

    private lateinit var heroFrame: View
    private lateinit var heroBackdrop: HeroBackdrop
    private lateinit var heroEyebrow: FrameLayout
    private lateinit var heroMeta: FrameLayout
    private lateinit var heroProvider: FrameLayout
    private lateinit var heroDots: LinearLayout
    private var heroEntries: List<Entry> = emptyList()
    private var heroRelated: List<Title> = emptyList()
    private var heroLabels: List<String> = emptyList()
    private var heroIndex = 0
    private val heroTick = Runnable { advanceHero() }

    /** Up to three featured titles, each with its eyebrow label ("#1 in films today"); the hero turns through them. */
    private fun setHero(entries: List<Entry>, related: List<Title>, labels: List<String>) {
        if (entries.isEmpty()) return
        heroSet = true
        heroEntries = entries.take(3); heroRelated = related; heroLabels = labels.take(3)
        heroEntries.forEach { e -> whenInLibrary(e) { } }
        heroDots.removeAllViews()
        if (heroEntries.size > 1) heroEntries.indices.forEach { i ->
            heroDots.addView(View(activity), LinearLayout.LayoutParams(Cinema.px(activity, 64), Cinema.px(activity, 10)).apply { if (i > 0) marginStart = Cinema.px(activity, 8) })
        }
        showHero(0)
        // The other featured titles' pictures, ready in memory before their turn comes.
        heroEntries.drop(1).forEach { prefetchBackdrop(it) }
        ui.removeCallbacks(heroTick)
        if (heroEntries.size > 1) ui.postDelayed(heroTick, HERO_TURN_MS)
    }

    private fun advanceHero() {
        if (!isVisible || detailOpen || heroEntries.size < 2 || !heroFrame.isAttachedToWindow) return
        // Only turn while the hero is on screen; otherwise just wait for the next turn.
        if (page.scrollY < heroFrame.height / 2) {
            val column = (heroFrame as HeroFrame).content
            column?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction { showHero((heroIndex + 1) % heroEntries.size); column.animate().alpha(1f).setDuration(260).start() }?.start()
        }
        ui.postDelayed(heroTick, HERO_TURN_MS)
    }

    private fun showHero(index: Int) {
        heroIndex = index
        val e = heroEntries[index]
        val t = e.title
        heroEyebrow.removeAllViews()
        heroEyebrow.addView(Cinema.eyebrow(activity, listOfNotNull("Featured", heroLabels.getOrNull(index))))
        heroTitle.text = t.name
        Cinema.style(heroTitle, heroTitleSize(t.name), Cinema.CREAM, serif = true, tracking = -0.02f)
        heroMeta.removeAllViews(); heroMeta.addView(meta(t))
        heroBody.text = t.overview.orEmpty(); heroBody.visibility = if (t.overview.isNullOrBlank()) View.GONE else View.VISIBLE
        heroProvider.removeAllViews(); Cinema.provider(activity, t.services)?.let { heroProvider.addView(it) }
        heroProvider.visibility = if (heroProvider.childCount == 0) View.GONE else View.VISIBLE
        // Rebuilding the buttons keeps focus on the same position if the hero had it.
        val focusedAt = (0 until heroButtons.childCount).firstOrNull { heroButtons.getChildAt(it).isFocused } ?: -1
        heroButtons.removeAllViews()
        val gap = Cinema.px(activity, 20)
        fun add(b: CinemaButton) {
            // Coming back up to the featured title shows all of it: the page returns to the very top, not just far
            // enough to reveal the button.
            b.setOnFocusChangeListener { _, f -> if (f && !detailOpen) page.post { pageGlideTo(0) } }
            heroButtons.addView(b, LinearLayout.LayoutParams(-2, Cinema.px(activity, 76)).apply { if (heroButtons.childCount > 0) marginStart = gap })
        }
        e.library?.let { lib ->
            val state = if (lib.isSeries) WatchState.NotStarted else WatchProgress.of(lib.played, lib.resumeMs, lib.runtimeMin)
            WatchProgress.filmActions(state).forEach { action ->
                val restart = action == FilmAction.RESTART
                add(CinemaButton(activity, if (restart) CinemaButton.Kind.GLASS else CinemaButton.Kind.PRIMARY, WatchProgress.label(action, state), R.drawable.ic_cin_play)
                    .apply { setOnClickListener { watch(lib, t, fromStart = restart) } })
            }
        }
            ?: if (t.kind == Kind.MOVIE && seerr.hasStreamService) add(CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Stream now", R.drawable.ic_cin_play).apply { setOnClickListener { streamNow(e, this) } }) else Unit
        add(CinemaButton(activity, CinemaButton.Kind.GLASS, "More info", R.drawable.ic_cin_info).apply { setOnClickListener { openDetail(e, heroRelated) } })
        heroButtons.visibility = View.VISIBLE
        (0 until heroDots.childCount).forEach { i ->
            heroDots.getChildAt(i).background = if (i == index) StripeDrawable(horizontalBands = true, radius = Cinema.pxf(activity, 5))
                else android.graphics.drawable.GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 5); setColor(Cinema.alpha(Cinema.CREAM, 0.22f)) }
        }
        // The last picture stays up until this one is ready, then cross-fades; a title with no picture clears it.
        backdropFor(e)?.let { u ->
            images.loadBackdrop(u, maxWidth = HERO_BACKDROP_PX) { b -> if (heroEntries.getOrNull(heroIndex) === e) { heroBackdrop.show(b); frost?.refresh(400); Perf.end("movies.open->hero") } }
        } ?: heroBackdrop.show(null)
        if (focusedAt >= 0) heroButtons.getChildAt(minOf(focusedAt, heroButtons.childCount - 1))?.requestFocus()
        else if (!detailOpen && overlay.findFocus() == null && current != Dest.Search) requestFocusRobust(overlay as ViewGroup, heroButtons.getChildAt(0))
    }

    /** Long names step down from the mockup's 128px so they still fit on two lines. */
    private fun heroTitleSize(name: String) = when { name.length > 30 -> 76; name.length > 18 -> 96; else -> 128 }

    /** The meta line for a title: certificate, year, running time, genres, IMDb score; only what is known. */
    private fun meta(t: Title, sizePx: Int = 23): View = Cinema.meta(activity, t.age?.substringAfter("GB-"),
        listOfNotNull(t.year?.toString(), t.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }, t.genres.take(3).joinToString(" · ").ifBlank { null }),
        t.imdbScore?.let { String.format(Locale.UK, "%.1f", it) }, sizePx)

    /** "Top 10 films", 0 -> "#1 in films today"; any other shelf is labelled by its own heading. */
    private fun rankLabel(heading: String, ranked: Boolean, position: Int): String {
        if (!ranked) return heading
        val rest = heading.removePrefix("Top 10").trim()
        return when {
            rest.isEmpty() || rest == "today" -> "#${position + 1} today"
            rest.startsWith("on ") -> "#${position + 1} $rest today"
            heading.startsWith("Trending in ") -> "#${position + 1} in ${heading.removePrefix("Trending in ")}"
            else -> "#${position + 1} in $rest today"
        }
    }

    // --- Title page --------------------------------------------------------------------------------

    private fun openDetail(e: Entry, related: List<Title>) {
        returnFocus = overlay.findFocus()
        detail.removeAllViews(); detail.visibility = View.VISIBLE
        // The page's container only swallows clicks; it must never hold focus itself, or hiding the page beneath
        // parks focus on it instead of on the first button.
        detail.isFocusable = false
        body.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val t = e.title

        // Ground, then the backdrop on the right faded in from the left (`10/12-detail`), then the page's two fades.
        detail.setBackgroundColor(Cinema.BG)
        val backdrop = HeroBackdrop(activity, focusX = 1f, fadeTo = 0.38f)
        detail.addView(backdrop, FrameLayout.LayoutParams(Cinema.px(activity, if (e.library != null) 1060 else 1230), -1, Gravity.END))
        detail.addView(View(activity).apply { background = Scrim.detail() }, FrameLayout.LayoutParams(-1, -1))
        // From memory this arrives before the page is on screen, and shows at once; otherwise it fades in when ready.
        backdropFor(e)?.let { u -> images.loadBackdrop(u, maxWidth = HERO_BACKDROP_PX) { b -> backdrop.show(b) } }

        val side = Cinema.px(activity, 96)
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(side, Cinema.px(activity, 70), side, Cinema.px(activity, 120)) }
        val topRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        text.addView(Cinema.eyebrow(activity, listOfNotNull(if (t.kind == Kind.SHOW) "Series" else "Film", if (e.library != null) "In your library" else null)))
        text.addView(Cinema.title(activity, t.name, when { t.name.length > 30 -> 68; t.name.length > 18 -> 84; else -> 104 }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 20) })
        text.addView(meta(t), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 24) })
        Cinema.provider(activity, t.services)?.let { text.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 22) }) }
        text.addView(Cinema.synopsis(activity, t.overview ?: e.library?.overview ?: "No description yet.", 26, maxLines = 3), LinearLayout.LayoutParams(Cinema.px(activity, 1040), -2).apply { topMargin = Cinema.px(activity, 22) })

        // No Back button: the remote's Back closes the page. Watch now / Delete for what is on disk, Queue download /
        // Stream now for the rest.
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val inLibrary = e.library != null
        val primary = CinemaButton(activity, if (inLibrary) CinemaButton.Kind.PRIMARY else CinemaButton.Kind.TERRA, "Checking…", if (inLibrary) R.drawable.ic_cin_play else R.drawable.ic_cin_dl)
        val clearStale = CinemaButton(activity, CinemaButton.Kind.GLASS, "Clear stale request").apply { visibility = View.GONE }
        // A part-watched library film: Continue (the primary) and Restart from the beginning.
        val restart = CinemaButton(activity, CinemaButton.Kind.GLASS, "Restart", R.drawable.ic_cin_play).apply { visibility = View.GONE }
        // Not in the library: play a seeded copy now, instead of waiting for a download to finish.
        val stream = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Stream now", R.drawable.ic_cin_play).apply {
            visibility = if (!inLibrary && t.kind == Kind.MOVIE && seerr.hasStreamService) View.VISIBLE else View.GONE
            setOnClickListener { streamNow(e, this) }
        }
        val gap = Cinema.px(activity, 20); val h = Cinema.px(activity, 76)
        // A series streamed here: its next-up episode ("Continue watching S1E03"), filled in by seasonSection once it is known.
        val nextStream = if (t.kind == Kind.SHOW && !inLibrary && seerr.hasStreamService)
            CinemaButton(activity, CinemaButton.Kind.PRIMARY, "", R.drawable.ic_cin_play).apply { visibility = View.GONE } else null
        nextStream?.let { buttons.addView(it, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap }) }
        buttons.addView(stream, LinearLayout.LayoutParams(-2, h).apply { marginEnd = gap })
        buttons.addView(primary, LinearLayout.LayoutParams(-2, h))
        // A series' season download, beside Request all seasons: drawSeasons sets it to the season in view.
        heroSeasonButton = if (t.kind == Kind.SHOW) CinemaButton(activity, CinemaButton.Kind.GLASS, "Download", R.drawable.ic_cin_dl).apply { visibility = View.GONE } else null
        heroSeasonButton?.let { buttons.addView(it, LinearLayout.LayoutParams(-2, h).apply { marginStart = gap }) }
        buttons.addView(restart, LinearLayout.LayoutParams(-2, h).apply { marginStart = gap })
        // Add to the owner's watchlist (its row in My library), or take it off again. The watchlist is keyed by IMDb id, so a title
        // known only by its TMDB id (a suggestion, say) asks Seerr for it, and the button appears once it has come back.
        fun addWatchButton(id: String) {
            // An icon only, square like the other buttons: a bookmark, with a tick in it once the title is on the watchlist.
            val watch = CinemaButton(activity, CinemaButton.Kind.GLASS, "", R.drawable.ic_cin_watchlist).apply { setPadding(0, 0, 0, 0); makeIconOnly() }
            fun paintWatch() {
                val on = watchlist().any { it.imdbId == id }
                watch.setIcon(if (on) R.drawable.ic_cin_watchlist_added else R.drawable.ic_cin_watchlist)
                watch.contentDescription = if (on) "On your watchlist" else "Add to watchlist"
            }
            paintWatch()
            watch.setOnClickListener {
                val films = watchlist()
                val on = films.any { it.imdbId == id }
                saveWatchlist(if (on) Watchlist.without(films, id) else Watchlist.merge(films, listOf(WatchlistFilm(id, t.name, t.year, t.posterUrl))))
                paintWatch()
                CinemaToast.show(activity, if (on) "Removed ${t.name} from your watchlist" else "Added ${t.name} to your watchlist")
            }
            val params = LinearLayout.LayoutParams(h, h).apply { marginStart = gap }
            // Placed before "Clear stale request", wherever it is in the row by now.
            val at = buttons.indexOfChild(clearStale)
            if (at >= 0) buttons.addView(watch, at, params) else buttons.addView(watch, params)
        }
        // Favourites: a film or whole series, kept per Jellyfin server and user, matched by any id it is known by.
        run {
            val fav = Favourite(t.kind, t.tmdbId, t.imdbId ?: e.library?.imdbId, e.library?.id, t.name, t.year, t.posterUrl, t.backdropUrl, System.currentTimeMillis())
            if (!fav.hasId) return@run
            val scope = jellyfin.ownerScope
            val favButton = CinemaButton(activity, CinemaButton.Kind.GLASS, "", R.drawable.ic_cin_star_outline).apply { setPadding(0, 0, 0, 0); makeIconOnly() }
            fun paintFav() {
                val on = favourites.isFavourite(scope, fav)
                favButton.setIcon(if (on) R.drawable.ic_cin_star else R.drawable.ic_cin_star_outline)
                favButton.contentDescription = if (on) "Remove from favourites" else "Add to favourites"
            }
            paintFav()
            favButton.setOnClickListener {
                val on = favourites.toggle(scope, fav)
                paintFav()
                favouritesChanged(fav, on)
                CinemaToast.show(activity, if (on) "Added ${t.name} to your favourites" else "Removed ${t.name} from your favourites")
            }
            buttons.addView(favButton, LinearLayout.LayoutParams(h, h).apply { marginStart = gap })
        }
        val knownImdb = t.imdbId
        val tmdb = t.tmdbId?.toLongOrNull()
        if (knownImdb != null) addWatchButton(knownImdb)
        else if (tmdb != null && seerr.isSignedIn) {
            val forTitle = t
            worker.execute {
                val id = runCatching { seerr.imdbId(forTitle.kind, tmdb) }.getOrNull() ?: return@execute
                activity.runOnUiThread { if (buttons.isAttachedToWindow || buttons.parent != null) addWatchButton(id) }
            }
        }
        buttons.addView(clearStale, LinearLayout.LayoutParams(-2, h).apply { marginStart = gap })
        // Anything on disk can be taken off it, a series or a film, from its own page.
        if (inLibrary) buttons.addView(CinemaButton(activity, CinemaButton.Kind.DANGER, "Delete", R.drawable.ic_cin_trash).apply { setOnClickListener { confirmDelete(e) } },
            LinearLayout.LayoutParams(-2, h).apply { marginStart = gap })
        text.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        topRow.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

        val posterBox = StripePoster(activity)
        t.posterUrl?.let { u -> images.load(u, e.posterHeaders) { b -> posterBox.image.setImageBitmap(b) } }
        topRow.addView(posterBox, LinearLayout.LayoutParams(Cinema.px(activity, 300), Cinema.px(activity, 450)).apply { marginStart = Cinema.px(activity, 48); topMargin = -Cinema.px(activity, 12) })
        column.addView(topRow, LinearLayout.LayoutParams(-1, -2))

        if (t.kind == Kind.SHOW) seasonSection(column, e, primary, nextStream)
        lbFilmSection(column, e)
        val more = related.filter { it.key != t.key }.take(14)
        var moreHeading: View? = null
        var moreRow: Row? = null
        if (more.isNotEmpty() && t.kind == Kind.MOVIE) {
            moreHeading = Cinema.rowHeading(activity, "More like this").apply { setPadding(0, Cinema.px(activity, 70), 0, Cinema.px(activity, 14)) }
            column.addView(moreHeading)
            moreRow = row(null, null, into = column, inset = false).also { r ->
                more.map { entry(it) }.forEachIndexed { i, m -> r.inner.addView(card(m, more, small = true, nested = false), cardParams(i, small = true)) }
            }
        }
        // Else focus parks here, stuck.
        val scroll = android.widget.ScrollView(activity).apply { isFocusable = false; isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(column, FrameLayout.LayoutParams(-1, -2)) }
        detail.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        // The title's own buttons sit at the top of the page: focusing one scrolls all the way up, so the title and
        // eyebrow come back, instead of stopping at the point where the button just becomes visible.
        (0 until buttons.childCount).forEach { i -> buttons.getChildAt(i).setOnFocusChangeListener { _, f -> if (f) scroll.post { scroll.smoothScrollTo(0, 0) } } }
        moreRow?.let { r ->
            // Room below the row, so the page can slide it up near the top of the screen.
            column.setPadding(column.paddingLeft, column.paddingTop, column.paddingRight, Cinema.px(activity, 520))
            detailScrolledState(scroll, moreHeading!!, r.scroller, t, topRow)
        }
        setupPrimary(primary, e, clearStale, restart) { openDetail(e, related) }
        // A film that can only be streamed: have the stream service search for it now, so Stream now starts quicker.
        if (!inLibrary && t.kind == Kind.MOVIE && seerr.isSignedIn) {
            slowPool.execute { seerr.prepareStream(t.name, t.year, runtimeMin = t.runtimeMin) }
            // and ask the add-ons now, as Stremio does when a page opens (a title from the Home spotlight has no IMDb id yet: found first)
            slowPool.execute { imdbOf(e)?.let { addonAnswersFor(it, null) } }
            // Rest on the page for a few seconds and the stream itself is found, so Stream now opens at once.
            startStreamAfterDwell(e, null)
        }
        detailEntry = e
        // A series' progress lives on its episode cards, so its page is drawn again; a film just redoes its buttons.
        detailButtons = if (t.kind == Kind.SHOW) ({ val back = returnFocus; closeDetail(restoreFocus = false); openDetail(e, related); returnFocus = back })
            else ({ setupPrimary(primary, e, clearStale, restart) { openDetail(e, related) } })
        // The title page covers the screen, so the page beneath it is not drawn at all while it is open (it keeps its
        // place and its scroll position, so Back returns to exactly where it was).
        body.visibility = View.INVISIBLE
        requestFocusRobust(detail, if (stream.visibility == View.VISIBLE) stream else primary)
    }

    private var detailFocusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener? = null

    /**
     * The title page's second state (`11/13-detail-*-scrolled`): when focus moves into "More like this" the page
     * slides up so the row sits high on screen, a faded reminder of the title appears top left and the remote hints
     * appear at the bottom; moving back up returns to the details.
     */
    private fun detailScrolledState(scroll: android.widget.ScrollView, heading: View, rowScroller: View, t: Title, details: View) {
        val reminder = Cinema.eyebrow(activity, listOfNotNull(t.name, listOfNotNull(t.year?.toString(),
            t.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }).joinToString(" · ").ifBlank { null })).apply { alpha = 0f }
        detail.addView(reminder, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 96); topMargin = Cinema.px(activity, 44) })
        val hints = Cinema.keyHints(activity, listOf("◀ ▶" to "Browse", "OK" to "Open", "▲" to "Back to details")).apply { alpha = 0f }
        detail.addView(hints, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(activity, 96); bottomMargin = Cinema.px(activity, 48) })
        var down = false
        detailFocusListener?.let { if (detail.viewTreeObserver.isAlive) detail.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        detailFocusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            if (!detailOpen || focused == null || !isWithin(focused, detail)) return@OnGlobalFocusChangeListener
            val inRow = isWithin(focused, rowScroller)
            if (inRow == down) return@OnGlobalFocusChangeListener
            down = inRow
            // After the card's own glide, so this scroll wins.
            scroll.post { scroll.post { scroll.smoothScrollTo(0, if (inRow) (heading.top - Cinema.px(activity, 200)).coerceAtLeast(0) else 0) } }
            reminder.animate().alpha(if (inRow) 0.55f else 0f).setDuration(220).start()
            details.animate().alpha(if (inRow) 0f else 1f).setDuration(220).start()   // the faded reminder stands in for them
            hints.animate().alpha(if (inRow) 1f else 0f).setDuration(220).start()
        }.also { detail.viewTreeObserver.addOnGlobalFocusChangeListener(it) }
    }

    /**
     * Draws the series page again where it is, after a season or episode is deleted: the owner stays on the series, and the
     * seasons and episodes are drawn afresh. Closing the page here would drop them back on the Films page.
     */
    private fun redrawSeriesPage() { detailButtons?.invoke() }

    private fun closeDetail(restoreFocus: Boolean) {
        if (!detailOpen) return
        cancelDwellStart()
        detailFocusListener?.let { if (detail.viewTreeObserver.isAlive) detail.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        detailFocusListener = null
        detailEntry = null; detailButtons = null
        detail.visibility = View.GONE; detail.removeAllViews()
        body.visibility = View.VISIBLE
        body.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (restoreFocus) returnFocus?.takeIf { it.isAttachedToWindow }?.let { f -> f.post { f.requestFocus() } }
    }

    /** Watch now for what is in the library; for the rest, queue it, or say where its request has got to. */
    /** A series' season download on its title row, beside Request all seasons; null for a film. */
    private var heroSeasonButton: CinemaButton? = null

    /**
     * Seasons the owner has deleted in this session, by (TMDB id, season). Seerr and Jellyfin can keep saying they are there
     * for a while after the files go, so such a season reads as not downloaded until it is asked for again.
     */
    private val deletedSeasons = HashSet<Pair<Long, Int>>()

    /** The title page on screen, and how to redo its buttons when the viewer's place in it changes. */
    private var detailEntry: Entry? = null
    private var detailButtons: (() -> Unit)? = null

    private fun setupPrimary(button: CinemaButton, e: Entry, clearStale: CinemaButton, restart: CinemaButton? = null, refresh: () -> Unit) {
        fun set(label: String, enabled: Boolean, action: (() -> Unit)?) {
            button.setText(label); button.isEnabled = enabled
            if (e.library == null && e.title.kind == Kind.MOVIE) {
                val iconOnly = label == "Queue download"
                button.makeIconOnly(iconOnly)
                button.setPaddingRelative(Cinema.px(activity, if (iconOnly) 23 else 30), 0,
                    Cinema.px(activity, if (iconOnly) 23 else 38), 0)
            }
            // Disabled while checking, so focus arrives late. Focus parked on the overlay itself (the page beneath was hidden
            // while this button could not take it yet) counts as none.
            if (enabled && detailOpen && overlay.findFocus().let { it == null || it === overlay }) button.requestFocus()
            // Terracotta only while there is something to queue; a request already on its way rests as glass.
            if (e.library == null) button.kind = if (enabled) CinemaButton.Kind.TERRA else CinemaButton.Kind.GLASS
            button.setOnClickListener { action?.invoke() }
        }
        val lib = e.library
        if (lib != null) {
            // A film part-way through: "Continue · 42 min left" and Restart; otherwise Watch now. Series keep Watch now
            // (the episode list below carries their progress).
            val state = if (lib.isSeries) WatchState.NotStarted else WatchProgress.of(lib.played, lib.resumeMs, lib.runtimeMin)
            val actions = WatchProgress.filmActions(state)
            set(WatchProgress.label(actions.first(), state), true) { watch(lib, e.title) }
            restart?.visibility = if (FilmAction.RESTART in actions) View.VISIBLE else View.GONE
            restart?.setOnClickListener { watch(lib, e.title, fromStart = true) }
            return
        }
        val tmdb = e.title.tmdbId?.toLongOrNull()
        if (tmdb == null) { set("Can't be requested", false, null); return }
        fun queue() {
            if (!seerr.isSignedIn) { seerrSheet.show(seerr, jellyfin) { queue() }; return }
            set("Queuing…", false, null)
            worker.execute {
                val outcome = seerr.request(e.title.kind, tmdb)
                activity.runOnUiThread {
                    when (outcome) {
                        RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { set("Requested ✓", false, null); CinemaToast.show(activity, "Request accepted for ${e.title.name}. The server will search for a release.", Toast.LENGTH_LONG) }
                        RequestOutcome.NEEDS_SIGN_IN -> { set("Queue download", true) { queue() }; seerrSheet.show(seerr, jellyfin) { queue() } }
                        RequestOutcome.FAILED -> { set("Queue download", true) { queue() }; CinemaToast.show(activity, "Couldn't queue it right now", Toast.LENGTH_LONG) }
                    }
                }
            }
        }
        if (!seerr.isSignedIn) { set("Queue download", true) { queue() }; return }
        set("Checking…", false, null)
        worker.execute {
            val state = seerr.titleState(e.title.kind, tmdb)
            activity.runOnUiThread {
                if (!detailOpen || !button.isAttachedToWindow) return@runOnUiThread
                if (state == null) {
                    set("Retry status check", true) { setupPrimary(button, e, clearStale, restart, refresh) }
                    return@runOnUiThread
                }
                if (state.staleCandidate && seerr.hasRequestHelper) {
                    set("Tracked · no request", false, null)
                    clearStale.visibility = View.VISIBLE
                    clearStale.setOnClickListener {
                        clearStale.isEnabled = false; clearStale.setText("Clearing…")
                        worker.execute {
                            val cleared = seerr.clearStaleStatus(e.title.kind, tmdb)
                            activity.runOnUiThread {
                                if (!detailOpen || !clearStale.isAttachedToWindow) return@runOnUiThread
                                if (cleared) {
                                    CinemaToast.show(activity, "Stale request cleared", Toast.LENGTH_SHORT)
                                    refresh()
                                } else {
                                    clearStale.isEnabled = true; clearStale.setText("Clear stale request")
                                    CinemaToast.show(activity, "Couldn't clear it. An admin must be signed in, with no requests, downloads or files for this title.", Toast.LENGTH_LONG)
                                }
                            }
                        }
                    }
                    return@runOnUiThread
                }
                clearStale.visibility = View.GONE
                when (state.availability) {
                    Availability.REQUESTED -> set("Requested ✓", false, null)
                    // "Processing" is Seerr's word for "handed to Radarr/Sonarr"; it does not mean anything is downloading.
                    Availability.PROCESSING -> set("Requested ✓ · finding a release", false, null)
                    Availability.DOWNLOADING -> set("Downloading…", false, null)
                    Availability.PARTIAL -> set("Partly downloaded", false, null)
                    Availability.AVAILABLE -> set("Downloaded, syncing", false, null)
                    Availability.NONE -> set(if (e.title.kind == Kind.SHOW) "Request all seasons" else "Queue download", true) { queue() }
                }
            }
        }
    }

    /**
     * Starts finding an episode's stream now, so Play starts quicker when it is pressed. Same keys as [streamNow] (the same
     * title, year, season and episode), so nothing is asked twice. Does nothing without a signed-in stream service.
     */
    private fun warmEpisodeStream(e: Entry, season: Int, episode: Int) {
        if (!seerr.hasStreamService || !seerr.isSignedIn) return
        slowPool.execute { seerr.prepareStream(e.title.name, e.title.year, season, episode) }
        slowPool.execute { imdbOf(e)?.let { addonAnswersFor(it, season to episode) } }
    }

    /**
     * The player's next streamed episode, a few minutes before the current one ends: its stream is found now, as Play would
     * find it. The entry is built as [streamPickedEpisode] builds it, so the search keys match when it is played.
     */
    private fun warmUpcoming(r: EpisodeRequest) {
        val e = lastStreamed?.takeIf { knownImdb(it) == r.imdbId }
            ?: entry(Title(r.imdbId, Kind.SHOW, r.seriesName, null, null, null, emptyList(), null, null, null, null, r.imdbId, null))
        warmEpisodeStream(e, r.season, r.episode)
        // The next episode is almost sure to be played, so its stream is found now, not after a rest.
        preStartStream(e, r.season to r.episode)
    }

    /** The library's next-up episode for a library series (see NextEpisode.of), or null when it has none. */
    private fun libraryNextUp(e: Entry, owned: List<JellyfinItem>): NextUp? {
        val episodes = owned.filter { (it.season ?: 0) > 0 && it.episode != null }
        // Only a stream whose latest viewing finished moves the series on (a rewatch stopped partway does not).
        val streamed = streamedEpisodes(e).filter { it.finishedLast }.map { EpisodeWatch(it.season, it.episode, true, 0L, it.atMs) }
        return NextEpisode.of(episodes.map { EpisodeWatch(it.season!!, it.episode!!, it.played, it.resumeMs, it.playedMs) }, streamed)
    }

    /** Streams a seeded copy from the home stream service into the player, without waiting for a download. */
    private var streamLoading: StreamLoadingView? = null
    private var streamToken = 0
    private var streamReturnFocus: View? = null

    /**
     * Streams a seeded copy from the home stream service into the player, without waiting for a download. The loading
     * screen goes up at once (the search can take a minute) and the player takes over the same screen when a copy is
     * found. Back cancels while it searches.
     */
    /** [episode] is (season, episode) for a TV episode; [shownTitle] is what the screens call it ("Show · S1 E7"). */
    private fun streamNow(e: Entry, button: View, episode: Pair<Int, Int>? = null, shownTitle: String? = null) {
        if (!seerr.hasStreamService) {
            CinemaToast.show(activity, "Not in your library yet. Use the download button to get it.", Toast.LENGTH_LONG)
            return
        }
        if (!seerr.isSignedIn) { seerrSheet.show(seerr, jellyfin) { streamNow(e, button, episode, shownTitle) }; return }
        val token = ++streamToken
        lastStreamed = e
        Perf.mark("stream.press"); Perf.begin("stream.press->startReply")
        streamReturnFocus = button
        val label = shownTitle ?: e.title.name
        val screen = StreamLoadingView(activity, label).apply {
            isFocusable = true; isFocusableInTouchMode = true
            // Nothing on the screen is selectable: keep the D-pad from wandering to the page underneath.
            setOnKeyListener { _, code, _ -> code != android.view.KeyEvent.KEYCODE_BACK }
        }
        (overlay as ViewGroup).addView(screen, FrameLayout.LayoutParams(-1, -1))
        streamLoading = screen
        screen.requestFocus()
        backdropFor(e)?.let { u -> images.loadBackdrop(u, maxWidth = HERO_BACKDROP_PX) { b -> screen.backdrop(b) } }
        fun failed(message: String) {
            screen.message(message); screen.stats(null, null, null, null)
            ui.postDelayed({ if (token == streamToken) closeStreamLoading() }, 3500)
        }
        cancelDwellStart()
        slowPool.execute {
            // A stream found while the viewer rested on the page is used as it is (or waited for, if it is still being found).
            // Anything else, or a search that failed, is searched for now.
            val ahead = aheadStreams.take(streamKey(e, episode))?.let { pending -> runCatching { pending.get(130, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
                ?.takeIf { it.links != null || it.service?.status == StreamStatus.READY }
            if (ahead != null) android.util.Log.i("StreamNow", "using the stream found ahead of time for ${e.title.name}")
            val found = ahead ?: findStream(e, episode)
            activity.runOnUiThread {
                Perf.end("stream.press->startReply", if (found.links != null) "DIRECT" else found.service?.status?.name.orEmpty())
                if (token != streamToken) return@runOnUiThread   // cancelled with Back
                fun play(ranked: List<RankedStream>) {
                    screen.message("Found a copy. Starting the player…")
                    remember(e.title, streamed = true)
                    PlayerActivity.launch(activity, ranked, label, found.imdb,
                        artUrl = e.title.backdropUrl ?: e.title.posterUrl,
                        meta = PlayerMeta(label, e.title.year, e.title.genres, e.title.runtimeMin, e.title.age, e.title.imdbScore, e.title.overview), episode = episode,
                        history = streamHistory(e, found.imdb, episode))
                    // The player opens over this same screen; take it down once it is covered.
                    ui.postDelayed({ if (token == streamToken) closeStreamLoading() }, 1500)
                }
                found.links?.let { play(it); return@runOnUiThread }
                val result = found.service ?: return@runOnUiThread failed("The stream service isn't reachable.")
                android.util.Log.i("StreamNow", "status=${result.status} for ${e.title.name}")
                when (result.status) {
                    StreamStatus.READY -> {
                        val url = result.url ?: return@runOnUiThread failed("The stream service didn't send a link. Try again in a minute.")
                        val candidate = StreamCandidate("stream:${e.title.key}", PlayerActivity.STREAM_PROVIDER, result.label ?: e.title.name, url, null, StreamMetadata())
                        play(listOf(RankedStream(candidate, 100, 0L, false, Verdict.RECOMMENDED, null, emptyList())))
                    }
                    StreamStatus.NOT_SIGNED_IN -> { closeStreamLoading(); seerrSheet.show(seerr, jellyfin) { streamNow(e, button, episode, shownTitle) } }
                    StreamStatus.NOT_FOUND -> failed("No seeded copy of ${e.title.name} was found on your indexers.")
                    StreamStatus.CONNECTING -> failed("Still connecting to the sources. Try again in a minute.")
                    StreamStatus.UNAVAILABLE -> failed("The stream service isn't reachable.")
                }
            }
        }
    }

    /** What finding a stream came to: direct links from the add-ons (the one that answered first), or the stream service's answer. */
    private class Found(val links: List<RankedStream>?, val service: StreamStart?, val imdb: String?)

    /**
     * Finds a stream for [e] (and [episode]): the add-ons are asked, a direct link that answers is used, and otherwise the
     * stream service starts a seeded copy. Slow, so never on the UI thread. Used when Play is pressed, and ahead of that
     * ([preStartStream]) once the viewer has rested on the page.
     */
    private fun findStream(e: Entry, episode: Pair<Int, Int>?): Found {
        val imdb = imdbOf(e)
        // What the owner's Stremio add-ons offer for it, asked from this TV so their addresses never leave it.
        // The service's own search runs meanwhile (skipped if the page already asked for it).
        slowPool.execute { seerr.prepareStream(e.title.name, e.title.year, episode?.first, episode?.second, if (episode == null) e.title.runtimeMin else null) }
        val addons = if (imdb != null) stremioAddons() else emptyList()
        val asked = Perf.now()
        val answers = imdb?.let { id -> runCatching { addonAnswersFor(id, episode).get() }.getOrNull() }
            ?: com.danielportelabyrne.cinema.data.stremio.AddonAnswers.NONE
        val candidates = answers.torrents.sortedByDescending { it.seeders }.take(STREAM_CANDIDATES)
        android.util.Log.i("StreamNow", "add-ons offered ${answers.torrents.size} torrents, ${answers.links.size} links " +
            "(${answers.links.count { !it.uncached }} cached) in ${Perf.now() - asked} ms for ${e.title.name} " +
            "(${addons.size} add-ons, ${addons.count { it.streams }} stream, imdb ${imdb != null})")
        // A direct link as good as the best torrent plays at once, as Stremio does with a debrid service.
        val runtimeSec = ((e.title.runtimeMin ?: if (episode == null) 110 else 45) * 60L)
        val links = com.danielportelabyrne.cinema.data.stream.DirectLinks.choose(answers.links, answers.torrents,
            com.danielportelabyrne.cinema.data.stream.DeviceProfile.detect(activity),
            com.danielportelabyrne.cinema.data.stream.NetworkEstimator(activity).current(), runtimeSec)
        val working = links.firstOrNull { linkAnswers(it.candidate.url!!, it.candidate.headers) }
        if (working != null) {
            android.util.Log.i("StreamNow", "playing a direct link from ${working.candidate.provider}: ${working.candidate.meta.qualityLabel()}")
            return Found(listOf(working) + links.filter { it !== working }, null, imdb)
        }
        return Found(null, seerr.startStream(e.title.name, e.title.year, episode?.first, episode?.second, if (episode == null) e.title.runtimeMin else null, candidates), imdb)
    }

    // --- Streams found ahead of time ---------------------------------------------------------------

    /** Streams found while the viewer rested on a page, kept a few minutes so Play can open one at once. */
    // Kept a little under the stream service's own hold (5 minutes): after that, Play asks the service again, which checks
    // the engine still has the stream rather than handing back a link that would have to be fetched again from scratch.
    private val aheadStreams = com.danielportelabyrne.cinema.data.stream.AheadStreams<Found>(ttlMs = 4 * 60_000L) { android.os.SystemClock.elapsedRealtime() }
    /** One search ahead of time at a time, so resting on a page never queues up work on the home server. */
    private val preStartPool = Executors.newSingleThreadExecutor { r -> Thread(r, "pre-start").apply { isDaemon = true } }
    private var dwellStart: Runnable? = null
    private var dwellKey: String? = null

    private fun streamKey(e: Entry, episode: Pair<Int, Int>?) = e.title.key + (episode?.let { ":${it.first}:${it.second}" } ?: "")

    /** Who a streamed play is, for the play history: the film, or the episode of its series. Ids and art come from the title shown. */
    private fun streamHistory(e: Entry, imdb: String?, episode: Pair<Int, Int>?): com.danielportelabyrne.cinema.data.movies.HistoryTarget? {
        val t = e.title
        val entry = if (t.kind == Kind.SHOW) {
            if (episode == null) return null
            com.danielportelabyrne.cinema.data.movies.PlayedEntry(Kind.SHOW, seriesTmdbId = t.tmdbId, seriesImdbId = imdb ?: t.imdbId, title = t.name, season = episode.first, episode = episode.second, poster = t.posterUrl, backdrop = t.backdropUrl)
        } else com.danielportelabyrne.cinema.data.movies.PlayedEntry(Kind.MOVIE, tmdbId = t.tmdbId, imdbId = imdb ?: t.imdbId, title = t.name, poster = t.posterUrl, backdrop = t.backdropUrl)
        return entry.takeIf { it.hasId }?.let { com.danielportelabyrne.cinema.data.movies.HistoryTarget(jellyfin.ownerScope, it) }
    }

    /**
     * Finds the stream for [e] (and [episode]) now, without playing it, and keeps it for Play. Does nothing without a
     * signed-in stream service, or when one is already held for it. A search that waits its turn skips itself if another
     * page has been rested on since.
     */
    private fun preStartStream(e: Entry, episode: Pair<Int, Int>?) {
        if (!seerr.hasStreamService || !seerr.isSignedIn || streamLoading != null) return
        val key = streamKey(e, episode)
        aheadStreams.begin(key) {
            preStartPool.submit(java.util.concurrent.Callable {
                if (!aheadStreams.holds(key)) throw java.util.concurrent.CancellationException()
                val at = android.os.SystemClock.elapsedRealtime()
                android.util.Log.i("StreamNow", "finding ${e.title.name}${episode?.let { " S${it.first} E${it.second}" }.orEmpty()} ahead of time")
                findStream(e, episode).also { android.util.Log.i("StreamNow", "found ahead of time in ${android.os.SystemClock.elapsedRealtime() - at} ms") }
            })
        }
    }

    /** Rested on [e] (a film, or an episode) for [DWELL_START_MS]: its stream is found ahead of time, so Play opens it at once. */
    private fun startStreamAfterDwell(e: Entry, episode: Pair<Int, Int>?) {
        cancelDwellStart()
        if (!seerr.hasStreamService || !seerr.isSignedIn) return
        val r = Runnable { dwellStart = null; dwellKey = null; if (detailOpen && streamLoading == null) preStartStream(e, episode) }
        dwellStart = r; dwellKey = streamKey(e, episode)
        ui.postDelayed(r, DWELL_START_MS)
    }

    /** Stops the wait for a rest, when the viewer moves on. With [forKey], only if the wait is for that title. */
    private fun cancelDwellStart(forKey: String? = null) {
        if (forKey != null && dwellKey != forKey) return
        dwellStart?.let { ui.removeCallbacks(it) }
        dwellStart = null; dwellKey = null
    }

    // --- IMDb ids ------------------------------------------------------------------------------------

    private val imdbMemo = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The IMDb id for [e] if it is already known, without asking anyone: its own, or one found before. */
    private fun knownImdb(e: Entry): String? = e.title.imdbId ?: imdbMemo[e.title.key]

    /**
     * The IMDb id for [e]: its own, or Seerr's for its TMDB id. A title opened from the Home spotlight has only the TMDB id,
     * and without the IMDb id the add-ons cannot be asked. Asks Seerr, so never on the UI thread.
     */
    private fun imdbOf(e: Entry): String? {
        knownImdb(e)?.let { return it }
        val tmdb = e.title.tmdbId?.toLongOrNull() ?: return null
        if (!seerr.isSignedIn) return null
        return runCatching { seerr.imdbId(e.title.kind, tmdb) }.getOrNull()?.also { imdbMemo[e.title.key] = it }
    }

    /** The title last streamed, so an episode picked in the player is streamed with everything the page knew of it. */
    private var lastStreamed: Entry? = null

    private fun streamPickedEpisode(r: EpisodeRequest) {
        val e = lastStreamed?.takeIf { knownImdb(it) == r.imdbId }
            ?: entry(Title(r.imdbId, Kind.SHOW, r.seriesName, null, null, null, emptyList(), null, null, null, null, r.imdbId, null))
        val name = e.title.name.ifBlank { r.seriesName }
        streamNow(e, streamReturnFocus?.takeIf { it.isAttachedToWindow } ?: page, r.season to r.episode, "$name · S${r.season} E${r.episode}")
    }

    /**
     * Streams a Continue watching title again after its saved link stopped working. The player that opens resumes where
     * this TV left off, as it keeps the resume point by the title's IMDb id (and episode), not by the link.
     */
    private fun streamAgain(r: StreamAgainRequest) {
        val kind = if (r.episode != null) Kind.SHOW else Kind.MOVIE
        val e = lastStreamed?.takeIf { r.imdbId != null && knownImdb(it) == r.imdbId && it.title.kind == kind }
            ?: entry(Title(r.imdbId ?: "stream:${r.name}", kind, r.name, null, null, null, emptyList(), null, null, null, null, r.imdbId, null))
        val focus = activity.currentFocus?.takeIf { it.isAttachedToWindow } ?: page
        if (r.episode == null) streamWithoutEpisode(e, focus, r.shownTitle) else streamNow(e, focus, r.episode, r.shownTitle)
    }

    /**
     * Streams a title that was started without an episode, such as a series' card or a Continue watching entry that did not
     * keep which episode it was. A series plays its next-up episode, as the series page's own button does (the first one
     * when nothing is watched), so the episode is known and its credits and next-episode card work. A film, or anything
     * Cinemeta does not list as a series, streams as before.
     */
    private fun streamWithoutEpisode(e: Entry, button: View, shownTitle: String?) {
        val imdb = e.title.imdbId?.takeIf { it.startsWith("tt") }
        if (imdb == null) { streamNow(e, button, null, shownTitle); return }
        val watched = streamedEpisodes(e).filter { it.finishedLast }
            .map { com.danielportelabyrne.cinema.data.movies.EpisodeWatch(it.season, it.episode, true, 0L, it.atMs) }
        worker.execute {
            val listing = com.danielportelabyrne.cinema.data.stremio.StremioClient().series(imdb)
            val up = listing?.takeIf { it.episodes.isNotEmpty() }?.let { l ->
                com.danielportelabyrne.cinema.data.movies.NextEpisode.of(
                    l.episodes.map { com.danielportelabyrne.cinema.data.movies.EpisodeWatch(it.season, it.episode, false, 0L, null) }, watched,
                    wrapAtEnd = true)?.let { next -> next to l }   // Stream now on a finished series starts it again, as before
            }
            activity.runOnUiThread {
                if (up == null) { streamNow(e, button, null, shownTitle); return@runOnUiThread }
                val (next, series) = up
                val name = series.name.ifBlank { e.title.name }
                val t = Title(imdb, Kind.SHOW, name, null, null, null, emptyList(), null, null, null, null, imdb, null)
                streamNow(entry(t), button, next.season to next.episode, "$name · S${next.season} E${next.episode}")
            }
        }
    }

    private fun closeStreamLoading() {
        val screen = streamLoading ?: return
        streamLoading = null; streamToken++
        (overlay as ViewGroup).removeView(screen)
        streamReturnFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
    }

    /** Set when a library title starts playing, so the page asks Jellyfin where the viewer got to once they are back. */
    private var playbackStarted = false

    /** Notes a title as watched or streamed here, for "Picked for you". */
    private fun remember(t: Title, streamed: Boolean) {
        val id = t.tmdbId?.toLongOrNull() ?: return
        worker.execute { picks.history.add(id, t.kind, t.name, t.genres, streamed) }
    }

    private fun watch(item: JellyfinItem, title: Title, fromStart: Boolean = false) {
        playbackStarted = true
        remember(title, streamed = false)
        if (!item.isSeries) { JellyfinPlayback.play(activity, jellyfin, item, title.name, title.imdbId ?: "jf:${item.id}", fromStart); return }
        CinemaToast.show(activity, "Finding the next episode…", Toast.LENGTH_SHORT)
        worker.execute {
            val ep = jellyfin.episodeToPlay(item.id)
            activity.runOnUiThread {
                if (ep == null) CinemaToast.show(activity, "No episodes found for ${title.name}", Toast.LENGTH_LONG)
                else JellyfinPlayback.play(activity, jellyfin, ep, listOfNotNull(title.name, ep.episodeLabel()).joinToString(" · "), "jf:${ep.id}", series = title.imdbId to title.tmdbId)
            }
        }
    }

    // --- Seasons and episodes ----------------------------------------------------------------------

    /** A season as the picker holds it: Seerr's view of it, which changes when the viewer asks for it. */
    private val sonarr by lazy { SonarrClient(seerr) }
    private var lastScanMs = 0L

    private class SeasonState(val number: Int, val name: String, val episodeCount: Int, var availability: Availability)

    /** One episode as a card shows it, from Seerr's listing or, without one, from the library. */
    private class EpisodeRow(val number: Int, val name: String, val overview: String?, val airDate: String?, val stillUrl: String?, val runtime: Int?)

    /**
     * Under a series' title (`docs/ui-review/series-episodes`): a bar of season tabs with the season's counts and its
     * download button, then a rail of episode title cards. Each card plays its episode (from the library, or streamed)
     * and asks Sonarr for that one episode. A season shows its episodes from Seerr's listing, merged with what Jellyfin holds.
     */
    private fun seasonSection(parent: LinearLayout, e: Entry, primary: CinemaButton?, nextStream: CinemaButton? = null) {
        val tmdb = e.title.tmdbId?.toLongOrNull()
        // An episode picked on Home's Recently watched: its season opens and the rail rests on it. Taken once.
        val pick = pendingEpisodePick?.takeIf { pendingPickFor == e.title.key }; pendingEpisodePick = null; pendingPickFor = null
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 60) })
        box.addView(loading("Loading seasons…"))
        Perf.begin("seasons.draw")
        worker.execute {
            // Jellyfin and Seerr first: Sonarr and the download queue can be slow or unreachable, so they fill in after.
            val owned = e.library?.let { jellyfin.episodes(it.id) }.orEmpty()
            val signedIn = tmdb != null && seerr.isSignedIn
            val fromSeerr = if (signedIn) seerr.seasons(tmdb!!) else emptyList()
            val seasons = fromSeerr.map { SeasonState(it.number, it.name, it.episodeCount, it.availability) }.ifEmpty {
                owned.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted().map { n -> SeasonState(n, "Season $n", owned.count { it.season == n }, Availability.AVAILABLE) }
            }
            val states = HashMap<Pair<Int, Int>, EpisodeState>()
            val downloads = ArrayList<QueueItem>()
            var refresh: () -> Unit = {}
            // Where the viewer got to: the library's next-up for a library series, else the one after the last stream played here.
            val streamedHere = streamedEpisodes(e)
            val nextUp: NextUp? = if (e.library?.isSeries == true) libraryNextUp(e, owned)
                else NextEpisode.forStream(seasons.filter { it.number > 0 }.flatMap { s -> (1..s.episodeCount).map { s.number to it } }, streamedHere)
            activity.runOnUiThread {
                if (!detailOpen || !box.isAttachedToWindow) return@runOnUiThread
                val picked = pick?.takeIf { p -> seasons.any { it.number == p.first } }?.let { NextUp(it.first, it.second, continuing = false) }
                refresh = drawSeasons(box, e, tmdb, seasons, owned, states, downloads, picked ?: nextUp, focusPicked = picked != null)
                resumeOnHero(primary, e, owned)
                Perf.end("seasons.draw", "${owned.size} episodes, ${seasons.size} seasons")
            }
            // A series streamed here: its next-up episode goes on the title's buttons once it has aired. Looked up here, off the UI thread.
            if (nextStream != null && nextUp != null && e.library == null && tmdb != null && signedIn) {
                val aired = seerr.episodes(tmdb, nextUp.season).firstOrNull { it.number == nextUp.episode }
                    ?.let { !EpisodeCards.unaired(it.airDate, EpisodeCards.today()) } == true
                if (aired) activity.runOnUiThread {
                    if (!detailOpen || !nextStream.isAttachedToWindow) return@runOnUiThread
                    val code = "S${nextUp.season}E${nextUp.episode.toString().padStart(2, '0')}"
                    val label = "${e.title.name} · S${nextUp.season} E${nextUp.episode}"
                    nextStream.setText(if (nextUp.continuing) "Continue watching $code" else "Watch $code")
                    nextStream.setOnClickListener { v -> streamNow(e, v, nextUp.season to nextUp.episode, label) }
                    nextStream.visibility = View.VISIBLE
                    // Only once the viewer has started this series here, so browsing does not ask the stream service about every title.
                    if (streamedHere.isNotEmpty()) warmEpisodeStream(e, nextUp.season, nextUp.episode)
                    // Rest on the page a few seconds and the stream itself is found, so the button opens it at once.
                    startStreamAfterDwell(e, nextUp.season to nextUp.episode)
                    // Focus moves to it from the page's first button, or from nowhere; never from a button the viewer has moved to.
                    val f = overlay.findFocus()
                    if (f == null || f === primary) nextStream.requestFocus()
                }
            }
            if (!signedIn) return@execute
            Perf.begin("seasons.arr")
            val known = sonarr.episodeStates(tmdb!!)
            // Episodes on disk that Jellyfin has not yet picked up: ask it to scan (at most every ten minutes), so they turn into Play.
            val unscanned = known.any { (k, v) -> v == EpisodeState.DOWNLOADED && owned.none { it.season == k.first && it.episode == k.second } }
            if (unscanned && System.currentTimeMillis() - lastScanMs > 10 * 60_000L) { lastScanMs = System.currentTimeMillis(); jellyfin.refreshLibrary() }
            // What is downloading right now, so a card can show how far its episode has got.
            val busy = known.values.any { it == EpisodeState.REQUESTED } || seasons.any { it.availability == Availability.PROCESSING || it.availability == Availability.DOWNLOADING || it.availability == Availability.PARTIAL }
            val active = if (busy) queueClient.items().orEmpty().filter { it.tmdbId == tmdb } else emptyList()
            Perf.end("seasons.arr", "${known.size} states, ${active.size} downloads")
            if (known.isEmpty() && active.isEmpty()) return@execute
            activity.runOnUiThread {
                if (!detailOpen || !box.isAttachedToWindow) return@runOnUiThread
                known.forEach { (k, v) -> states.putIfAbsent(k, v) }
                downloads.addAll(active)
                refresh()
            }
        }
    }

    /**
     * The series' main button, once its episodes are known: "Continue watching S3E2" when that episode is part-way through,
     * otherwise "Watch S3E3" for the next one (or "Watch S1E1" when nothing has been watched). Films keep their own button.
     */
    private fun resumeOnHero(primary: CinemaButton?, e: Entry, owned: List<JellyfinItem>) {
        if (primary == null || e.library?.isSeries != true) return
        val episodes = owned.filter { (it.season ?: 0) > 0 && it.episode != null }
        val up = libraryNextUp(e, owned) ?: return
        val ep = episodes.firstOrNull { it.season == up.season && it.episode == up.episode } ?: return
        val code = "S${ep.season}E${ep.episode.toString().padStart(2, '0')}"
        primary.setText(if (up.continuing) "Continue watching $code" else "Watch $code")
        primary.setOnClickListener { playbackStarted = true; remember(e.title, streamed = false); JellyfinPlayback.play(activity, jellyfin, ep, "${e.title.name} · S${ep.season} E${ep.episode}", "jf:${ep.id}", series = e.title.imdbId to e.title.tmdbId) }
    }

    /** Returns a restate of the shown season, for when Sonarr's episode states or downloads arrive later. */
    private fun drawSeasons(box: LinearLayout, e: Entry, tmdb: Long?, seasons: List<SeasonState>, owned: List<JellyfinItem>,
                            states: MutableMap<Pair<Int, Int>, EpisodeState>, downloads: List<QueueItem>, nextUp: NextUp? = null, focusPicked: Boolean = false): () -> Unit {
        box.removeAllViews()
        if (seasons.isEmpty()) {
            box.addView(note(if (!seerr.isSignedIn && e.library == null) "Press Queue download to sign in, then the seasons and episodes will be listed here." else "No season information available."))
            return {}
        }
        // The season bar (`.sbar`): stripe marker, the tabs, then the season's counts and its action on the right.
        val bar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        box.addView(bar, LinearLayout.LayoutParams(-1, Cinema.px(activity, 62)))
        bar.addView(View(activity).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(activity, 7)) },
            LinearLayout.LayoutParams(Cinema.px(activity, 14), Cinema.px(activity, 30)).apply { marginEnd = Cinema.px(activity, 24) })
        val tabRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        val tabScroller = CalmHorizontalScrollView(activity).apply {
            // Child rows deliberately draw focus effects outside themselves; bound the entire strip
            // explicitly so later seasons cannot paint over the adjacent episode count and action.
            isHorizontalScrollBarEnabled = false; clipChildren = true; clipToPadding = true; isHorizontalFadingEdgeEnabled = false
            addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> v.clipBounds = android.graphics.Rect(0, 0, r - l, b - t) }
            leftLeavesToRail = false
            addView(tabRow)
        }
        bar.addView(tabScroller, LinearLayout.LayoutParams(0, -1, 1f))
        val info = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(info, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 24); marginEnd = Cinema.px(activity, 22) })
        val actionSlot = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        bar.addView(actionSlot, LinearLayout.LayoutParams(-2, Cinema.px(activity, 60)))

        // The episode rail, 32 px under the bar (the row's own 16 px padding plus 16 more).
        val rail = row(null, null, into = box, inset = false)
        (rail.scroller.layoutParams as LinearLayout.LayoutParams).topMargin = Cinema.px(activity, 16)

        val tabs = LinkedHashMap<View, SeasonState>()
        val paints = HashMap<View, () -> Unit>()
        // The season being watched opens first (its next-up episode), else Season 1 as before.
        val opening = seasons.firstOrNull { it.number == nextUp?.season } ?: seasons.first()
        var shown = opening
        var loading = 0
        var railTarget: View? = null
        var pickFocus = focusPicked
        var restateShown: () -> Unit = {}
        fun selectedTab(): View? = tabs.entries.firstOrNull { it.value === shown }?.key

        fun setInfo(parts: List<Pair<String, Boolean>>) {
            info.removeAllViews()
            parts.forEachIndexed { i, (text, strong) ->
                if (i > 0) info.addView(Cinema.dot(activity, Cinema.DIM, 4), LinearLayout.LayoutParams(Cinema.px(activity, 4), Cinema.px(activity, 4)).apply {
                    marginStart = Cinema.px(activity, 14); marginEnd = Cinema.px(activity, 14) })
                info.addView(Cinema.text(activity, text, 21, if (strong) Cinema.CREAM else Cinema.MUTED, weight = if (strong) 500 else 400).apply { maxLines = 1 })
            }
        }

        // Down from anywhere on the bar lands on the resume episode, or the first card.
        val downToRail = View.OnKeyListener { _, code, ev ->
            ev.action == android.view.KeyEvent.ACTION_DOWN && code == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                railTarget?.takeIf { it.isAttachedToWindow }?.requestFocus() == true
        }

        lateinit var showSeason: (SeasonState) -> Unit

        // The season's action lives on the title's own row, beside Request all seasons, and follows the season in view.
        fun setAction(s: SeasonState, allInLibrary: Boolean) {
            val button = heroSeasonButton ?: return
            val gone = tmdb != null && (tmdb to s.number) in deletedSeasons
            val availability = if (gone) Availability.NONE else s.availability
            val onDisk = !gone && (availability == Availability.AVAILABLE || availability == Availability.PARTIAL || owned.any { it.season == s.number })
            if (onDisk) {
                button.visibility = View.VISIBLE; button.kind = CinemaButton.Kind.DANGER
                button.setText("Delete ${s.name}"); button.setIcon(R.drawable.ic_cin_trash); button.isEnabled = true
                button.setOnClickListener { confirmDeleteSeason(e, tmdb, s, owned, states.keys.filter { it.first == s.number }.map { it.second }.toSet()) { redrawSeriesPage() } }
                return
            }
            val label = EpisodeCards.seasonAction(availability, s.name, allInLibrary)
            if (label == null) { button.visibility = View.GONE; return }
            val can = availability == Availability.NONE && tmdb != null
            button.visibility = View.VISIBLE
            button.kind = if (can) CinemaButton.Kind.TERRA else CinemaButton.Kind.GLASS
            button.setText(label); button.setIcon(R.drawable.ic_cin_dl)
            button.isEnabled = can
            button.setOnClickListener {
                if (!seerr.isSignedIn) { seerrSheet.show(seerr, jellyfin) { showSeason(s) }; return@setOnClickListener }
                button.setText("Requesting…"); button.isEnabled = false
                worker.execute {
                    val outcome = seerr.request(Kind.SHOW, tmdb!!, listOf(s.number))
                    activity.runOnUiThread {
                        when (outcome) {
                            RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { s.availability = Availability.REQUESTED; deletedSeasons -= tmdb!! to s.number; CinemaToast.show(activity, "${e.title.name}, ${s.name} is queued for download", Toast.LENGTH_LONG) }
                            RequestOutcome.NEEDS_SIGN_IN -> seerrSheet.show(seerr, jellyfin) { showSeason(s) }
                            RequestOutcome.FAILED -> CinemaToast.show(activity, "Couldn't queue ${s.name} right now", Toast.LENGTH_LONG)
                        }
                        if (detailOpen && shown === s) { showSeason(s); selectedTab()?.requestFocus() }
                    }
                }
            }
        }

        /** A drawn card; [restate] rebinds it in place from the latest states and downloads. */
        class Shown(val card: EpisodeCard, val mine: JellyfinItem?, val look: () -> CardLook, val latest: () -> CardLook, val restate: () -> Unit)

        /** One episode's card, with its actions. [rebind] redraws it after a request, without reloading the season. */
        fun episodeCard(s: SeasonState, ep: EpisodeRow, mine: JellyfinItem?, token: Int, today: String): Shown {
            val card = EpisodeCard(activity, ep.number, ep.name, ep.overview ?: mine?.overview)
            val label = "${e.title.name} · S${s.number} E${ep.number}"
            fun latest() = episodeCardState(mine != null, states[s.number to ep.number], s.availability, ep.airDate, today, tmdb != null,
                seerr.hasStreamService, EpisodeCards.download(downloads, tmdb, s.number, ep.number))
            var look = latest()
            fun rebind() {
                card.bind(look, EpisodeCards.formatDate(ep.airDate)?.uppercase() ?: "TBA")
                card.tipText = when (look.state) {
                    CardState.REQUEST -> "Download S${s.number} E${ep.number}"
                    CardState.UNAIRED -> EpisodeCards.preRequestTip(s.number, ep.number)
                    else -> null
                }
            }
            rebind()
            val runtime = mine?.runtimeMin ?: ep.runtime
            val resume = mine?.takeIf { !it.played }?.let { EpisodeCards.resume(it.resumeMs, runtime) }
            card.setMeta(EpisodeCards.metaParts(runtime, resume?.second, ep.airDate))
            val progress = mine?.let { WatchProgress.of(it.played, it.resumeMs, runtime) } ?: WatchState.NotStarted
            card.setResume(WatchProgress.episodeBar(progress))
            card.watched = WatchProgress.episodeShowsTick(progress)
            if (mine == null && streamedEpisodes(e).any { it.season == s.number && it.episode == ep.number && it.played }) card.watched = true
            card.onDelete = { confirmDeleteEpisode(e, tmdb, s.number, ep.number, mine) { removed -> if (removed) redrawSeriesPage() } }
            // The library's own still when it has one (sent with the Jellyfin sign-in), otherwise TMDB's.
            val (url, headers) = when {
                mine?.hasPrimaryImage == true -> jellyfin.episodeImageUrl(mine.id) to jellyfin.playbackHeaders()
                ep.stillUrl != null -> ep.stillUrl to emptyMap<String, String>()
                else -> null to emptyMap<String, String>()
            }
            if (url == null) card.showPlaceholder()
            else images.load(url, headers, maxWidth = Cinema.px(activity, 400)) { b -> if (token == loading) card.setStill(b) }

            card.play.setOnClickListener { v ->
                if (mine != null) { playbackStarted = true; remember(e.title, streamed = false); JellyfinPlayback.play(activity, jellyfin, mine, label, "jf:${mine.id}", series = e.title.imdbId to e.title.tmdbId) }
                else streamNow(e, v, s.number to ep.number, label)
            }
            // The whole blurb, with the still and the episode's facts. Play (when there is one to press) reuses the card's own Play disc.
            card.onInfo = {
                val about = (ep.overview ?: mine?.overview)?.trim().orEmpty()
                val facts = EpisodeCards.metaParts(runtime, resume?.second, ep.airDate).joinToString(" · ").ifBlank { null }
                val actions = if (look.playShown) listOf(SheetAction(if (mine != null) "Play" else "Stream", label, primary = true) { card.play.performClick() }) else emptyList()
                infoSheet.show("S${s.number} · E${ep.number}", ep.name.ifBlank { "Episode ${ep.number}" }, facts,
                    listOf(about.ifBlank { "No description yet." }), card.stillBitmap(), actions, imageSizeDp = 320 to 180)
            }
            card.download.setOnClickListener {
                when (look.state) {
                    CardState.REQUESTED -> CinemaToast.show(activity, "Requested, waiting for a release", Toast.LENGTH_SHORT)
                    CardState.DOWNLOADING -> CinemaToast.show(activity, look.progress?.let { "Downloading, ${Math.round(it * 100)}% so far" } ?: "Downloading", Toast.LENGTH_SHORT)
                    // Asked for before it aired: offer to stop waiting for it.
                    CardState.UNAIRED_REQUESTED -> if (tmdb != null) infoSheet.show("REQUESTED", label, EpisodeCards.airsLabel(ep.airDate),
                        listOf("It will download by itself once it airs."), null, listOf(
                            SheetAction("Cancel request", "Stop waiting for this episode", primary = true) {
                                worker.execute {
                                    val done = sonarr.unwantEpisodes(tmdb, s.number, listOf(ep.number))
                                    activity.runOnUiThread {
                                        if (done) {
                                            states[s.number to ep.number] = EpisodeState.NONE
                                            look = episodeCardState(mine != null, EpisodeState.NONE, s.availability, ep.airDate, today, true, seerr.hasStreamService, null)
                                            if (token == loading) rebind()
                                        }
                                        CinemaToast.show(activity, if (done) "Cancelled S${s.number} E${ep.number}" else "Couldn't cancel it right now", Toast.LENGTH_LONG)
                                    }
                                }
                            },
                            SheetAction("Keep it") { }))
                    CardState.REQUEST, CardState.UNAIRED -> when {
                        tmdb == null -> Unit
                        !seerr.isSignedIn -> seerrSheet.show(seerr, jellyfin) { }
                        else -> {
                            card.download.isClickable = false; card.download.alpha = 0.5f
                            worker.execute {
                                val outcome = sonarr.requestEpisodes(tmdb, s.number, listOf(ep.number))
                                activity.runOnUiThread {
                                    card.download.isClickable = true; card.download.alpha = 1f
                                    when (outcome) {
                                        EpisodeOutcome.QUEUED, EpisodeOutcome.ALREADY -> {
                                            states[s.number to ep.number] = EpisodeState.REQUESTED
                                            look = episodeCardState(mine != null, EpisodeState.REQUESTED, s.availability, ep.airDate, today, true, seerr.hasStreamService, null)
                                            if (token == loading) rebind()
                                            CinemaToast.show(activity, if (look.unaired) EpisodeCards.preRequestDone(s.number, ep.number, ep.airDate)
                                                else "${e.title.name} S${s.number} E${ep.number} is queued", Toast.LENGTH_LONG)
                                        }
                                        EpisodeOutcome.NOT_CONFIGURED -> CinemaToast.show(activity, "Sonarr's details aren't available, so only whole seasons can be requested here", Toast.LENGTH_LONG)
                                        EpisodeOutcome.NOT_FOUND -> CinemaToast.show(activity, "Sonarr couldn't find that episode yet. Try again in a moment", Toast.LENGTH_LONG)
                                        EpisodeOutcome.FAILED -> CinemaToast.show(activity, "Couldn't queue that episode right now", Toast.LENGTH_LONG)
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
            card.onLit = { lit ->
                if (lit) FastFocus.reveal(card, null, dp(24), dp(56), 0)
                // An episode that would be streamed: search for it while the viewer looks at it.
                if (lit && mine == null && !look.unaired && seerr.isSignedIn) slowPool.execute { seerr.prepareStream(e.title.name, e.title.year, s.number, ep.number) }
                // The add-ons are asked once the viewer has rested on the episode, not for every card passed while scrolling.
                ui.removeCallbacks(addonPrefetch)
                if (lit && mine == null && !look.unaired) {
                    addonPrefetch = Runnable { slowPool.execute { imdbOf(e)?.let { addonAnswersFor(it, s.number to ep.number) } } }
                    ui.postDelayed(addonPrefetch, ADDON_PREFETCH_DELAY_MS)
                    // And once it has been rested on a few seconds longer, the stream itself is found, so Play opens it at once.
                    startStreamAfterDwell(e, s.number to ep.number)
                } else if (!lit) cancelDwellStart(streamKey(e, s.number to ep.number))
            }
            return Shown(card, mine, { look }, ::latest) { look = latest(); rebind() }
        }

        showSeason = { s ->
            shown = s
            val token = ++loading
            paints.values.forEach { it() }
            setInfo(EpisodeCards.seasonInfo(s.episodeCount, 0, 0))
            setAction(s, allInLibrary = false)
            railTarget = null
            restateShown = {}
            rail.inner.removeAllViews(); rail.inner.addView(loading("Loading episodes…"))
            worker.execute {
                val listing = if (tmdb != null && seerr.isSignedIn) seerr.episodes(tmdb, s.number) else emptyList()
                activity.runOnUiThread {
                    if (!detailOpen || token != loading) return@runOnUiThread
                    rail.inner.removeAllViews()
                    val ownedHere = owned.filter { it.season == s.number }
                    val rows = if (listing.isNotEmpty()) listing.map { EpisodeRow(it.number, it.name, it.overview, it.airDate, it.stillUrl, it.runtime) }
                               else ownedHere.filter { it.episode != null }.map { EpisodeRow(it.episode!!, it.name, it.overview, null, null, it.runtimeMin) }
                    if (rows.isEmpty()) { rail.inner.addView(note("No episode list available for this season.")); return@runOnUiThread }
                    val today = EpisodeCards.today()
                    val cards = rows.sortedBy { it.number }.mapIndexed { i, ep ->
                        val mine = ownedHere.firstOrNull { it.episode == ep.number }
                        episodeCard(s, ep, mine, token, today).also {
                            rail.inner.addView(it.card, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = Cinema.px(activity, 40) })
                        }
                    }
                    var actionAllIn = false
                    fun settle() {
                        val inLibrary = cards.count { it.look().state == CardState.LIBRARY }
                        setInfo(EpisodeCards.seasonInfo(rows.size, inLibrary, cards.count { it.look().state == CardState.DOWNLOADING }))
                        val allIn = inLibrary >= rows.size
                        // Rebuilding a focused action would drop focus.
                        if (allIn != actionAllIn) {
                            actionAllIn = allIn
                            val held = actionSlot.hasFocus()
                            if (held) selectedTab()?.requestFocus()
                            setAction(s, allInLibrary = allIn)
                            if (held) actionSlot.getChildAt(0)?.takeIf { it.isFocusable && it.isEnabled }?.requestFocus()
                        }

                        // Focus runs along the rail, Play then Download on each card; Up returns to the season's tab.
                        val stops = cards.flatMap { it.card.focusStops }
                        stops.forEachIndexed { i, stop ->
                            stop.setOnKeyListener { _, code, ev ->
                                if (ev.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                when (code) {
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { stops.getOrNull(i + 1)?.requestFocus(); true }
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { stops.getOrNull(i - 1)?.requestFocus(); true }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> selectedTab()?.requestFocus() == true
                                    else -> false
                                }
                            }
                        }
                        // The next-up episode when this season holds it; else the library episode part-way through; else the first.
                        railTarget = cards.firstOrNull { nextUp != null && s.number == nextUp.season && it.card.number == nextUp.episode }?.card?.firstStop
                            ?: cards.firstOrNull { it.mine != null && !it.mine.played && it.mine.resumeMs > 0 }?.card?.firstStop
                            ?: cards.first().card.firstStop
                    }
                    settle()
                    // An episode picked from Home gets focus once, unless the viewer has already moved into the seasons.
                    // Only that exact episode: when its card is not listed, focus is left alone rather than moved to another episode.
                    if (pickFocus && nextUp != null && s.number == nextUp.season) { pickFocus = false
                        cards.firstOrNull { it.card.number == nextUp.episode }?.card?.firstStop?.let { t ->
                            t.post { if (t.isAttachedToWindow && overlay.findFocus()?.let { f -> isWithin(f, box) } != true) t.requestFocus() } } }
                    // Scrolls the rail to the next-up episode so it is in view, without moving focus.
                    if (nextUp != null && s.number == nextUp.season) cards.firstOrNull { it.card.number == nextUp.episode }?.card?.let { c ->
                        rail.scroller.post { (rail.scroller as android.widget.HorizontalScrollView).scrollTo((c.left - Cinema.px(activity, 24)).coerceAtLeast(0), 0) }
                    }
                    restateShown = {
                        val held = cards.firstOrNull { it.card.hasFocus() }
                        val focused = held?.card?.findFocus()
                        // A stop about to go unfocusable would hand focus to the page's first view; park it on the tab.
                        val park = held != null && focused != null && !held.card.keepsStop(focused, held.latest())
                        if (park) selectedTab()?.requestFocus()
                        cards.forEach { it.restate() }
                        settle()
                        if (park) held?.card?.firstStop?.requestFocus()
                    }
                }
            }
        }

        seasons.forEachIndexed { i, s ->
            val label = Cinema.text(activity, s.name, 36, Cinema.DIM, serif = true, tracking = -0.01f).apply { maxLines = 1 }
            val underline = View(activity).apply { background = StripeDrawable(horizontalBands = false, radius = Cinema.pxf(activity, 3)) }
            val tab = FrameLayout(activity).apply {
                isFocusable = true; isClickable = true
                setPadding(Cinema.px(activity, 26), 0, Cinema.px(activity, 26), 0)
                addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL))
                addView(underline, FrameLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 6), Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = Cinema.px(activity, 26); bottomMargin = Cinema.px(activity, 2) })
                contentDescription = s.name
            }
            val lit = GradientDrawable().apply { cornerRadius = Cinema.pxf(activity, 31); setColor(Cinema.CREAM) }
            // Unselected tabs are dim, the shown season is cream with the stripe underline, a focused tab fills cream.
            paints[tab] = {
                val focused = tab.isFocused; val selected = shown === s
                tab.background = if (focused) lit else null
                label.setTextColor(if (focused) Cinema.INK else if (selected) Cinema.CREAM else Cinema.DIM)
                underline.visibility = if (selected && !focused) View.VISIBLE else View.GONE
            }
            tab.setOnFocusChangeListener { v, focused ->
                if (focused && shown !== s) showSeason(s) else paints[tab]?.invoke()
                if (focused) FastFocus.reveal(v, null, dp(24), dp(56), 0)
            }
            tab.setOnClickListener { if (shown !== s) showSeason(s) }
            tab.setOnKeyListener(downToRail)
            tabs[tab] = s
            tabRow.addView(tab, LinearLayout.LayoutParams(-2, Cinema.px(activity, 62)).apply { if (i > 0) marginStart = Cinema.px(activity, 10) })
        }
        showSeason(opening)
        // Brings the opening season's tab into view on the strip, which may be far along a long series.
        tabs.entries.firstOrNull { it.value === opening }?.key?.let { tab ->
            tabScroller.post { tabScroller.scrollTo((tab.left - Cinema.px(activity, 26)).coerceAtLeast(0), 0) }
        }
        return { restateShown() }
    }

    // --- Category tiles ------------------------------------------------------------------------

    /** The stripe set's colours plus the arches' olive and a deep teal, for the painted browse tiles. */
    private val tileColours = intArrayOf(Cinema.STRIPE_TERRA, 0xFF2F6E66.toInt(), Cinema.STRIPE_SAFFRON, 0xFF4D5B2C.toInt(), Cinema.STRIPE_RED, Cinema.MAROON)

    /** One card per service, in its own colours with its app icon and a fan of what is popular on it now. */
    private fun serviceTiles(gen: Int) {
        val r = row("Browse by service", null)
        r.inner.minimumHeight = Cinema.px(activity, ServiceCard.H + 8)
        val fans = ArrayList<() -> Unit>()
        Catalog.SERVICES.forEachIndexed { i, s ->
            val card = ServiceCard(activity, s.label, ServiceCard.coloursFor(s.code))
            Catalog.logoUrl(s)?.let { url -> images.load(url, emptyMap(), maxWidth = Cinema.px(activity, 92), tag = pageTag) { b -> card.logo.setImageBitmap(b) } }
            card.onFocus = { lit -> if (lit) reveal(card) }
            card.setOnClickListener { go(Dest.OnService(s), remember = true) }
            r.inner.addView(card, cardParams(i))
            // The fan is a shelf Home or the service's own page asks for anyway (and keeps), so this costs no extra fetch.
            val spec = Catalog.cardShelf(s)
            // Only once the row nears the screen, and never ahead of the shelves on show (worker, not the shelf pool).
            fans += { worker.execute {
                val posters = catalog.shelf(spec).titles.mapNotNull { it.posterUrl }.take(3)
                activity.runOnUiThread {
                    if (gen != generation) return@runOnUiThread
                    // Middle first: the front card is the service's most popular title.
                    listOf(1, 0, 2).zip(posters).forEach { (slot, url) ->
                        images.load(url, emptyMap(), maxWidth = Cinema.px(activity, ServiceCard.POSTER_W), tag = pageTag) { b -> if (gen == generation) card.setPoster(slot, b) }
                    }
                }
            } }
        }
        // The last card: where services are added and removed.
        val manage = ManageServicesCard(activity).apply {
            onFocus = { lit -> if (lit) reveal(this) }
            setOnClickListener { go(Dest.ServiceManage, remember = true) }
        }
        r.inner.addView(manage, cardParams(Catalog.SERVICES.size))
        pending += Lazy(r) { fans.forEach { it() } }
    }

    // --- Following services ----------------------------------------------------------------------

    /** Every service JustWatch lists, for finding one to follow. Fetched once and kept on this TV for a week. */
    private val directory = ServiceDirectoryRepository(AndroidPlatform(activity))

    /** Set when the services followed change, so Home is built again rather than reopened as it was left. */
    private var servicesDirty = false

    private fun isFollowing(s: Service) = FollowedServices.isFollowing(Catalog.followed, s.code)

    private fun setFollowing(s: Service, follow: Boolean) {
        Catalog.followed = if (follow) FollowedServices.follow(Catalog.followed, s) else FollowedServices.unfollow(Catalog.followed, s.code)
        saveFollowedServices(Catalog.followed)
        servicesDirty = true
        CinemaToast.show(activity, if (follow) "Following ${s.label}" else "Stopped following ${s.label}", Toast.LENGTH_SHORT)
    }

    /**
     * One service as a card to follow or unfollow: its app icon, its name, where it runs, and whether it is followed.
     * Pressing it flips that. Used by the services page and by the services search, so both look and act alike.
     */
    private fun serviceRow(s: Service): View {
        val card = GlowCard(activity, 26f).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(activity, 22), 0, Cinema.px(activity, 26), 0); contentDescription = s.label
        }
        val icon = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; setBackgroundColor(Cinema.PLACEHOLDER)
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(activity, 18)) } }
        }
        card.addView(icon, LinearLayout.LayoutParams(Cinema.px(activity, 88), Cinema.px(activity, 88)))
        Catalog.logoUrl(s)?.let { url -> images.load(url, emptyMap(), maxWidth = Cinema.px(activity, 88), tag = pageTag) { b -> icon.setImageBitmap(b) } }
        val words = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        words.addView(Cinema.text(activity, s.label, 28, Cinema.CREAM, serif = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        words.addView(Cinema.text(activity, ServiceDirectory.whereLine(s, Catalog.country), 18, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) })
        card.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 20) })
        val state = Cinema.text(activity, "", 20, Cinema.CREAM, weight = 600).apply { gravity = Gravity.CENTER; maxLines = 1; setPadding(Cinema.px(activity, 26), 0, Cinema.px(activity, 26), 0) }
        fun paint() {
            val on = isFollowing(s); val r = Cinema.pxf(activity, 200)
            state.text = if (on) "✓  Following" else "+  Follow"
            state.setTextColor(if (on) Cinema.CREAM else 0xFF1D0D05.toInt())
            state.background = if (on) GradientDrawable().apply { cornerRadius = r; setColor(Cinema.GLASS_STRONG) }
                else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFEB8A56.toInt(), 0xFFD4623A.toInt())).apply { cornerRadius = r }
            card.contentDescription = (if (on) "Following " else "Follow ") + s.label
        }
        paint()
        card.addView(state, LinearLayout.LayoutParams(-2, Cinema.px(activity, 52)).apply { marginStart = Cinema.px(activity, 14) })
        card.setOnClickListener { setFollowing(s, !isFollowing(s)); paint() }
        return card
    }

    private fun addServiceRows(into: LinearLayout, services: List<Service>) =
        services.forEach { into.addView(serviceRow(it), LinearLayout.LayoutParams(-1, Cinema.px(activity, 120)).apply { topMargin = Cinema.px(activity, 16) }) }

    private fun smallHeading(text: String) = Cinema.text(activity, text, 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true)

    /**
     * Your services: the ones followed (press one to stop following it), a way to find more, and a few the app knows that
     * are not followed yet. Reached from the last card of Browse by service on Home.
     */
    private fun buildServiceManage(gen: Int) {
        // The search is next: have the list of services ready by the time it is opened.
        worker.execute { directory.all(Catalog.country) }
        val followed = Catalog.followed
        val header = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; setPadding(pageStart, Cinema.px(activity, 44), pageEnd, 0) }
        header.addView(Cinema.eyebrow(activity, listOf("Browse by service", if (followed.isEmpty()) "None followed" else "${followed.size} followed")))
        header.addView(Cinema.title(activity, "Your services", 96, maxLines = 1), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 16) })
        header.addView(Cinema.text(activity, "Follow the places films and series stream, air or first appear. They show on Home under Browse by service.", 24, Cinema.MUTED).apply { maxLines = 2 },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 14) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val find = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Find services", R.drawable.ic_cin_search).apply { setOnClickListener { go(Dest.ServiceSearch, remember = true) } }
        buttons.addView(find)
        val reset = CinemaButton(activity, CinemaButton.Kind.GLASS, "Back to the original six").apply {
            setOnClickListener {
                Catalog.followed = FollowedServices.DEFAULT; saveFollowedServices(Catalog.followed); servicesDirty = true
                CinemaToast.show(activity, "Following the original six again", Toast.LENGTH_SHORT)
                go(Dest.ServiceManage)
            }
        }
        buttons.addView(reset, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 18) })
        header.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 30) })
        content.addView(header)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(pageStart, Cinema.px(activity, 36), pageEnd, Cinema.px(activity, 60)) }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))
        if (followed.isEmpty()) list.addView(note("You follow no services. Find one, or go back to the original six."))
        else { list.addView(smallHeading("Following")); addServiceRows(list, followed) }
        val others = Catalog.KNOWN.filter { k -> followed.none { it.code == k.code } }
        if (others.isNotEmpty()) {
            list.addView(smallHeading("Suggested"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 44) })
            addServiceRows(list, others)
        }
        if (gen == generation) focusContent()
    }

    /**
     * Find services: the same search page as the Letterboxd lists search (the in-app keyboard, a phone or the microphone),
     * over every streaming app and catch-up player JustWatch lists in about fifty countries. Each result follows or
     * unfollows when pressed. A service that only runs in another country is still found: its pages are asked for there.
     */
    private fun buildServiceSearch(gen: Int) {
        val lead = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lead.addView(Cinema.icon(activity, R.drawable.ic_cin_search, Cinema.SAFFRON), LinearLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 40)))
        lead.addView(Cinema.text(activity, "Services", 20, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 22) })
        val kb = keyboardSearchPage(lead, "Search streaming services and channels", "service-search", "Search services")
        val side = kb.side; val below = kb.below
        worker.execute { directory.all(Catalog.country) }
        fun showIdle() {
            kb.clear()
            addRecent(SearchKeyboard.SERVICES, side, search = { term -> kb.search(term) }, refresh = { showIdle() })
            side.addView(smallHeading("Try a channel, an app or a network"))
            listOf("bbc", "sky", "adult swim", "e4", "itv", "crunchyroll", "hulu").forEachIndexed { i, s ->
                side.addView(pill(s, accent = false).apply { setOnClickListener { kb.search(s) } },
                    LinearLayout.LayoutParams(-2, Cinema.px(activity, 54)).apply { topMargin = Cinema.px(activity, if (i == 0) 22 else 12) })
            }
        }
        /** [found] null while it is being looked up; [offline] when the list of services could not be fetched at all. */
        fun showResults(q: String, found: List<Service>?, offline: Boolean = false) {
            kb.clear()
            side.addView(smallHeading(if (found == null) "Looking up “$q”…" else "Services matching “$q”").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (found == null) return
            if (offline) { side.addView(note("Couldn't reach the list of services. Check the network and try again."), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) }); return }
            if (found.isEmpty()) { side.addView(note("No service called “$q”. Streaming apps and catch-up players are listed here, not every channel."), LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 18) }); return }
            saveRecentSearches(SearchKeyboard.SERVICES, RecentSearches.add(recentSearches(SearchKeyboard.SERVICES), q))
            addServiceRows(side, found.take(3))
            if (found.size > 3) {
                val more = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(pageStart, Cinema.px(activity, 20), pageEnd, Cinema.px(activity, 60)) }
                more.addView(smallHeading("More matches"))
                addServiceRows(more, found.drop(3))
                below.addView(more)
            }
        }
        fun runQuery(q: String) {
            showResults(q, null)
            val current = { gen == generation && kb.typed.trim() == q }
            worker.execute {
                val all = directory.all(Catalog.country)
                val found = ServiceDirectory.search(all, q, Catalog.country)
                activity.runOnUiThread { if (current()) showResults(q, found, offline = all.isEmpty()) }
            }
        }
        kb.onIdle = { showIdle() }
        kb.onQuery = { q -> runQuery(q) }
        kb.start()
    }


    private fun genreTiles() {
        val r = row("Browse by genre", null)
        r.inner.minimumHeight = Cinema.px(activity, GENRE_H + 8)
        val gen = generation
        val cards = Catalog.GENRES.mapIndexed { i, g ->
            GenreCard(activity, g.code, g.label, GENRE_W, GENRE_H).apply {
                onFocus = { lit -> if (lit) reveal(this) }
                setOnClickListener { go(Dest.InGenre(g), remember = true) }
            }.also { r.inner.addView(it, cardParams(i)) }
        }
        // Each card's still: the first title with a wide picture on that genre's own Trending shelf (the shelf its page
        // opens with, so it is kept for that page too), never one another genre already shows. Fetched only when the row
        // nears the screen.
        pending += Lazy(r) {
            worker.execute {
                val used = HashSet<String>()
                Catalog.GENRES.forEachIndexed { i, g ->
                    if (gen != generation) return@execute
                    val pick = catalog.shelf(Catalog.genrePage(g).first()).titles.firstOrNull { it.backdropUrl != null && used.add(it.key) } ?: return@forEachIndexed
                    activity.runOnUiThread {
                        if (gen != generation) return@runOnUiThread
                        images.load(pick.backdropUrl!!, emptyMap(), maxWidth = Cinema.px(activity, GENRE_W), tag = pageTag) { b -> if (gen == generation) cards[i].setArt(b) }
                    }
                }
            }
        }
    }

    /** A painted tile: a flat Cinema colour with a pale sun or stripes in it, the name in Fraunces; it grows and glows on focus. */
    private fun tile(label: String, seed: Int, wide: Boolean, onSelect: () -> Unit): View {
        val colour = tileColours[seed % tileColours.size]
        val light = colour == Cinema.STRIPE_SAFFRON
        val card = PosterCard(activity, if (wide) 352 else 264, if (wide) 184 else 144, titled = false, radiusPx = 22f)
        card.contentDescription = label
        card.poster.background = MotifDrawable(colour, Cinema.pxf(activity, 22), seed)
        card.poster.foreground = null
        card.poster.addView(Cinema.text(activity, label, if (wide) 36 else 30, if (light) Cinema.INK else Cinema.CREAM, serif = true, weight = 500).apply {
            gravity = Gravity.BOTTOM or Gravity.START; maxLines = 2
            setPadding(Cinema.px(activity, 26), 0, Cinema.px(activity, 20), Cinema.px(activity, 20))
        }, FrameLayout.LayoutParams(-1, -1))
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { onSelect() }
        return card
    }

    /**
     * The last card of a genre shelf ("More Thriller"), which opens that genre's page: its trending, film, series and
     * per-service shelves. Until now the only way there was a theme in Search. None on the genre's own page, where it would
     * only lead back to itself, nor on a Top 10 row, which is exactly ten titles.
     */
    private fun moreCard(spec: ShelfSpec, w: Int = 260, h: Int = 390, art: String? = null): View? {
        val code = spec.genre?.takeUnless { spec.ranked } ?: return null
        val genre = (Catalog.GENRES + Genre("doc", "Documentaries")).firstOrNull { it.code == code } ?: return null
        if ((current as? Dest.InGenre)?.genre?.code == code) return null
        val card = GenreCard(activity, genre.code, genre.label, w, h, more = true)
        art?.let { url -> images.load(url, emptyMap(), maxWidth = Cinema.px(activity, w), tag = pageTag) { b -> card.setArt(b) } }
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { go(Dest.InGenre(genre), remember = true) }
        return card
    }

    // --- Pieces ------------------------------------------------------------------------------------

    /** A heading and an empty scrolling row beneath it. */
    private fun row(heading: String?, caption: String?, into: LinearLayout = content, inset: Boolean = true): Row {
        val margin = if (inset) pageStart else 0
        val end = if (inset) pageEnd else 0
        // `.rowhead`: stripe marker, Fraunces 38px heading, the count. Spacing from the mockup: 50px above the heading,
        // cards 78px below its top, 66px from a row's last line to the next heading.
        val head = heading?.let { h ->
            Cinema.rowHeading(activity, h, caption).apply { setPadding(margin, Cinema.px(activity, 50), end, Cinema.px(activity, 14)) }
        }
        head?.let { into.addView(it) }
        val inner = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(0, Cinema.px(activity, 16), 0, Cinema.px(activity, 16)) }
        val scroller = CalmHorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false
            setPadding(margin, 0, end, 0)
            isHorizontalFadingEdgeEnabled = false
            // Posters slide beneath the frosted rail; the row's far posters load as it nears them.
            setOnScrollChangeListener { _, _, _, _, _ -> refreshGlass(); rowOf[this]?.loadNear() }
            leftLeavesToRail = inset   // a page row hands Left at its start to the rail (title pages have no rail)
            // Only its cards take focus: a row still loading (or empty) is passed over, never focused as a blank strip.
            isFocusable = false
            addView(inner)
        }
        into.addView(scroller, LinearLayout.LayoutParams(-1, -2))
        return Row(head, scroller, inner).also { rowOf[scroller] = it }
    }

    /** Each row's scroller to its row, for loading far posters as it scrolls. Weak, so a dropped page frees its rows. */
    private val rowOf = java.util.WeakHashMap<View, Row>()

    /** Card spacing from the mockup: posters 28px apart (64px for the small ones on a title page); Top 10 items abut. */
    private fun cardParams(index: Int, small: Boolean = false, wide: Boolean = false) =
        LinearLayout.LayoutParams(-2, -2).apply { if (index > 0 && !wide) marginStart = Cinema.px(activity, if (small) 44 else 28) }

    /** "2026 · Animation · 2h 5m": the subline a focused card shows under its name. */
    private fun subline(t: Title) = listOfNotNull(t.year?.toString(), t.genres.firstOrNull(),
        t.runtimeMin?.takeIf { it > 0 && t.kind == Kind.MOVIE }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }).joinToString(" · ")

    /** Cards whose titles the library had not been checked for when they were drawn, with what to do if one is found. */
    private val awaitingLibrary = ArrayList<Pair<Entry, () -> Unit>>()

    /** Runs [found] once [e] turns out to be in the library: now, if the library is already loaded, or when it arrives. */
    private fun whenInLibrary(e: Entry, found: () -> Unit) {
        if (e.library != null || !jellyfin.isConnected) return
        jellyfin.findLoaded(e.title.imdbId, e.title.tmdbId, e.title.kind == Kind.SHOW)?.let { e.library = it; found(); return }
        awaitingLibrary += e to found
    }

    /** The library has arrived: badge every card on the page that is in it, and give the featured title Watch now. */
    private fun rebadge(gen: Int) {
        if (gen != generation || awaitingLibrary.isEmpty()) return
        var heroChanged = false
        val it = awaitingLibrary.iterator()
        while (it.hasNext()) {
            val (e, found) = it.next()
            val item = e.library ?: jellyfin.findLoaded(e.title.imdbId, e.title.tmdbId, e.title.kind == Kind.SHOW) ?: continue
            e.library = item; found(); it.remove()
            if (heroSet && heroEntries.any { h -> h === e }) heroChanged = true
        }
        if (heroChanged && !detailOpen) showHero(heroIndex)
    }

    /** Fills [card]'s poster: the picture, initials when there is none, and the IN LIBRARY badge. */
    private fun dress(card: PosterCard, e: Entry, badge: Boolean = true, defer: Row? = null, holder: View = card) {
        if (e.title.posterUrl == null) card.placeholder(e.title.name)
        if (badge && e.library != null) card.badge()
        else if (badge) whenInLibrary(e) { card.badge() }
        e.title.posterUrl?.let { u ->
            val load = { images.loadShadedPoster(u, e.posterHeaders, 332, pageTag) { b: Bitmap -> card.setShadedPoster(b); Perf.end("movies.open->firstPoster") } }
            if (defer != null) defer.deferred += holder to load else load()
        }
    }

    /** A poster with its name beneath; grows and glows on focus; a badge says when it is already in the library. */
    private fun card(e: Entry, related: List<Title>, small: Boolean = false, nested: Boolean = true, badge: Boolean = true, defer: Row? = null, note: String? = null, chip: String? = null, star: Boolean = true, onSelect: (() -> Unit)? = null): View {
        val card = PosterCard(activity, if (small) 200 else 260, if (small) 300 else 390)
        card.contentDescription = e.title.name
        card.title?.text = e.title.name
        card.subline?.text = note ?: subline(e.title)
        dress(card, e, badge, defer)
        chip?.let { card.chip(it) }
        if (star) trackFavourite(card, e)
        card.onFocus = { lit -> if (lit) { reveal(card); if (onSelect == null) prefetchOnRest(card, e) } }
        card.setOnClickListener { if (onSelect != null) onSelect() else if (nested) openDetail(e, related) else { closeDetail(restoreFocus = false); openDetail(e, related) } }
        return card
    }

    /**
     * A wide 16:9 still with the name, year and genre over its foot. [spotlight] is a row's large lead card: a SPOTLIGHT
     * kicker, a bigger name and two lines of the overview. In the library shows as a pill at the top, clear of the text.
     */
    private fun stillCard(e: Entry, related: List<Title>, w: Int, h: Int, defer: Row?, spotlight: Boolean = false, holder: View? = null, libraryChip: Boolean = true): View {
        val t = e.title
        val card = PosterCard(activity, w, h, titled = false, focusScale = if (spotlight) 1.04f else 1.06f)
        card.contentDescription = t.name
        if (t.backdropUrl == null && t.posterUrl == null) card.placeholder(t.name)
        // A scrim under the words, so they read on any picture.
        card.poster.addView(View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0, 0x33000000, 0xD9000000.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val words = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.START
            val side = Cinema.px(activity, if (spotlight) 38 else 24)
            setPadding(side, 0, side, Cinema.px(activity, if (spotlight) 34 else 20))
        }
        if (spotlight) words.addView(Cinema.text(activity, "Spotlight", 16, Cinema.SAFFRON, weight = 700, tracking = 0.2f, caps = true),
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = Cinema.px(activity, 6) })
        val nameSize = when { spotlight -> 50; w >= STILL_W -> 30; else -> 25 }
        val name = Cinema.text(activity, t.name, nameSize, Cinema.CREAM, serif = true, weight = 500).apply {
            maxLines = if (spotlight) 2 else 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        // The title's own logo, as on its poster, when one exists; the name in type until it arrives, or if there is none.
        val logoW = if (spotlight) w / 2 else w * 3 / 5
        val logoH = when { spotlight -> 150; w >= STILL_W -> 92; else -> 72 }
        val logo = android.widget.ImageView(activity).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_START; adjustViewBounds = true; visibility = View.GONE
            maxWidth = Cinema.px(activity, if (spotlight) w / 2 else w * 3 / 5); maxHeight = Cinema.px(activity, logoH)
            contentDescription = t.name
        }
        val nameSlot = FrameLayout(activity)
        nameSlot.addView(name, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        nameSlot.addView(logo, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = Cinema.px(activity, if (spotlight) 12 else 8) })
        words.addView(nameSlot, LinearLayout.LayoutParams(-2, -2))
        // Logos only on the large stills: the smaller ones (and a grid's many) keep the name in type, which costs no picture.
        t.imdbId?.takeIf { it.startsWith("tt") && (spotlight || w >= STILL_W) }?.let { imdb ->
            images.loadLogo(LOGO_URL.format(imdb), Cinema.px(activity, logoW.coerceAtLeast(1)), pageTag) { b ->
                logo.setImageBitmap(b); logo.visibility = View.VISIBLE
                if (com.danielportelabyrne.cinema.design.Motion.animationsOn(logo)) { logo.alpha = 0f; logo.animate().alpha(1f).setDuration(220).start(); name.animate().alpha(0f).setDuration(220).withEndAction { name.visibility = View.GONE }.start() }
                else name.visibility = View.GONE
            }
        }
        subline(t).takeIf { it.isNotBlank() }?.let {
            words.addView(Cinema.text(activity, it, if (spotlight) 20 else 16, Cinema.alpha(Cinema.CREAM, 0.72f)).apply { maxLines = 1 },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 4) })
        }
        if (spotlight) t.overview?.let {
            words.addView(Cinema.text(activity, it, 20, Cinema.alpha(Cinema.CREAM, 0.86f)).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = Cinema.px(activity, w * 3 / 4)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 12) })
        }
        card.poster.addView(words, FrameLayout.LayoutParams(-1, -1))
        if (libraryChip) { if (e.library != null) card.chip("In library") else whenInLibrary(e) { card.chip("In library") } }
        // No frost refresh when it arrives: card pictures are hardware bitmaps, which the rail's blur leaves out anyway.
        (t.backdropUrl ?: t.posterUrl)?.let { u ->
            val load = { images.loadShadedPoster(u, e.posterHeaders, w, pageTag) { b: Bitmap -> card.setShadedPoster(b); Perf.end("movies.open->firstPoster") } }
            if (defer != null) defer.deferred += (holder ?: card) to load else load()
        }
        card.onFocus = { lit -> if (lit) { reveal(card); prefetchOnRest(card, e) } }
        card.setOnClickListener { openDetail(e, related) }
        return card
    }

    /** A Top 10 item: an outlined Fraunces numeral tucked behind the left of the poster (`.top10`). */
    private fun rankCard(rank: Int, e: Entry, related: List<Title>, defer: Row? = null): View {
        val box = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val numeral = Cinema.text(activity, "$rank", 210, Cinema.alpha(Cinema.SAFFRON, 0.6f), serif = true, weight = 600, tracking = -0.06f).apply {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = Cinema.pxf(activity, 2.5f); maxLines = 1
        }
        box.addView(numeral, FrameLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 28) })
        val card = PosterCard(activity, 224, 336, titled = false)
        card.contentDescription = "$rank. ${e.title.name}"
        dress(card, e, defer = defer, holder = box)
        card.onFocus = { lit -> if (lit) { reveal(card); prefetchOnRest(card, e) } }
        card.setOnClickListener { openDetail(e, related) }
        box.addView(card, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = Cinema.px(activity, 118) })
        box.minimumWidth = Cinema.px(activity, 350)
        return box
    }

    /** A glass card asking for something (connect Jellyfin, sign in); cream when focused. */
    private fun actionCard(title: String, subtitle: String, onSelect: () -> Unit): View {
        val tile = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; gravity = Gravity.CENTER_VERTICAL; setPadding(Cinema.px(activity, 36), 0, Cinema.px(activity, 36), 0) }
        val head = Cinema.text(activity, title, 26, Cinema.CREAM, weight = 500)
        val sub = Cinema.text(activity, subtitle, 20, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        tile.addView(head); tile.addView(sub)
        val rest = Cinema.glass(activity, 26f, Cinema.GLASS); val lit = Cinema.glass(activity, 26f, Cinema.CREAM)
        tile.background = rest
        tile.setOnFocusChangeListener { _, f ->
            tile.background = if (f) lit else rest
            head.setTextColor(if (f) Cinema.INK else Cinema.CREAM); sub.setTextColor(if (f) Cinema.INK else Cinema.MUTED)
            if (f) reveal(tile)
        }
        tile.setOnClickListener { onSelect() }
        tile.layoutParams = LinearLayout.LayoutParams(Cinema.px(activity, 720), Cinema.px(activity, 128))
        return tile
    }

    /** A small Cinema pill (season picker, downloads): glass, or terracotta for a call to action; cream when focused. */
    private fun pill(label: String, accent: Boolean): TextView {
        val r = Cinema.pxf(activity, 200)
        val rest = if (accent) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFEB8A56.toInt(), 0xFFD4623A.toInt())).apply { cornerRadius = r }
                   else GradientDrawable().apply { cornerRadius = r; setColor(Cinema.GLASS_STRONG) }
        val lit = GradientDrawable().apply { cornerRadius = r; setColor(Cinema.CREAM) }
        val restText = if (accent) 0xFF1D0D05.toInt() else Cinema.CREAM
        return Cinema.text(activity, label, 23, restText, weight = 600).apply {
            gravity = Gravity.CENTER; isFocusable = true; isClickable = true; maxLines = 1
            setPadding(Cinema.px(activity, 30), 0, Cinema.px(activity, 30), 0)
            background = android.graphics.drawable.TransitionDrawable(arrayOf(rest, lit)).apply { isCrossFadeEnabled = true }
            setOnFocusChangeListener { v, focused ->
                val d = v.background as android.graphics.drawable.TransitionDrawable
                if (focused) d.startTransition(120) else d.reverseTransition(120)
                (v as TextView).setTextColor(if (focused) Cinema.INK else restText)
            }
        }
    }

    private fun note(text: String) = Cinema.text(activity, text, 22, Cinema.MUTED).apply { setPadding(dp(4), dp(10), 0, dp(10)) }

    /** Something still on its way: the wavy loader with a quiet word beside it, where a note would sit. */
    private fun loading(text: String): View = com.danielportelabyrne.cinema.design.cinema.WaveLoader.withLabel(activity, text).apply { setPadding(dp(4), dp(6), 0, dp(6)) }

    private val surfaces12 by lazy { FastFocus.Surfaces(activity, dp(12).toFloat()) }
    private val surfaces16 by lazy { FastFocus.Surfaces(activity, dp(16).toFloat()) }

    /** Glides the page and the row to the focused card, the same way for every card. */
    private fun reveal(view: View) = FastFocus.reveal(view, if (detailOpen) null else page, dp(24), dp(56), if (detailOpen) Cinema.px(activity, 96) else pageStart)

    private fun dp(v: Int) = Math.round(v * activity.resources.displayMetrics.density)
    private fun px(res: Int) = activity.resources.getDimensionPixelSize(res)

    private companion object {
        const val SHELF_LIMIT = 16
        /** Wide-card sizes in mockup pixels: stills, smaller stills and a spotlight row's lead card (all 16:9). */
        const val STILL_W = 520; const val STILL_H = 292
        const val SMALL_STILL_W = 400; const val SMALL_STILL_H = 225
        const val SPOTLIGHT_W = 880; const val SPOTLIGHT_H = 495
        /** A Browse by genre card, in mockup pixels. */
        const val GENRE_W = 400; const val GENRE_H = 225
        /** Posters in the watchlist row before its way to the full page, and in each line of that page's grid. */
        const val WATCHLIST_ROW = 16
        const val WATCHLIST_PER_LINE = 5
        /** Space between the two stills of a grid row's column. */
        const val GRID_GAP = 28
        /** Title logos (transparent PNGs) by IMDb id, from the artwork service Stremio uses. No key; 404 when there is none. */
        const val LOGO_URL = "https://images.metahub.space/logo/medium/%s/img"
        const val HERO_HEIGHT = 762      // mockup px: the first row's heading sits at 812
        const val HERO_TURN_MS = 9_000L  // the featured title turns this often
        const val RAIL_WIDTH = 92        // mockup px, collapsed
        const val RAIL_WIDTH_OPEN = 420  // mockup px, with labels while the rail has focus
        const val RAIL_EDGE = 24         // mockup px: the open rail's shadow on the page
        const val PREWARM_SHELVES = 4
        const val CARDS_PER_FRAME = 4
        const val TRACK_HEIGHT = 84
        const val TRACK_GAP = 8
        const val EAGER_POSTERS = 9
        const val LB_ROW_LIMIT = 40     // a Letterboxd row shows at most this many; the page itself holds more      // a screen and a half of posters per row; the rest load as the row scrolls
        const val HERO_BACKDROP_PX = 1920
        const val STORAGE_TTL_MS = 60 * 60_000L
        const val REST_PREFETCH_MS = 350L   // how long focus rests on a poster before its backdrop is fetched
        const val DWELL_START_MS = 4_000L   // how long a film page, or an episode, is rested on before its stream is found ahead of time
        const val RETURN_REFRESH_DELAY_MS = 1_500L
        /** Cards on the Continue watching shelf, and how long before a series' episode listing is fetched again. */
        const val CONTINUE_LIMIT = 13
        const val CATALOGUE_RETRY_MS = 10 * 60_000L
        /** Title details for the personal shelves: written this long after the last row taught some; each title asked about at most this often, and this many per page. */
        const val FACTS_FLUSH_MS = 4_000L
        const val FACTS_RETRY_MS = 30 * 60_000L
        const val FACTS_PER_PAGE = 24
        /** A page chosen in the rail takes focus once it has something to focus: looked for this often, this many times. */
        const val ENTER_PAGE_RETRY_MS = 100L
        const val ENTER_PAGE_TRIES = 40
        const val EAGER_SHELVES = 2
        const val PREWARM_DELAY_MS = 4_000L
        const val REUSE_MS = 5 * 60_000L // reopening Movies within this keeps the Home page as it was
    }

    // --- The page's own marks ----------------------------------------------------------------------

    /**
     * The featured title's frame. It is as tall as its text and buttons need, never shorter than [minHeight], so a long
     * title or overview makes the frame taller instead of squeezing the buttons out of the bottom. Only [content]
     * decides the height; the picture and fade simply fill whatever it comes to.
     */
    private class HeroFrame(context: Context, private val minHeight: Int) : FrameLayout(context) {
        var content: View? = null
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val width = MeasureSpec.getSize(widthSpec)
            var height = minHeight
            content?.let {
                it.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                height = maxOf(height, it.measuredHeight)
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    /**
     * The warm band behind Continue watching: the page darkens into a deep brown that fades out again top and bottom, lit
     * by a soft terracotta glow low on the left and a fainter saffron one at the right, like stage light on a curtain.
     */
    private class StatementBand(private val glowRadius: Float) : Drawable() {
        // Painted once per size into a bitmap a quarter the size and blitted after: as live gradients its two large glows
        // were computed for every pixel on every frame, a real cost on a weak TV GPU.
        private var cache: android.graphics.Bitmap? = null
        private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        override fun onBoundsChange(b: android.graphics.Rect) {
            cache = null
            val w = b.width(); val h = b.height()
            if (w <= 0 || h <= 0) return
            val sw = (w / 2).coerceAtLeast(1); val sh = (h / 2).coerceAtLeast(1)
            val bmp = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, sh.toFloat(),
                intArrayOf(0, Cinema.alpha(0xFF1C110C.toInt(), 0.92f), Cinema.alpha(0xFF160D09.toInt(), 0.92f), 0), floatArrayOf(0f, 0.16f, 0.84f, 1f), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), p)
            fun glow(cx: Float, cy: Float, r: Float, color: Int) {
                p.shader = android.graphics.RadialGradient(cx, cy, r, intArrayOf(color, Cinema.alpha(color, 0f)), null, android.graphics.Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
            }
            glow(sw * 0.12f, sh * 0.62f, glowRadius * 0.8f, Cinema.alpha(Cinema.STRIPE_TERRA, 0.16f))
            glow(sw * 0.86f, sh * 0.4f, glowRadius * 0.6f, Cinema.alpha(Cinema.STRIPE_SAFFRON, 0.07f))
            bmp.prepareToDraw()
            cache = bmp
        }
        override fun draw(canvas: Canvas) { cache?.let { canvas.drawBitmap(it, null, bounds, blit) } }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** A tile's painted face: a flat colour with a pale sun, a pair of stripes or a half moon, varying with [seed]. */
    private class MotifDrawable(private val colour: Int, private val radius: Float, private val seed: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val clip = Path().apply { addRoundRect(RectF(b), radius, radius, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(clip)
            paint.color = colour; canvas.drawRect(RectF(b), paint)
            paint.color = Color.argb(46, 246, 236, 219)
            when (seed % 3) {
                0 -> canvas.drawCircle(b.left + w * 0.84f, b.top + h * 0.28f, h * 0.46f, paint)
                1 -> { canvas.drawRect(b.left + w * 0.62f, b.top.toFloat(), b.left + w * 0.72f, b.bottom.toFloat(), paint); canvas.drawRect(b.left + w * 0.78f, b.top.toFloat(), b.left + w * 0.84f, b.bottom.toFloat(), paint) }
                else -> canvas.drawCircle(b.left + w * 0.12f, b.top + h * 1.0f, h * 0.55f, paint)
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

