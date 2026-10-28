package com.danielportelabyrne.cinema.ui.music

import android.app.Activity
import android.content.Intent
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.danielportelabyrne.cinema.data.music.SeekKeys
import com.danielportelabyrne.cinema.data.music.MusicTrack
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.WaveScrubberView
import com.danielportelabyrne.cinema.ui.RemoteImages
import java.util.concurrent.Executors

/**
 * Full-screen now playing for a queue of tracks from the music server, laid out like Apple Music on a TV: the cover
 * large with its words and progress centred beneath, over the cover itself blurred into slowly turning colour.
 * Built to sit on an OLED panel for hours: the background never stops moving. Paused, the cover sinks back and
 * the screen may sleep. Leaving stops the music.
 *
 * With lyrics, that column moves to the left and the lyrics fill the right.
 *
 * Remote: OK plays or pauses. Left and Right jump 10 s on a tap, change track on a double tap, and scan while held.
 * Up and Down show or hide lyrics. Rewind and fast forward seek, Back leaves.
 */
@UnstableApi
class MusicPlayerActivity : Activity() {
    private lateinit var client: SubsonicClient
    private lateinit var tracks: List<MusicTrack>
    private lateinit var player: ExoPlayer
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val images by lazy { RemoteImages.shared(this) }

    private lateinit var backdrop: ArtBackdrop
    private lateinit var row: LinearLayout
    private lateinit var art: ImageView
    private lateinit var words: LinearLayout
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var album: TextView
    private lateinit var scrubber: WaveScrubberView
    private lateinit var left: LinearLayout
    private lateinit var lyricsView: LyricsView
    private lateinit var lyricsColumn: LinearLayout
    private lateinit var status: LinearLayout
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private var statusShown = ""
    private var lyricsShown: Boolean? = null
    private lateinit var elapsed: TextView
    private lateinit var remaining: TextView

