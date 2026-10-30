package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.data.player.PlayerUi
import com.danielportelabyrne.cinema.desktop.player.chrome.ChromeFocus
import com.danielportelabyrne.cinema.desktop.player.chrome.ChromeModel
import com.danielportelabyrne.cinema.desktop.player.chrome.ChromeRenderer
import com.danielportelabyrne.cinema.desktop.player.chrome.ChromeSpec
import java.io.File

/**
 * Renders the player chrome in its three states from the same details the Android TV showed when its own screenshots
 * were taken (Dark, S1 E5), as PNGs to lay over them. `./gradlew :desktop:chromePreview`.
 */
fun main(args: Array<String>) {
    val out = File(args[0]).apply { mkdirs() }
    val width = args.getOrNull(1)?.toInt() ?: 1920
    val height = args.getOrNull(2)?.toInt() ?: 1080
    val base = ChromeModel(
        title = "Dark · S1 E5", year = 2017, genres = listOf("Crime", "Drama", "Mystery"), runtimeMin = 45, rating = 8.5,
        overview = "Hannah takes her obsession with Ulrich too far. The stranger asks Regina to deliver an important package. Martha is torn between Jonas and Bartosz.",
        durationMs = 2_728_000, positionMs = 388_000, subtitlesChip = "Subtitles · Off", audioChip = "German · 5.1", qualityChip = "1080p", clock = "18:16",
        specs = listOf(
            ChromeSpec("Resolution", "1080p · HD"), ChromeSpec("Video", "HEVC 10-bit"), ChromeSpec("Audio", "German · 5.1", "AAC"),
            ChromeSpec("Subtitles", "Off", "5 languages available"), ChromeSpec("Size", "520 MB", "Buffered 0m 56s ahead"),
        ),
        health = "Healthy" to true, bitrateSamples = listOf(3_200_000L, 3_200_000L), bitratePeak = "3.2 Mbps", endsAt = "18:55",
    )
    val shots = mapOf(
        "controls" to base.copy(ui = PlayerUi.Controls, focus = ChromeFocus.PAUSE),
        "scrub" to base.copy(ui = PlayerUi.Scrubbing(369_000, PlayerUi.Controls), focus = ChromeFocus.SCRUBBER, clock = "18:17", resumeMs = 365_000),
        "paused" to base.copy(ui = PlayerUi.Paused, focus = ChromeFocus.RESUME, positionMs = 394_000),
    )
    val renderer = ChromeRenderer()
    for ((name, model) in shots) File(out, "deck-$name.png").writeBytes(renderer.png(model, width, height))
    println("Chrome written to ${out.absolutePath}")
    kotlin.system.exitProcess(0)
}
