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
    private lateinit var bar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var nextButton: ImageButton
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
    }

    // --- Views ---------------------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply {
            setShowSubtitleButton(true)
            setShowNextButton(false); setShowPreviousButton(false)
            controllerShowTimeoutMs = 3500
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> bar.visibility = v })
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0x66000000)
        }
        fun icon(res: Int, desc: String, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res); contentDescription = desc; setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE); setOnClickListener { onClick() }
        }
        bar.addView(icon(androidx.media3.ui.R.drawable.exo_ic_chevron_left, "Back") { finish() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        titleView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 17f; maxLines = 1; setPadding(dp(8), 0, dp(8), 0) }
        bar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        nextButton = icon(androidx.media3.ui.R.drawable.exo_ic_skip_next, "Next episode") { playNext() }.apply { visibility = View.GONE }
        bar.addView(nextButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        if (Build.VERSION.SDK_INT >= 26) bar.addView(icon(androidx.media3.ui.R.drawable.exo_ic_fullscreen_exit, "Picture in picture") { enterPip() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        runCatching {
            val route = MediaRouteButton(this)
            CastButtonFactory.setUpMediaRouteButton(applicationContext, route)
            bar.addView(route, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)

        // Double-tap the left or right of the screen to go back or forward ten seconds; a single tap shows the controls.
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (view.isControllerFullyVisible) view.hideController() else view.showController(); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = view.player ?: return false
                if (e.x < view.width / 2f) p.seekBack() else p.seekForward()
                toast(if (e.x < view.width / 2f) "−10 s" else "+10 s")
                return true
            }
        })
        view.setOnTouchListener { _, e -> gestures.onTouchEvent(e) }
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
        titleView.text = label(i)
        nextButton.visibility = if (i.isEpisode) View.VISIBLE else View.GONE
        playLocal(client.streamUrl(i), label(i), positionMs, null)
        report("Playing")
    }

    private fun playOffline(id: String) {
        val copy = model.offline.find(id) ?: run { finish(); return }
        offlineId = id
        titleView.text = copy.name + (copy.subtitle?.let { " · $it" } ?: "")
        playLocal(Uri.fromFile(java.io.File(copy.file)).toString(), copy.name, copy.resumeMs, null)
    }

    private fun playUrl(url: String, title: String) { titleView.text = title; playLocal(url, title, 0L, null) }

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
            else -> Triple(local.currentMediaItem?.localConfiguration?.uri?.toString() ?: return, null, titleView.text.toString())
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
        view.useController = !inPip
        bar.visibility = if (inPip) View.GONE else bar.visibility
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
