package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.letterboxd.LbCredits
import com.danielportelabyrne.cinema.data.letterboxd.LbFilmInfo
import com.danielportelabyrne.cinema.data.letterboxd.LbReview
import com.danielportelabyrne.cinema.data.player.Chapter
import com.danielportelabyrne.cinema.data.player.PlayerMath
import com.danielportelabyrne.cinema.data.player.PlayerUi
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.cinema.AmbientBackground
import com.danielportelabyrne.cinema.design.cinema.BitrateSparkline
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.WaveScrubberView

/** What the chrome shows about the film; only what is known. */
data class PlayerMeta(
    val title: String, val year: Int? = null, val genres: List<String> = emptyList(), val runtimeMin: Int? = null,
    val certificate: String? = null, val rating: Double? = null, val overview: String? = null
)

/** One figure on the paused screen's Stream card: label, value and an optional second line. */
data class StreamSpec(val label: String, val value: String, val sub: String? = null)

/**
 * The Cinema player chrome over the picture (`31`/`32`/`33`): controls with the wavy scrubber, a frame preview while
 * scrubbing, and the paused information screen with the Stream card and chapters. State comes from [PlayerUi]; the
 * activity provides the player position and the data through the hooks below and performs the actions.
 */
class PlayerChrome(context: Context) : FrameLayout(context) {
    // --- Hooks the activity sets ---
    var positionMs: () -> Long = { 0L }
    var durationMs: () -> Long = { 0L }
    var isPlaying: () -> Boolean = { false }
    var onPlay: () -> Unit = {}
    var onPause: () -> Unit = {}
    var onSeek: (Long) -> Unit = {}
    var onCaptions: () -> Unit = {}
    var onTracks: (Int) -> Unit = {}          // a C.TRACK_TYPE_* to choose from
    var onPrevious: () -> Unit = {}
    var onNext: (() -> Unit)? = null           // null when there is nothing next
    var backdrop: Bitmap? = null
    var chapterImage: (Chapter, (Bitmap) -> Unit) -> Unit = { _, _ -> }
    var previewFrame: ((Long, (Bitmap) -> Unit) -> Unit)? = null   // trickplay, when the film has it
    var resumeMs: Long? = null
    var streamSpecs: () -> List<StreamSpec> = { emptyList() }
    var streamHealth: () -> Pair<String, Boolean>? = { null }      // ("Healthy", true) / ("Buffering", false)
    var bitrateSamples: () -> List<Long> = { emptyList() }
    var chipLabels: () -> Triple<String?, String?, String?> = { Triple(null, null, null) }   // subtitles, audio, quality

    var state: PlayerUi = PlayerUi.Hidden
        private set

    private val ui = Handler(Looper.getMainLooper())
    private var meta = PlayerMeta("")
    private var chapters: List<Chapter> = emptyList()
    private var lastKeyAt = 0L
    private var downConsumed = false

    private fun p(n: Number) = Cinema.px(context, n)

    // --- Controls & scrubbing (31/32) ---
    // Glows (the focused button's bloom, the playhead's) are drawn past their views' edges, so the layers holding them
    // must not clip their children: otherwise a glow is cut into a box at the edge of its column or of the scrubber.
    private val controls = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val eyebrowHolder = FrameLayout(context)
    private val title = Cinema.title(context, "", 60, maxLines = 1)
    private val metaLine = Cinema.text(context, "", 21, Cinema.MUTED).apply { maxLines = 1 }
    private val clock = Cinema.text(context, "", 22, Cinema.MUTED, weight = 500)
    private val transport = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
    private val pauseButton = PauseDisc(context)
    private lateinit var nextButton: View
    private val scrubber = WaveScrubberView(context).apply { isFocusable = true; contentDescription = "Scrubber" }
    private val chapterLabel = Cinema.text(context, "", 17, Cinema.SAFFRON, weight = 600, tracking = 0.14f, caps = true).apply { gravity = Gravity.CENTER }
    private val timeNow = Cinema.text(context, "", 23, Cinema.CREAM, weight = 500)
    private val timeLeft = Cinema.text(context, "", 23, Cinema.MUTED)
    private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
    private val subtitlesChip = chip(R.drawable.ic_cin_cc) { onCaptions(); refreshChips() }
    private val audioChip = chip(R.drawable.ic_cin_vol) { onTracks(androidx.media3.common.C.TRACK_TYPE_AUDIO) }
    private val qualityChip = chip(R.drawable.ic_cin_film) { onTracks(androidx.media3.common.C.TRACK_TYPE_VIDEO) }
    private val slidersButton = roundButton(R.drawable.ic_cin_sliders, 54, 28) { onTracks(androidx.media3.common.C.TRACK_TYPE_TEXT) }
    // Scrubbing preview.
    private val preview = FrameLayout(context).apply { clipChildren = false }
    private val previewCard = PreviewCard(context)
    private val ghostLeft = ghost()
    private val ghostRight = ghost()
    private val previewChapter = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val previewChapterOf = Cinema.text(context, "", 15, Cinema.SAFFRON, weight = 600, tracking = 0.2f, caps = true)
    private val previewChapterName = Cinema.text(context, "", 30, Cinema.CREAM, serif = true)
    private val resumeLabel = Cinema.text(context, "", 15, Cinema.MUTED, tracking = 0.12f, caps = true)
    private val scrubFooter = FrameLayout(context)
    private val scrubStart = Cinema.text(context, "0:00", 18, Cinema.DIM)
    private val scrubEnd = Cinema.text(context, "", 18, Cinema.DIM)

    // --- Paused (33) ---
    private val paused = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val pausedScrim = View(context)

