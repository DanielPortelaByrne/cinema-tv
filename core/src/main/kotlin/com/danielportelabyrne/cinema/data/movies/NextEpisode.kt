package com.danielportelabyrne.cinema.data.movies

/** One episode, as far as its watching goes. [playedMs] is when it was last played, null when never. */
data class EpisodeWatch(val season: Int, val episode: Int, val played: Boolean, val resumeMs: Long, val playedMs: Long?)

/** The episode a series' main button plays. [continuing] is set when it is a part-watched episode to carry on from. */
data class NextUp(val season: Int, val episode: Int, val continuing: Boolean)

object NextEpisode {
    /**
     * What the series' main button should play. [episodes] are the library's episodes (the only ones the button can play);
     * [streamed] are episodes finished in a stream, which can make the most recent watch without being in the library.
     * The episode watched most recently decides it: part-way through, it is continued ("Continue watching S3E2"); finished,
     * the one after it in order is next ("Watch S3E3"), wrapping to the first. With nothing watched yet it is the first
     * episode ("Watch S1E1"). Null when the library holds no episodes.
     */
    fun of(episodes: List<EpisodeWatch>, streamed: List<EpisodeWatch> = emptyList()): NextUp? {
        if (episodes.isEmpty()) return null
        val inOrder = episodes.sortedWith(compareBy<EpisodeWatch>({ it.season }, { it.episode }))
        // A part-watched stream cannot be carried on from (only library files resume), so only finished streams count.
        val watched = episodes.filter { it.playedMs != null || it.resumeMs > 0 } + streamed.filter { it.played && it.playedMs != null }
        val last = watched.maxByOrNull { it.playedMs ?: 0L } ?: return inOrder.first().let { NextUp(it.season, it.episode, continuing = false) }
        val inLibrary = episodes.any { it.season == last.season && it.episode == last.episode }
        if (inLibrary && !last.played && last.resumeMs > 0) return NextUp(last.season, last.episode, continuing = true)
        val after = inOrder.firstOrNull { it.season > last.season || (it.season == last.season && it.episode > last.episode) }
        return (after ?: inOrder.first()).let { NextUp(it.season, it.episode, continuing = false) }
    }
}
