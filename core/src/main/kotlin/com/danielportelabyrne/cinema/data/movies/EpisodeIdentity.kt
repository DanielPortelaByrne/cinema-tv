package com.danielportelabyrne.cinema.data.movies

/**
 * Which episode a release or stream name says it is, checked against the episode that was asked for. A two-part episode,
 * or a provider that numbers a series differently, can hand back part two (or a neighbour) under the number of part one;
 * a name that plainly says another episode must never be played as the one the viewer picked.
 */
object EpisodeIdentity {
    enum class Verdict { MATCH, MISMATCH, UNKNOWN }

    private val SXE = Regex("""(?<![A-Za-z0-9])[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})(?:[ ._-]?(?:[Ee]|-[Ee]?)(\d{1,3}))?(?!\d)""")
    private val NXM = Regex("""(?<![A-Za-z0-9])(\d{1,2})x(\d{2,3})(?!\d)""")

    /** Every (season, first episode, last episode) the [text] names. A combined release ("S02E06E07", "S02E06-07") covers a range. */
    fun named(text: String): List<Triple<Int, Int, Int>> =
        SXE.findAll(text).map { m ->
            val first = m.groupValues[2].toInt(); val last = m.groupValues[3].toIntOrNull() ?: first
            Triple(m.groupValues[1].toInt(), first, maxOf(first, last))
        }.toList() + NXM.findAll(text).map { m -> m.groupValues[2].toInt().let { Triple(m.groupValues[1].toInt(), it, it) } }.toList()

    /**
     * MATCH when the [text] names the requested [season] and [episode]; MISMATCH when it names episodes and none is that
     * one; UNKNOWN when it names none (a season pack, or a name without numbers), which is left to the service to pick from.
     */
    fun check(season: Int, episode: Int, text: String?): Verdict {
        val found = named(text.orEmpty())
        if (found.isEmpty()) return Verdict.UNKNOWN
        return if (found.any { (s, a, b) -> s == season && episode in a..b }) Verdict.MATCH else Verdict.MISMATCH
    }

    /** True unless the [text] plainly names a different episode than [requested] (season, episode). Films ([requested] null) always pass. */
    fun acceptable(requested: Pair<Int, Int>?, text: String?): Boolean =
        requested == null || check(requested.first, requested.second, text) != Verdict.MISMATCH
}
