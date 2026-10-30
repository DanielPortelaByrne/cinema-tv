package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import com.danielportelabyrne.cinema.device.AndroidPlatform
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.graphics.Bitmap
import com.danielportelabyrne.cinema.data.ContinueWatching
import com.danielportelabyrne.cinema.data.ResumeItem
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import android.view.KeyEvent
import android.view.Gravity
import android.widget.TextView
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.movies.StreamProgress
import com.danielportelabyrne.cinema.data.stream.NetworkEstimator
import com.danielportelabyrne.cinema.data.stream.PlaybackRecord
import com.danielportelabyrne.cinema.data.stream.PlaybackStore
import com.danielportelabyrne.cinema.data.stream.RankedStream
import com.danielportelabyrne.cinema.data.stream.Resolution
import com.danielportelabyrne.cinema.data.stream.SourceType
import com.danielportelabyrne.cinema.data.stream.VideoCodec
import org.json.JSONArray
import org.json.JSONObject
import com.danielportelabyrne.cinema.data.jellyfin.EpisodeAutoplay
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.Type
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.widget.ImageView
import android.widget.LinearLayout

private const val TAG = "SmartStream"

/** One stream the player may try, in order. Serialised through the launch intent. */
data class PlayItem(
    val provider: String, val url: String, val headers: Map<String, String>,
    val resolution: Resolution, val codec: VideoCodec, val source: SourceType,
    val bitrateBps: Long?, val label: String, val viaEngine: Boolean = false
)

