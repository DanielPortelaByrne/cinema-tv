package com.danielportelabyrne.cinema.data.stream

/** One copy of the title the player holds, as the "Other streams" sheet needs it. Only what is known is filled in. */
data class StreamOption(val label: String, val provider: String, val bitrateBps: Long?)

/** One row of the sheet: [tag] is "Current", "Recommended", both, or null; [detail] never names anything unknown. */
data class StreamChoiceRow(val index: Int, val title: String, val detail: String?, val tag: String?)

/** What the player does when a copy fails. */
sealed interface AfterFailure {
    /** Try the copy at [index] (the automatic order carries on). */
    data class TryNext(val index: Int) : AfterFailure
    /** A copy the viewer picked failed: stop and let them pick again, never move on silently. */
    object AskAgain : AfterFailure
    /** Nothing left to try. */
    object GiveUp : AfterFailure
}

object StreamChoices {
    /** The automatic pick is always the first copy: the player holds them best first. */
    const val RECOMMENDED_INDEX = 0

    /** Whether the player offers "Other streams": only with a second copy to choose and not for a live channel. */
    fun offered(count: Int, live: Boolean): Boolean = !live && count > 1

    fun rows(options: List<StreamOption>, current: Int): List<StreamChoiceRow> = options.mapIndexed { i, o ->
        val tag = listOfNotNull(if (i == current) "Current" else null, if (i == RECOMMENDED_INDEX) "Recommended" else null)
            .joinToString(" · ").ifBlank { null }
        val speed = o.bitrateBps?.takeIf { it > 0 }?.let { "${(it + 500_000) / 1_000_000} Mbps" }
        val title = o.label.ifBlank { o.provider }
        val detail = listOfNotNull(o.provider.takeIf { it.isNotBlank() && it != title }, speed).joinToString(" · ").ifBlank { null }
        StreamChoiceRow(i, title, detail, tag)
    }

    /** [manual]: the viewer chose this copy themselves. */
    fun afterFailure(index: Int, count: Int, manual: Boolean): AfterFailure = when {
        manual -> AfterFailure.AskAgain
        index + 1 < count -> AfterFailure.TryNext(index + 1)
        else -> AfterFailure.GiveUp
    }

    /** Where playback picks up when the viewer switches copy: where the picture was, or the saved point if nothing had played. */
    fun resumeAt(startedPositionMs: Long, started: Boolean): Long? = if (started && startedPositionMs > 0) startedPositionMs else null
}
