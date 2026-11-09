package com.danielportelabyrne.cinema.ui.live

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danielportelabyrne.cinema.data.live.*
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.device.AndroidPlatform
import java.util.concurrent.Executors

/** A guide and player in one activity: browsing never stops the current broadcast. Back restores the same guide. */
@UnstableApi
class LiveTvActivity : AppCompatActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val guideWorker = Executors.newSingleThreadExecutor()
    private val images = Executors.newFixedThreadPool(2)
    private val logos = android.util.LruCache<String, android.graphics.Bitmap>(40)
    private lateinit var repository: LiveRepository
    private lateinit var prefs: LivePreferences
    private val guides = LiveGuideRepository()
    private var channels = LiveProviders.seeds()
    private var visible = emptyList<LiveChannel>()
    private var group = "Favourites"
    private var category = "all"
    private var query = ""
    private var selected: LiveChannel? = null
    private var playing: LiveChannel? = null
    private var source: LiveSource? = null
    private var resolver: LiveResolver? = null
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var successRecorded = false
    private var started = false
    private var full = false
    private var bufferSince = 0L
    private var startupSince = 0L
    private var lastFailure = ""
    private var refreshing = false
    private var nextRefreshAt = 0L
    private var nextGuideAt = 0L
    private var scheduleView: TextView? = null
    private var selectionToken = 0
    private lateinit var browser: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var right: LinearLayout
    private lateinit var root: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var playbackTools: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var status: TextView
    private lateinit var playingLabel: TextView
    private lateinit var catalogStatus: TextView
    private lateinit var listHeading: TextView
    private val adapter = ChannelsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        repository = LiveRepository(AndroidPlatform(this)); prefs = repository.preferences
        group = savedInstanceState?.getString("group") ?: if (prefs.favourites.isEmpty()) "Ireland" else "Favourites"
        category = savedInstanceState?.getString("category") ?: "all"
        query = savedInstanceState?.getString("query").orEmpty()
        root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Cinema.BG); setPadding(px(40), px(28), px(40), px(20)) }
        browser = column()
        root.addView(browser, LinearLayout.LayoutParams(0, -1, 1.25f))
        browser.addView(Cinema.text(this, "Live TV", 48, Cinema.CREAM, serif = true))
        browser.addView(Cinema.text(this, "Television already underway", 22, Cinema.MUTED))
        catalogStatus = Cinema.text(this, "Loading saved channels…", 18, Cinema.DIM); browser.addView(catalogStatus)
        val search = EditText(this).apply {
            hint = "Search channels and alternate names"; setTextColor(Cinema.CREAM); setHintTextColor(Cinema.MUTED)
            showSoftInputOnFocus = false
            setOnClickListener { requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            textSize = 16f; isSingleLine = true; setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); renderList() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        browser.addView(search, LinearLayout.LayoutParams(-1, px(70)))
        chips(browser, listOf("Favourites", "Recently watched", "Ireland", "UK", "Brazil", "American Entertainment", "Canada", "Explore the World", "My playlist", "Hidden")) { group = it; renderList() }
        chips(browser, listOf("all", "movies", "comedy", "animation", "entertainment", "news", "documentary", "music", "other")) { category = it; renderList() }
        val tools = LinearLayout(this)
        tools.addView(button("Refresh") { refresh(true) }); tools.addView(button("Playlist") { playlistDialog() }); tools.addView(button("Back to Cinema") { finish() })
        browser.addView(tools)
        listHeading = Cinema.text(this, "", 22, Cinema.SAFFRON)
        browser.addView(listHeading)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@LiveTvActivity); adapter = this@LiveTvActivity.adapter; setHasFixedSize(true) }
        browser.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        browser.addView(Cinema.text(this, "Hold OK for favourites, ordering and hide · CH+/− to tune", 17, Cinema.MUTED))
        right = column().apply { setPadding(px(30), 0, 0, 0) }
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        playingLabel = Cinema.text(this, "Choose a channel", 27, Cinema.CREAM); right.addView(playingLabel)
        playerView = PlayerView(this).apply { useController = true; controllerShowTimeoutMs = 3500; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) }
        right.addView(playerView, LinearLayout.LayoutParams(-1, px(380)))
        status = Cinema.text(this, "Direct streams and official providers · availability varies by location", 20, Cinema.MUTED); right.addView(status)
        playbackTools = LinearLayout(this)
        playbackTools.addView(button("Full screen") { fullscreen(!full) }); playbackTools.addView(button("Stop") { stopPlayback(); status.text = "Playback stopped" })
        right.addView(playbackTools)
        detailScroll = ScrollView(this)
        details = column(); detailScroll.addView(details)
        right.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { if (full) fullscreen(false) else finish() } })
        renderList()
        list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: list.requestFocus() }
        refresh(false) {
            val restoreId = savedInstanceState?.getString("playing")
                ?: if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) intent.getStringExtra("debug_live_channel") else null
            restoreId?.let { id -> channels.firstOrNull { it.id == id }?.let { tune(it) } }
        }
        ui.post(tick)
    }
    private fun px(value: Int) = Cinema.px(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun button(label: String, action: () -> Unit) = CinemaButton(this, CinemaButton.Kind.GLASS, label, small = true).apply {
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(-2, px(58)).apply { marginEnd = px(8); topMargin = px(5) }
    }
    private fun chips(parent: LinearLayout, values: List<String>, onSelect: (String) -> Unit) {
        val row = LinearLayout(this)
        values.forEach { value -> row.addView(button(when (value) { "all" -> "All categories"; "other" -> "Other international"; else -> value.replaceFirstChar { it.uppercase() } }) { onSelect(value) }) }
        parent.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }
    private fun refresh(force: Boolean, done: () -> Unit = {}) {
        if (refreshing) return
        refreshing = true
        nextRefreshAt = android.os.SystemClock.elapsedRealtime() + 24 * 60 * 60_000L
        catalogStatus.text = "Updating television catalogue…"
        worker.execute {
            val result = repository.load(force); val imported = repository.imported()
            ui.post { if (!isDestroyed) {
                refreshing = false
                channels = (result.channels + imported).distinctBy { it.id }
                catalogStatus.text = result.warning ?: "Catalogue updated · stream playback verified only after tuning"
                renderList(); done()
            } }
        }
    }
    private fun renderList() {
        if (!::list.isInitialized) return
        val restoreListFocus = list.hasFocus()
        val country = mapOf("Ireland" to "IE", "UK" to "UK", "Brazil" to "BR", "American Entertainment" to "US", "Canada" to "CA")[group]
        val ids = when (group) { "Favourites" -> prefs.favourites; "Recently watched" -> prefs.recent; "Hidden" -> prefs.hidden; else -> emptyList() }
        val useful = channels.filter { it.provider != null || it.sources.any { s -> s.authorised } }
        visible = useful.filter { c ->
            (if (group == "Hidden") c.id in prefs.hidden else c.id !in prefs.hidden) &&
                (when (group) { "Favourites", "Recently watched", "Hidden" -> c.id in ids; "My playlist" -> c.id.startsWith("playlist:"); else -> country == null || c.country == country }) &&
                (category == "all" || (category == "other" && c.country !in listOf("IE", "UK", "BR", "US", "CA", "USER")) ||
                    c.categories.any { it == category || (category == "entertainment" && it == "general") || (category == "animation" && it == "kids") }) && c.matches(query)
        }.sortedWith(compareBy<LiveChannel> {
            if (ids.isNotEmpty()) ids.indexOf(it.id) else when {
                it.sources.any { source -> source.authorised } -> 0
                it.alternative -> 1
                it.provider?.subscription == false -> 2
                else -> 3
            }
        }.thenBy { it.name })
        adapter.notifyDataSetChanged()
        if (restoreListFocus) list.post {
            val position = visible.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
            list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: list.requestFocus()
        }
        listHeading.text = "$group · ${if (category == "all") "All categories" else category} · ${visible.size} channels"
        if (visible.isEmpty()) {
            details.removeAllViews(); details.addView(Cinema.text(this, "No channels in $group${if (category != "all") " · $category" else ""}.\nChoose a country or Explore the World. Hold OK on a channel to save favourites.", 23, Cinema.MUTED))
        }
    }
    private fun select(channel: LiveChannel) {
        selected = channel
        val token = ++selectionToken
        details.removeAllViews()
        details.addView(Cinema.text(this, channel.name, 32, Cinema.CREAM, serif = true))
        details.addView(Cinema.text(this, "${channel.country} · ${channel.availability(prefs::health).label}${if (channel.alternative) " · Free alternative" else ""}", 20, Cinema.SAFFRON))
        details.addView(Cinema.text(this, channel.provider?.note ?: "Official direct source · UK playback is verified only on this device after tuning", 20, Cinema.MUTED))
        val failure = channel.sources.filter { it.authorised }.map { prefs.health(it.url) }.firstOrNull { it.failedAt > it.lastSuccess }
        failure?.let { details.addView(Cinema.text(this, "Last attempt: ${it.reason}", 19, Cinema.DANGER_TEXT)) }
        val actions = LinearLayout(this)
        if (channel.sources.any { it.authorised }) actions.addView(button(if (channel.availability(prefs::health) == LiveAvailability.PLAY_NOW) "Play now" else "Try stream") { tune(channel) })
        channel.provider?.let { p -> actions.addView(button("Open ${p.name}") { openProvider(p) }) }
        actions.addView(button(if (channel.id in prefs.favourites) "Unfavourite" else "Favourite") { prefs.favourite(channel.id); select(channel); renderList() })
        details.addView(actions)
        val schedule = Cinema.text(this, "Live broadcast · programme information unavailable", 21, Cinema.READING)
        details.addView(schedule)
        scheduleView = schedule
        nextGuideAt = android.os.SystemClock.elapsedRealtime() + 60_000
        ui.postDelayed({ if (token != selectionToken || isDestroyed) return@postDelayed
            requestSchedule(channel, token, schedule)
        }, 350)
    }
    private fun requestSchedule(channel: LiveChannel, token: Int, schedule: TextView) {
        guideWorker.execute {
            if (token != selectionToken || isDestroyed) return@execute
            val programmes = guides.schedule(channel)
            val nn = LiveEpg.nowNext(programmes)
            val text = if (programmes.isEmpty()) "Live broadcast · programme information unavailable" else buildString {
                append("Now: ${nn.now?.title ?: "Live broadcast"}\n")
                nn.next?.let { append("Next ${it.localTime()}: ${it.title}\n\n") }
                append("Guide · ${java.util.TimeZone.getDefault().id}\n")
                programmes.take(12).forEach { append("${it.localTime()}  ${it.title}\n") }
            }
            ui.post { if (!isDestroyed && token == selectionToken) schedule.text = text }
        }
    }
    private fun options(channel: LiveChannel) {
        val labels = arrayOf(if (channel.id in prefs.favourites) "Remove favourite" else "Add favourite", "Move to first favourite", if (channel.id in prefs.hidden) "Unhide channel" else "Hide channel", "Open provider")
        AlertDialog.Builder(this).setTitle(channel.name).setItems(labels) { _, which ->
            when (which) { 0 -> prefs.favourite(channel.id); 1 -> prefs.moveFirst(channel.id); 2 -> if (channel.id in prefs.hidden) prefs.hidden = prefs.hidden - channel.id else prefs.hide(channel.id); 3 -> channel.provider?.let(::openProvider) }
            renderList(); select(channel)
        }.show()
    }
    private fun playlistDialog() {
        val input = EditText(this).apply { hint = "https://provider.example/channels.m3u"; isSingleLine = true; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        AlertDialog.Builder(this).setTitle("Your authorised live playlist").setMessage("Import only externally programmed channels you are entitled to play. Public HTTPS only; credentials, private networks and player options are rejected. No media is downloaded.")
            .setView(input).setPositiveButton("Import") { _, _ ->
                status.text = "Importing playlist…"
                worker.execute {
                    val result = runCatching { repository.importPlaylist(input.text.toString().trim()) }
                    ui.post { if (!isDestroyed) { status.text = result.fold({ "Imported $it channels" }, { "Playlist could not be imported. Use a public HTTPS M3U without credentials." }); refresh(false) } }
                }
            }.setNeutralButton("Remove playlist") { _, _ -> worker.execute { repository.clearPlaylist(); ui.post { refresh(false) } } }.setNegativeButton("Cancel", null).show()
    }
    private fun openProvider(provider: LiveProvider) {
        // Launch home, since these providers do not document stable public channel intents.
        for (pkg in provider.packages) {
            val launch = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null && runCatching { startActivity(launch) }.isSuccess) return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.website))) }.onFailure {
            AlertDialog.Builder(this).setTitle(provider.name).setMessage("Install the official provider app from your TV's app store, or visit ${provider.website}\n\n${provider.note}").setPositiveButton("OK", null).show()
        }
    }
    private fun tune(channel: LiveChannel) {
        if (channel.sources.none { it.authorised }) { channel.provider?.let(::openProvider) ?: run { status.text = "Currently unavailable · no authorised playback route" }; return }
        stopPlayback(); playing = channel; lastFailure = ""; resolver = LiveResolver(channel, prefs::health)
        playingLabel.text = channel.name; nextSource()
    }
    private fun nextSource() {
        session?.release(); session = null; player?.release(); player = null; playerView.player = null
        val next = resolver?.next()
        if (next == null) {
            source = null; status.text = "Unable to play ${playing?.name}. $lastFailure. ${playing?.provider?.let { "Use Open ${it.name} below." } ?: "No other authorised feeds. Try again later or choose another channel."}"
            playing?.let(::select); return
        }
        source = next; successRecorded = false; bufferSince = android.os.SystemClock.elapsedRealtime(); startupSince = bufferSince
        status.text = "Connecting to ${playing?.name}…${if (next.labels.isNotEmpty()) " · ${next.labels.joinToString()}" else ""}"
        val factory = DefaultMediaSourceFactory(LiveHttpDataSource.Factory()).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
        val currentPlayer = ExoPlayer.Builder(this).setMediaSourceFactory(factory).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true); setHandleAudioBecomingNoisy(true)
        }
        // The previous activity can still be stopping during a rapid relaunch/provider return.
        // Media3 requires process-wide unique IDs even while a released session is being torn down.
        session = MediaSession.Builder(this, currentPlayer).setId("cinema-live-${java.util.UUID.randomUUID()}").build()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player = currentPlayer; playerView.player = currentPlayer
        currentPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (player !== currentPlayer) return
                if (!successRecorded) { successRecorded = true; startupSince = 0; prefs.result(next.url, true); playing?.let { prefs.watched(it.id) } }
                status.text = "Live · ${playing?.name} · verified on this device"; android.util.Log.i("CinemaLive", "First video frame: ${playing?.id}")
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (player !== currentPlayer) return
                if (state == Player.STATE_BUFFERING && bufferSince == 0L) bufferSince = android.os.SystemClock.elapsedRealtime()
                if (state == Player.STATE_READY) bufferSince = 0L
                if (state == Player.STATE_ENDED) failed("Broadcast ended")
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (player !== currentPlayer) return
                val now = android.os.SystemClock.elapsedRealtime()
                bufferSince = if (playWhenReady && currentPlayer.playbackState == Player.STATE_BUFFERING) now else 0L
                if (!successRecorded) startupSince = if (playWhenReady) now else 0L
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player !== currentPlayer) return
                val cause = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.firstOrNull { it.contains("HTTP ") || it.contains("Unsafe") }
                failed(cause ?: error.errorCodeName)
            }
        })
        currentPlayer.setMediaItem(MediaItem.fromUri(next.url)); currentPlayer.prepare(); currentPlayer.playWhenReady = true
    }
    private fun failed(reason: String) {
        lastFailure = reason
        source?.let { prefs.result(it.url, false, reason) }
        android.util.Log.w("CinemaLive", "Feed failed: ${playing?.id} · $reason")
        bufferSince = 0L; status.text = "$reason · trying another feed of the same channel"
        nextSource()
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtime()
            if (started && now >= nextRefreshAt) refresh(false)
            if (started && now >= nextGuideAt) {
                nextGuideAt = now + 60_000
                selected?.let { c -> scheduleView?.let { requestSchedule(c, selectionToken, it) } }
            }
            if (started && player?.playWhenReady == true && bufferSince > 0 && now - bufferSince > 20_000) failed("Stream timed out")
            else if (started && player?.playWhenReady == true && startupSince > 0 && now - startupSince > 20_000) failed("No video received before startup timeout")
            ui.postDelayed(this, 1000)
        }
    }
    private fun stopPlayback() { playerView.player = null; session?.release(); session = null; player?.release(); player = null; source = null; resolver = null; playing = null; bufferSince = 0; startupSince = 0; playingLabel.text = "Choose a channel"; window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    private fun fullscreen(value: Boolean) {
        full = value; browser.visibility = if (value) View.GONE else View.VISIBLE
        val guideVisibility = if (value) View.GONE else View.VISIBLE
        detailScroll.visibility = guideVisibility; playingLabel.visibility = guideVisibility
        status.visibility = guideVisibility; playbackTools.visibility = guideVisibility
        root.setPadding(if (value) 0 else px(40), if (value) 0 else px(28), if (value) 0 else px(40), if (value) 0 else px(20))
        right.setPadding(if (value) 0 else px(30), 0, 0, 0)
        playerView.layoutParams = (playerView.layoutParams as LinearLayout.LayoutParams).apply { height = if (value) 0 else px(380); weight = if (value) 1f else 0f }
        if (value) playerView.requestFocus() else {
            val position = visible.indexOfFirst { it.id == selected?.id }
            list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: list.requestFocus()
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && player != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { player?.let { it.playWhenReady = !it.playWhenReady }; return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { stopPlayback(); return true }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            val playable = visible.filter { c -> c.sources.any { it.authorised } }
            if (playable.isNotEmpty()) { val at = playable.indexOfFirst { it.id == playing?.id }; val delta = if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1; val next = playable[(at + delta + playable.size) % playable.size]; tune(next); select(next) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart() { super.onStart(); started = true; if (player == null) playing?.let(::tune) }
    override fun onStop() { started = false; playerView.player = null; session?.release(); session = null; player?.release(); player = null; bufferSince = 0; startupSince = 0; window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("group", group); outState.putString("category", category); outState.putString("query", query); outState.putString("playing", playing?.id); super.onSaveInstanceState(outState) }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); stopPlayback(); worker.shutdownNow(); guideWorker.shutdownNow(); images.shutdownNow(); super.onDestroy() }

    private inner class ChannelHolder(val row: LinearLayout, val logo: ImageView, val name: TextView) : RecyclerView.ViewHolder(row)
    private inner class ChannelsAdapter : RecyclerView.Adapter<ChannelHolder>() {
        private val stableIds = mutableMapOf<String, Long>()
        private var nextId = 0L
        init { setHasStableIds(true) }
        override fun getItemId(position: Int): Long = stableIds.getOrPut(visible[position].id) { ++nextId }
        override fun getItemCount() = visible.size
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ChannelHolder {
            val row = LinearLayout(this@LiveTvActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(px(16), px(10), px(16), px(10)); layoutParams = RecyclerView.LayoutParams(-1, px(104))
                setOnFocusChangeListener { _, focused -> setBackgroundColor(if (focused) Cinema.GLASS_FOCUS else Cinema.BG); if (focused) (tag as? LiveChannel)?.let(::select) }
            }
            val logo = ImageView(this@LiveTvActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            row.addView(logo, LinearLayout.LayoutParams(px(95), px(70)).apply { marginEnd = px(20) })
            val name = Cinema.text(this@LiveTvActivity, "", 23, Cinema.CREAM).apply { maxLines = 2 }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            return ChannelHolder(row, logo, name)
        }
        override fun onBindViewHolder(holder: ChannelHolder, position: Int) {
            val channel = visible[position]
            holder.row.tag = channel
            holder.name.text = "${if (channel.id in prefs.favourites) "★ " else ""}${channel.name}\n${channel.availability(prefs::health).label}${if (channel.alternative) " · Free alternative" else ""}"
            holder.row.contentDescription = holder.name.text
            holder.row.setOnClickListener { select(channel); tune(channel) }
            holder.row.setOnLongClickListener { options(channel); true }
            holder.logo.setImageResource(com.danielportelabyrne.cinema.R.drawable.ic_cin_tv)
            // Imgur currently returns an outage graphic with HTTP 200 for these hotlinked logos.
            channel.logo?.takeUnless { java.net.URI(it).host?.endsWith("imgur.com") == true }?.let { url ->
                val cached = logos.get(url)
                if (cached != null) holder.logo.setImageBitmap(cached) else images.execute {
                    val bitmap = runCatching {
                        // A strict byte limit and bounds check prevent untrusted logo allocations.
                        PublicTvUrl.validate(url)
                        val bytes = PublicTvUrl.readBytesForLogo(url)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 160) })
                    }.getOrNull()
                    if (bitmap != null) { logos.put(url, bitmap); ui.post { if (!isDestroyed && (holder.row.tag as? LiveChannel)?.id == channel.id) holder.logo.setImageBitmap(bitmap) } }
                }
            }
        }
    }
}