/**
 * Full-screen player for PLAY. It tries the ranked streams in order (at most three), records how each
 * behaves (startup time, buffering, failures, throughput) so future ranking improves, and if none can
 * be played it hands over to Stremio instead of leaving you on a spinner.
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    /** Loading screen for a stream: artwork, title and live status, until the first frame. */
    private var loadingView: View? = null
    private var titleLabel: TextView? = null
    private var art: String? = null
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var captionsButton: TextView
    private lateinit var nextCard: NextEpisodeCard
    private var autoplay = EpisodeAutoplay()
    private var nextItem: JellyfinItem? = null
    private var nextPlan: JellyfinPlayback.Plan? = null
    private var episodeGeneration = 0
    private var foreground = false
    private val episodeLookup = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val jellyfin by lazy { JellyfinClient(AndroidPlatform(this)) }
    private val cancelNext = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { cancelAutoplay() }
    }
    private val captionPrefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }
    /** Whether captions are wanted. Remembered, and on by default, as the player has always shown them. */
    private var captionsOn = true
    private lateinit var items: List<PlayItem>
    private var imdbId: String? = null
    /** The season and episode of a TV episode played from a stream, so its progress is kept (see StreamProgress). */
    private var streamEpisode: Pair<Int, Int>? = null
    /** A live channel: no resume point, no Continue watching card, and no Stremio to fall back on. */
    private var live = false
    /** Set for a Jellyfin title: playback is reported to the server, so "continue watching" and watched marks follow the viewer. */
    private var jf: com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport? = null
    private val jfSession = com.danielportelabyrne.cinema.data.jellyfin.JellyfinPlaybackSession()
    private val reporter by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }
    private var title: String = ""

    private lateinit var history: PlaybackStore
    private lateinit var continueStore: ContinueWatching
    private var payload: String? = null
    /** Restart was pressed: play from 0:00 and let neither the server's nor this TV's resume point move it. */
    private var forcedStart = false
    private lateinit var network: NetworkEstimator
    private lateinit var bandwidth: DefaultBandwidthMeter
    private val resumePrefs by lazy { getSharedPreferences("resume_positions", MODE_PRIVATE) }

    private var index = 0
    private var audioFallbackQueued = false
    private var releasingPlayer = false
    private var prepareAt = 0L
    private var started = false
    private var ended = false
    private var recorded = false
    private var startupMs: Long? = null
    private var bufferEvents = 0
    private var bufferMs = 0L
    private var bufferStart = 0L
    private var seeking = false
    private var playMs = 0L
    private var playingSince = 0L

    private val startupTimeout = Runnable { fail("no picture in time") }
    private val saveTick = object : Runnable {
        override fun run() { saveResume(); snapshotToContinue(); handler.postDelayed(this, 15_000) }
    }
    private val reportTick = object : Runnable {
        override fun run() { reportToJellyfin("Progress"); recordStreamProgress(); handler.postDelayed(this, REPORT_EVERY_MS) }
    }
    /** The next-episode card is up while the credits still play (it appears as they begin, not at the file's end). */
    private var creditsCard = false

    /** Once a second: when an episode with a next one reaches its credits, the next-episode card comes up. */
    private val creditsTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (::player.isInitialized && !ended && !creditsCard && nextPlan != null && nextItem != null && !autoplay.cancelled && foreground && player.isPlaying) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                val start = com.danielportelabyrne.cinema.data.player.PlayerMath.creditsStartMs(itemChapters, duration)
                if (start != null && player.currentPosition >= start) showCreditsCard()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun showCreditsCard() {
        val next = nextItem ?: return
        val now = SystemClock.elapsedRealtime()
        creditsCard = true
        autoplay = EpisodeAutoplay(CREDITS_COUNTDOWN_MS).also { it.ready(next.id, now); it.end(now) }
        Perf.mark("player.creditsCard at ${player.currentPosition} of ${player.duration}")
        view.hideController()
        chrome?.setState(com.danielportelabyrne.cinema.data.player.PlayerUi.Hidden)
        nextCard.showCard()
        cancelNext.isEnabled = true
        handler.removeCallbacks(autoplayTick)
        autoplayTick.run()
    }

    private val autoplayTick = object : Runnable {
        override fun run() {
            if (!foreground || isFinishing || isDestroyed || !(ended || creditsCard)) return
            val now = SystemClock.elapsedRealtime()
            nextCard.countdown(autoplay.remainingMs(now), autoplay.fraction(now))
            if (autoplay.takeNext(now) != null) {
                startNextEpisode()
            } else if (autoplay.remainingMs(now) != null) {
                handler.postDelayed(this, if (Motion.animationsOn(nextCard)) 16L else 250L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Perf.mark("player.create")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        items = parse(intent.getStringExtra(EXTRA_ITEMS))
        imdbId = intent.getStringExtra(EXTRA_IMDB)
        streamEpisode = intent.getStringExtra(EXTRA_EPISODE)?.split(":")?.takeIf { it.size == 2 }?.let { p -> p[0].toIntOrNull()?.let { s -> p[1].toIntOrNull()?.let { e -> s to e } } }
        live = intent.getBooleanExtra(EXTRA_LIVE, false)
        jf = com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport.fromJson(intent.getStringExtra(EXTRA_JF))
        forcedStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        title =intent.getStringExtra(EXTRA_TITLE).orEmpty()
        art = intent.getStringExtra(EXTRA_ART)
        history = PlaybackStore(this)
        continueStore = ContinueWatching(this)
        payload = intent.getStringExtra(EXTRA_PAYLOAD)
        network = NetworkEstimator(this)
        if (items.isEmpty()) { fallBackToStremio("nothing to play"); return }

        bandwidth = DefaultBandwidthMeter.getSingletonInstance(this)
        trackSelector = DefaultTrackSelector(this)
        captionsOn = captionPrefs.getBoolean("captions", true)
        // The FFmpeg audio decoder comes after the TV's own decoders: it is used only for a soundtrack the TV cannot play
        // (or pass through to the screen), such as Dolby Digital Plus or DTS.
        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
            .setTrackSelector(trackSelector).setBandwidthMeter(bandwidth).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .apply {
                // The buffer lives in this app's own memory. Left to itself it can grow to ~131 MB for video, more than the
                // whole heap on a small TV, which a 4K remux reaches in seconds: always cap it to what the heap can spare.
                setLoadControl(DefaultLoadControl.Builder().setTargetBufferBytes(bufferBytes()).build())
                // Library playback starts promptly over the LAN. Keep the normal
                // long-term and rebuffer reserves; only the first-picture threshold changes.
                if (jf != null && !live) setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    750, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS).setTargetBufferBytes(bufferBytes()).build())
                // A torrent stream arrives unevenly: start quickly, then hold a deep buffer so one slow stretch doesn't stop playback.
                if (streamOnly()) setLoadControl(streamLoadControl())
            }.build()
        view = PlayerView(this).apply {
            player = this@PlayerActivity.player
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 4000
            setBackgroundColor(0xFF000000.toInt())
            keepScreenOn = true
        }
        // The CC button lives in the controls' bottom bar, beside the settings gear, so the remote can select it.
        // The remote's captions key works at any time as well.
        captionsButton = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER; setPadding(28, 0, 28, 0)
            isFocusable = true; isClickable = true
            setOnClickListener { toggleCaptions() }
            setOnFocusChangeListener { v, focused ->
                v.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 40f; setColor(if (focused) 0xFFF3E9CF.toInt() else 0xCC302319.toInt()) }
                (v as TextView).setTextColor(if (focused) 0xFF26271D.toInt() else 0xFFF3E9CF.toInt())
            }
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 40f; setColor(0xCC302319.toInt()) }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()); addView(view, -1, -1) }
        if (live) {
            // Live channels keep the stock controls (there is no timeline to scrub), with the CC button beside the gear.
            val bottomBar = view.findViewById<android.view.ViewGroup?>(androidx.media3.ui.R.id.exo_bottom_bar)
            val settingsGear = view.findViewById<View?>(androidx.media3.ui.R.id.exo_settings)
            if (bottomBar != null) {
                val at = settingsGear?.let { bottomBar.indexOfChild(it) }?.takeIf { it >= 0 } ?: bottomBar.childCount
                bottomBar.addView(captionsButton, at, android.widget.LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL })
            }
        } else {
            // Films and episodes get the Cinema chrome: wavy scrubber, scrubbing preview and the paused screen.
            view.useController = false
            view.isFocusable = false
            chrome = PlayerChrome(this).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)); wireChrome(it) }
        }
        nextCard = NextEpisodeCard(this, play = { playNextNow() }, cancel = { cancelAutoplay() })
        root.addView(nextCard, FrameLayout.LayoutParams(-1, -1))
        onBackPressedDispatcher.addCallback(this, cancelNext)
        if (live) {
            titleLabel = Type.text(this, title, Type.Style.TITLE).apply { textSize = 26f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; visibility = View.GONE }
            root.addView(titleLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(48), dp(32), dp(48), 0) })
            view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { updateTitleLabel(it == View.VISIBLE) })
        }
        if (streamOnly()) {
            loadingView = buildLoading().also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
            pollStatus()
        }
        setContentView(root)
        applyCaptions()
        updateCaptionsLabel()
        player.addListener(listener)
        player.addAnalyticsListener(bandwidthListener)
        handler.postDelayed(saveTick, 15_000)
        handler.postDelayed(reportTick, REPORT_EVERY_MS)
        handler.postDelayed(bitrateTick, 1000)
        handler.postDelayed(creditsTick, 1000)
        if (streamOnly()) handler.postDelayed(statsTick, 5000)
        intent.getStringExtra(EXTRA_META)?.let { m -> runCatching { chrome?.setMeta(metaFrom(JSONObject(m))) } }
        val report = jf
        // A stream or a JustWatch title carries its IMDb id; a library item's ids arrive with its details below.
        lbSource = Triple(imdbId, null, false)
        if (report == null) startAttempt(0) else {
            // Start loading at once from what the library list already said (source and resume point), held paused; the
            // item's details (chapters, trickplay, the server's latest resume point) are fetched alongside. Playback
            // begins when they arrive, or after ITEM_WAIT_MS at most, so a slow answer never holds up the film.
            startAttempt(0, play = false)
            val release = Runnable { if (!isFinishing && !isDestroyed) player.playWhenReady = true }
            handler.postDelayed(release, ITEM_WAIT_MS)
            val client = jellyfin
            reporter.execute {
                val fresh = client.item(report.itemId)
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    if (fresh != null) { applyFreshItem(report, fresh); lbSource = Triple(fresh.imdbId, fresh.tmdbId, fresh.isEpisode) }
                    handler.removeCallbacks(release); player.playWhenReady = true
                    prefetchNextEpisode(fresh)
                }
            }
        }
    }

    /** The player's own Letterboxd reads: the browser may load a page here, because they only happen when the screen pauses. */
    private val lbRepo by lazy { com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository(this, allowBrowser = true) }
    private val lbWorker by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }
    private val lbPictures by lazy { java.util.concurrent.Executors.newFixedThreadPool(2) }
    /** Where the item being played is on IMDb or TMDB, and whether it is an episode (which keeps the Stream card). */
    private var lbSource: Triple<String?, String?, Boolean>? = null

    /**
     * The paused screen's Letterboxd card, read each time the screen appears: the film's page (by IMDb or TMDB id), its cast
     * and crew, its chart and what the followed members made of it. Then the people's pictures and the reviews arrive. Films
     * only: an episode keeps the Stream card. Nothing is loaded while the film plays.
     */
    private fun loadLetterboxd() {
        val (imdb, tmdb, episode) = lbSource ?: return
        if (live || chrome == null || episode) return
        if (!CinemaSettings(AndroidPlatform(this)).isOn(Integration.LETTERBOXD)) return
        val path = imdb?.takeIf { it.startsWith("tt") }?.let { "/imdb/$it/" } ?: tmdb?.toLongOrNull()?.let { "/tmdb/$it/" } ?: return
        lbWorker.execute {
            val L = com.danielportelabyrne.cinema.data.letterboxd.Lb
            val H = com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.HOUR
            val html = lbRepo.pageNow(path, 72 * H) ?: return@execute
            val info = L.film(html) ?: return@execute
            val credits = L.credits(html)
            val chart = lbRepo.pageNow("/csi/film/${info.slug}/rating-histogram/", 24 * 7 * H)?.let { L.histogram(it) }.orEmpty()
            val circle = lbRepo.following.map { u ->
                val name = lbRepo.pageNow("/$u/", 24 * 7 * H)?.let { L.profile(it, u)?.name?.substringBefore(' ') } ?: u
                val mine = lbRepo.pageNow("/$u/film/${info.slug}/", 6 * H)?.let { h -> if (lbRepo.missing(h)) null else L.memberFilm(h, u) }
                name to (mine?.rating?.let { L.stars(it) } ?: if (mine != null) "watched" else null)
            }
            handler.post { if (!isFinishing && !isDestroyed) chrome?.setLetterboxd(info.copy(histogram = chart), circle, credits) }
            // Each person's picture is on their own page: fetched a few at a time, shown as each arrives.
            (credits.cast + credits.crew.flatMap { it.people }).distinctBy { it.path }.forEach { person ->
                lbPictures.execute {
                    val url = lbRepo.pageNow(person.path, 7 * 24 * H)?.let { L.headshot(it) } ?: return@execute
                    handler.post { if (!isFinishing && !isDestroyed) chrome?.setHeadshot(person.path, url) }
                }
            }
            lbPictures.execute {
                val reviews = lbRepo.pageNow("/film/${info.slug}/reviews/by/activity/", 24 * H)?.let { L.reviews(it) }.orEmpty()
                handler.post { if (!isFinishing && !isDestroyed) chrome?.setReviews(reviews) }
            }
        }
    }

    /**
     * The full item arrived after loading started from the list's copy. Another version of the file means starting
     * again with it; a resume point that moved (watched elsewhere, or the list was a few minutes old) means a seek.
     */
    private fun applyFreshItem(report: com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport, fresh: JellyfinItem) {
        val start = if (fresh.played) 0L else fresh.resumeMs
        val updated = report.copy(sourceId = fresh.sourceId, startMs = if (forcedStart) 0L else start)
        jf = updated
        itemAudioCodec = fresh.audioCodec
        if (!started && index == 0 && fresh.audioCodec != null && fresh.audioCodec in stereoCodecs() && items.firstOrNull()?.label != STEREO_LABEL) {
            // This TV could not play this soundtrack last time: go straight to the stereo copy instead of finding out again.
            Perf.mark("player.stereoFirst ${fresh.audioCodec}")
            if (fresh.sourceId != report.sourceId) {
                val plan = JellyfinPlayback.prepare(jellyfin, fresh, title, imdbId ?: "jf:${fresh.id}")
                items = parse(playbackIntent(this, plan.streams, plan.title, plan.resumeKey, updated).getStringExtra(EXTRA_ITEMS))
            }
            items = listOf(stereoItem(updated)) + items
            startAttempt(0, play = false)
        } else if (fresh.sourceId != report.sourceId) {
            Perf.mark("player.sourceChanged")
            val plan = JellyfinPlayback.prepare(jellyfin, fresh, title, imdbId ?: "jf:${fresh.id}")
            items = parse(playbackIntent(this, plan.streams, plan.title, plan.resumeKey, updated).getStringExtra(EXTRA_ITEMS))
            startAttempt(0, play = false)
        } else if (!started && kotlin.math.abs(updated.startMs - report.startMs) > RESUME_DRIFT_MS) {
            player.seekTo(updated.startMs)
        }
        showItem(fresh)
    }

    /** The audio codec of the library file playing, once its full item has arrived. */
    private var itemAudioCodec: String? = null

    /** Soundtrack codecs this TV has had to convert to stereo before. */
    private fun stereoCodecs(): Set<String> = captionPrefs.getStringSet(STEREO_CODECS, null).orEmpty()

    /** The library file with its soundtrack converted to stereo AAC and the picture untouched. */
    private fun stereoItem(report: com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport) = items[0].copy(
        url = com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.stereoStreamUrl(report.base, report.itemId, report.sourceId, "mcm-stereo-${jfSession.id}"),
        label = STEREO_LABEL)

    /** How much of a stream's file the server has, 0..1, from its status; null before the first answer. */
    @Volatile private var streamDownloaded: Float? = null

    /**
     * Every seek from the chrome. Media that cannot seek (a stream whose soundtrack is converted on the fly) would go
     * back to the start, so the seek is refused with a word instead. A stream can only go forward as far as the server
     * has downloaded (sequentially) or the player has buffered; further is held there, with a word.
     */
    private fun seekChecked(ms: Long) {
        val from = player.currentPosition
        if (!player.isCurrentMediaItemSeekable && ms != 0L) {
            CinemaToast.show(this, "Skipping isn't available for this stream", Toast.LENGTH_SHORT)
            Perf.mark("player.seek refused (unseekable)")
            return
        }
        val target = if (!streamOnly()) ms else {
            val limit = com.danielportelabyrne.cinema.data.player.PlayerMath.streamSeekLimit(player.duration, streamDownloaded, player.bufferedPosition)
            com.danielportelabyrne.cinema.data.player.PlayerMath.clampStreamSeek(ms, from, limit)
        }
        if (target != ms) CinemaToast.show(this, "Not downloaded yet", Toast.LENGTH_SHORT)
        if (target != from) player.seekTo(target)
        Perf.mark("player.seek $from -> $ms (to $target, seekable=${player.isCurrentMediaItemSeekable})")
        handler.postDelayed({ if (!isFinishing) Perf.mark("player.seek landed at ${player.currentPosition}") }, 3000)
    }

    private fun prefetchNextEpisode(current: JellyfinItem?) {
        val report = jf ?: return
        if (live || current?.isEpisode != true || current.seriesId == null ||
            com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.cleanBase(jellyfin.server) != report.base) return
        val generation = ++episodeGeneration
        episodeLookup.execute {
            val next = jellyfin.followingEpisode(current) ?: return@execute
            val name = listOfNotNull(next.seriesName, next.episodeLabel(), next.name).joinToString(" · ")
            val plan = JellyfinPlayback.prepare(jellyfin, next, name, "jf:${next.id}")
            handler.post {
                if (generation != episodeGeneration || isFinishing || isDestroyed) return@post
                nextItem = next; nextPlan = plan
                refreshNextHook()
                autoplay.ready(next.id, SystemClock.elapsedRealtime())
                nextCard.bind(next)
                RemoteImages.shared(this).load(jellyfin.imageUrl(next.id, 560), jellyfin.playbackHeaders(), maxWidth = 1000) { bitmap ->
                    if (generation == episodeGeneration) nextCard.artwork.setImageBitmap(bitmap)
                }
                showNextEpisode()
            }
        }
    }

    private fun showNextEpisode() {
        if (!ended || !foreground || nextPlan == null || autoplay.cancelled) return
        view.hideController()
        chrome?.setState(com.danielportelabyrne.cinema.data.player.PlayerUi.Hidden)
        nextCard.showCard()
        cancelNext.isEnabled = true
        handler.removeCallbacks(autoplayTick)
        autoplayTick.run()
    }

    private fun cancelAutoplay() {
        creditsCard = false
        autoplay.cancel()
        handler.removeCallbacks(autoplayTick)
        if (::nextCard.isInitialized) nextCard.hideCard()
        cancelNext.isEnabled = false
        val c = chrome
        if (c != null) c.setState(com.danielportelabyrne.cinema.data.player.PlayerUi.Controls)
        else if (::view.isInitialized) { view.requestFocus(); view.showController() }
    }

    private fun playNextNow() {
        if (foreground && autoplay.takeNext(SystemClock.elapsedRealtime(), playNow = true) != null) startNextEpisode()
    }

    private fun startNextEpisode() {
        val plan = nextPlan ?: return
        val next = nextItem ?: return
        handler.removeCallbacks(autoplayTick)
        nextCard.hideCard(); cancelNext.isEnabled = false
        // Leaving during the credits: this episode is finished, so Jellyfin and the resume list should say so.
        if (creditsCard && !ended) { reportToJellyfin("Stopped", atEnd = true); resumePrefs.edit().remove(imdbId ?: "").apply() }
        creditsCard = false
        record(failed = false)
        // Reuse the player and activity: a long viewing session never builds an activity stack.
        val nextIntent = playbackIntent(this, plan.streams, plan.title, plan.resumeKey, plan.report)
        intent = nextIntent
        items = parse(nextIntent.getStringExtra(EXTRA_ITEMS))
        imdbId = plan.resumeKey; title = plan.title; jf = plan.report
        payload = nextIntent.getStringExtra(EXTRA_PAYLOAD)
        nextItem = null; nextPlan = null; autoplay = EpisodeAutoplay(); episodeGeneration++; forcedStart = false
        startAttempt(0)
        prefetchNextEpisode(next)
    }

    // --- attempts ---------------------------------------------------------------------------------

    private fun startAttempt(i: Int, play: Boolean = true) {
        index = i
        if (i == 0) audioFallbackQueued = false
        val item = items[i]
        started = false; ended = false; recorded = false; keepAwake()
        startupMs = null; bufferEvents = 0; bufferMs = 0; bufferStart = 0; seeking = false; playMs = 0; playingSince = 0
        Log.i(TAG, "Attempt ${i + 1}/${items.size}: ${item.label} [${item.provider}]")

        // A torrent has to find peers before the first byte arrives, so the engine gets far longer than the 8 s default.
        // A torrent has to find peers, and a live torrent can pause for a while between pieces: wait for those, not 15 s.
        val slow = item.viaEngine || item.provider == STREAM_PROVIDER
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(item.headers)
            .setConnectTimeoutMs(if (slow) 60_000 else 15_000).setReadTimeoutMs(if (slow) 60_000 else 15_000)
        // A Jellyfin title picks up where the server says the viewer left off; anything else, where this TV last did.
        val serverStart = jf?.startMs
        val resume = if (forcedStart) 0L else com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.resumePosition(serverStart, resumePrefs.getLong(imdbId ?: "", 0L))
        // Retry transient network errors more patiently for streams: a stalled torrent recovers far more often than it dies.
        val retries = if (slow) STREAM_LOAD_RETRIES else DEFAULT_LOAD_RETRIES
        player.setMediaSource(DefaultMediaSourceFactory(this, extractorsFor(item)).setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(retries))
            .createMediaSource(MediaItem.fromUri(Uri.parse(item.url))), resume)
        prepareAt = SystemClock.elapsedRealtime()
        Perf.mark("player.prepare")
        player.prepare()
        player.playWhenReady = play
        handler.removeCallbacks(startupTimeout)
        handler.postDelayed(startupTimeout, if (slow) ENGINE_STARTUP_TIMEOUT_MS else STARTUP_TIMEOUT_MS)
    }

    /** The current attempt cannot work: log it against its provider, then move on or hand over to Stremio. */
    private fun fail(reason: String) {
        if (releasingPlayer || isFinishing || isDestroyed) return
        handler.removeCallbacks(startupTimeout)
        Log.w(TAG, "Attempt ${index + 1} failed: $reason")
        val position = if (started) player.currentPosition else 0L
        record(failed = true)
        player.stop()
        if (index + 1 < items.size) {
            // While the loading screen is up it says so itself; mid-film a short note is all there is room for.
            loadingScreen?.takeIf { loadingView?.visibility == View.VISIBLE }?.message("Trying another version…")
                ?: CinemaToast.show(this, "Trying another version…", Toast.LENGTH_SHORT)
            startAttempt(index + 1)
            if (position > 60_000) player.seekTo(position)
        } else fallBackToStremio("no version could be played")
    }

    /**
     * Streams keep the Matroska seek index (Cues). It usually sits at the end of the file, but the stream service adds
     * torrents with first-and-last-piece priority, so it arrives within the first seconds. Without it (the old
     * FLAG_DISABLE_SEEK_FOR_CUES) the file was unseekable, and Media3 turns every seek in unseekable media into a seek
     * to the start: scrubbing and the 10 s buttons jumped back to 0:00.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun extractorsFor(item: PlayItem): DefaultExtractorsFactory = DefaultExtractorsFactory()

    /** Streams from the home service never hand over to Stremio: the viewer asked for the film in this player. */
    private fun streamOnly(): Boolean = items.isNotEmpty() && items.all { it.provider == STREAM_PROVIDER }

    /** Start quickly, hold up to 90 s ahead, and rebuild after a rebuffer only once 6 s are buffered again. */
    private fun streamLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(STREAM_MIN_BUFFER_MS, STREAM_MAX_BUFFER_MS, STREAM_START_BUFFER_MS, STREAM_REBUFFER_BUFFER_MS)
        .setTargetBufferBytes(bufferBytes())
        .build()

    /**
     * The most the player may buffer: a quarter of this app's heap (the Movies page and everything else share it), between
     * 24 and 96 MB. 64 MB with the large heap on a Chromecast: several seconds even of a 4K remux, which the LAN refills fast.
     */
    private fun bufferBytes(): Int = (Runtime.getRuntime().maxMemory() / 4).coerceIn(24L * 1024 * 1024, STREAM_TARGET_BUFFER_BYTES.toLong()).toInt()

    private fun fallBackToStremio(reason: String) {
        if (streamOnly()) {
            Log.w(TAG, "Stream failed: $reason")
            CinemaToast.show(this, "Couldn't play this stream. Try again in a minute.", Toast.LENGTH_LONG)
            finish(); return
        }
        if (live) {
            Log.w(TAG, "Live channel failed: $reason")
            CinemaToast.show(this, "This channel isn't available right now", Toast.LENGTH_LONG)
            finish(); return
        }
        Log.w(TAG, "Falling back to Stremio: $reason")
        CinemaToast.show(this, "Couldn't play this automatically, opening Stremio", Toast.LENGTH_LONG)
        val id = imdbId
        val uri = if (id != null) "stremio:///detail/movie/$id/$id" else "stremio:///search?search=" + Uri.encode(title)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.stremio.one").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { CinemaToast.show(this, "Stremio isn't installed on this TV", Toast.LENGTH_LONG) }
        finish()
    }

    // --- telemetry --------------------------------------------------------------------------------

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            updateCaptionsLabel()
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            audio.forEach { group ->
                val format = group.mediaTrackGroup.getFormat(0)
                Log.i(TAG, "Audio: ${format.sampleMimeType}, channels=${format.channelCount}, selected=${group.isSelected}, supported=${group.isTrackSupported(0)}")
            }
            // A valid picture does not mean its soundtrack can play. For example, Chromecast HD
            // cannot decode AC-3/E-AC-3 when the connected TV only accepts stereo PCM.
            val report = jf ?: return
            if (index != 0 || audioFallbackQueued || audio.isEmpty() || tracks.isTypeSelected(C.TRACK_TYPE_AUDIO) || items[0].label == STEREO_LABEL) return
            audioFallbackQueued = true
            // Remember the codec, so the next film with this soundtrack starts on the stereo copy straight away.
            itemAudioCodec?.let { c -> captionPrefs.edit().putStringSet(STEREO_CODECS, stereoCodecs() + c).apply() }
            handler.post {
                if (!foreground || releasingPlayer || isFinishing || isDestroyed || index != 0 || jf?.itemId != report.itemId) return@post
                items = listOf(items[0], stereoItem(report)) + items.drop(1)
                fail("Soundtrack is unsupported; converting audio to stereo")
            }
        }

        override fun onRenderedFirstFrame() {
            if (started) return
            started = true
            keepAwake()
            hideLoading()
            // The film's length is known now, so the chapter marks can be placed on the scrubber.
            chrome?.setChapters(itemChapters); refreshNextHook()
            startupMs = SystemClock.elapsedRealtime() - prepareAt
            handler.removeCallbacks(startupTimeout)
            Log.i(TAG, "First frame after ${startupMs} ms")
            Perf.mark("player.firstFrame (prepare->frame ${startupMs} ms, seekable=${player.isCurrentMediaItemSeekable}, at ${player.currentPosition} ms)")
            reportToJellyfin("Playing")
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> if (started && !seeking && player.playWhenReady && bufferStart == 0L) {
                    bufferStart = SystemClock.elapsedRealtime(); bufferEvents++; rebufferedAt = bufferStart
                }
                Player.STATE_READY -> { closeBuffer(); seeking = false }
                Player.STATE_ENDED -> if (!ended) {
                    closeBuffer(); ended = true; reportToJellyfin("Stopped", atEnd = true); recordStreamProgress(atEnd = true); resumePrefs.edit().remove(imdbId ?: "").apply()
                    continueStore.save(ResumeItem(title, null, packageName, null, player.duration, player.duration, null, System.currentTimeMillis()), null)
                    if (!foreground) autoplay.cancel()
                    autoplay.end(SystemClock.elapsedRealtime())
                    showNextEpisode()
                }
            }
        }

        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            // Seeking is the viewer's choice, not a network fault: never count the wait that follows it.
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                seeking = true; closeBuffer(discard = true)
                if (started && !ended) reportToJellyfin("Progress")
                if (creditsCard && !ended) {
                    // Back before the credits: the card goes, and comes back when they begin again.
                    val start = com.danielportelabyrne.cinema.data.player.PlayerMath.creditsStartMs(itemChapters, player.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
                    if (start == null || new.positionMs < start) {
                        creditsCard = false; handler.removeCallbacks(autoplayTick); nextCard.hideCard(); cancelNext.isEnabled = false
                        autoplay = EpisodeAutoplay().also { state -> nextItem?.let { state.ready(it.id, SystemClock.elapsedRealtime()) } }
                    }
                }
                if (ended && new.positionMs < player.duration) {
                    ended = false; handler.removeCallbacks(autoplayTick); nextCard.hideCard(); cancelNext.isEnabled = false
                    autoplay = EpisodeAutoplay().also { state -> nextItem?.let { state.ready(it.id, SystemClock.elapsedRealtime()) } }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = keepAwake()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (started && !ended) {
                if (isPlaying && jfSession.stopped) reportToJellyfin("Playing")
                reportToJellyfin("Progress")
            }
            val now = SystemClock.elapsedRealtime()
            if (isPlaying) playingSince = now else if (playingSince != 0L) { playMs += now - playingSince; playingSince = 0 }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Player error: ${error.errorCodeName}")
            fail(error.errorCodeName)
        }
    }

    private fun dp(v: Int) = Math.round(v * resources.displayMetrics.density)

    /** The Cinema loading screen (the same one the Movies page shows while finding a copy), until the first frame. */
    private fun buildLoading(): View = com.danielportelabyrne.cinema.design.cinema.StreamLoadingView(this, title).also { v ->
        loadingScreen = v
        v.message("Connecting to the sources…")
        art?.let { url -> RemoteImages.shared(this).load(url, maxWidth = 960) { v.backdrop(it) } }
    }

    private var loadingScreen: com.danielportelabyrne.cinema.design.cinema.StreamLoadingView? = null

    // --- Cinema chrome -------------------------------------------------------------------------------

    private var chrome: PlayerChrome? = null
    private var itemChapters: List<com.danielportelabyrne.cinema.data.player.Chapter> = emptyList()
    private var itemTrickplay: com.danielportelabyrne.cinema.data.player.Trickplay? = null
    private var itemSizeBytes: Long? = null
    private val chromeImages by lazy { RemoteImages.shared(this) }
    private val trickplaySheets = android.util.LruCache<Int, Bitmap>(4)
    private val bitrates = com.danielportelabyrne.cinema.data.player.BitrateHistory()
    @Volatile private var lastBitrate = 0L
    private var rebufferedAt = 0L
    // Live figures from the home stream service, refreshed every few seconds for a stream.
    private var streamSeeds: Int? = null
    private var streamPeers: Int? = null
    private var streamSpeed: Long? = null

    private val bandwidthListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onBandwidthEstimate(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
            lastBitrate = bitrateEstimate
        }
        // Debug builds: where in the file each read starts, to see whether start-up waits on the seek index at the end.
        override fun onLoadStarted(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, load: androidx.media3.exoplayer.source.LoadEventInfo, media: androidx.media3.exoplayer.source.MediaLoadData) {
            if (!started) Perf.mark("player.load from byte ${load.dataSpec.position}")
        }
    }

    /** One bitrate sample a second while playing, for the paused screen's 60-second line. */
    private val bitrateTick = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) bitrates.add(lastBitrate)
            handler.postDelayed(this, 1000)
        }
    }

    /** For a stream, asks the home server for seeds, peers, speed and size every five seconds. */
    private val statsTick = object : Runnable {
        override fun run() {
            if (isFinishing) return
            val url = items.firstOrNull()?.url?.replace("/stream/", "/status/") ?: return
            statusPoller.execute {
                val info = fetchStatus(url)
                runOnUiThread {
                    info?.let { i ->
                        streamSeeds = i.takeIf { it.has("seeds") }?.optInt("seeds"); streamPeers = i.takeIf { it.has("peers") }?.optInt("peers")
                        i.takeIf { it.has("progress") }?.let { streamDownloaded = it.optDouble("progress").toFloat() }
                        streamSpeed = i.takeIf { it.has("speed") }?.optLong("speed"); i.takeIf { it.has("size") }?.optLong("size")?.takeIf { it > 0 }?.let { itemSizeBytes = it }
                    }
                    handler.postDelayed(this, 5000)
                }
            }
        }
    }

    private fun metaFrom(o: JSONObject) = PlayerMeta(
        title = o.optString("title").ifBlank { title }, year = o.optInt("year", 0).takeIf { it > 0 },
        genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        runtimeMin = o.optInt("runtime", 0).takeIf { it > 0 }, certificate = o.optString("cert").ifBlank { null },
        rating = o.optDouble("rating", 0.0).takeIf { it > 0 }, overview = o.optString("overview").ifBlank { null })

    /** What the Jellyfin item says about the film: details, chapters, trickplay tiles and file size. */
    private fun showItem(item: JellyfinItem) {
        val c = chrome ?: return
        c.setMeta(PlayerMeta(title, item.year, item.genres, item.runtimeMin, item.officialRating, item.communityRating, item.overview))
        itemChapters = item.chapters; itemTrickplay = item.trickplay; itemSizeBytes = item.sizeBytes
        Log.i("CinemaPlayer", "Item has ${item.chapters.size} chapters, trickplay=${item.trickplay?.width ?: "none"}, size=${item.sizeBytes ?: "unknown"}")
        c.setChapters(itemChapters)
        c.resumeMs = jf?.startMs?.takeIf { it > 0 }
        c.previewFrame = item.trickplay?.let { t -> { ms: Long, done: (Bitmap) -> Unit -> trickplayFrame(item.id, t, ms, done) } }
        if (item.hasBackdrop) chromeImages.load(jellyfin.backdropUrl(item.id), jellyfin.playbackHeaders(), maxWidth = 960) { c.backdrop = it }
    }

    private fun trickplayFrame(itemId: String, t: com.danielportelabyrne.cinema.data.player.Trickplay, ms: Long, done: (Bitmap) -> Unit) {
        val r = jf ?: return
        val (sheet, x, y) = t.tile(ms)
        fun crop(b: Bitmap) {
            if (x + t.width > b.width || y + t.height > b.height) { Perf.mark("trickplay sheet $sheet is ${b.width}x${b.height}, tile at $x,$y misses"); return }
            done(Bitmap.createBitmap(b, x, y, t.width, t.height))
        }
        trickplaySheets.get(sheet)?.let { crop(it); return }
        Perf.mark("trickplay sheet $sheet for $ms ms (${t.width}px ${t.tileWidth}x${t.tileHeight})")
        val url = com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.trickplayUrl(r.base, itemId, t.width, sheet, r.sourceId)
        chromeImages.load(url, jellyfin.playbackHeaders(), maxWidth = t.width * t.tileWidth) { b -> trickplaySheets.put(sheet, b); crop(b) }
    }

    private fun wireChrome(c: PlayerChrome) {
        c.setMeta(PlayerMeta(title))
        c.positionMs = { player.currentPosition }
        c.durationMs = { player.duration.takeIf { it > 0 } ?: 0L }
        c.isPlaying = { player.playWhenReady }
        c.onPlay = { player.play() }
        c.onPause = { player.pause() }
        c.onSeek = { ms -> seekChecked(ms) }
        c.onCaptions = { toggleCaptions() }
        c.onTracks = { type ->
            val label = when (type) { C.TRACK_TYPE_AUDIO -> "Audio"; C.TRACK_TYPE_VIDEO -> "Quality"; else -> "Subtitles" }
            runCatching {
                val dialog = androidx.media3.ui.TrackSelectionDialogBuilder(this, label, player, type)
                // The stock namer drops forced flags and track names.
                if (type == C.TRACK_TYPE_TEXT) dialog.setTrackNameProvider { f ->
                    com.danielportelabyrne.cinema.data.player.PlayerMath.subtitleName(f.language, f.label,
                        forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                        captions = f.roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0)
                }
                dialog.build().show()
            }
        }
        c.onPrevious = {
            // Back to the start of this chapter, or to the one before if it only just began; without chapters, the start.
            val pos = player.currentPosition
            val now = com.danielportelabyrne.cinema.data.player.PlayerMath.chapterAt(itemChapters, pos)
            val target = when {
                now == null -> 0L
                pos - now.startMs > 5000 -> now.startMs
                else -> itemChapters.getOrNull(now.index - 1)?.startMs ?: 0L
            }
            seekChecked(target)
        }
        c.onNext = { nextTarget()?.invoke() }
        c.onPaused = { loadLetterboxd() }
        c.loadPicture = { url, width, done -> chromeImages.load(url, maxWidth = width) { done(it) } }
        art?.let { url -> chromeImages.load(url, maxWidth = 960) { c.backdrop = it } }
        c.chapterImage = { chapter, done ->
            val r = jf; val tag = chapter.imageTag
            if (r != null && tag != null) chromeImages.load(com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.chapterImageUrl(r.base, r.itemId, chapter.index, tag), jellyfin.playbackHeaders(), maxWidth = 480) { done(it) }
        }
        c.chipLabels = {
            val subs = if (!hasCaptions()) null else "Subtitles · ${if (captionsOn) "On" else "Off"}"
            val a = player.audioFormat
            val audio = a?.let { listOfNotNull(com.danielportelabyrne.cinema.data.player.PlayerMath.language(it.language), com.danielportelabyrne.cinema.data.player.PlayerMath.channels(it.channelCount, it.sampleMimeType)).joinToString(" · ").ifBlank { null } }
            val v = player.videoFormat
            Triple(subs, audio, v?.let { com.danielportelabyrne.cinema.data.player.PlayerMath.qualityChip(it.width, it.height, isHdr(it)) })
        }
        c.streamSpecs = { streamSpecs() }
        c.streamHealth = {
            when {
                !::player.isInitialized || !started -> null
                player.playbackState == Player.STATE_BUFFERING -> "Buffering" to false
                SystemClock.elapsedRealtime() - rebufferedAt < 30_000 || player.totalBufferedDuration < 5_000 -> "Low buffer" to false
                else -> "Healthy" to true
            }
        }
        c.bitrateSamples = { bitrates.values() }
        refreshNextHook()
    }

    /** What Next does: the next chapter, else the next episode once it is known, else nothing. */
    private fun nextTarget(): (() -> Unit)? {
        val pos = if (::player.isInitialized) player.currentPosition else 0L
        val next = com.danielportelabyrne.cinema.data.player.PlayerMath.chapterAt(itemChapters, pos)?.let { itemChapters.getOrNull(it.index + 1) }
        if (next != null) return { player.seekTo(next.startMs) }
        if (nextPlan != null) return { record(failed = false); startNextEpisode() }
        return null
    }

    private fun refreshNextHook() {
        val c = chrome ?: return
        c.onNext = if (nextTarget() != null) ({ nextTarget()?.invoke(); Unit }) else null
        c.refreshNext()
    }

    private fun isHdr(f: androidx.media3.common.Format): Boolean =
        f.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || f.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG

    /** The paused screen's Stream card: only figures something actually reports. */
    private fun streamSpecs(): List<StreamSpec> {
        if (!::player.isInitialized) return emptyList()
        val m = com.danielportelabyrne.cinema.data.player.PlayerMath
        val out = ArrayList<StreamSpec>()
        player.videoFormat?.let { v ->
            m.resolution(v.width, v.height)?.let { res ->
                val sub = listOfNotNull(if (isHdr(v)) (if (v.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG) "HLG" else "HDR10") else null,
                    v.frameRate.takeIf { it > 0 }?.let { String.format(java.util.Locale.UK, "%.3f fps", it).replace(".000", "") }).joinToString(" · ").ifBlank { null }
                out += StreamSpec("Resolution", res, sub)
            }
            m.videoCodec(v.sampleMimeType)?.let { codec ->
                val tenBit = v.colorInfo?.lumaBitdepth?.let { it >= 10 } == true || v.codecs?.contains("Main10", ignoreCase = true) == true || v.codecs?.startsWith("hvc1.2") == true || v.codecs?.startsWith("hev1.2") == true
                out += StreamSpec("Video", codec + if (tenBit) " 10-bit" else "", m.bitrate(v.bitrate.toLong()))
            }
        }
        player.audioFormat?.let { a ->
            val name = listOfNotNull(m.language(a.language), m.channels(a.channelCount, a.sampleMimeType)).joinToString(" · ")
            if (name.isNotBlank()) out += StreamSpec("Audio", name, listOfNotNull(m.audioCodec(a.sampleMimeType), m.bitrate(a.bitrate.toLong())).joinToString(" · ").ifBlank { null })
        }
        val textTracks = player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
        if (textTracks > 0) out += StreamSpec("Subtitles", if (captionsOn) "On" else "Off", "$textTracks ${if (textTracks == 1) "language" else "languages"} available")
        val ahead = player.totalBufferedDuration.takeIf { it > 0 }?.let { "Buffered ${com.danielportelabyrne.cinema.data.player.PlayerMath.clock(it).let { c -> c.replace(":", "m ") + "s" }} ahead" }
        m.size(itemSizeBytes ?: 0)?.let { out += StreamSpec("Size", it, ahead) } ?: ahead?.let { out += StreamSpec("Buffer", it.removePrefix("Buffered ").removeSuffix(" ahead"), "ahead of playback") }
        if (streamPeers != null || streamSeeds != null) {
            out += StreamSpec("Network", listOfNotNull(streamPeers?.let { "$it peers" }, streamSeeds?.let { "$it seeds" }).joinToString(" · "),
                com.danielportelabyrne.cinema.data.stream.LoadingStep.speed(streamSpeed)?.let { "$it down" })
        }
        return out
    }

    /** Asks the home server where the film is, once a second, until the first frame is on screen. */
    /** One thread for the stream service's /status polls (every second while loading, every five while playing). */
    private val statusPoller = java.util.concurrent.Executors.newSingleThreadExecutor()   // its thread starts on first use

    /**
     * One /status answer, or null. The body is read to the end and the stream closed rather than the connection
     * dropped, so the next poll reuses the same keep-alive connection instead of opening a new one.
     */
    private fun fetchStatus(url: String): JSONObject? = try {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 4000
        if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        else { c.errorStream?.use { it.readBytes() }; null }
    } catch (_: Exception) { null }

    private fun pollStatus() {
        if (started || isFinishing || !streamOnly()) return
        val url = items.firstOrNull()?.url?.replace("/stream/", "/status/") ?: return
        statusPoller.execute {
            val info = fetchStatus(url)
            runOnUiThread { applyStatus(info) }
        }
    }

    private fun applyStatus(info: JSONObject?) {
        if (started || isFinishing) return
        val screen = loadingScreen
        val phase = info?.optString("phase")
        val seeds = info?.takeIf { it.has("seeds") }?.optInt("seeds")
        val peers = info?.takeIf { it.has("peers") }?.optInt("peers")
        // `speed` and `size` come from newer stream services only; without them the chips show a dash.
        val speed = info?.takeIf { it.has("speed") }?.optLong("speed")
        val progress = info?.takeIf { it.has("progress") }?.optDouble("progress")?.toFloat()
        progress?.let { streamDownloaded = it }
        if (phase == "failed") {
            screen?.message("Couldn't start this film. Try again in a minute.")
            handler.postDelayed({ if (!isFinishing) finish() }, 3500)
            return
        }
        screen?.step(com.danielportelabyrne.cinema.data.stream.LoadingStep.from(phase, seeds ?: 0, peers ?: 0))
        screen?.stats(seeds, peers, speed, progress)
        screen?.message(when (phase) {
            "connecting" -> if ((seeds ?: 0) + (peers ?: 0) > 0) "Connecting to ${(seeds ?: 0) + (peers ?: 0)} sources." else "Looking for people sharing $title."
            "buffering" -> "Buffering the opening minutes."
            "ready" -> "Starting…"
            else -> "Connecting to the sources…"
        })
        handler.postDelayed({ pollStatus() }, 1000)
    }

    private fun hideLoading() {
        loadingView?.animate()?.alpha(0f)?.setDuration(400)?.withEndAction {
            loadingView?.visibility = View.GONE
            updateTitleLabel(view.isControllerFullyVisible)
        }?.start()
    }

    /** The title sits over the picture only while the controls are up, and never over the loading screen. */
    private fun updateTitleLabel(controlsVisible: Boolean) {
        titleLabel?.visibility = if (controlsVisible && loadingView?.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun hasCaptions(): Boolean = ::player.isInitialized && player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }

    /** Turns captions on or off for everything that plays from here, and remembers the choice. */
    private fun toggleCaptions() {
        captionsOn = !captionsOn
        captionPrefs.edit().putBoolean("captions", captionsOn).apply()
        applyCaptions()
        if (captionsOn && !hasCaptions()) CinemaToast.show(this, "This stream has no captions", Toast.LENGTH_SHORT)
        updateCaptionsLabel()
    }

    private fun applyCaptions() {
        // Without a preferred language, unflagged tracks never auto-select.
        val locales = resources.configuration.locales
        val languages = ((0 until locales.size()).map { locales[it].language } + "en").distinct()
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsOn)
            .setPreferredTextLanguages(*languages.toTypedArray())
            .setSelectUndeterminedTextLanguage(true)
            .build()
    }

    private fun updateCaptionsLabel() {
        if (!::captionsButton.isInitialized) return
        captionsButton.text = when { !hasCaptions() -> "CC · none"; captionsOn -> "CC · On"; else -> "CC · Off" }
    }

    /** The remote's captions key toggles captions from anywhere in the player. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::nextCard.isInitialized && nextCard.visibility == View.VISIBLE &&
            event.keyCode in setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP) cancelAutoplay() else playNextNow()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) toggleCaptions()
            return true
        }
        // The Cinema chrome has the remote once the picture is up (the loading screen and the next-episode card keep theirs).
        val c = chrome
        if (c != null && started && loadingView?.visibility != View.VISIBLE && nextCard.visibility != View.VISIBLE && c.handleKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun closeBuffer(discard: Boolean = false) {
        if (bufferStart != 0L) {
            if (discard) bufferEvents = (bufferEvents - 1).coerceAtLeast(0) else bufferMs += SystemClock.elapsedRealtime() - bufferStart
            bufferStart = 0
        }
    }

    private fun record(failed: Boolean) {
        if (recorded || live) return   // a channel's startup says nothing about the film sources the ranker learns from
        recorded = true
        closeBuffer()
        if (playingSince != 0L) { playMs += SystemClock.elapsedRealtime() - playingSince; playingSince = 0 }
        val item = items.getOrNull(index) ?: return
        val observed = bandwidth.bitrateEstimate.takeIf { it > 0 }
        // A session the viewer just walked out of quickly says nothing about the source, so only real sessions count.
        if (!failed && !started) return
        if (!failed && playMs < 15_000) return
        history.add(PlaybackRecord(
            imdbId ?: title, item.provider, item.resolution, item.codec, item.source, item.bitrateBps,
            startupMs, observed, bufferEvents, bufferMs, playMs, completedNormally = ended, failed = failed, timestampMs = System.currentTimeMillis()
        ))
        // Throughput seen while playing is only a floor: a low-bitrate film downloads no faster than it plays.
        // So it may raise the estimate, or lower it if playback actually stalled, but never lower it otherwise.
        if (started && observed != null && playMs > 15_000 && (bufferEvents > 0 || observed > network.current().bps)) network.record(observed)
        Log.i(TAG, "Recorded: provider=${item.provider} startup=$startupMs bufferEvents=$bufferEvents bufferMs=$bufferMs playMs=$playMs failed=$failed observed=${observed?.div(1_000_000)} Mbps")
    }

    /** Grabs the actual video frame and files it under Continue watching, with what is needed to resume this stream. */
    private fun snapshotToContinue() {
        if (live) return
        if (!started || ended || items.isEmpty() || player.duration <= 0) return
        val position = player.currentPosition
        if (position < 60_000) return
        val item = ResumeItem(
            title, items[index].label.takeIf { it.isNotBlank() }, packageName, null, position, player.duration, null,
            System.currentTimeMillis(), payload
        )
        val generation = episodeGeneration
        captureFrame { frame -> if (generation == episodeGeneration && !ended) continueStore.save(item, frame) }
    }

    private fun captureFrame(onDone: (Bitmap?) -> Unit) {
        val surface = view.videoSurfaceView as? SurfaceView
        if (surface == null || android.os.Build.VERSION.SDK_INT < 26 || surface.width == 0 || surface.height == 0 || !surface.holder.surface.isValid) { onDone(null); return }
        try {
            val bitmap = Bitmap.createBitmap(640, (640f * surface.height / surface.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            PixelCopy.request(surface, bitmap, { result -> onDone(if (result == PixelCopy.SUCCESS) bitmap else null) }, handler)
        } catch (e: Exception) { Log.w(TAG, "Frame capture failed: ${e.javaClass.simpleName}"); onDone(null) }
    }

    private fun saveResume() {
        if (live) return
        val id = imdbId ?: return
        if (started && !ended && player.duration > 0) {
            val p = player.currentPosition
            if (p > 60_000 && p < player.duration * 0.95) resumePrefs.edit().putLong(id, p).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (::nextCard.isInitialized) showNextEpisode()
    }

    /** Paused, the TV's own screensaver may take over: OLED panels burn in. */
    private fun keepAwake() {
        if (!started || player.playWhenReady) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        foreground = false
        if (ended) autoplay.cancel()
        handler.removeCallbacks(autoplayTick)
        if (::nextCard.isInitialized) nextCard.countdown(null, 0f)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            saveResume()
            snapshotToContinue()
            player.pause()
            reportToJellyfin("Stopped")
            record(failed = false)
        }
    }

    /**
     * Tells Jellyfin where playback is. "Playing" opens the session, "Progress" updates it (also on pause), and
     * "Stopped" closes it with the final position, at which point Jellyfin counts a title as watched if it is near the end.
     */
    /** Keeps how far a stream episode got, here, since a stream leaves no record in Jellyfin. Library playback is reported to Jellyfin instead. */
    private fun recordStreamProgress(atEnd: Boolean = false) {
        val episode = streamEpisode ?: return
        val id = imdbId?.takeIf { it.startsWith("tt") } ?: return
        if (jf != null || !::player.isInitialized) return
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val position = if (atEnd && duration > 0) duration else player.currentPosition
        if (position <= 0) return
        val settings = CinemaSettings(AndroidPlatform(this))
        settings.streamProgress = StreamProgress.record(settings.streamProgress, id, episode.first, episode.second, position, duration, System.currentTimeMillis())
    }

    private fun reportToJellyfin(kind: String, pausedOverride: Boolean? = null, atEnd: Boolean = false) {
        val r = jf ?: return
        if (!::player.isInitialized) return
        val paused = pausedOverride ?: !player.playWhenReady   // read only once the player exists
        if (!jfSession.accept(kind)) return
        val position = if (atEnd && player.duration > 0) player.duration else player.currentPosition
        val body = com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.playbackBody(r.itemId, r.sourceId, position, paused, jfSession.id)
        val url = com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi.reportUrl(r.base, kind)
        val headers = items.getOrNull(index)?.headers.orEmpty()
        reporter.execute {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 5000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                try {
                    c.outputStream.use { it.write(body.toByteArray()) }
                    val code = c.responseCode
                    if (code !in 200..299) Log.w("Jellyfin", "Playback $kind report failed (HTTP $code)")
                } finally { c.disconnect() }
            } catch (e: Exception) { Log.w("Jellyfin", "Playback $kind report failed: ${e.javaClass.simpleName}") }
        }
    }

    override fun onDestroy() {
        recordStreamProgress()
        lbWorker.shutdownNow(); lbPictures.shutdownNow()
        episodeGeneration++
        episodeLookup.shutdownNow(); statusPoller.shutdownNow()
        if (chrome != null) chromeImages.close()
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            record(failed = false)
            releasingPlayer = true
            player.removeListener(listener)
            player.release()
        }
        reporter.shutdown() // let the final stop report finish, then release its thread
        super.onDestroy()
    }

    companion object {
        /** Provider name for films from the home stream service. */
        const val STREAM_PROVIDER = "Stream"
        /** How long the next-episode card counts down when it comes up with the credits. */
        private const val CREDITS_COUNTDOWN_MS = 10_000L
        private const val STREAM_MIN_BUFFER_MS = 30_000
        private const val STREAM_MAX_BUFFER_MS = 90_000
        private const val STREAM_START_BUFFER_MS = 1_500
        private const val STREAM_REBUFFER_BUFFER_MS = 6_000
        private const val STREAM_TARGET_BUFFER_BYTES = 96 * 1024 * 1024   // the ceiling; see bufferBytes()
        private const val STREAM_LOAD_RETRIES = 8
        private const val DEFAULT_LOAD_RETRIES = 3

        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_IMDB = "imdb"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_LIVE = "live"
        private const val EXTRA_ART = "art"
        private const val EXTRA_JF = "jf"
        private const val EXTRA_EPISODE = "episode"
        private const val EXTRA_FROM_START = "fromStart"
        /** The film's details for the chrome (year, genres, runtime, certificate, rating, overview), as JSON. */
        private const val EXTRA_META = "meta"
        private const val STARTUP_TIMEOUT_MS = 25_000L
        /** How long a library film waits, paused and loading, for its full item before playing anyway. */
        private const val ITEM_WAIT_MS = 1_500L
        private const val STEREO_LABEL = "Stereo audio"
        // Renamed when the FFmpeg decoder arrived: what was learned without it (DTS, E-AC-3) no longer applies.
        private const val STEREO_CODECS = "stereo_codecs_ffmpeg"
        /** A fresher resume point further than this from the list's one is worth a seek before playback starts. */
        private const val RESUME_DRIFT_MS = 5_000L
        /** How often Jellyfin hears the position while playing. */
        private const val REPORT_EVERY_MS = 5_000L
        /** A torrent has to find peers and buffer first. */
        private const val ENGINE_STARTUP_TIMEOUT_MS = 75_000L

        /** Starts playback of [ranked] in order, capped at three attempts. */
        fun launch(context: Activity, ranked: List<RankedStream>, title: String, imdbId: String?, report: com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport? = null, artUrl: String? = null, meta: PlayerMeta? = null, fromStart: Boolean = false, episode: Pair<Int, Int>? = null) {
            context.startActivity(playbackIntent(context, ranked, title, imdbId, report).putExtra(EXTRA_ART, artUrl).putExtra(EXTRA_META, meta?.let { metaJson(it) })
                .putExtra(EXTRA_FROM_START, fromStart).putExtra(EXTRA_EPISODE, episode?.let { "${it.first}:${it.second}" }))
        }

        private fun metaJson(m: PlayerMeta): String = JSONObject().put("title", m.title).put("year", m.year ?: 0)
            .put("genres", JSONArray(m.genres)).put("runtime", m.runtimeMin ?: 0).put("cert", m.certificate ?: "")
            .put("rating", m.rating ?: 0.0).put("overview", m.overview ?: "").toString()

        private fun playbackIntent(context: Activity, ranked: List<RankedStream>, title: String, imdbId: String?, report: com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport?): Intent {
            val a = JSONArray()
            ranked.filter { it.playable }.take(3).forEach { r ->
                val c = r.candidate
                a.put(JSONObject().put("provider", c.provider).put("url", c.url).put("headers", JSONObject(c.headers as Map<*, *>))
                    .put("res", c.meta.resolution.name).put("codec", c.meta.codec.name).put("src", c.meta.source.name)
                    .put("bitrate", r.requiredBps).put("label", c.meta.qualityLabel()).put("engine", c.viaEngine))
            }
            val payload = JSONObject().put("items", a.toString()).put("imdb", imdbId ?: "").put("title", title).put("jf", report?.toJson() ?: "").toString()
            return Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ITEMS, a.toString()).putExtra(EXTRA_IMDB, imdbId).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_PAYLOAD, payload).putExtra(EXTRA_JF, report?.toJson())
        }

        /** Plays one live channel from [url]. [headers] carry any sign-in the stream needs. */
        fun launchLive(context: Activity, title: String, url: String, headers: Map<String, String> = emptyMap()) {
            val a = JSONArray().put(JSONObject().put("provider", "Live").put("url", url).put("headers", JSONObject(headers as Map<*, *>))
                .put("res", Resolution.UNKNOWN.name).put("codec", VideoCodec.UNKNOWN.name).put("src", SourceType.UNKNOWN.name)
                .put("bitrate", 0).put("label", title).put("engine", false))
            context.startActivity(Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ITEMS, a.toString()).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_LIVE, true))
        }

        /** Reopens a stream saved by a Continue watching card. Returns false if the payload is unusable. */
        fun resume(context: Activity, payload: String): Boolean = try {
            val o = JSONObject(payload)
            context.startActivity(Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ITEMS, o.getString("items")).putExtra(EXTRA_IMDB, o.optString("imdb").ifBlank { null })
                .putExtra(EXTRA_TITLE, o.optString("title")).putExtra(EXTRA_PAYLOAD, payload).putExtra(EXTRA_JF, o.optString("jf").ifBlank { null }))
            true
        } catch (_: Exception) { false }

        private fun parse(json: String?): List<PlayItem> = try {
            val a = JSONArray(json ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val h = o.optJSONObject("headers")
                PlayItem(
                    o.getString("provider"), o.getString("url"),
                    h?.keys()?.asSequence()?.associateWith { k -> h.optString(k) }.orEmpty(),
                    enumOr(o.optString("res"), Resolution.UNKNOWN), enumOr(o.optString("codec"), VideoCodec.UNKNOWN), enumOr(o.optString("src"), SourceType.UNKNOWN),
                    o.optLong("bitrate", -1).takeIf { it > 0 }, o.optString("label"), o.optBoolean("engine", false)
                )
            }
        } catch (_: Exception) { emptyList() }

        private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