    private val pausedTitle = Cinema.title(context, "", 104)
    private val pausedMeta = FrameLayout(context)
    private val pausedSynopsis = Cinema.synopsis(context, "", 24, maxLines = 3)
    private val resumeButton = CinemaButton(context, CinemaButton.Kind.PRIMARY, "Resume", R.drawable.ic_cin_play)
    private val startOverButton = CinemaButton(context, CinemaButton.Kind.GLASS, "Start over", R.drawable.ic_cin_back)
    private val endsAt = Cinema.text(context, "", 20, Cinema.MUTED)
    private val streamCard = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val streamGrid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val healthDot = Cinema.dot(context, 0xFF5FD08A.toInt(), 10)
    private val healthText = Cinema.text(context, "", 18, 0xFF8FD6A8.toInt(), weight = 500)
    private val bitrateHead = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val bitratePeak = Cinema.text(context, "", 14, Cinema.SAFFRON, weight = 600, tracking = 0.16f)
    private val sparkline = BitrateSparkline(context)
    // The Letterboxd card, in the Stream card's place when Letterboxd knows the film.
    private val lbCard = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = GONE }
    private var lbShown = false
    private val lbHead = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val lbTabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val lbTabLabels = ArrayList<TextView>()
    private val lbTabNames = listOf("Overview", "Cast", "Crew", "Details", "Reviews")
    private var lbTab = 0
    private val lbBody = android.widget.ScrollView(context).apply { isScrollbarFadingEnabled = true }
    // A soft fade where the card's content runs off its top or bottom edge; shown only while there is more that way.
    private val lbBodyBox = FrameLayout(context)
    private val lbFadeTop = View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF120D0A.toInt(), 0x00120D0A)); alpha = 0f }
    private val lbFadeBottom = View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00120D0A, 0xFF120D0A.toInt())); alpha = 0f }
    private val lbPanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, p(8), p(24)) }
    private var lbInfo: LbFilmInfo? = null
    private var lbCredits = LbCredits(emptyList(), emptyList())
    private var lbCircle: List<Pair<String, String?>> = emptyList()
    private var lbReviews: List<LbReview>? = null
    private val lbUrls = HashMap<String, String>()
    private val lbBitmaps = HashMap<String, Bitmap>()
    private val lbAvatars = HashMap<String, MutableList<com.danielportelabyrne.cinema.design.cinema.AvatarView>>()
    /** Called when the paused screen appears: the activity reads what Letterboxd knows of the film then. */
    var onPaused: (() -> Unit)? = null
    /** Loads a picture at a width in pixels (the activity's image loader). */
    var loadPicture: (String, Int, (Bitmap) -> Unit) -> Unit = { _, _, _ -> }
    private val chaptersHeading = Cinema.rowHeading(context, "Chapters")
    private val chapterRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
    private val chapterScroller = CalmHorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(chapterRow) }
    private val pausedScrubber = WaveScrubberView(context, amplitudePx = 11f, thicknessPx = 26f)
    private val pausedNow = Cinema.text(context, "", 21, Cinema.CREAM, weight = 500)
    private val pausedLeft = Cinema.text(context, "", 21, Cinema.MUTED)

    init {
        clipChildren = false
        isFocusable = false
        buildControls()
        buildPaused()
        controls.visibility = GONE; paused.visibility = GONE
        addView(controls, LayoutParams(-1, -1))
        addView(paused, LayoutParams(-1, -1))
    }

    private fun place(parent: FrameLayout, v: View, x: Number, y: Number, w: Int = -2, h: Int = -2) =
        parent.addView(v, LayoutParams(w, h).apply { leftMargin = p(x); topMargin = p(y) })

    private fun buildControls() {
        // The picture dimmed top and bottom (`still(.12)`).
        controls.addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xD9080605.toInt(), 0x1F080605, 0x1F080605, 0xF2080605.toInt())) }, LayoutParams(-1, -1))
        val top = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        top.addView(eyebrowHolder)
        top.addView(title, LinearLayout.LayoutParams(p(1100), -2).apply { topMargin = p(14) })
        top.addView(metaLine, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(12) })
        place(controls, top, 96, 64)
        controls.addView(clock, LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { rightMargin = p(96); topMargin = p(80) })

        val gap = p(100)
        transport.addView(roundButton(R.drawable.ic_cin_prev, 72, 44) { onPrevious(); nudge() })
        transport.addView(seekButton(R.drawable.ic_cin_b10, -10_000), LinearLayout.LayoutParams(p(96), p(96)).apply { marginStart = gap - p(28) })
        // The disc is 112 px, but its five rings and focus glow reach well past it, so its view is sized to hold them
        // (otherwise the controls layer clips the rings); the extra is taken back out of the gaps either side.
        val ringRoom = p((PauseDisc.BOX - 112) / 2)
        transport.addView(pauseButton.apply { setOnClickListener { if (isPlaying()) { onPause(); setState(PlayerUi.Paused) } else { onPlay(); nudge() } } },
            LinearLayout.LayoutParams(p(PauseDisc.BOX), p(PauseDisc.BOX)).apply { marginStart = gap - p(16) - ringRoom })
        transport.addView(seekButton(R.drawable.ic_cin_f10, 10_000), LinearLayout.LayoutParams(p(96), p(96)).apply { marginStart = gap - p(16) - ringRoom })
        nextButton = roundButton(R.drawable.ic_cin_next, 72, 44) { onNext?.invoke(); nudge() }
        transport.addView(nextButton, LinearLayout.LayoutParams(p(72), p(72)).apply { marginStart = gap - p(28) })
        // Centred on y = 696 as in the mockup; the row is as tall as the pause disc's ringed box.
        controls.addView(transport, LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL).apply { topMargin = p(696 - PauseDisc.BOX / 2) })

        place(controls, scrubber, 50, 854, p(1820), p(92))
        controls.addView(chapterLabel, LayoutParams(-1, -2).apply { topMargin = p(952) })
        place(controls, timeNow, 96, 946)
        controls.addView(timeLeft, LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { rightMargin = p(96); topMargin = p(946) })
        listOf(subtitlesChip, audioChip, qualityChip).forEachIndexed { i, c -> chips.addView(c, LinearLayout.LayoutParams(-2, p(54)).apply { if (i > 0) marginStart = p(16) }) }
        controls.addView(chips, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = p(96); bottomMargin = p(40) })
        controls.addView(slidersButton, LayoutParams(p(54), p(54), Gravity.BOTTOM or Gravity.END).apply { rightMargin = p(96); bottomMargin = p(40) })

        // Scrubbing preview (shown only while scrubbing).
        preview.addView(ghostLeft, LayoutParams(p(266), p(150)))
        preview.addView(ghostRight, LayoutParams(p(266), p(150)))
        preview.addView(previewCard, LayoutParams(p(420), p(236)))
        previewChapter.addView(previewChapterOf)
        previewChapter.addView(previewChapterName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(6) })
        preview.addView(previewChapter, LayoutParams(p(500), -2))
        preview.addView(resumeLabel, LayoutParams(-2, -2))
        scrubFooter.addView(scrubStart, LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        scrubFooter.addView(Cinema.keyHints(context, listOf("◀ ▶" to "Scrub", "OK" to "Play from here", "Back" to "Cancel")), LayoutParams(-2, -2, Gravity.CENTER))
        scrubFooter.addView(scrubEnd, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        preview.addView(scrubFooter, LayoutParams(-1, p(40)).apply { leftMargin = p(96); rightMargin = p(96); topMargin = p(996) })
        controls.addView(preview, LayoutParams(-1, -1))
    }

    private fun buildPaused() {
        // The paused picture itself shows through, sharp, with only a light dim: deeper behind the words on the left
        // and the chapters at the foot, barely there on the right.
        paused.addView(pausedScrim.apply { background = com.danielportelabyrne.cinema.design.cinema.Scrim(
            listOf(0f to 0.80f, 0.30f to 0.62f, 0.58f to 0.22f, 1f to 0.10f), listOf(0f to 0.30f, 0.18f to 0f, 0.48f to 0f, 0.76f to 0.72f, 1f to 0.90f)) }, LayoutParams(-1, -1))

        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val eyebrow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        eyebrow.addView(FrameLayout(context).apply {
            background = com.danielportelabyrne.cinema.design.cinema.StripeDrawable(horizontalBands = true, radius = Cinema.pxf(context, 15))
            addView(Cinema.icon(context, R.drawable.ic_cin_pause, 0xFF1B0D06.toInt()), LayoutParams(p(16), p(16), Gravity.CENTER))
        }, LinearLayout.LayoutParams(p(30), p(30)).apply { marginEnd = p(14) })
        eyebrow.addView(Cinema.text(context, "Paused", 17, Cinema.SAFFRON, weight = 600, tracking = 0.24f, caps = true))
        left.addView(eyebrow)
        left.addView(pausedTitle, LinearLayout.LayoutParams(p(900), -2).apply { topMargin = p(22) })
        left.addView(pausedMeta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(22) })
        left.addView(pausedSynopsis, LinearLayout.LayoutParams(p(800), -2).apply { topMargin = p(20) })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        resumeButton.setOnClickListener { onPlay(); setState(PlayerUi.Hidden) }
        startOverButton.setOnClickListener { onSeek(0); onPlay(); setState(PlayerUi.Hidden) }
        buttons.addView(resumeButton, LinearLayout.LayoutParams(-2, p(76)))
        buttons.addView(startOverButton, LinearLayout.LayoutParams(-2, p(76)).apply { marginStart = p(18) })
        buttons.addView(endsAt, LinearLayout.LayoutParams(-2, -2).apply { marginStart = p(32) })
        left.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(28) })
        place(paused, left, 96, 70)

        // The Stream card: frosted dark panel, no edge line.
        streamCard.background = Cinema.glass(context, 30f, 0x9E140E0B.toInt())
        streamCard.setPadding(p(36), p(30), p(36), p(28))
        val head = FrameLayout(context)
        head.addView(Cinema.eyebrow(context, listOf("Stream"), 15))
        val health = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        health.addView(healthDot, LinearLayout.LayoutParams(p(10), p(10)).apply { marginEnd = p(10) })
        health.addView(healthText)
        head.addView(health, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        streamCard.addView(head)
        streamCard.addView(streamGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(26) })
        bitrateHead.addView(Cinema.text(context, "Bitrate · last 60 s", 14, Cinema.DIM, weight = 600, tracking = 0.16f, caps = true), LinearLayout.LayoutParams(0, -2, 1f))
        bitrateHead.addView(bitratePeak)
        streamCard.addView(bitrateHead, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(40) })
        streamCard.addView(sparkline, LinearLayout.LayoutParams(-1, p(62)).apply { topMargin = p(12) })
        place(paused, streamCard, 1130, 70, p(694))
        lbCard.background = Cinema.glass(context, 34f, 0xB8120D0A.toInt())
        lbCard.setPadding(p(38), p(34), p(38), p(30))
        lbCard.addView(lbHead)
        lbCard.addView(lbTabRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(22) })
        lbTabNames.forEachIndexed { i, name ->
            lbTabLabels += Cinema.text(context, name, 19, Cinema.MUTED, weight = 600).apply {
                gravity = Gravity.CENTER; isFocusable = true; isClickable = true; maxLines = 1
                setPadding(p(6), p(10), p(6), p(10))
                setOnClickListener { selectLbTab(i) }
                setOnFocusChangeListener { v, focused ->
                    v.background = if (focused) Cinema.glass(context, 18f, Cinema.CREAM) else null
                    (v as TextView).setTextColor(if (focused) Cinema.INK else if (i == lbTab) Cinema.SAFFRON else Cinema.MUTED)
                }
            }
            lbTabRow.addView(lbTabLabels[i], LinearLayout.LayoutParams(0, -2, 1f))
        }
        lbCard.addView(lbBodyBox, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = p(16) })
        lbBodyBox.addView(lbBody, FrameLayout.LayoutParams(-1, -1))
        lbBodyBox.addView(lbFadeTop, FrameLayout.LayoutParams(-1, p(48), Gravity.TOP))
        lbBodyBox.addView(lbFadeBottom, FrameLayout.LayoutParams(-1, p(56), Gravity.BOTTOM))
        lbBody.addView(lbPanel, FrameLayout.LayoutParams(-1, -2))
        lbBody.setOnScrollChangeListener { _, _, _, _, _ -> updateLbFades() }
        lbPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateLbFades() }
        place(paused, lbCard, 1130, 70, p(694), p(756))

        // The chapters sit above the progress bar, which is at the same height as in the controls, so the bar does not move on pause.
        place(paused, chaptersHeading, 96, 580)
        place(paused, chapterScroller, 96, 626, p(1000), p(214))
        place(paused, pausedScrubber, 50, 854, p(1820), p(92))
        place(paused, pausedNow, 96, 946)
        paused.addView(pausedLeft, LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { rightMargin = p(96); topMargin = p(946) })
    }

    // --- Content ---

    fun setMeta(m: PlayerMeta) {
        meta = m
        title.text = m.title
        pausedTitle.text = m.title
        Cinema.style(pausedTitle, when { m.title.length > 30 -> 68; m.title.length > 16 -> 84; else -> 104 }, Cinema.CREAM, serif = true, tracking = -0.02f)
        val runtime = m.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" }
        metaLine.text = listOfNotNull(m.year?.toString(), m.genres.take(2).joinToString(", ").ifBlank { null }, runtime).joinToString(" · ")
        pausedMeta.removeAllViews()
        pausedMeta.addView(Cinema.meta(context, m.certificate?.substringAfter("GB-"), listOfNotNull(m.year?.toString(), runtime, m.genres.take(3).joinToString(" · ").ifBlank { null }),
            m.rating?.let { String.format(java.util.Locale.UK, "%.1f", it) }))
        pausedSynopsis.text = m.overview.orEmpty(); pausedSynopsis.visibility = if (m.overview.isNullOrBlank()) GONE else VISIBLE
    }

    fun setChapters(list: List<Chapter>) {
        chapters = list
        val d = durationMs().takeIf { it > 0 }
        val marks = if (d == null) emptyList() else list.map { it.startMs.toFloat() / d }
        scrubber.chapters = marks; pausedScrubber.chapters = marks
        chapterRow.removeAllViews()
        chaptersHeading.visibility = if (list.isEmpty()) GONE else VISIBLE
        chapterScroller.visibility = chaptersHeading.visibility
        chaptersHeading.findViewWithTag<TextView>(Cinema.COUNT_TAG)?.let { it.text = "${list.size}"; it.visibility = if (list.isEmpty()) GONE else VISIBLE }
        list.forEachIndexed { i, c -> chapterRow.addView(ChapterCard(context, c).apply {
            setOnClickListener { onSeek(c.startMs); onPlay(); setState(PlayerUi.Hidden) }
            chapterImage(c) { b -> image.setImageBitmap(b) }
        }, LinearLayout.LayoutParams(p(268), -2).apply { if (i > 0) marginStart = p(24) }) }
    }

    /** Next exists or not (a next chapter or episode). */
    fun refreshNext() { nextButton.alpha = if (onNext != null) 0.9f else 0.3f; nextButton.isEnabled = onNext != null }

    // --- State ---

    fun setState(next: PlayerUi) {
        val before = state
        state = next
        val showControls = next == PlayerUi.Controls || next is PlayerUi.Scrubbing
        controls.visibility = if (showControls) VISIBLE else GONE
        paused.visibility = if (next == PlayerUi.Paused) VISIBLE else GONE
        val scrubbing = next is PlayerUi.Scrubbing
        transport.visibility = if (scrubbing) INVISIBLE else VISIBLE
        chips.visibility = if (scrubbing) INVISIBLE else VISIBLE
        slidersButton.visibility = chips.visibility
        chapterLabel.visibility = if (scrubbing || chapters.isEmpty()) INVISIBLE else VISIBLE
        timeNow.visibility = if (scrubbing) INVISIBLE else VISIBLE; timeLeft.visibility = timeNow.visibility
        preview.visibility = if (scrubbing) VISIBLE else GONE
        eyebrowHolder.removeAllViews(); eyebrowHolder.addView(Cinema.eyebrow(context, listOf(if (scrubbing) "Scrubbing" else "Now playing"), 15))
        when {
            next == PlayerUi.Controls && before != PlayerUi.Controls && before !is PlayerUi.Scrubbing -> { refreshChips(); refreshNext(); pauseButton.requestFocus() }
            next == PlayerUi.Controls && before is PlayerUi.Scrubbing -> scrubber.requestFocus()
            next == PlayerUi.Paused && before != PlayerUi.Paused -> enterPaused()
            next == PlayerUi.Hidden -> { findFocus()?.clearFocus() }
        }
        nudge()
        tick()
    }

    private fun enterPaused() {
        resumeButton.requestFocus()
        onPaused?.invoke()
        refreshStream()
        // Scroll the chapter row to the one playing.
        PlayerMath.chapterAt(chapters, positionMs())?.let { c -> chapterRow.getChildAt(c.index)?.let { v -> chapterScroller.post { chapterScroller.scrollTo((v.left - p(300)).coerceAtLeast(0), 0) } } }
    }

    /** Remote keys while the chrome is in charge. Returns false to let the activity (Back) or focus have the key. */
    fun handleKey(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> PlayerUi.Key.OK
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerUi.Key.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerUi.Key.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> PlayerUi.Key.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> PlayerUi.Key.DOWN
            KeyEvent.KEYCODE_BACK -> PlayerUi.Key.BACK
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> PlayerUi.Key.PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerUi.Key.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerUi.Key.PAUSE
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_UP) return downConsumed.also { downConsumed = false }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        lastKeyAt = android.os.SystemClock.uptimeMillis()
        // In the controls, Up and Down step between the three rows (transport, scrubber, chips) rather than leaving it
        // to geometry, which can skip the thin scrubber.
        if (state == PlayerUi.Controls && (key == PlayerUi.Key.UP || key == PlayerUi.Key.DOWN)) {
            val inTransport = isInside(findFocus(), transport)
            val inChips = isInside(findFocus(), chips) || slidersButton.isFocused
            val target: View? = when {
                key == PlayerUi.Key.DOWN && inTransport -> scrubber
                key == PlayerUi.Key.DOWN && scrubber.isFocused -> (0 until chips.childCount).map { chips.getChildAt(it) }.firstOrNull { it.visibility == VISIBLE }
                key == PlayerUi.Key.UP && inChips -> scrubber
                key == PlayerUi.Key.UP && scrubber.isFocused -> pauseButton
                key == PlayerUi.Key.UP && inTransport -> null.also { setState(PlayerUi.Hidden) }
                else -> null
            }
            target?.requestFocus()
            downConsumed = true; nudge()
            return true
        }
        val step = state.onKey(key, event.repeatCount, positionMs(), durationMs(), isPlaying(), scrubber.isFocused)
        when (val a = step.action) {
            PlayerUi.Action.Play -> onPlay()
            PlayerUi.Action.Pause -> onPause()
            is PlayerUi.Action.Seek -> onSeek(a.ms)
            PlayerUi.Action.None -> Unit
        }
        if (step.state != state) setState(step.state) else if (step.state is PlayerUi.Scrubbing) { state = step.state; tick() }
        downConsumed = step.consumed
        // Keys the state machine leaves alone (moving between buttons) still count as activity.
        nudge()
        return step.consumed
    }

    private fun isInside(view: View?, parent: View): Boolean {
        var node: View? = view
        while (node != null) { if (node === parent) return true; node = node.parent as? View }
        return false
    }

    // --- Ticking ---

    private val ticker = object : Runnable {
        override fun run() { if (isAttachedToWindow && state != PlayerUi.Hidden) { tick(); ui.postDelayed(this, if (state == PlayerUi.Paused) 1000L else 250L) } }
    }

    /** Restarts the auto-hide clock and the ticker. */
    private fun nudge() {
        lastKeyAt = android.os.SystemClock.uptimeMillis()
        ui.removeCallbacks(ticker); if (state != PlayerUi.Hidden) ui.post(ticker)
    }

    private fun tick() {
        val pos = positionMs(); val dur = durationMs()
        val shown = (state as? PlayerUi.Scrubbing)?.targetMs ?: pos
        val fraction = if (dur > 0) shown.toFloat() / dur else 0f
        scrubber.progress = fraction; pausedScrubber.progress = if (dur > 0) pos.toFloat() / dur else 0f
        timeNow.text = PlayerMath.clock(pos); pausedNow.text = timeNow.text
        timeLeft.text = if (dur > 0) "${PlayerMath.clock(dur - pos)} left" else ""; pausedLeft.text = timeLeft.text
        clock.text = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
        pauseButton.playing = isPlaying()
        PlayerMath.chapterAt(chapters, pos)?.let { chapterLabel.text = "Ch ${it.index + 1} · ${it.name}" }
        if (state is PlayerUi.Scrubbing) updatePreview(shown, dur)
        if (state == PlayerUi.Paused) { refreshStream(); refreshChapterCards(pos, dur) }
        PlayerMath.endsAt(System.currentTimeMillis(), pos, dur)?.let { t ->
            endsAt.text = android.text.SpannableStringBuilder("Ends at ").apply {
                val s = length; append(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(t)))
                setSpan(android.text.style.ForegroundColorSpan(Cinema.CREAM), s, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // Controls slip away after five quiet seconds while the film plays.
        if (state == PlayerUi.Controls && isPlaying() && android.os.SystemClock.uptimeMillis() - lastKeyAt > 5000) setState(PlayerUi.Hidden)
    }

    private var previewAt = -1L

    private fun updatePreview(targetMs: Long, durationMs: Long) {
        val sx = scrubber.left + scrubber.xFor(if (durationMs > 0) targetMs.toFloat() / durationMs else 0f)
        val cw = p(420)
        val cx = (sx - cw / 2f).coerceIn(p(96).toFloat(), (width - p(96) - cw).toFloat())
        fun at(v: View, x: Float, y: Int) { (v.layoutParams as LayoutParams).let { it.leftMargin = x.toInt(); it.topMargin = y; v.layoutParams = it } }
        at(previewCard, cx, p(500)); at(ghostLeft, cx - p(300), p(543)); at(ghostRight, cx + cw + p(34), p(543))
        at(previewChapter, cx - p(40), p(500 + 236 + 40))
        previewCard.time.text = PlayerMath.clock(targetMs)
        scrubEnd.text = PlayerMath.clock(durationMs)
        val ch = PlayerMath.chapterAt(chapters, targetMs)
        previewChapter.visibility = if (ch == null) INVISIBLE else VISIBLE
        ch?.let { previewChapterOf.text = PlayerMath.chapterLabel(it, chapters.size); previewChapterName.text = it.name }
        val resume = resumeMs?.takeIf { it > 0 && durationMs > 0 }
        resumeLabel.visibility = if (resume == null) GONE else VISIBLE
        resume?.let { r -> resumeLabel.text = "Resume point · ${PlayerMath.clock(r)}"; resumeLabel.post { at(resumeLabel, scrubber.left + scrubber.xFor(r.toFloat() / durationMs) - resumeLabel.width / 2f, p(958)) } }
        // Frames: trickplay where the film has it, otherwise the chapter stills, otherwise the time alone.
        val bucket = targetMs / 5000
        if (bucket == previewAt) return
        previewAt = bucket
        val frame = previewFrame
        if (frame != null) {
            frame(targetMs) { b -> if (state is PlayerUi.Scrubbing) previewCard.image.setImageBitmap(b) }
            frame((targetMs - 60_000).coerceAtLeast(0)) { b -> ghostLeft.setImageBitmap(b) }
            frame((targetMs + 60_000).coerceAtMost(durationMs)) { b -> ghostRight.setImageBitmap(b) }
            ghostLeft.visibility = VISIBLE; ghostRight.visibility = VISIBLE
        } else if (ch != null && ch.imageTag != null) {
            chapterImage(ch) { b -> previewCard.image.setImageBitmap(b) }
            chapters.getOrNull(ch.index - 1)?.takeIf { it.imageTag != null }?.let { prev -> chapterImage(prev) { b -> ghostLeft.setImageBitmap(b) } }
            chapters.getOrNull(ch.index + 1)?.takeIf { it.imageTag != null }?.let { nx -> chapterImage(nx) { b -> ghostRight.setImageBitmap(b) } }
            ghostLeft.visibility = if (ch.index > 0) VISIBLE else INVISIBLE; ghostRight.visibility = if (ch.index < chapters.size - 1) VISIBLE else INVISIBLE
        } else {
            // No frames to show (a stream): the film's backdrop, dimmed behind the time, rather than an empty card.
            previewCard.image.setImageBitmap(backdrop); ghostLeft.visibility = INVISIBLE; ghostRight.visibility = INVISIBLE
        }
        previewCard.image.alpha = if (frame == null && (ch == null || ch.imageTag == null)) 0.45f else 1f
    }

    private fun refreshChips() {
        val (subs, audio, quality) = chipLabels()
        (subtitlesChip.getChildAt(1) as TextView).text = subs ?: "Subtitles · none"
        audio?.let { (audioChip.getChildAt(1) as TextView).text = it }; audioChip.visibility = if (audio == null) GONE else VISIBLE
        quality?.let { (qualityChip.getChildAt(1) as TextView).text = it }; qualityChip.visibility = if (quality == null) GONE else VISIBLE
    }

    private fun refreshStream() {
        val specs = streamSpecs()
        streamGrid.removeAllViews()
        specs.chunked(2).forEachIndexed { r, pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { s ->
                val cell = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                cell.addView(Cinema.text(context, s.label, 14, Cinema.DIM, weight = 600, tracking = 0.16f, caps = true))
                cell.addView(Cinema.text(context, s.value, 25, Cinema.CREAM, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(6) })
                s.sub?.let { cell.addView(Cinema.text(context, it, 17, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(3) }) }
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            streamGrid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = p(26) })
        }
        val health = streamHealth()
        healthDot.visibility = if (health == null) GONE else VISIBLE; healthText.visibility = healthDot.visibility
        health?.let { (text, good) ->
            healthText.text = text; healthText.setTextColor(if (good) 0xFF8FD6A8.toInt() else Cinema.SAFFRON)
            (healthDot.background as? GradientDrawable)?.setColor(if (good) 0xFF5FD08A.toInt() else Cinema.SAFFRON)
        }
        val samples = bitrateSamples()
        val showBitrate = samples.size >= 2
        bitrateHead.visibility = if (showBitrate) VISIBLE else GONE; sparkline.visibility = bitrateHead.visibility
        if (showBitrate) { sparkline.set(samples); bitratePeak.text = "PEAK ${PlayerMath.bitrate(samples.max())?.uppercase() ?: ""}" }
        streamCard.visibility = if (lbShown || (specs.isEmpty() && !showBitrate)) GONE else VISIBLE
    }

    /**
     * What Letterboxd knows of the film, on five tabs (Overview, Cast, Crew, Details, Reviews) in the Stream card's place.
     * [circle] is the followed members (a display name and their stars, or null when not logged). [credits] are the cast
     * and crew; their pictures come through [setHeadshot] and the reviews through [setReviews].
     */
    fun setLetterboxd(info: LbFilmInfo, circle: List<Pair<String, String?>>, credits: LbCredits) {
        lbInfo = info; lbCircle = circle; lbCredits = credits
        lbReviews = null
        lbHead.removeAllViews()
        lbHead.addView(com.danielportelabyrne.cinema.design.cinema.LbDots.view(context, 12))
        lbHead.addView(Cinema.text(context, "Letterboxd", 16, Cinema.SAFFRON, weight = 600, tracking = 0.24f, caps = true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = p(14) })
        if (info.histogram.size == 10) lbHead.addView(com.danielportelabyrne.cinema.design.cinema.LbHistogram(context).apply { counts = info.histogram }, LinearLayout.LayoutParams(p(118), p(38)).apply { marginEnd = p(16) })
        info.average?.let { a ->
            lbHead.addView(Cinema.text(context, String.format(java.util.Locale.UK, "%.1f", a), 44, Cinema.CREAM, serif = true))
            lbHead.addView(Cinema.text(context, "Average", 14, Cinema.DIM, weight = 600, tracking = 0.16f, caps = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = p(12) })
        }
        selectLbTab(lbTab)
        lbShown = true
        lbCard.visibility = VISIBLE
        streamCard.visibility = GONE
    }

    /** The film's reviews, once they are read (an empty list when there are none). */
    fun setReviews(reviews: List<LbReview>) {
        lbReviews = reviews
        if (lbTab == 4) selectLbTab(4)
    }

    /** A cast or crew member's picture, by their Letterboxd page path. Shown wherever they appear on the card. */
    fun setHeadshot(path: String, url: String) {
        if (lbUrls[path] == url) return
        lbUrls[path] = url
        loadPicture(url, p(160)) { bitmap ->
            val round = circular(bitmap)
            lbBitmaps[path] = round
            lbAvatars[path]?.forEach { av -> av.set(av.contentDescription.toString(), round) }
        }
    }

    /** A picture cut to a circle: the centre square of it, with everything outside the circle transparent. */
    private fun circular(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
            val scale = side.toFloat() / minOf(src.width, src.height)
            val dx = (side - src.width * scale) / 2f; val dy = (side - src.height * scale) / 2f
            setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) })
        }
        canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)
        return out
    }

    /** Shows each fade only while the content runs off that edge. */
    private fun updateLbFades() {
        val content = lbPanel.height
        lbFadeTop.alpha = if (lbBody.scrollY > 0) 1f else 0f
        lbFadeBottom.alpha = if (lbBody.scrollY + lbBody.height < content) 1f else 0f
    }

    private fun selectLbTab(index: Int) {
        lbTab = index
        lbTabLabels.forEachIndexed { i, t ->
            if (!t.isFocused) t.setTextColor(if (i == index) Cinema.SAFFRON else Cinema.MUTED)
            Cinema.weight(t, 19, serif = false, weight = if (i == index) 700 else 600)
        }
        lbPanel.removeAllViews()
        lbAvatars.clear()
        val info = lbInfo ?: return
        when (index) {
            0 -> lbOverview(info)
            1 -> lbPeople(lbCredits.cast.map { Triple(it.path, it.name, it.role) }, 84, lbPanel)
            2 -> lbCrewTab()
            3 -> lbDetails(info)
            else -> lbReviewList()
        }
        lbBody.scrollTo(0, 0)
    }

    private fun lbSection(label: String) = Cinema.text(context, label, 15, Cinema.DIM, weight = 600, tracking = 0.2f, caps = true)

    private fun lbOverview(info: LbFilmInfo) {
        val page = info.page
        page.tagline?.let { t ->
            lbPanel.addView(Cinema.text(context, "“$t”", 34, 0xFFF1E4CF.toInt(), serif = true, weight = 300).apply { setTypeface(typeface, android.graphics.Typeface.ITALIC) })
        }
        page.synopsis?.let { syn ->
            lbPanel.addView(Cinema.text(context, syn, 22, 0xFFE6D9C6.toInt()).apply { setLineSpacing(0f, 1.2f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(18) })
        }
        fun crewNames(vararg roles: String) = lbCredits.crew.firstOrNull { g -> roles.any { r -> g.role.equals(r, ignoreCase = true) } }?.people?.map { it.name }.orEmpty()
        val credits = listOfNotNull(
            (page.director?.let { listOf(it) } ?: crewNames("Director", "Directors")).takeIf { it.isNotEmpty() }?.let { "Directed by" to it },
            crewNames("Writer", "Writers", "Screenplay").takeIf { it.isNotEmpty() }?.let { "Written by" to it },
            crewNames("Composer", "Composers").takeIf { it.isNotEmpty() }?.let { "Music" to it },
            crewNames("Cinematography").takeIf { it.isNotEmpty() }?.let { "Cinematography" to it },
            page.cast.take(5).map { it.name }.takeIf { it.isNotEmpty() }?.let { "Starring" to it })
        credits.forEach { (label, names) ->
            lbPanel.addView(lbSection(label), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(24) })
            lbPanel.addView(Cinema.text(context, names.joinToString(", "), 22, Cinema.CREAM).apply { setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(6) })
        }
        if (lbCircle.isNotEmpty()) {
            val logged = lbCircle.filter { it.second != null }
            val who = android.text.SpannableStringBuilder().apply {
                if (logged.isEmpty()) append("Not logged by ${lbCircle.joinToString(" or ") { it.first }} yet")
                else logged.forEachIndexed { i, (name, stars) ->
                    if (i > 0) append("   ·   ")
                    append("$name  "); val at = length; append(stars ?: "watched")
                    setSpan(android.text.style.ForegroundColorSpan(Cinema.SAFFRON), at, length, 0)
                }
            }
            lbPanel.addView(lbSection("Your circle"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(24) })
            lbPanel.addView(Cinema.text(context, who, 21, Cinema.MUTED).apply { setLineSpacing(0f, 1.2f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(6) })
        }
    }

    private fun lbCrewTab() {
        lbCredits.crew.forEachIndexed { i, role ->
            lbPanel.addView(lbSection(role.role), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(if (i == 0) 0 else 22) })
            lbPeople(role.people.map { Triple(it.path, it.name, null) }, 64, lbPanel)
        }
    }

    /** People in two columns, each with a picture, a name and (for the cast) their character. */
    private fun lbPeople(people: List<Triple<String, String, String?>>, photo: Int, parent: LinearLayout) {
        people.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (path, name, role) ->
                row.addView(lbPerson(path, name, role, photo), LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = p(16) })
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = p(16) })
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(12) })
        }
    }

    private fun lbPerson(path: String, name: String, role: String?, photo: Int): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding(p(12), p(10), p(12), p(10))
        }
        val av = com.danielportelabyrne.cinema.design.cinema.AvatarView(context, photo).apply { contentDescription = name; drawRing = false; set(name, lbBitmaps[path]) }
        lbAvatars.getOrPut(path) { mutableListOf() }.add(av)
        item.addView(av, LinearLayout.LayoutParams(p(photo), p(photo)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(Cinema.text(context, name, 24, Cinema.CREAM, weight = 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        role?.takeIf { it.isNotBlank() }?.let { names.addView(Cinema.text(context, it, 19, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(4) }) }
        item.addView(names, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = p(18) })
        item.setOnFocusChangeListener { v, focused ->
            v.background = if (focused) Cinema.glass(context, 20f, Cinema.CREAM) else null
            (names.getChildAt(0) as? TextView)?.setTextColor(if (focused) Cinema.INK else Cinema.CREAM)
            (names.getChildAt(1) as? TextView)?.setTextColor(if (focused) 0xFF6A5848.toInt() else Cinema.MUTED)
        }
        return item
    }

    private fun lbDetails(info: LbFilmInfo) {
        val page = info.page
        val rows = listOfNotNull(
            page.year?.let { "Year" to "$it" },
            page.runtimeMin?.let { "Runtime" to "$it min" },
            page.studios.takeIf { it.isNotEmpty() }?.let { "Studios" to it.joinToString(", ") },
            page.countries.takeIf { it.isNotEmpty() }?.let { "Countries" to it.joinToString(", ") },
            page.languages.takeIf { it.isNotEmpty() }?.let { "Languages" to it.joinToString(", ") },
            page.genres.takeIf { it.isNotEmpty() }?.let { "Genres" to it.joinToString(", ") },
            page.themes.takeIf { it.isNotEmpty() }?.let { "Themes" to it.joinToString(", ") })
        if (rows.isEmpty()) lbPanel.addView(Cinema.text(context, "Nothing more on the film's page yet.", 22, Cinema.MUTED))
        rows.forEachIndexed { i, (label, value) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; setPadding(p(12), p(10), p(12), p(12))
                setOnFocusChangeListener { v, focused -> v.background = if (focused) Cinema.glass(context, 18f, Cinema.CREAM) else null }
            }
            row.addView(lbSection(label))
            row.addView(Cinema.text(context, value, 22, Cinema.CREAM).apply { setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(4) })
            lbPanel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(if (i == 0) 0 else 6) })
        }
    }

    private fun lbReviewList() {
        val reviews = lbReviews
        if (reviews == null) { lbPanel.addView(Cinema.text(context, "Loading the reviews…", 22, Cinema.MUTED)); return }
        if (reviews.isEmpty()) { lbPanel.addView(Cinema.text(context, "No reviews yet.", 22, Cinema.MUTED)); return }
        reviews.take(12).forEachIndexed { i, r ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; setPadding(p(14), p(12), p(14), p(14))
                setOnFocusChangeListener { v, focused -> v.background = if (focused) Cinema.glass(context, 18f, Cinema.CREAM) else null }
            }
            card.addView(Cinema.text(context, r.authorName, 22, Cinema.CREAM, weight = 600))
            r.rating?.let { card.addView(Cinema.text(context, com.danielportelabyrne.cinema.data.letterboxd.Lb.stars(it), 19, Cinema.SAFFRON), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(2) }) }
            card.addView(Cinema.text(context, r.text, 21, 0xFFE6D9C6.toInt()).apply { setLineSpacing(0f, 1.18f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(8) })
            lbPanel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(if (i == 0) 0 else 10) })
        }
    }

    private fun refreshChapterCards(pos: Long, dur: Long) {
        val now = PlayerMath.chapterAt(chapters, pos)
        (0 until chapterRow.childCount).forEach { i ->
            (chapterRow.getChildAt(i) as? ChapterCard)?.setCurrent(now?.index == i, if (now?.index == i) PlayerMath.chapterProgress(chapters, now, pos, dur) else 0f)
        }
    }

    // --- Pieces ---

    private fun chip(icon: Int, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
        setPadding(p(26), 0, p(26), 0)
        val img = Cinema.icon(context, icon, 0xFFDDD0BD.toInt())
        val label = Cinema.text(context, "", 20, 0xFFDDD0BD.toInt()).apply { maxLines = 1 }
        addView(img, LinearLayout.LayoutParams(p(28), p(28)).apply { marginEnd = p(10) }); addView(label)
        fun look(f: Boolean) { background = Cinema.glass(context, 27f, if (f) Cinema.CREAM else Cinema.GLASS); val c = if (f) Cinema.INK else 0xFFDDD0BD.toInt(); img.setColorFilter(c); label.setTextColor(c) }
        look(false)
        setOnFocusChangeListener { _, f -> look(f); nudge() }
        setOnClickListener { onClick() }
    }

    private fun roundButton(icon: Int, sizePx: Int, iconPx: Int, onClick: () -> Unit): FrameLayout = FrameLayout(context).apply {
        isFocusable = true; isClickable = true
        val img = Cinema.icon(context, icon, Cinema.CREAM)
        addView(img, LayoutParams(p(iconPx), p(iconPx), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(p(sizePx), p(sizePx))
        fun look(f: Boolean) {
            background = if (f) GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.CREAM) } else if (sizePx == 54) Cinema.glass(context, 27f) else null
            img.setColorFilter(if (f) Cinema.INK else Cinema.CREAM)
        }
        look(false)
        setOnFocusChangeListener { _, f -> look(f); nudge() }
        setOnClickListener { onClick() }
    }

    /** ±10 s: the arrow icon with "10" set in it. */
    private fun seekButton(icon: Int, deltaMs: Long): FrameLayout = roundButton(icon, 96, 64) { onSeek((positionMs() + deltaMs).coerceIn(0, durationMs().coerceAtLeast(0))); nudge() }.apply {
        addView(Cinema.text(context, "10", 17, Cinema.CREAM, weight = 700).apply { gravity = Gravity.CENTER; tag = "ten" }, LayoutParams(-1, -1).apply { topMargin = p(6) })
        setOnFocusChangeListener { _, f ->
            background = if (f) GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Cinema.CREAM) } else null
            (getChildAt(0) as ImageView).setColorFilter(if (f) Cinema.INK else Cinema.CREAM)
            (findViewWithTag<TextView>("ten")).setTextColor(if (f) Cinema.INK else Cinema.CREAM)
            nudge()
        }
    }

    private fun ghost() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.55f; clipToOutline = true; setBackgroundColor(Cinema.PLACEHOLDER)
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 14)) } }
    }

    /** The scrubbing preview: the frame, the time in Fraunces, and the stripe offset shadow (no outline). */
    private class PreviewCard(context: Context) : FrameLayout(context) {
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val time = Cinema.text(context, "", 44, Cinema.CREAM, serif = true, tracking = -0.01f)
        private val r = Cinema.pxf(context, 20)
        private val band = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            clipChildren = false; setWillNotDraw(false)
            val face = FrameLayout(context).apply {
                setBackgroundColor(0xFF1A1310.toInt()); clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) } }
                addView(image, LayoutParams(-1, -1))
                addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0xD9080605.toInt())) },
                    LayoutParams(-1, Cinema.px(context, 90), Gravity.BOTTOM))
                addView(time, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = Cinema.px(context, 20); bottomMargin = Cinema.px(context, 10) })
            }
            addView(face, LayoutParams(-1, -1))
        }
        override fun dispatchDraw(canvas: Canvas) {
            for ((i, c) in listOf(Cinema.STRIPE_ORANGE, Cinema.STRIPE_SAFFRON).withIndex()) {
                val d = Cinema.pxf(context, 20f - i * 10f)
                band.color = c; canvas.drawRoundRect(d, d, width + d, height + d, r, r, band)
            }
            super.dispatchDraw(canvas)
        }
    }

    /** The big pause/play disc, ringed in the stripe set (`box-shadow` rings in the mockup), glowing when focused. */
    private class PauseDisc(context: Context) : View(context) {
        var playing = true
            set(value) { if (field != value) { field = value; invalidate() } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Cinema.alpha(Cinema.CREAM, 0.3f)
            if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 30), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private val pause = context.getDrawable(R.drawable.ic_cin_pause)!!.mutate().apply { setTint(0xFF1B120C.toInt()) }
        private val play = context.getDrawable(R.drawable.ic_cin_play)!!.mutate().apply { setTint(0xFF1B120C.toInt()) }
        init { isFocusable = true; isClickable = true; contentDescription = "Play or pause" }
        companion object {
            /** Mockup px the view needs: the 112 px disc, 5 rings of 9 px each side, and room for the soft focus glow. */
            const val BOX = 112 + 2 * 45 + 2 * 50
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
            animate().scaleX(if (gainFocus) 1.06f else 1f).scaleY(if (gainFocus) 1.06f else 1f).setDuration(160).start()
        }
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            val r = Cinema.pxf(context, 56)   // the cream disc itself; the view is larger to hold the rings and glow
            val step = Cinema.pxf(context, 9)
            if (isFocused && android.os.Build.VERSION.SDK_INT >= 28) canvas.drawCircle(c, c, r + step * 6, glow)
            // Outermost first: maroon, red, terracotta, orange, saffron, then the cream disc.
            for (i in 5 downTo 1) { paint.color = Cinema.RINGS[5 - i]; canvas.drawCircle(c, c, r + step * i, paint) }
            paint.color = Cinema.CREAM; canvas.drawCircle(c, c, r, paint)
            val icon = if (playing) pause else play
            val s = Cinema.px(context, 25)
            icon.setBounds((c - s).toInt(), (c - s).toInt(), (c + s).toInt(), (c + s).toInt()); icon.draw(canvas)
        }
    }

    /** A chapter on the paused screen: its still, number, name and start; the one playing is lit and grown. */
    private inner class ChapterCard(context: Context, val chapter: Chapter) : LinearLayout(context) {
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        private val face = FrameLayout(context)
        private val name = Cinema.text(context, chapter.name, 21, 0xFFC4B6A3.toInt(), weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val nowTag = Cinema.text(context, "NOW", 13, 0xFF1B0D06.toInt(), weight = 700, tracking = 0.14f).apply {
            setPadding(p(10), p(4), p(10), p(4)); background = com.danielportelabyrne.cinema.design.cinema.StripeDrawable(horizontalBands = true, radius = Cinema.pxf(context, 9)); visibility = GONE
        }
        private val underline = View(context).apply { background = com.danielportelabyrne.cinema.design.cinema.StripeDrawable(horizontalBands = true, radius = 0f); visibility = GONE }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Cinema.alpha(Cinema.SAFFRON, 0.35f)
            if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 26), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private var current = false
        init {
            orientation = VERTICAL; isFocusable = true; isClickable = true; clipChildren = false; setWillNotDraw(false)
            contentDescription = chapter.name
            face.setBackgroundColor(Cinema.PLACEHOLDER); face.clipToOutline = true
            face.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 16)) } }
            face.addView(image, FrameLayout.LayoutParams(-1, -1))
            face.addView(Cinema.text(context, "${chapter.index + 1}", 15, Cinema.CREAM, weight = 700).apply {
                setPadding(p(10), p(4), p(10), p(4)); background = Cinema.glass(context, 9f, 0xB30B0807.toInt())
            }, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = p(10); topMargin = p(10) })
            face.addView(nowTag, FrameLayout.LayoutParams(-2, -2, Gravity.END).apply { rightMargin = p(10); topMargin = p(10) })
            face.addView(underline, FrameLayout.LayoutParams(0, p(5), Gravity.BOTTOM))
            addView(face, LayoutParams(p(268), p(151)))
            addView(name, LayoutParams(p(268), -2).apply { topMargin = p(14) })
            addView(Cinema.text(context, PlayerMath.clock(chapter.startMs), 16, Cinema.DIM), LayoutParams(-2, -2).apply { topMargin = p(3) })
            pivotY = 0f
        }
        fun setCurrent(on: Boolean, progress: Float) {
            current = on
            nowTag.visibility = if (on) VISIBLE else GONE
            underline.visibility = if (on) VISIBLE else GONE
            (underline.layoutParams as FrameLayout.LayoutParams).width = (p(268) * progress).toInt().coerceAtLeast(1); underline.requestLayout()
            image.alpha = if (on || isFocused) 1f else 0.8f
            name.setTextColor(if (on || isFocused) Cinema.CREAM else 0xFFC4B6A3.toInt())
            val s = if (on || isFocused) 1.06f else 1f; scaleX = s; scaleY = s
            invalidate()
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            setCurrent(current, if (current) (underline.layoutParams.width.toFloat() / p(268)) else 0f)
            if (gainFocus) FastFocus.reveal(this, null, 0, 0, p(120))
            nudge()
        }
        override fun dispatchDraw(canvas: Canvas) {
            if (isFocused && android.os.Build.VERSION.SDK_INT >= 28) canvas.drawRoundRect(face.left.toFloat(), face.top.toFloat(), face.right.toFloat(), face.bottom.toFloat(), Cinema.pxf(context, 16), Cinema.pxf(context, 16), glow)
            super.dispatchDraw(canvas)
        }
    }

    /** The faint arch columns on the paused screen, painted once. */
    private class ArchesView(context: Context) : View(context) {
        private var cache: Bitmap? = null
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            cache?.recycle(); cache = null
            if (w > 0 && h > 0) cache = Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888).also { AmbientBackground.drawArches(Canvas(it), w / 2, h / 2, 0.2f) }
        }
        override fun onDraw(canvas: Canvas) { cache?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG)) } }
    }

    companion object {

        /** A soft, cheap blur for the paused background: shrink hard, box-blur three times, let the view scale it back up. */
        fun blur(src: Bitmap): Bitmap {
            val w = (src.width / 8).coerceAtLeast(8); val h = (src.height / 8).coerceAtLeast(8)
            val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
            repeat(3) { boxBlur(px, w, h, 2) }
            small.setPixels(px, 0, w, 0, 0, w, h)
            return small
        }

        private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
            val out = IntArray(px.size)
            fun pass(horizontal: Boolean, src: IntArray, dst: IntArray) {
                val outer = if (horizontal) h else w; val inner = if (horizontal) w else h
                for (o in 0 until outer) for (i in 0 until inner) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val c = if (horizontal) src[o * w + j] else src[j * w + o]
                        a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    }
                    val idx = if (horizontal) o * w + i else i * w + o
                    dst[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
            pass(true, px, out); pass(false, out, px)
        }
    }
}
