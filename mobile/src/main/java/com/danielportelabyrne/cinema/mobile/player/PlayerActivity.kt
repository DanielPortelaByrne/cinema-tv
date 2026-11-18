package com.danielportelabyrne.cinema.mobile.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinPlaybackSession
import com.danielportelabyrne.cinema.mobile.MobileApp
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Plays a library film or episode (reporting to Jellyfin as the TV does), an offline copy, or a stream from the
 * stream service. Landscape, full screen, picture-in-picture when the owner leaves, and Cast to a Chromecast or
 * Google TV from the top bar.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private val model get() = (application as MobileApp).model
    private val client get() = model.client
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var view: PlayerView
    private val ui = PlayerUi()
    private val active get() = view.player ?: local
    private var chapterMs: List<Long> = emptyList()
    /** Where the server says this item's intro runs; null when it does not know (or this is not a library item). */
    @Volatile private var intro: com.danielportelabyrne.cinema.data.intro.IntroWindow? = null
    private lateinit var local: ExoPlayer
    private var cast: CastPlayer? = null
    private var session: MediaSession? = null

    private var item: JellyfinItem? = null
    private var offlineId: String? = null
    private var transcoding = false
    private val report = JellyfinPlaybackSession()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        local = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,
                DefaultHttpDataSource.Factory().setDefaultRequestProperties(client.playbackHeaders()).setAllowCrossProtocolRedirects(true))))
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .build()
        local.addListener(listener)
        session = runCatching { MediaSession.Builder(this, local).build() }.getOrNull()

        buildViews()
        view.player = local
        setUpCast()

        val i = intent
        when {
            i.getStringExtra(OFFLINE) != null -> playOffline(i.getStringExtra(OFFLINE)!!)
            i.getStringExtra(URL) != null -> playUrl(i.getStringExtra(URL)!!, i.getStringExtra(TITLE) ?: "Cinema")
            i.getStringExtra(ITEM) != null -> loadItem(i.getStringExtra(ITEM)!!, i.getBooleanExtra(FROM_START, false))
            else -> finish()
        }
        main.postDelayed(progress, PROGRESS_MS)
        main.post(tick)
    }

    // --- Views ---------------------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val actions = object : PlayerActions {
        override fun back() { finish() }
        override fun playPause() { val p = active; if (p.isPlaying) p.pause() else p.play() }
        override fun seekBy(ms: Long) { val p = active; p.seekTo((p.currentPosition + ms).coerceIn(0L, if (p.duration > 0) p.duration else Long.MAX_VALUE)) }
        override fun seekTo(ms: Long) { active.seekTo(ms) }
        override fun next() { playNext() }
        override fun pip() { enterPip() }
        override fun skipIntro() { intro?.let { active.seekTo(it.endMs) } }
    }

    /** The picture, with the Compose controls over it (the wavy scrubber, the ribbon, the tracks panel). */
    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply { useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(ComposeView(this).apply {
            setContent { PlayerOverlay(ui, actions) { ctx -> MediaRouteButton(ctx).also { runCatching { CastButtonFactory.setUpMediaRouteButton(applicationContext, it) } } } }
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    /** Twice a second: the controls read the position, state and chapters from whichever player is in use. */
    private val tick = object : Runnable {
        override fun run() {
            val p = active
            ui.positionMs = p.currentPosition.coerceAtLeast(0L)
            ui.durationMs = p.duration.takeIf { it > 0 } ?: 0L
            ui.playing = p.isPlaying
            ui.buffering = p.playbackState == Player.STATE_BUFFERING
            ui.casting = view.player === cast
            ui.inIntro = intro?.contains(ui.positionMs) == true
            if (ui.durationMs > 0 && chapterMs.isNotEmpty()) ui.chapters = chapterMs.map { it.toFloat() / ui.durationMs }
            main.postDelayed(this, 250)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // --- Sources -------------------------------------------------------------------------------

    private fun loadItem(id: String, fromStart: Boolean) {
        io.execute {
            val it = client.item(id)
            main.post {
                if (it == null) { toast("Couldn't load this from Jellyfin"); finish(); return@post }
                start(it, if (fromStart) 0L else it.resumeMs)
            }
        }
    }

    private fun label(i: JellyfinItem) = if (i.isEpisode) "${i.seriesName ?: ""} · S${i.season ?: 0} E${i.episode ?: 0} ${i.name}" else i.name

    private fun start(i: JellyfinItem, positionMs: Long) {
        if (item != null) report("Stopped")
        item = i; offlineId = null; transcoding = false
        ui.title = if (i.isEpisode) i.name else i.name
        ui.eyebrow = if (i.isEpisode) listOfNotNull(i.seriesName, "S${i.season ?: 0} E${i.episode ?: 0}") else listOfNotNull("Film", i.year?.toString())
        ui.hasNext = i.isEpisode
        chapterMs = i.chapters.map { it.startMs }.filter { it > 0 }
        intro = null
        io.execute { val w = client.introWindow(i.id); if (item?.id == i.id) intro = w }
        playLocal(client.streamUrl(i), label(i), positionMs, null)
        report("Playing")
    }

    private fun playOffline(id: String) {
        val copy = model.offline.find(id) ?: run { finish(); return }
        offlineId = id
        ui.title = copy.name; ui.eyebrow = listOfNotNull("On this phone", copy.subtitle); ui.hasNext = false; chapterMs = emptyList(); intro = null
        playLocal(Uri.fromFile(java.io.File(copy.file)).toString(), copy.name, copy.resumeMs, null)
    }

    private fun playUrl(url: String, title: String) { ui.title = title; ui.eyebrow = listOf("Streaming"); ui.hasNext = false; chapterMs = emptyList(); intro = null; playLocal(url, title, 0L, null) }

    private fun playLocal(url: String, title: String, positionMs: Long, mime: String?) {
        val media = MediaItem.Builder().setUri(url).setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()
        local.setMediaItem(media, positionMs.coerceAtLeast(0L))
        local.prepare()
        local.playWhenReady = true
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val i = item
            // The original file would not play here: ask Jellyfin for a converted copy, once.
            if (i != null && !transcoding) {
                transcoding = true
                toast("Converting on the server…")
                playLocal(client.transcodeUrl(i), label(i), local.currentPosition, MimeTypes.APPLICATION_M3U8)
            } else { toast("This won't play: ${error.errorCodeName}") }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { buildTracks(tracks) }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (item != null) report("Progress")
            updatePipParams()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                if (item?.isEpisode == true) playNext() else { report("Stopped", atEnd = true); finish() }
            }
        }
    }

    private fun playNext() {
        val cur = item ?: return
        io.execute {
            val next = client.followingEpisode(cur)
            main.post {
                if (next == null) { report("Stopped", atEnd = true); toast("That was the last episode"); finish() }
                else { report("Stopped", atEnd = local.duration > 0 && local.currentPosition >= local.duration - 1000); start(next, next.resumeMs) }
            }
        }
    }

    // --- Jellyfin reports ----------------------------------------------------------------------

    private val progress = object : Runnable {
        override fun run() {
            if (item != null && local.isPlaying) report("Progress")
            offlineId?.let { model.offline.saveResume(it, local.currentPosition) }
            main.postDelayed(this, PROGRESS_MS)
        }
    }

    private fun report(kind: String, atEnd: Boolean = false) {
        val i = item ?: return
        if (!report.accept(kind)) return
        val active = view.player ?: local
        val position = if (atEnd && active.duration > 0) active.duration else active.currentPosition
        val body = JellyfinApi.playbackBody(i.id, i.sourceId, position, !active.isPlaying, report.id)
        val url = JellyfinApi.reportUrl(JellyfinApi.cleanBase(client.server), kind)
        val headers = client.playbackHeaders()
        io.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 5000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                try { c.outputStream.use { it.write(body.toByteArray()) }; c.responseCode } finally { c.disconnect() }
            } catch (_: Exception) { }
        }
    }

    // --- Audio and subtitles -------------------------------------------------------------------

    private fun trackName(f: androidx.media3.common.Format, n: Int): String {
        val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { it } }
        val base = f.label ?: lang ?: "Track $n"
        val extra = listOfNotNull(f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { f.sampleMimeType?.startsWith("audio") == true }, f.channelCount.takeIf { it > 0 && f.sampleMimeType?.startsWith("audio") == true }?.let { "${it}ch" })
        return if (extra.isEmpty() || base.contains(extra.first())) base else base + " · " + extra.joinToString(" ")
    }

    private fun buildTracks(tracks: androidx.media3.common.Tracks) {
        val p = local
        fun select(group: androidx.media3.common.Tracks.Group, index: Int, type: Int) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(type, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, index)).build()
            buildTracks(p.currentTracks)
        }
        val audio = ArrayList<TrackOption>(); val text = ArrayList<TrackOption>()
        var an = 0; var tn = 0
        tracks.groups.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                when (g.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(trackName(g.getTrackFormat(i), ++an), g.isTrackSelected(i)) { select(g, i, C.TRACK_TYPE_AUDIO) }
                    C.TRACK_TYPE_TEXT -> text += TrackOption(trackName(g.getTrackFormat(i), ++tn), g.isTrackSelected(i) && !p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) { select(g, i, C.TRACK_TYPE_TEXT) }
                }
            }
        }
        if (text.isNotEmpty()) text.add(0, TrackOption("Off", text.none { it.selected }) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); buildTracks(p.currentTracks)
        })
        ui.audio = audio; ui.text = text
    }

    // --- Cast ----------------------------------------------------------------------------------

    private fun setUpCast() {
        val ctx = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        cast = CastPlayer(ctx).apply {
            setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = toCast()
                override fun onCastSessionUnavailable() = fromCast()
            })
            addListener(listener)
        }
        if (cast?.isCastSessionAvailable == true) main.post { toCast() }
    }

    /**
     * A Chromecast cannot send Jellyfin's sign-in header, so the cast copy carries the token in its address. That
     * address only goes to the Chromecast on the home network. Jellyfin's converted copy (H.264 + AAC) is what every
     * Cast device plays.
     */
    private fun toCast() {
        val c = cast ?: return
        val i = item
        val position = local.currentPosition
        val (url, mime, title) = when {
            i != null -> Triple(client.transcodeUrl(i) + "&api_key=" + token(), MimeTypes.APPLICATION_M3U8, label(i))
            offlineId != null -> { toast("Offline copies play on this phone only"); return }
            else -> Triple(local.currentMediaItem?.localConfiguration?.uri?.toString() ?: return, null, ui.title)
        }
        local.pause()
        c.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mime ?: MimeTypes.VIDEO_MP4)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build(), position)
        c.prepare(); c.playWhenReady = true
        view.player = c
        toast("Playing on the TV")
    }

    private fun fromCast() {
        val c = cast ?: return
        val position = c.currentPosition
        view.player = local
        if (position > 0) local.seekTo(position)
        local.playWhenReady = true
    }

    private fun token(): String = model.platform.preferences("jellyfin").getString("token", null).orEmpty()

    // --- Picture in picture --------------------------------------------------------------------

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < 26) return
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(local.isPlaying)
        runCatching { setPictureInPictureParams(b.build()) }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in 26..30 && local.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        ui.pip = inPip
    }

    override fun onStop() {
        super.onStop()
        // Left for good (not shrunk to picture-in-picture): pause, so sound does not play on with nothing on screen.
        if (Build.VERSION.SDK_INT < 24 || !isInPictureInPictureMode) { if (view.player === local) local.pause() }
        report("Progress")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        offlineId?.let { model.offline.saveResume(it, local.currentPosition) }
        report("Stopped")
        session?.release()
        cast?.setSessionAvailabilityListener(null); cast?.release()
        local.release()
        io.shutdown()
        model.refresh()
        super.onDestroy()
    }

    companion object {
        const val ITEM = "item"
        const val FROM_START = "fromStart"
        const val OFFLINE = "offline"
        const val URL = "url"
        const val TITLE = "title"
        private const val PROGRESS_MS = 10_000L
    }
}
