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
     * the one after it in order is next ("Watch S3E3"). With nothing watched yet it is the first episode ("Watch S1E1").
     * Null when the library holds no episodes, and when the episode finished last is the last one there is: a finished
     * series is never offered from its first episode again unless [wrapAtEnd] asks for that (a deliberate rewatch).
     */
    fun of(episodes: List<EpisodeWatch>, streamed: List<EpisodeWatch> = emptyList(), wrapAtEnd: Boolean = false): NextUp? {
        if (episodes.isEmpty()) return null
        val inOrder = episodes.sortedWith(compareBy<EpisodeWatch>({ it.season }, { it.episode }))
        // A part-watched stream cannot be carried on from (only library files resume), so only finished streams count.
        val watched = episodes.filter { it.playedMs != null || it.resumeMs > 0 } + streamed.filter { it.played && it.playedMs != null }
        val last = watched.maxByOrNull { it.playedMs ?: 0L } ?: return inOrder.first().let { NextUp(it.season, it.episode, continuing = false) }
        val inLibrary = episodes.any { it.season == last.season && it.episode == last.episode }
        if (inLibrary && !last.played && last.resumeMs > 0) return NextUp(last.season, last.episode, continuing = true)
        val after = inOrder.firstOrNull { it.season > last.season || (it.season == last.season && it.episode > last.episode) }
        return (after ?: inOrder.first().takeIf { wrapAtEnd })?.let { NextUp(it.season, it.episode, continuing = false) }
    }

    /**
     * What a streamed series' main button plays. [episodes] are the series' episodes as (season, episode), in any order;
     * [streamed] is what was played here (StreamProgress). The episode played most recently decides it: not finished, it
     * is played again, carrying on when at least [minResumeMs] in ("Continue watching S1E3"); finished, the one after it
     * is next ("Watch S1E4"). Nothing played yet gives the first episode. Null when there are no episodes, or the last
     * one has been finished.
     */
    fun forStream(episodes: List<Pair<Int, Int>>, streamed: List<StreamedEpisode>, minResumeMs: Long = 60_000L): NextUp? {
        if (episodes.isEmpty()) return null
        val inOrder = episodes.distinct().sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val last = streamed.maxByOrNull { it.atMs }
            ?: return inOrder.first().let { NextUp(it.first, it.second, continuing = false) }
        // The latest viewing decides, never the furthest point ever reached: a finished episode watched again and stopped is carried on.
        if (!last.finishedLast) return NextUp(last.season, last.episode, continuing = last.resumeMs >= minResumeMs)
        return following(inOrder, last.season, last.episode)?.let { NextUp(it.first, it.second, continuing = false) }
    }

    /** The episode after [season] and [episode] in [episodes] (given in any order), or null when it is the last one. */
    fun following(episodes: List<Pair<Int, Int>>, season: Int, episode: Int): Pair<Int, Int>? =
        episodes.sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
            .firstOrNull { it.first > season || (it.first == season && it.second > episode) }
}