    private val opened = SystemClock.elapsedRealtime()
    private val seekKeys = SeekKeys()
    /** While Left or Right is held: which way, from where, since when (uptime), and whether to play after. */
    private var scanDirection = 0
    private var scanFrom = 0L
    private var scanSince = 0L
    private var scanResume = false
    private var shownIndex = -1
    private var scrobbled = -1
    private var failures = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val queue = Music.queued
        val c = Music.client
        if (queue == null || c == null || queue.tracks.isEmpty()) { finish(); return }
        client = c; tracks = queue.tracks
        buildViews()

        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val base = client.config.baseUrl
        val signing = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)) { spec ->
            val uri = spec.uri.toString()
            if (uri.startsWith(base)) spec.withUri(Uri.parse(client.signed(uri.removePrefix(base)))) else spec
        }
        player = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(signing)).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        player.setMediaItems(tracks.map { item(it) }, queue.start.coerceIn(0, tracks.lastIndex), 0L)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { showTrack() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { keepAwake(); settleArt() }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) failures = 0; keepAwake() }
            override fun onPlayerError(error: PlaybackException) { skipBroken(error) }
        })
        player.prepare(); player.play()
        showTrack()
        Choreographer.getInstance().postFrameCallback(tick)
    }

    private fun item(t: MusicTrack): MediaItem = MediaItem.Builder()
        .setUri(client.config.baseUrl + client.streamPath(t.id)).setMediaId(t.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
        .build()

    // --- Layout ---------------------------------------------------------------------------------------

    private fun px(mock: Int) = Cinema.px(this, mock)

    private fun white(alpha: Float) = Cinema.alpha(0xFFFFFFFF.toInt(), alpha)

    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Cinema.BG); clipChildren = false }
        backdrop = ArtBackdrop(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(white(0.08f))
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 22)) } }
            clipToOutline = true
            elevation = Cinema.pxf(context, 60)
            if (Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = 0xFF000000.toInt(); outlineAmbientShadowColor = 0xFF000000.toInt() }
        }
        left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        lyricsView = LyricsView(this)
        lyricsColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusIcon = Cinema.icon(this, R.drawable.ic_cin_play, white(0.6f))
        statusText = Cinema.text(this, "", 19, white(0.6f), weight = 700, tracking = 0.18f, caps = true)
        status.addView(statusIcon, LinearLayout.LayoutParams(px(22), px(22)).apply { marginEnd = px(12) })
        status.addView(statusText)

        words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        title = Cinema.text(this, "", 54, white(0.96f), weight = 700).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_HORIZONTAL }
        artist = Cinema.text(this, "", 34, white(0.72f), weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_HORIZONTAL }
        album = Cinema.text(this, "", 28, white(0.48f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_HORIZONTAL }
        scrubber = WaveScrubberView(this, amplitudePx = 17f, wavelengthPx = 92f, thicknessPx = 22f)
        elapsed = Cinema.text(this, "", 25, white(0.55f), weight = 500)
        remaining = Cinema.text(this, "", 25, white(0.55f), weight = 500).apply { gravity = Gravity.END }
        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
            addView(remaining, LinearLayout.LayoutParams(0, -2, 1f))
        }
        words.addView(title)
        words.addView(artist, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        words.addView(album, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
        // The wave keeps room for its playhead's glow; pull it out so the wave's ends line up with the text.
        words.addView(scrubber, LinearLayout.LayoutParams(-1, px(86)).apply { topMargin = px(20); marginStart = -px(46); marginEnd = -px(46) })
        words.addView(times, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(2) })
        root.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        arrange()
    }

    /** The cover with the words centred under it: in the middle of the screen, or on the left beside the lyrics. */
    private fun arrange() {
        val withLyrics = Music.lyricsOn && lyricsView.hasLyrics
        if (withLyrics == lyricsShown) return
        lyricsShown = withLyrics
        row.removeAllViews()
        if (left.childCount == 0) {
            left.addView(art, LinearLayout.LayoutParams(px(COVER), px(COVER)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            left.addView(words, LinearLayout.LayoutParams(px(COVER), -2).apply { topMargin = px(28) })
        }
        row.addView(left, LinearLayout.LayoutParams(-2, -2))
        // Playing or paused sits over the lyrics when they show, else centred over the title.
        (status.parent as? LinearLayout)?.removeView(status)
        lyricsColumn.removeAllViews()
        if (withLyrics) {
            lyricsColumn.addView(status, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = px(18) })
            lyricsColumn.addView(lyricsView, LinearLayout.LayoutParams(-1, px(LYRICS_HEIGHT - 50)))
            row.addView(lyricsColumn, LinearLayout.LayoutParams(px(LYRICS_WIDTH), -2).apply { marginStart = px(150) })
        } else {
            words.addView(status, 0, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = px(14) })
        }
        row.alpha = 0f
        row.animate().alpha(1f).setDuration(400).start()
    }

    private fun showTrack() {
        val i = player.currentMediaItemIndex
        val t = tracks.getOrNull(i) ?: return
        if (i != shownIndex) {
            shownIndex = i
            Music.cover(t.coverArt, 1000)?.let { key ->
                images.load(key, maxWidth = px(COVER)) { b ->
                    if (shownIndex != i) return@load
                    art.setImageBitmap(b)
                    worker.execute {
                        val layers = runCatching { backdrop.prepare(b) }.getOrNull() ?: return@execute
                        ui.post { if (shownIndex == i && !isDestroyed) backdrop.show(layers) }
                    }
                }
            }
            worker.execute { client.scrobble(t.id, submission = false) }
            lyricsView.set(null)
            worker.execute {
                val l = client.lyrics(t)
                ui.post { if (shownIndex == i && !isDestroyed) { lyricsView.set(l); arrange() } }
            }
        }
        title.text = t.title
        artist.text = t.artist.orEmpty()
        album.text = t.album.orEmpty()
    }

    /** Playing, the cover sits full size; paused, it sinks back a little. */
    private fun settleArt() {
        val s = if (player.isPlaying || scanDirection != 0 || player.playbackState == Player.STATE_BUFFERING) 1f else PAUSED_SCALE
        art.animate().scaleX(s).scaleY(s).setDuration(450).setInterpolator(EASE).start()
    }

    // --- The loop -------------------------------------------------------------------------------------

    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameNs: Long) {
            if (isDestroyed) return
            val now = SystemClock.elapsedRealtime()
            val clock = now - opened
            backdrop.advance(clock)
            act(seekKeys.due(SystemClock.uptimeMillis()))
            showStatus()
            updateProgress()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updateProgress() {
        val pos = if (scanDirection != 0) scanTarget() else player.currentPosition.coerceAtLeast(0)
        val len = length()
        scrubber.progress = if (len > 0) pos.toFloat() / len else 0f
        lyricsView.position(pos, scrubber.progress)
        elapsed.text = clock(pos)
        remaining.text = if (len > 0) "-" + clock((len - pos).coerceAtLeast(0)) else ""
        // Counts as played at half the track or four minutes, the usual scrobble rule.
        if (scanDirection == 0 && len > 0 && scrobbled != shownIndex && (pos > len / 2 || pos > 4 * 60_000)) {
            scrobbled = shownIndex
            tracks.getOrNull(shownIndex)?.let { t -> worker.execute { client.scrobble(t.id, submission = true) } }
        }
    }

    private fun showStatus() {
        val (label, playing) = when {
            scanDirection > 0 -> "Scanning forward" to true
            scanDirection < 0 -> "Scanning back" to true
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> "Loading" to true
            player.isPlaying -> "Playing" to true
            else -> "Paused" to false
        }
        if (label == statusShown) return
        statusShown = label
        statusText.text = label
        statusIcon.setImageResource(if (playing) R.drawable.ic_cin_play else R.drawable.ic_cin_pause)
    }

    private fun length(): Long =
        player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (tracks.getOrNull(shownIndex)?.durationSec?.times(1000L) ?: 0L)

    /** Where a scan has reached, short of the track's end so letting go never lands on the next track. */
    private fun scanTarget(): Long {
        val last = (length() - 1_000).coerceAtLeast(0)
        return (scanFrom + scanDirection * SeekKeys.scanOffset(SystemClock.uptimeMillis() - scanSince)).coerceIn(0, last)
    }

    private fun act(action: SeekKeys.Action?) {
        when (action) {
            is SeekKeys.Action.Jump -> player.seekTo((player.currentPosition + action.direction * SeekKeys.JUMP_MS).coerceIn(0, (length() - 1_000).coerceAtLeast(0)))
            is SeekKeys.Action.Skip -> if (action.direction > 0) { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
                else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
            is SeekKeys.Action.ScanStart -> {
                scanDirection = action.direction; scanFrom = player.currentPosition; scanSince = SystemClock.uptimeMillis()
                scanResume = player.playWhenReady
                player.pause()
            }
            SeekKeys.Action.ScanEnd -> {
                val to = scanTarget()
                scanDirection = 0
                player.seekTo(to)
                if (scanResume) player.play()
            }
            null -> Unit
        }
    }

    private fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    private fun keepAwake() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** A track that will not play is skipped; three in a row stops trying. */
    private fun skipBroken(error: PlaybackException) {
        android.util.Log.w(TAG, "track failed: ${error.errorCodeName}")
        failures++
        if (failures < 3 && player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
        else { album.text = "Couldn't play this track."; keepAwake() }
    }

    // --- Remote ---------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE ->
                if (event.repeatCount == 0) { if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0, 0L); player.play() } else if (player.isPlaying) player.pause() else player.play() }
            KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
            KeyEvent.KEYCODE_DPAD_RIGHT -> act(seekKeys.down(1, event.repeatCount, event.eventTime))
            KeyEvent.KEYCODE_DPAD_LEFT -> act(seekKeys.down(-1, event.repeatCount, event.eventTime))
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (event.repeatCount == 0 && player.hasNextMediaItem()) player.seekToNextMediaItem()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (event.repeatCount == 0) player.seekToPrevious()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player.seekTo(player.currentPosition + SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (event.repeatCount == 0 && lyricsView.hasLyrics) { Music.lyricsOn = !Music.lyricsOn; arrange() }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { act(seekKeys.up(event.eventTime)); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(tick)
        if (::player.isInitialized) player.release()
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CinemaMusic"
        private const val COVER = 560
        private const val LYRICS_WIDTH = 900
        private const val LYRICS_HEIGHT = 900
        private const val PAUSED_SCALE = 0.86f
        private const val SEEK_MS = 10_000L
        private val EASE = PathInterpolator(0.3f, 0f, 0.2f, 1f)

        fun launch(activity: Activity, tracks: List<MusicTrack>, start: Int) {
            if (tracks.isEmpty() || Music.client == null) return
            Music.queued = Music.Queue(tracks, start)
            activity.startActivity(Intent(activity, MusicPlayerActivity::class.java))
        }
    }
}
