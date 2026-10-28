package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.device.AndroidPlatform
import android.app.Activity
import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.ContinueWatching
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Availability
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.CatalogRepository
import com.danielportelabyrne.cinema.data.movies.Genre
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.ByteSize
import com.danielportelabyrne.cinema.data.movies.EpisodeOutcome
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
    private val music: () -> com.danielportelabyrne.cinema.data.music.SubsonicConfig? = { null }
) {
    /** Where the page is: one of the tabs, or a service or genre opened from Home, or search. */
    private sealed class Dest {
        object Home : Dest()
        object Films : Dest()
        object Series : Dest()
        object Docs : Dest()
        object Library : Dest()
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
        /** Music: the server's album lists, one album, one artist. */
        object Music : Dest()
        class MusicAlbum(val id: String) : Dest()
        class MusicArtist(val id: String, val name: String) : Dest()
        /** Not a page: the rail's gear opens Settings over Cinema. */
        object Settings : Dest()
    }

    /** The rail item a page belongs to: a service or genre page is part of Home, every Letterboxd page of Letterboxd. */
    private fun railOf(d: Dest): Dest = when (d) {
        is Dest.OnService, is Dest.InGenre -> Dest.Home
        is Dest.LbPerson, is Dest.LbListPage, Dest.LbSearch, Dest.LbFollow -> Dest.Letterboxd
        is Dest.MusicAlbum, is Dest.MusicArtist -> Dest.Music
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
        jellyfin.onLibraryChanged = { ui.post { rebadge(generation) } }
        // A few seconds after the app starts, read Home's first shelves into memory (from disk; the network only
        // when there is no copy yet), so the first open of Movies does not pay for reading and parsing them.
        ui.postDelayed({
            shelfPool.execute { Catalog.homePage().take(PREWARM_SHELVES).forEach { catalog.shelf(it) } }
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
        overlay.setBackgroundColor(Cinema.BG)
        ambientView = AmbientBackground(activity).also { host.addView(it, 0, FrameLayout.LayoutParams(-1, -1)) }
    }

    /** Where page content starts: clear of the floating rail (184 mockup px), and its right edge (96). */
    private val pageStart: Int get() = Cinema.px(activity, 184)
    private val pageEnd: Int get() = Cinema.px(activity, 96)

    fun show() {
        Perf.mark("movies.show")
        Perf.begin("movies.open->firstRow"); Perf.begin("movies.open->firstPoster"); Perf.begin("movies.open->hero")
        overlay.visibility = View.VISIBLE
        showAmbient()
        com.danielportelabyrne.cinema.design.Motion.enter(overlay, null, 0f)
        if (!topBuilt) { buildTop(); topBuilt = true }
        syncIntegrations()
        val reuse = current === Dest.Home && trail.isEmpty() && content.childCount > 0 && heroSet &&
            Perf.now() - builtAt < REUSE_MS && jellyfin.isConnected == builtConnected
        trail.clear(); current = Dest.Home
        styleTabs()
        if (reuse) reopen() else build()
        loadStorage()
    }

    /** The Home page as it was left, back at the top with the featured title focused; nothing is rebuilt or refetched. */
    private fun reopen() {
        page.scrollTo(0, 0)
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
        if (voiceActive) voice?.cancel(); closeStreamLoading(); closeDetail(restoreFocus = false); overlay.visibility = View.GONE; ui.removeCallbacks(heroTick)
        // Home is kept for a quick reopen, so its rows keep loading; any other page is dropped.
        if (current !== Dest.Home || trail.isNotEmpty()) { generation++; builtAt = 0L }
    }
    fun close() {
        navigationFocusListener?.let { if (top.viewTreeObserver.isAlive) top.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        worker.shutdownNow(); shelfPool.shutdownNow(); slowPool.shutdownNow(); infoPool.shutdownNow(); images.close(); if (lbStarted) lb.close()
    }

    /**
     * Cinema is back in front. After playing something from the library, ask Jellyfin where the viewer got to (a
     * moment later, so the player's last report has landed) and redo the buttons that depend on it: the title page's
     * Continue / Restart, the featured title's, and a series' episode cards (by reopening its page).
     */
    fun onResume() {
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

    /** Back closes a title's page, then steps back through where the viewer has been, then lets the panel close. */
    fun back(): Boolean {
        if (voiceActive) { voice?.cancel(); return true }
        if (streamLoading != null) { closeStreamLoading(); return true }
        if (detailOpen) { closeDetail(restoreFocus = true); return true }
        if (trail.isNotEmpty()) { current = trail.removeLast(); styleTabs(); build(); return true }
        if (!top.hasFocus()) { activeNavigationItem()?.requestFocus(); return true }
        if (current != Dest.Home) { current = Dest.Home; styleTabs(); build(); return true }
        return false
    }

    /** Debug builds only (see MainActivity): opens a destination by name for UI-review screenshots. */
    fun debugGo(name: String) {
        // "letterboxd", "downloads", "library": open that rail page.
        when (name) { "letterboxd" -> { go(Dest.Letterboxd); return }; "downloads" -> { go(Dest.Downloads); return }; "library" -> { go(Dest.Library); return } }
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
        (top.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.width = Cinema.px(activity, RAIL_WIDTH); lp.height = -1
            lp.setMargins(Cinema.px(activity, 32), Cinema.px(activity, 32), 0, Cinema.px(activity, 32))
            top.layoutParams = lp
        }
        // Rows fade into the ground at the right edge (`.fade-r`), once the featured title has scrolled away.
        edgeFade = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Cinema.alpha(Cinema.BG, 0f), Cinema.alpha(Cinema.BG, 0.75f), Cinema.BG))
            isFocusable = false; alpha = 0f
        }
        body.addView(edgeFade, body.indexOfChild(page) + 1, FrameLayout.LayoutParams(Cinema.px(activity, 220), -1, Gravity.END))
        // Frosted glass behind the rail: a blur of the page and ground beneath, in a sibling so the rail's stripe bar
        // can still draw past the pill's edge.
        frost = FrostView(activity, listOfNotNull(ambientView, page), 46f).also { f ->
            body.addView(f, body.indexOfChild(top), FrameLayout.LayoutParams(Cinema.px(activity, RAIL_WIDTH), -1).apply {
                setMargins(Cinema.px(activity, 32), Cinema.px(activity, 32), 0, Cinema.px(activity, 32))
            })
        }
        // The active item's stripe bar sits just outside the pill's left edge.
        body.clipChildren = false; body.clipToPadding = false
        (page.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.marginStart = 0; page.layoutParams = it }
        page.setOnScrollChangeListener { _, _, _, _, _ -> fillNear(); frost?.refresh(); updateEdgeFade() }
        (page as? CinemaContentScrollView)?.navigationTarget = { activeNavigationItem() }
        val brand = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(activity, 18), 0, 0, 0); contentDescription = "Cinema"
        }
        brand.addView(ImageView(activity).apply { setImageResource(R.drawable.cinema_mark) }, LinearLayout.LayoutParams(Cinema.px(activity, 56), Cinema.px(activity, 56)))
        wordmark = Cinema.text(activity, "Cinema", 36, Cinema.CREAM, serif = true, weight = 500, tracking = -0.02f).apply { visibility = View.GONE; maxLines = 1 }
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
            if (isWithin(focused, page)) lastContentFocus = focused
            setNavigationExpanded(isWithin(focused, top))
            styleTabs()
            frost?.refresh(450)   // a card scaling or a row gliding beneath the rail
        }.also { top.viewTreeObserver.addOnGlobalFocusChangeListener(it) }
        setNavigationExpanded(false)
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
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.circle = circle
        row.addView(circle, LinearLayout.LayoutParams(Cinema.px(activity, 64), Cinema.px(activity, 64)))
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 14) })
        tabs[row] = dest
        navigationLabels[row] = image to name
        row.setOnFocusChangeListener { _, _ -> styleTabs() }
        row.setOnClickListener { if (dest === Dest.Settings) onOpenSettings() else if (current !== dest) go(dest) }
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

    private fun focusContent() {
        val previous = lastContentFocus?.takeIf { it.isAttachedToWindow && it.isShown && it.isEnabled && it.isFocusable }
        if (previous?.requestFocus() == true) return
        val candidates = ArrayList<View>()
        content.addFocusables(candidates, View.FOCUS_FORWARD)
        candidates.firstOrNull { it.isShown && it.isEnabled }?.requestFocus()
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
    private fun setNavigationExpanded(expanded: Boolean) {
        if (navigationExpanded == expanded) return
        navigationExpanded = expanded
        top.layoutParams = top.layoutParams.apply { width = Cinema.px(activity, if (expanded) RAIL_WIDTH_OPEN else RAIL_WIDTH) }
        frost?.let { f ->
            f.layoutParams = f.layoutParams.apply { width = Cinema.px(activity, if (expanded) RAIL_WIDTH_OPEN else RAIL_WIDTH) }
            // Open, the pill lies over the page's text, so the frost is tinted a little deeper to keep the labels clear.
            f.tint = if (expanded) 0xB8120D0A.toInt() else Cinema.RAIL_FILL
        }
        navigationLabels.values.forEach { (_, name) -> name.visibility = if (expanded) View.VISIBLE else View.GONE }
        wordmark.visibility = if (expanded) View.VISIBLE else View.GONE
        storageView.visibility = if (expanded && storageView.text.isNotBlank()) View.VISIBLE else View.GONE
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
        content.removeAllViews(); page.scrollTo(0, 0); heroSet = false; pending.clear(); lastContentFocus = null
        // The rail floats over the page, so nothing is reserved for a top bar. A page that opens on a featured title
        // starts at the very top, so its picture runs up to the screen edge; the others start a little way down.
        val opensOnHero = current === Dest.Home || current === Dest.Films || current === Dest.Series || current === Dest.Docs || current === Dest.Library
        // Search lays itself out in screen coordinates, straight from the mockup.
        content.setPadding(0, if (opensOnHero || current === Dest.Search) 0 else Cinema.px(activity, 52), 0, dp(48))
        if (current !== Dest.Search) { if (voiceActive) voice?.cancel(); searchUi = null }
        frost?.post { frost?.refresh(500); updateEdgeFade() }
        builtAt = Perf.now(); builtConnected = jellyfin.isConnected; awaitingLibrary.clear()
        // Rows never wait for the library: they draw from the catalogue at once, and this one fetch badges them when it lands.
        jellyfin.takeIf { it.isConnected }?.let { j -> worker.execute { j.library(); ui.post { rebadge(gen) } } }
        when (val d = current) {
            Dest.Home -> { buildHero(); continueRow(gen); picksRow(gen); libraryRow(gen, "My library", 18); serviceTiles(); genreTiles(); shelves(Catalog.homePage(), gen) }
            Dest.Films -> { buildHero(); shelves(Catalog.filmsPage(), gen) }
            Dest.Series -> { buildHero(); shelves(Catalog.seriesPage(), gen) }
            Dest.Docs -> { buildHero(); shelves(Catalog.documentariesPage(), gen) }
            Dest.Library -> buildLibrary(gen)
            Dest.Downloads -> buildDownloads(gen)
            Dest.Search -> buildSearch()
            is Dest.OnService -> { pageTitle("On ${d.service.label}"); buildHero(); shelves(Catalog.servicePage(d.service), gen) }
            is Dest.InGenre -> { pageTitle(d.genre.label); buildHero(); shelves(Catalog.genrePage(d.genre), gen) }
            Dest.Letterboxd -> buildLetterboxd(gen)
            is Dest.LbPerson -> buildLbPerson(d.user, gen)
            is Dest.LbListPage -> buildLbList(d.path, gen)
            Dest.LbSearch -> buildLbSearch(gen)
            Dest.LbFollow -> buildLbFollow(gen)
            Dest.Music -> buildMusic(gen)
            is Dest.MusicAlbum -> buildMusicAlbum(d.id, gen)
            is Dest.MusicArtist -> buildMusicArtist(d.id, d.name, gen)
            Dest.Settings -> Unit
        }
        Perf.took("movies.build (main thread)", Perf.now() - buildStart)
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

    /** Rows of posters, loaded three at a time and filled as each arrives; an empty one hides itself. */
    private fun shelves(specs: List<ShelfSpec>, gen: Int) {
        // Every row gets its place on the page now, with room reserved so the page does not jump; its titles are fetched
        // and drawn only when it is about to be seen, which is what keeps opening the page quick.
        specs.forEachIndexed { i, spec ->
            val row = row(spec.heading, null)
            // Room for the row before it loads, so the page does not jump: a poster row, or a shorter Top 10 row.
            row.inner.minimumHeight = Cinema.px(activity, if (spec.ranked) 368 else 488)
            // The first rows are always near the top: fetch them now rather than after the page's first layout (a second
            // or more on a cold start), so they are ready the moment the main thread is free to draw them.
            if (i < EAGER_SHELVES) loadShelf(spec, row, gen) else pending += Lazy(row) { loadShelf(spec, row, gen) }
        }
        page.post { fillNear() }
    }

    private fun loadShelf(spec: ShelfSpec, row: Row, gen: Int) {
        val queuedAt = Perf.now()
        Perf.mark("shelf '${spec.heading}' queued")
        shelfPool.execute {
            val startAt = Perf.now()
            val libraryAt = Perf.now()
            // A Top 10 row is exactly that: ten numbered posters, nothing after them.
            val entries = catalog.shelf(spec).titles.take(if (spec.ranked) 10 else SHELF_LIMIT).map { entry(it) }
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
                if (!heroSet) {
                    val featured = entries.withIndex().filter { it.value.title.backdropUrl != null }.take(3)
                    setHero(featured.map { it.value }, related, featured.map { rankLabel(spec.heading, spec.ranked, it.index) })
                }
                // A few cards a frame, not sixteen in one go (each is a small tree of views): the page keeps moving while a
                // row fills. Posters further along than the screen wait until the row scrolls near them.
                fun addFrom(from: Int) {
                    if (gen != generation) return
                    val to = minOf(from + CARDS_PER_FRAME, entries.size)
                    for (i in from until to) {
                        val e = entries[i]
                        val ranked = spec.ranked && i < 10
                        val defer = if (i >= EAGER_POSTERS) row else null
                        row.inner.addView(if (ranked) rankCard(i + 1, e, related, defer) else card(e, related, defer = defer), cardParams(i, wide = ranked))
                    }
                    if (to < entries.size) android.view.Choreographer.getInstance().postFrameCallback { addFrom(to) }
                    else { Perf.took("shelf '${spec.heading}' ui add ${entries.size} (spread)", Perf.now() - uiAt); row.scroller.post { row.loadNear() }; page.post { fillNear() } }
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
        if (tracks.isEmpty()) { Toast.makeText(activity, "Nothing to play", Toast.LENGTH_SHORT).show(); return }
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
        shuffle.setOnClickListener { musicLoad(gen, { it.randomSongs(100) }) { songs -> if (songs == null) Toast.makeText(activity, "Couldn't reach the music server", Toast.LENGTH_SHORT).show() else playMusic(songs) } }
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
        val wait = note("Loading the album…").apply { setPadding(pageStart, 0, 0, 0) }
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
        Toast.makeText(activity, "Opening ${f.name}…", Toast.LENGTH_SHORT).show()
        slowPool.execute {
            val info = lb.pageNow("/film/${f.slug}/", 7 * 24 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR)
                ?.let { com.danielportelabyrne.cinema.data.letterboxd.Lb.film(it) }
            if (jellyfin.isConnected) jellyfin.library()
            activity.runOnUiThread {
                if (gen != generation || !isVisible) return@runOnUiThread
                val p = info?.page
                val t = lbTitle(f, poster).copy(
                    kind = if (info?.isTv == true) Kind.SHOW else Kind.MOVIE, tmdbId = info?.tmdbId?.toString(),
                    overview = p?.synopsis, runtimeMin = p?.runtimeMin, genres = p?.genres.orEmpty().take(3), year = f.year ?: p?.year,
                    // Letterboxd resizes its backdrops on request: ask for the full 1920 rather than its 1200 default.
                    backdropUrl = info?.backdrop?.replace("-1200-1200-675-675-crop", "-1920-1920-1080-1080-crop"))
                openDetail(entry(t), emptyList())
            }
        }
    }

    /** A row of Letterboxd posters under [heading]: [note] is the line a focused poster shows, [chip] its corner tag. */
    private fun lbFilmRow(
        heading: String, caption: String?, films: List<com.danielportelabyrne.cinema.data.letterboxd.LbFilm>, gen: Int,
        into: LinearLayout = content, line: (com.danielportelabyrne.cinema.data.letterboxd.LbFilm) -> String? = { it.stars },
        chip: (com.danielportelabyrne.cinema.data.letterboxd.LbFilm) -> String? = { null }, empty: String = "Nothing here yet"
    ): Row {
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
                                    val done: (Bitmap) -> Unit = { b -> if (gen == generation) { v.setShadedPoster(b); frost?.refresh() } }
                                    if (visible) images.loadShadedPosterPriority(url, Cinema.px(activity, 260), tag, done)
                                    else images.loadShadedPoster(url, emptyMap(), Cinema.px(activity, 260), tag, done)
                                }
                            }
                        }
                    }
                }
                if (i < EAGER_POSTERS) load() else row.deferred += v to load
            }
            if (from == 0) lbFocusIfLost(row)
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
            lb.load("/$u/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, u) }) { p ->
                if (gen != generation || p == null) return@load
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
            lb.load("/$u/films/diary/", 30 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.MINUTE, { com.danielportelabyrne.cinema.data.letterboxd.Lb.diary(it) }) { d ->
                if (gen != generation) return@load
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
            lb.load("/$u/watchlist/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }) { f ->
                if (gen != generation) return@load
                watch.removeAllViews(); lbFilmRow("${lbName(u)}’s watchlist", null, f.orEmpty().take(20), gen, into = watch, line = { null }, empty = if (f == null) "Couldn't load this watchlist just now" else "Nothing here yet")
            }
            lb.load("/$u/likes/films/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }) { f ->
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
                if (lb.following.any { it.equals(user, true) }) { lb.unfollow(user); Toast.makeText(activity, "Stopped following ${lbName(user)}", Toast.LENGTH_SHORT).show(); current = Dest.Letterboxd; trail.clear(); styleTabs(); build() }
                else { lb.follow(user); Toast.makeText(activity, "Following ${lbName(user)}", Toast.LENGTH_SHORT).show(); build() }
            }
        }

        // Tabs: Fraunces words, the shown one with a stripe beneath, a focused one filled cream.
        val tabRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(tabRow, LinearLayout.LayoutParams(-2, Cinema.px(activity, 62)).apply { marginStart = pageStart - Cinema.px(activity, 26); topMargin = Cinema.px(activity, 44) })
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(holder, LinearLayout.LayoutParams(-1, -2))
        val tabNames = listOf("Watchlist", "Liked", "Diary", "Reviews", "Lists")
        var shown = -1
        val paints = ArrayList<() -> Unit>()
        fun show(i: Int) {
            if (shown == i) return
            shown = i; paints.forEach { it() }
            holder.removeAllViews()
            val loading = note("Loading…").apply { setPadding(pageStart, Cinema.px(activity, 30), 0, 0) }
            holder.addView(loading)
            val H = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR
            fun ready(): Boolean = gen == generation && shown == i
            when (i) {
                0 -> lb.load("/$user/watchlist/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }) { f -> if (!ready()) return@load; holder.removeAllViews(); lbFilmRow("Watchlist", null, f.orEmpty(), gen, holder, line = { null }) }
                1 -> lb.load("/$user/likes/films/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.films(it) }) { f -> if (!ready()) return@load; holder.removeAllViews(); lbFilmRow("Liked films", null, f.orEmpty(), gen, holder, line = { it.stars?.let { s -> "$s  ♥" } ?: "♥" }) }
                2 -> lb.load("/$user/films/diary/", H / 2, { com.danielportelabyrne.cinema.data.letterboxd.Lb.diary(it) }) { d -> if (!ready()) return@load; holder.removeAllViews()
                    lbFilmRow("Diary", null, d.orEmpty().map { it.film.copy(rating = it.rating) }, gen, holder,
                        line = { f -> listOfNotNull(f.stars, d.orEmpty().firstOrNull { it.film.slug == f.slug }?.date?.let { lbDay(it) }).joinToString("  ·  ").ifBlank { null } }) }
                3 -> lb.load("/$user/films/reviews/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.reviews(it) }) { r -> if (!ready()) return@load; holder.removeAllViews(); lbReviewRow("Reviews", r.orEmpty(), gen, holder, byFilm = true) }
                4 -> lb.load("/$user/lists/", H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }) { l -> if (!ready()) return@load; holder.removeAllViews(); lbListRow("Lists", null, l.orEmpty(), gen, holder) }
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

        lb.load("/$user/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(it, user) }) { p ->
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

    /** A list: its name, owner and description, a fan of its first posters, then its films in order. */
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
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(rows, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 10) })
        rows.addView(note("Loading the list…").apply { setPadding(pageStart, Cinema.px(activity, 40), 0, 0) })
        lb.load(path, com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.listPage(it, path) }) { l ->
            if (gen != generation) return@load
            rows.removeAllViews()
            if (l == null) { rows.addView(note("Couldn't load this list just now").apply { setPadding(pageStart, Cinema.px(activity, 40), 0, 0) }); return@load }
            ((header.getChildAt(1) as? TextView))?.let { t -> t.text = l.name; Cinema.style(t, when { l.name.length > 30 -> 64; l.name.length > 16 -> 84; else -> 104 }, Cinema.CREAM, serif = true, tracking = -0.02f) }
            meta.text = listOfNotNull(l.count?.let { "$it films" }, if (l.films().size < (l.count ?: 0)) "first ${l.films().size} shown" else null).joinToString("  ·  ")
            l.description?.let { desc.text = it; desc.visibility = View.VISIBLE }
            val films = l.films()
            // Twelve to a row, each row headed by its places ("1–12"), so a long list stays easy to move through.
            films.chunked(12).forEachIndexed { r, chunk ->
                val from = r * 12 + 1
                lbFilmRow(if (films.size > 12) "$from–${from + chunk.size - 1}" else "In order", null, chunk, gen, into = rows, line = { null }, chip = { f -> "${films.indexOf(f) + 1}" })
            }
            // The fan's five first, ahead of the rows' posters, which queue on the shelf workers.
            slowPool.execute {
                val posters = lbPosters(films.take(5))
                activity.runOnUiThread { if (gen == generation) films.take(5).forEachIndexed { k, f -> posters[f.slug]?.let { u -> images.load(u, maxWidth = 400, tag = pageTag) { b -> fan.posters[k].setImageBitmap(b) } } } }
            }
        }
    }

    private fun com.danielportelabyrne.cinema.data.letterboxd.LbList.films() = previews

    /** Typed text on the Letterboxd search and follow pages. */
    private var lbTyped = ""

    /**
     * List search (`C`): the keyboard on the left, the best matches on the right as they arrive, every match in a row
     * beneath, and with nothing typed, the lists of the people followed here.
     */
    private fun buildLbSearch(gen: Int) {
        lbTyped = ""
        val stage = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        content.addView(stage, LinearLayout.LayoutParams(-1, Cinema.px(activity, 720)))
        val left = Cinema.px(activity, 176)
        fun place(v: View, x: Int, y: Int, w: Int = -2, h: Int = -2) = stage.addView(v, FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
        val field = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Cinema.px(activity, 42), 0, Cinema.px(activity, 42), 0); background = Cinema.glass(activity, 56f, 0x17FFF2DE)
        }
        field.addView(Cinema.icon(activity, R.drawable.ic_cin_search, Cinema.SAFFRON), LinearLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 40)))
        field.addView(com.danielportelabyrne.cinema.design.cinema.LbDots.view(activity, 12), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 22) })
        field.addView(Cinema.text(activity, "Lists", 20, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 16) })
        val query = Cinema.text(activity, "Search Letterboxd lists", 36, 0xFF8F8272.toInt(), weight = 300).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START }
        field.addView(query, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 22) })
        place(field, left, Cinema.px(activity, 12), activity.resources.displayMetrics.widthPixels - left - pageEnd, Cinema.px(activity, 112))
        place(Cinema.eyebrow(activity, listOf("Type to search"), color = Cinema.DIM, markAlpha = 0.5f), left, Cinema.px(activity, 170))
        val side = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        place(side, Cinema.px(activity, 1232), Cinema.px(activity, 170), Cinema.px(activity, 592))
        val below = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(below, LinearLayout.LayoutParams(-1, -2))
        var pending: Runnable? = null
        fun showResults(q: String, lists: List<com.danielportelabyrne.cinema.data.letterboxd.LbList>?) {
            side.removeAllViews(); below.removeAllViews()
            side.addView(Cinema.text(activity, if (lists == null) "Searching for “$q”…" else "Lists matching “$q”", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (lists == null) return
            if (lists.isEmpty()) { side.addView(note("No lists found"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) }); return }
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
            side.removeAllViews(); below.removeAllViews()
            side.addView(Cinema.text(activity, "Try a theme, a director or a mood", 17, Cinema.DIM, weight = 600, tracking = 0.24f, caps = true))
            listOf("a24", "comfort", "horror", "ghibli", "noir").forEachIndexed { i, s ->
                side.addView(pill(s, accent = false).apply { setOnClickListener { lbTyped = s; query.text = s; query.setTextColor(Cinema.CREAM); lbSearchRunner?.invoke(s) } },
                    LinearLayout.LayoutParams(-2, Cinema.px(activity, 54)).apply { topMargin = Cinema.px(activity, if (i == 0) 22 else 12) })
            }
            // Lists by the people followed here.
            val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            below.addView(holder)
            val all = java.util.concurrent.ConcurrentHashMap<String, List<com.danielportelabyrne.cinema.data.letterboxd.LbList>>()
            lb.following.forEach { u ->
                lb.load("/$u/lists/", com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }) { l ->
                    if (gen != generation || lbTyped.isNotBlank() || l == null) return@load
                    all[u] = l.map { it.copy(ownerName = it.ownerName ?: lbName(u)) }
                    holder.removeAllViews(); lbListRow("Lists by the people you follow", null, all.values.flatten(), gen, into = holder)
                }
            }
        }
        fun runQuery(q: String) {
            if (q.length < 2) { showIdle(); return }
            showResults(q, null)
            lb.load("/search/lists/${java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")}/", 24 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR,
                { com.danielportelabyrne.cinema.data.letterboxd.Lb.lists(it) }) { l -> if (gen == generation && lbTyped.trim() == q) showResults(q, l.orEmpty()) }
        }
        lbSearchRunner = { q -> runQuery(q) }
        val keyboard = QwertyKeyboard(activity) { key ->
            lbTyped = Qwerty.type(lbTyped, key)
            query.text = lbTyped.ifEmpty { "Search Letterboxd lists" }; query.setTextColor(if (lbTyped.isEmpty()) 0xFF8F8272.toInt() else Cinema.CREAM)
            pending?.let { ui.removeCallbacks(it) }
            pending = Runnable { runQuery(lbTyped.trim()) }.also { ui.postDelayed(it, 700) }
        }
        place(keyboard, left, Cinema.px(activity, 240), Cinema.px(activity, 980), Cinema.px(activity, 476))
        showIdle()
        keyboard.keys.firstOrNull { it.contentDescription == "q" }?.let { requestFocusRobust(overlay as ViewGroup, it) }
    }

    /** Runs a list search for a suggestion pressed on the search page (set by [buildLbSearch]). */
    private var lbSearchRunner: ((String) -> Unit)? = null

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
            lb.load("/$user/", 2 * com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR, { html -> if (lb.missing(html)) null else com.danielportelabyrne.cinema.data.letterboxd.Lb.profile(html, user) }) { p ->
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
                    setOnClickListener { lb.follow(user); lbNames[user.lowercase()] = p.name.substringBefore(' '); Toast.makeText(activity, "Following ${p.name}", Toast.LENGTH_SHORT).show(); current = Dest.Letterboxd; trail.clear(); styleTabs(); build() }
                }, LinearLayout.LayoutParams(-2, Cinema.px(activity, 76)))
                preview.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 34) })
            }
        }
        val keyboard = QwertyKeyboard(activity) { key ->
            // Usernames have no spaces: Space types an underscore, which they may contain.
            lbTyped = (if (key == Qwerty.Key.Space) Qwerty.type(lbTyped, Qwerty.Key.Char('_')) else Qwerty.type(lbTyped, key)).take(30)
            typed.text = lbTyped
            pending?.let { ui.removeCallbacks(it) }
            if (lbTyped.length >= 2) pending = Runnable { showPreview(lbTyped) }.also { ui.postDelayed(it, 900) } else preview.visibility = View.INVISIBLE
        }
        place(keyboard, left, Cinema.px(activity, 320), Cinema.px(activity, 980), Cinema.px(activity, 476))
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
                figures.addView(Cinema.text(activity, info.average?.let { String.format(Locale.UK, "%.1f", it) } ?: "–", 96, Cinema.CREAM, serif = true))
                val chart = com.danielportelabyrne.cinema.design.cinema.LbHistogram(activity)
                figures.addView(chart, LinearLayout.LayoutParams(Cinema.px(activity, 260), Cinema.px(activity, 90)).apply { marginStart = Cinema.px(activity, 26); bottomMargin = Cinema.px(activity, 14) })
                avg.addView(figures, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 6) })
                avg.addView(Cinema.text(activity, listOfNotNull(info.ratings?.let { "from ${lbCount(it)} ratings" }, info.reviews?.let { "${lbCount(it)} reviews" }).joinToString(" · "), 19, Cinema.MUTED),
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 8) })
                panels.addView(avg, LinearLayout.LayoutParams(Cinema.px(activity, 520), -1))
                lb.load("/csi/film/${info.slug}/rating-histogram/", 24 * H, { com.danielportelabyrne.cinema.data.letterboxd.Lb.histogram(it) }) { c -> c?.let { chart.counts = it } }

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
                    lb.load("/$u/film/${info.slug}/", 6 * H, { h -> if (lb.missing(h)) com.danielportelabyrne.cinema.data.letterboxd.LbMemberFilm(u, null, false, null) else com.danielportelabyrne.cinema.data.letterboxd.Lb.memberFilm(h, u) }) { m ->
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
    // --- Continue watching ------------------------------------------------------------------------

    private val continueStore by lazy { ContinueWatching(activity) }
    private var continueRow: Row? = null

    /** A part-watched title for the Continue row: a stream this app saved, or one of the owner's Jellyfin films or episodes. */
    private class Resumable(
        val title: String, val subtitle: String?, val positionMs: Long, val durationMs: Long, val lastMs: Long,
        val open: () -> Unit, val still: Bitmap?, val stillUrl: String?
    )

    /** The row under the hero: what is part-way through, streams and Jellyfin together, most recent first. Hidden when empty. */
    private fun continueRow(gen: Int) {
        val row = row("Continue watching", null)
        row.heading?.visibility = View.GONE; row.scroller.visibility = View.GONE
        continueRow = row
        fillContinue(row, gen)
    }

    private fun fillContinue(row: Row, gen: Int) {
        worker.execute {
            // Only streams with a saved payload can be reopened.
            val streams = continueStore.load(12).mapNotNull { s ->
                val payload = s.payload ?: return@mapNotNull null
                Resumable(s.title, s.subtitle, s.positionMs, s.durationMs, s.lastEngagedMs,
                    open = { playbackStarted = true; if (!PlayerActivity.resume(activity, payload)) Toast.makeText(activity, "Couldn't reopen ${s.title}", Toast.LENGTH_LONG).show() },
                    still = s.imageUri?.let { continueStore.image(it) }, stillUrl = null)
            }
            val library = if (jellyfin.isConnected) jellyfin.continueWatching() else emptyList()
            val films = library.map { item ->
                Resumable(item.displayTitle, item.displaySubtitle, item.resumeMs, (item.runtimeMin ?: 0) * 60_000L, item.playedMs ?: 0L,
                    open = { playbackStarted = true; JellyfinPlayback.play(activity, jellyfin, item, listOfNotNull(item.displayTitle, item.episodeLabel()).joinToString(" · "), item.resumeKey) },
                    still = null, stillUrl = if (item.hasBackdrop) jellyfin.backdropUrl(item.id) else JellyfinApi.episodeImageUrl(JellyfinApi.cleanBase(jellyfin.server), item.id, 640))
            }
            val all = (streams + films).sortedByDescending { it.lastMs }.take(12)
            activity.runOnUiThread {
                if (gen != generation) return@runOnUiThread
                row.inner.removeAllViews()
                if (all.isEmpty()) { row.hide(); return@runOnUiThread }
                row.heading?.visibility = View.VISIBLE; row.scroller.visibility = View.VISIBLE
                all.forEachIndexed { i, r -> row.inner.addView(resumeCard(r, gen), cardParams(i)) }
                row.setCount(all.size)
                row.scroller.post { row.loadNear() }
            }
        }
    }

    private fun resumeCard(r: Resumable, gen: Int): View {
        val card = ResumeCard(activity)
        card.contentDescription = r.title
        card.title.text = r.title
        val left = if (r.durationMs > 0 && r.positionMs > 0) ((r.durationMs - r.positionMs) / 60_000).toInt().coerceAtLeast(1) else null
        card.meta.text = listOfNotNull(r.subtitle, left?.let { if (it >= 60) "${it / 60}h ${it % 60}m left" else "$it min left" }).joinToString(" · ")
        card.setProgress(if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else 0f)
        r.still?.let { card.setStill(it) }
        r.stillUrl?.let { url -> images.load(url, jellyfin.playbackHeaders(), maxWidth = 640, tag = pageTag) { b -> if (gen == generation) card.setStill(b) } }
        card.onFocus = { lit -> if (lit) reveal(card) }
        card.setOnClickListener { r.open() }
        return card
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
        row.inner.addView(note("Loading…"))
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
            return
        }
        buildHero()
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(holder, LinearLayout.LayoutParams(-1, -2))
        holder.addView(note("Loading your library…").apply { setPadding(margin, dp(10), 0, dp(10)) })
        worker.execute {
            val items = libraryOrder().sort(jellyfin.library())
            activity.runOnUiThread {
                if (gen != generation) return@runOnUiThread
                holder.removeAllViews()
                if (items.isEmpty()) { holder.addView(note("Nothing in your library yet, or Jellyfin couldn't be reached").apply { setPadding(margin, dp(10), 0, dp(10)) }); return@runOnUiThread }
                val all = libraryEntries(items)
                all.filter { it.title.backdropUrl != null }.take(3).let { f -> setHero(f, all.map { e -> e.title }, f.map { recencyLabel(it) }) }
                fun add(heading: String, list: List<Entry>) {
                    if (list.isEmpty()) return
                    val r = row(heading, "${list.size}", into = holder)
                    val related = list.map { it.title }
                    list.forEachIndexed { i, e -> r.inner.addView(card(e, related, badge = false), cardParams(i)) }
                }
                add(libraryOrder().label, all.take(20))
                add("Films", all.filter { it.title.kind == Kind.MOVIE })
                add("Series", all.filter { it.title.kind == Kind.SHOW })
                all.flatMap { e -> e.title.genres.map { g -> g to e } }.groupBy({ it.first }, { it.second })
                    .filterValues { it.size >= 2 }.entries.sortedByDescending { it.value.size }.take(6).forEach { (g, list) -> add(g, list) }
            }
        }
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
        holder.addView(note("Loading…").apply { setPadding(margin, dp(10), 0, 0) })
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
    private fun confirmDelete(e: Entry) {
        val ref = LibraryRef(e.title.name, e.title.year, e.title.tmdbId?.toLongOrNull(), e.title.imdbId)
        infoSheet.show("DELETE", e.title.name, null, listOf("Removes every file for this title from the storage, and from Radarr or Sonarr so it is not downloaded again. Titles those do not hold are removed from Jellyfin. This cannot be undone."), null, listOf(
            SheetAction("Delete", "Remove it from disk", primary = true) {
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
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                        if (removed && detailOpen) { closeDetail(restoreFocus = true); build() }
                        loadStorage(force = true)
                    }
                }
            },
            SheetAction("Keep it") { }
        ))
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
                        Toast.makeText(activity, if (failed == 0) "Cancelled everything pending" else "$failed couldn't be cancelled right now", Toast.LENGTH_LONG).show()
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
                    activity.runOnUiThread { Toast.makeText(activity, if (done) "Cancelled ${item.title}" else "Couldn't cancel it right now", Toast.LENGTH_LONG).show(); if (done) refresh() }
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
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
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
                        activity.runOnUiThread { Toast.makeText(activity, if (done) "Stopped looking for $title" else "Couldn't cancel it right now", Toast.LENGTH_LONG).show(); if (done) refresh() }
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
                        activity.runOnUiThread { Toast.makeText(activity, if (done) "Stopped waiting for $title" else "Couldn't cancel it right now", Toast.LENGTH_LONG).show(); if (done) refresh() }
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
                        Toast.makeText(activity, if (accepted) "Search requested" else "Search couldn't start yet. Try again shortly.", Toast.LENGTH_LONG).show()
                        refresh()
                    }
                }
            }
            actions += SheetAction("Cancel request", "Stop looking for a release", primary = true) {
                    worker.execute {
                        val done = queueClient.cancelRequest(r)
                        activity.runOnUiThread {
                            Toast.makeText(activity, if (done) "Cancelled $name" else "Couldn't cancel this request right now", Toast.LENGTH_LONG).show()
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
        storageView.visibility = if (navigationExpanded && text.isNotEmpty()) View.VISIBLE else View.GONE
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
        val field = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(Cinema.px(activity, 42), 0, Cinema.px(activity, 14), 0)
            background = Cinema.glass(activity, 56f, 0x17FFF2DE)
        }
        val icon = Cinema.icon(activity, R.drawable.ic_cin_search, Cinema.SAFFRON)
        field.addView(icon, LinearLayout.LayoutParams(Cinema.px(activity, 40), Cinema.px(activity, 40)))
        // The query and its caret share a line: the caret follows the last letter typed, and sits before the grey prompt
        // while nothing has been typed (see placeCaret). The query is capped so the caret always has room after it.
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        field.addView(line, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 22) })
        val caret = View(activity).apply { setBackgroundColor(Cinema.SAFFRON) }
        val query = Cinema.text(activity, "", 36, Cinema.CREAM, weight = 300).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START }
        line.addView(caret, LinearLayout.LayoutParams(Cinema.px(activity, 3), Cinema.px(activity, 48)))
        line.addView(query, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(activity, 22) })
        line.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) { query.maxWidth = (v.width - Cinema.px(activity, 40)).coerceAtLeast(0); query.post { query.requestLayout() } }
        }
        val hint = Cinema.text(activity, "Say a title, actor or theme", 18, Cinema.MUTED, tracking = 0.04f)
        field.addView(hint, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Cinema.px(activity, 20) })
        val mic = MicButton(activity).apply { contentDescription = "Voice search"; setOnClickListener { startVoice(held = false) } }
        field.addView(mic, LinearLayout.LayoutParams(Cinema.px(activity, 84), Cinema.px(activity, 84)))
        place(field, left, Cinema.px(activity, 64), activity.resources.displayMetrics.widthPixels - left - pageEnd, Cinema.px(activity, 112))
        caret.animate().alpha(0f).setDuration(530).withEndAction(object : Runnable {
            override fun run() { if (caret.isAttachedToWindow) caret.animate().alpha(if (caret.alpha < 0.5f) 1f else 0f).setDuration(530).withEndAction(this).start() }
        }).start()

        // Keyboard side.
        val eyebrow = Cinema.eyebrow(activity, listOf("Type to search"), color = Cinema.DIM, markAlpha = 0.5f)
        place(eyebrow, left, Cinema.px(activity, 222))
        val keyboard = QwertyKeyboard(activity) { key -> typeKey(key) }
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
        val listening = partial != null
        s.query.text = when {
            listening && partial!!.isNotEmpty() -> android.text.SpannableStringBuilder(partial).apply {
                // The word still being recognised is shown dimmer than the settled ones.
                val cut = partial.lastIndexOf(' ')
                if (cut > 0) setSpan(android.text.style.ForegroundColorSpan(Cinema.DIM), cut, partial.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            listening -> ""
            searchText.isEmpty() -> "Search films, series and documentaries"
            else -> searchText
        }
        s.query.setTextColor(if (!listening && searchText.isEmpty()) 0xFF8F8272.toInt() else Cinema.CREAM)
        Cinema.weight(s.query, 36, serif = false, weight = if (!listening && searchText.isEmpty()) 300 else 400)
        s.caret.visibility = if (listening) View.GONE else View.VISIBLE
        placeCaret(s, after = !listening && searchText.isNotEmpty())
        s.icon.setColorFilter(if (listening) Cinema.TERRA else Cinema.SAFFRON)
        s.hint.text = if (listening) "LISTENING" else "Say a title, actor or theme"
        Cinema.style(s.hint, 18, if (listening) Cinema.TERRA else Cinema.MUTED, weight = if (listening) 600 else 400, tracking = if (listening) 0.14f else 0.04f)
        s.field.background = Cinema.glass(activity, 56f, if (listening) Cinema.alpha(Cinema.STRIPE_TERRA, 0.16f) else 0x17FFF2DE)
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
        val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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
                val top = (library + shelves.flatMap { it.second }).distinctBy { it.title.name.lowercase() + it.title.year }.take(5)
                if (top.isEmpty()) s.side.addView(note("Nothing found for “$query”"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(activity, 18) })
                top.forEach { e -> s.side.addView(searchRow(e, top.map { it.title }), LinearLayout.LayoutParams(-1, Cinema.px(activity, 104)).apply { topMargin = Cinema.px(activity, 18) }) }
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
        val v = voice ?: VoiceSearch(activity) { state -> onVoice(state) }.also { voice = it }
        v.start()
    }

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
    private class MicButton(context: Context) : View(context) {
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        private val icon = context.getDrawable(R.drawable.ic_cin_mic)!!.mutate().apply { setTint(0xFF1D0D05.toInt()) }
        init { isFocusable = true; isClickable = true }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            disc.shader = android.graphics.RadialGradient(w / 2f, h * 0.4f, w / 2f, Cinema.STRIPE_SAFFRON, Cinema.STRIPE_TERRA, android.graphics.Shader.TileMode.CLAMP)
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            val s = if (gainFocus) 1.1f else 1f
            animate().scaleX(s).scaleY(s).setDuration(160).start(); invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            ring.color = Cinema.alpha(Cinema.STRIPE_ORANGE, if (isFocused) 0.3f else 0.12f); canvas.drawCircle(c, c, c + Cinema.pxf(context, 15), ring)
            ring.color = Cinema.alpha(Cinema.STRIPE_TERRA, if (isFocused) 0.45f else 0.25f); canvas.drawCircle(c, c, c + Cinema.pxf(context, 7), ring)
            canvas.drawCircle(c, c, c, disc)
            val i = Cinema.px(context, 19)
            icon.setBounds(i, i, width - i, height - i); icon.draw(canvas)
        }
    }

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
            b.setOnFocusChangeListener { _, f -> if (f && !detailOpen) page.post { page.smoothScrollTo(0, 0) } }
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
        val stream = CinemaButton(activity, CinemaButton.Kind.GLASS, "Stream now", R.drawable.ic_cin_play).apply {
            visibility = if (!inLibrary && t.kind == Kind.MOVIE && seerr.hasStreamService) View.VISIBLE else View.GONE
            setOnClickListener { streamNow(e, this) }
        }
        val gap = Cinema.px(activity, 20); val h = Cinema.px(activity, 76)
        buttons.addView(primary, LinearLayout.LayoutParams(-2, h))
        buttons.addView(restart, LinearLayout.LayoutParams(-2, h).apply { marginStart = gap })
        buttons.addView(stream, LinearLayout.LayoutParams(-2, h).apply { marginStart = gap })
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

        if (t.kind == Kind.SHOW) seasonSection(column, e, primary)
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
        if (!inLibrary && t.kind == Kind.MOVIE && seerr.isSignedIn) slowPool.execute { seerr.prepareStream(t.name, t.year, runtimeMin = t.runtimeMin) }
        detailEntry = e
        // A series' progress lives on its episode cards, so its page is drawn again; a film just redoes its buttons.
        detailButtons = if (t.kind == Kind.SHOW) ({ val back = returnFocus; closeDetail(restoreFocus = false); openDetail(e, related); returnFocus = back })
            else ({ setupPrimary(primary, e, clearStale, restart) { openDetail(e, related) } })
        // The title page covers the screen, so the page beneath it is not drawn at all while it is open (it keeps its
        // place and its scroll position, so Back returns to exactly where it was).
        body.visibility = View.INVISIBLE
        requestFocusRobust(detail, primary)
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

    private fun closeDetail(restoreFocus: Boolean) {
        if (!detailOpen) return
        detailFocusListener?.let { if (detail.viewTreeObserver.isAlive) detail.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        detailFocusListener = null
        detailEntry = null; detailButtons = null
        detail.visibility = View.GONE; detail.removeAllViews()
        body.visibility = View.VISIBLE
        body.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (restoreFocus) returnFocus?.takeIf { it.isAttachedToWindow }?.let { f -> f.post { f.requestFocus() } }
    }

    /** Watch now for what is in the library; for the rest, queue it, or say where its request has got to. */
    /** The title page on screen, and how to redo its buttons when the viewer's place in it changes. */
    private var detailEntry: Entry? = null
    private var detailButtons: (() -> Unit)? = null

    private fun setupPrimary(button: CinemaButton, e: Entry, clearStale: CinemaButton, restart: CinemaButton? = null, refresh: () -> Unit) {
        fun set(label: String, enabled: Boolean, action: (() -> Unit)?) {
            button.setText(label); button.isEnabled = enabled
            // Disabled while checking, so focus arrives late.
            if (enabled && detailOpen && overlay.findFocus() == null) button.requestFocus()
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
                        RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { set("Requested ✓", false, null); Toast.makeText(activity, "Request accepted for ${e.title.name}. The server will search for a release.", Toast.LENGTH_LONG).show() }
                        RequestOutcome.NEEDS_SIGN_IN -> { set("Queue download", true) { queue() }; seerrSheet.show(seerr, jellyfin) { queue() } }
                        RequestOutcome.FAILED -> { set("Queue download", true) { queue() }; Toast.makeText(activity, "Couldn't queue it right now", Toast.LENGTH_LONG).show() }
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
                                    Toast.makeText(activity, "Stale request cleared", Toast.LENGTH_SHORT).show()
                                    refresh()
                                } else {
                                    clearStale.isEnabled = true; clearStale.setText("Clear stale request")
                                    Toast.makeText(activity, "Couldn't clear it. An admin must be signed in, with no requests, downloads or files for this title.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(activity, "Not in your library yet. Use the download button to get it.", Toast.LENGTH_LONG).show()
            return
        }
        if (!seerr.isSignedIn) { seerrSheet.show(seerr, jellyfin) { streamNow(e, button, episode, shownTitle) }; return }
        val token = ++streamToken
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
        slowPool.execute {
            val result = seerr.startStream(e.title.name, e.title.year, episode?.first, episode?.second, if (episode == null) e.title.runtimeMin else null)
            activity.runOnUiThread {
                Perf.end("stream.press->startReply", result.status.name)
                if (token != streamToken) return@runOnUiThread   // cancelled with Back
                android.util.Log.i("StreamNow", "status=${result.status} for ${e.title.name}")
                when (result.status) {
                    StreamStatus.READY -> {
                        val url = result.url ?: return@runOnUiThread failed("The stream service didn't send a link. Try again in a minute.")
                        screen.message("Found a copy. Starting the player…")
                        remember(e.title, streamed = true)
                        val candidate = StreamCandidate("stream:${e.title.key}", PlayerActivity.STREAM_PROVIDER, result.label ?: e.title.name, url, null, StreamMetadata())
                        PlayerActivity.launch(activity, listOf(RankedStream(candidate, 100, 0L, false, Verdict.RECOMMENDED, null, emptyList())), label, e.title.imdbId,
                            artUrl = e.title.backdropUrl ?: e.title.posterUrl,
                            meta = PlayerMeta(label, e.title.year, e.title.genres, e.title.runtimeMin, e.title.age, e.title.imdbScore, e.title.overview))
                        // The player opens over this same screen; take it down once it is covered.
                        ui.postDelayed({ if (token == streamToken) closeStreamLoading() }, 1500)
                    }
                    StreamStatus.NOT_SIGNED_IN -> { closeStreamLoading(); seerrSheet.show(seerr, jellyfin) { streamNow(e, button, episode, shownTitle) } }
                    StreamStatus.NOT_FOUND -> failed("No seeded copy of ${e.title.name} was found on your indexers.")
                    StreamStatus.CONNECTING -> failed("Still connecting to the sources. Try again in a minute.")
                    StreamStatus.UNAVAILABLE -> failed("The stream service isn't reachable.")
                }
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
        Toast.makeText(activity, "Finding the next episode…", Toast.LENGTH_SHORT).show()
        worker.execute {
            val ep = jellyfin.episodeToPlay(item.id)
            activity.runOnUiThread {
                if (ep == null) Toast.makeText(activity, "No episodes found for ${title.name}", Toast.LENGTH_LONG).show()
                else JellyfinPlayback.play(activity, jellyfin, ep, listOfNotNull(title.name, ep.episodeLabel()).joinToString(" · "), "jf:${ep.id}")
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
    private fun seasonSection(parent: LinearLayout, e: Entry, primary: CinemaButton?) {
        val tmdb = e.title.tmdbId?.toLongOrNull()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 60) })
        box.addView(note("Loading seasons…"))
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
            activity.runOnUiThread {
                if (!detailOpen || !box.isAttachedToWindow) return@runOnUiThread
                refresh = drawSeasons(box, e, tmdb, seasons, owned, states, downloads)
                resumeOnHero(primary, e, owned)
                Perf.end("seasons.draw", "${owned.size} episodes, ${seasons.size} seasons")
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

    /** "Resume S1 E2" on the title's own button when Jellyfin has a part-watched episode of it. */
    private fun resumeOnHero(primary: CinemaButton?, e: Entry, owned: List<JellyfinItem>) {
        if (primary == null || e.library == null) return
        val ep = owned.filter { it.resumeMs > 0 && !it.played && it.season != null && it.episode != null }
            .maxWithOrNull(compareBy<JellyfinItem> { it.season }.thenBy { it.episode }) ?: return
        primary.setText("Resume S${ep.season} E${ep.episode}")
        primary.setOnClickListener { playbackStarted = true; remember(e.title, streamed = false); JellyfinPlayback.play(activity, jellyfin, ep, "${e.title.name} · S${ep.season} E${ep.episode}", "jf:${ep.id}") }
    }

    /** Returns a restate of the shown season, for when Sonarr's episode states or downloads arrive later. */
    private fun drawSeasons(box: LinearLayout, e: Entry, tmdb: Long?, seasons: List<SeasonState>, owned: List<JellyfinItem>,
                            states: MutableMap<Pair<Int, Int>, EpisodeState>, downloads: List<QueueItem>): () -> Unit {
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
        var shown = seasons.first()
        var loading = 0
        var railTarget: View? = null
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

        fun setAction(s: SeasonState, allInLibrary: Boolean) {
            actionSlot.removeAllViews()
            val label = EpisodeCards.seasonAction(s.availability, s.name, allInLibrary) ?: return
            val can = s.availability == Availability.NONE && tmdb != null
            val button = CinemaButton(activity, if (can) CinemaButton.Kind.TERRA else CinemaButton.Kind.GLASS, label, if (can) R.drawable.ic_cin_dl else null, small = true)
            button.isEnabled = can
            button.setOnKeyListener(downToRail)
            button.setOnFocusChangeListener { v, f -> if (f) reveal(v) }
            button.setOnClickListener {
                if (!seerr.isSignedIn) { seerrSheet.show(seerr, jellyfin) { showSeason(s) }; return@setOnClickListener }
                button.setText("Requesting…"); button.isEnabled = false
                worker.execute {
                    val outcome = seerr.request(Kind.SHOW, tmdb!!, listOf(s.number))
                    activity.runOnUiThread {
                        when (outcome) {
                            RequestOutcome.QUEUED, RequestOutcome.ALREADY -> { s.availability = Availability.REQUESTED; Toast.makeText(activity, "${e.title.name}, ${s.name} is queued for download", Toast.LENGTH_LONG).show() }
                            RequestOutcome.NEEDS_SIGN_IN -> seerrSheet.show(seerr, jellyfin) { showSeason(s) }
                            RequestOutcome.FAILED -> Toast.makeText(activity, "Couldn't queue ${s.name} right now", Toast.LENGTH_LONG).show()
                        }
                        if (detailOpen && shown === s) { showSeason(s); selectedTab()?.requestFocus() }
                    }
                }
            }
            actionSlot.addView(button, FrameLayout.LayoutParams(-2, -1))
        }

        /** A drawn card; [restate] rebinds it in place from the latest states and downloads. */
        class Shown(val card: EpisodeCard, val mine: JellyfinItem?, val look: () -> CardLook, val latest: () -> CardLook, val restate: () -> Unit)

        /** One episode's card, with its actions. [rebind] redraws it after a request, without reloading the season. */
        fun episodeCard(s: SeasonState, ep: EpisodeRow, mine: JellyfinItem?, token: Int, today: String): Shown {
            val card = EpisodeCard(activity, ep.number, ep.name, ep.overview ?: mine?.overview)
            val label = "${e.title.name} · S${s.number} E${ep.number}"
            fun latest() = episodeCardState(mine != null, states[s.number to ep.number], s.availability, ep.airDate, today, tmdb != null,
                EpisodeCards.download(downloads, tmdb, s.number, ep.number))
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
            // The library's own still when it has one (sent with the Jellyfin sign-in), otherwise TMDB's.
            val (url, headers) = when {
                mine?.hasPrimaryImage == true -> jellyfin.episodeImageUrl(mine.id) to jellyfin.playbackHeaders()
                ep.stillUrl != null -> ep.stillUrl to emptyMap<String, String>()
                else -> null to emptyMap<String, String>()
            }
            if (url == null) card.showPlaceholder()
            else images.load(url, headers, maxWidth = Cinema.px(activity, 400)) { b -> if (token == loading) card.setStill(b) }

            card.play.setOnClickListener { v ->
                if (mine != null) { playbackStarted = true; remember(e.title, streamed = false); JellyfinPlayback.play(activity, jellyfin, mine, label, "jf:${mine.id}") }
                else streamNow(e, v, s.number to ep.number, label)
            }
            card.download.setOnClickListener {
                when (look.state) {
                    CardState.REQUESTED -> Toast.makeText(activity, "Requested, waiting for a release", Toast.LENGTH_SHORT).show()
                    CardState.DOWNLOADING -> Toast.makeText(activity, look.progress?.let { "Downloading, ${Math.round(it * 100)}% so far" } ?: "Downloading", Toast.LENGTH_SHORT).show()
                    // Asked for before it aired: offer to stop waiting for it.
                    CardState.UNAIRED_REQUESTED -> if (tmdb != null) infoSheet.show("REQUESTED", label, EpisodeCards.airsLabel(ep.airDate),
                        listOf("It will download by itself once it airs."), null, listOf(
                            SheetAction("Cancel request", "Stop waiting for this episode", primary = true) {
                                worker.execute {
                                    val done = sonarr.unwantEpisodes(tmdb, s.number, listOf(ep.number))
                                    activity.runOnUiThread {
                                        if (done) {
                                            states[s.number to ep.number] = EpisodeState.NONE
                                            look = episodeCardState(mine != null, EpisodeState.NONE, s.availability, ep.airDate, today, true, null)
                                            if (token == loading) rebind()
                                        }
                                        Toast.makeText(activity, if (done) "Cancelled S${s.number} E${ep.number}" else "Couldn't cancel it right now", Toast.LENGTH_LONG).show()
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
                                            look = episodeCardState(mine != null, EpisodeState.REQUESTED, s.availability, ep.airDate, today, true, null)
                                            if (token == loading) rebind()
                                            Toast.makeText(activity, if (look.unaired) EpisodeCards.preRequestDone(s.number, ep.number, ep.airDate)
                                                else "${e.title.name} S${s.number} E${ep.number} is queued", Toast.LENGTH_LONG).show()
                                        }
                                        EpisodeOutcome.NOT_CONFIGURED -> Toast.makeText(activity, "Sonarr's details aren't available, so only whole seasons can be requested here", Toast.LENGTH_LONG).show()
                                        EpisodeOutcome.NOT_FOUND -> Toast.makeText(activity, "Sonarr couldn't find that episode yet. Try again in a moment", Toast.LENGTH_LONG).show()
                                        EpisodeOutcome.FAILED -> Toast.makeText(activity, "Couldn't queue that episode right now", Toast.LENGTH_LONG).show()
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
            rail.inner.removeAllViews(); rail.inner.addView(note("Loading episodes…"))
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
                        railTarget = cards.firstOrNull { it.mine != null && !it.mine.played && it.mine.resumeMs > 0 }?.card?.firstStop
                            ?: cards.first().card.firstStop
                    }
                    settle()
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
        showSeason(seasons.first())
        return { restateShown() }
    }

    // --- Category tiles ------------------------------------------------------------------------

    /** The stripe set's colours plus the arches' olive and a deep teal, for the painted browse tiles. */
    private val tileColours = intArrayOf(Cinema.STRIPE_TERRA, 0xFF2F6E66.toInt(), Cinema.STRIPE_SAFFRON, 0xFF4D5B2C.toInt(), Cinema.STRIPE_RED, Cinema.MAROON)

    private fun serviceTiles() {
        val r = row("Browse by service", null)
        Catalog.SERVICES.forEachIndexed { i, s -> r.inner.addView(tile(s.label, i, wide = true) { go(Dest.OnService(s), remember = true) }, cardParams(i)) }
    }

    private fun genreTiles() {
        val r = row("Browse by genre", null)
        Catalog.GENRES.forEachIndexed { i, g -> r.inner.addView(tile(g.label, i + 2, wide = false) { go(Dest.InGenre(g), remember = true) }, cardParams(i)) }
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
            setOnScrollChangeListener { _, _, _, _, _ -> frost?.refresh(); rowOf[this]?.loadNear() }
            leftLeavesToRail = inset   // a page row hands Left at its start to the rail (title pages have no rail)
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
            val load = { images.loadShadedPoster(u, e.posterHeaders, 332, pageTag) { b: Bitmap -> card.setShadedPoster(b); frost?.refresh(); Perf.end("movies.open->firstPoster") } }
            if (defer != null) defer.deferred += holder to load else load()
        }
    }

    /** A poster with its name beneath; grows and glows on focus; a badge says when it is already in the library. */
    private fun card(e: Entry, related: List<Title>, small: Boolean = false, nested: Boolean = true, badge: Boolean = true, defer: Row? = null, note: String? = null, chip: String? = null, onSelect: (() -> Unit)? = null): View {
        val card = PosterCard(activity, if (small) 200 else 260, if (small) 300 else 390)
        card.contentDescription = e.title.name
        card.title?.text = e.title.name
        card.subline?.text = note ?: subline(e.title)
        dress(card, e, badge, defer)
        chip?.let { card.chip(it) }
        card.onFocus = { lit -> if (lit) { reveal(card); if (onSelect == null) prefetchOnRest(card, e) } }
        card.setOnClickListener { if (onSelect != null) onSelect() else if (nested) openDetail(e, related) else { closeDetail(restoreFocus = false); openDetail(e, related) } }
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

    private val surfaces12 by lazy { FastFocus.Surfaces(activity, dp(12).toFloat()) }
    private val surfaces16 by lazy { FastFocus.Surfaces(activity, dp(16).toFloat()) }

    /** Glides the page and the row to the focused card, the same way for every card. */
    private fun reveal(view: View) = FastFocus.reveal(view, if (detailOpen) null else page, dp(24), dp(56), if (detailOpen) Cinema.px(activity, 96) else pageStart)

    private fun dp(v: Int) = Math.round(v * activity.resources.displayMetrics.density)
    private fun px(res: Int) = activity.resources.getDimensionPixelSize(res)

    private companion object {
        const val SHELF_LIMIT = 16
        const val HERO_HEIGHT = 762      // mockup px: the first row's heading sits at 812
        const val HERO_TURN_MS = 9_000L  // the featured title turns this often
        const val RAIL_WIDTH = 92        // mockup px, collapsed
        const val RAIL_WIDTH_OPEN = 420  // mockup px, with labels while the rail has focus
        const val PREWARM_SHELVES = 4
        const val CARDS_PER_FRAME = 4
        const val TRACK_HEIGHT = 84
        const val TRACK_GAP = 8
        const val EAGER_POSTERS = 9
        const val LB_ROW_LIMIT = 40     // a Letterboxd row shows at most this many; the page itself holds more      // a screen and a half of posters per row; the rest load as the row scrolls
        const val HERO_BACKDROP_PX = 1920
        const val STORAGE_TTL_MS = 60 * 60_000L
        const val REST_PREFETCH_MS = 350L   // how long focus rests on a poster before its backdrop is fetched
        const val RETURN_REFRESH_DELAY_MS = 1_500L
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

