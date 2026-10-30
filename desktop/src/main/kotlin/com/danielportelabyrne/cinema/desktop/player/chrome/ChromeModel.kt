package com.danielportelabyrne.cinema.desktop.player.chrome

import com.danielportelabyrne.cinema.data.player.PlayerUi

/** What has focus in the chrome: the TV's views take focus the same way (the transport row, the scrubber, the chips). */
enum class ChromeFocus { PREVIOUS, BACK_10, PAUSE, FORWARD_10, NEXT, SCRUBBER, SUBTITLES, AUDIO, QUALITY, SLIDERS, RESUME, START_OVER, CHAPTERS }

data class ChromeChapter(val index: Int, val name: String, val startMs: Long)

/** One figure on the paused screen's Stream card (the TV's StreamSpec). */
data class ChromeSpec(val label: String, val value: String, val sub: String? = null)

/**
 * Everything the chrome draws, as plain values. The TV's PlayerChrome reads the same things from its hooks; here the
 * player fills this in and [ChromeRenderer] turns it into a picture for mpv.
 */
data class ChromeModel(
    val ui: PlayerUi = PlayerUi.Hidden,
    val focus: ChromeFocus = ChromeFocus.PAUSE,
    /** The chapter card focused on the paused screen, when [focus] is [ChromeFocus.CHAPTERS]. */
    val chapterFocus: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val runtimeMin: Int? = null,
    val certificate: String? = null,
    val rating: Double? = null,
    val overview: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = true,
    val chapters: List<ChromeChapter> = emptyList(),
    val subtitlesChip: String? = null,
    val audioChip: String? = null,
    val qualityChip: String? = null,
    val hasNext: Boolean = true,
    val specs: List<ChromeSpec> = emptyList(),
    /** ("Healthy", true) or ("Buffering", false); null hides the badge. */
    val health: Pair<String, Boolean>? = null,
    val bitrateSamples: List<Long> = emptyList(),
    val bitratePeak: String? = null,
    val clock: String = "",
    val endsAt: String? = null,
    val resumeMs: Long? = null,
)
