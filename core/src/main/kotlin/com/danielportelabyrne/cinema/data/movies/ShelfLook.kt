package com.danielportelabyrne.cinema.data.movies

/** How a row of titles is drawn. A page cycles through them so neighbouring rows never look the same. */
enum class ShelfLook {
    /** Wide 16:9 stills with the name over the picture. */
    STILLS,
    /** Tall posters with the name beneath (the classic row). */
    POSTERS,
    /** Smaller wide stills, more to a screen. */
    SMALL_STILLS,
    /** Smaller wide stills stacked two high, so the row reads as a grid. */
    GRID,
    /** One large wide card with the overview, then posters. */
    SPOTLIGHT,
    /** A numbered Top 10 row. */
    RANKED
}

object ShelfLooks {
    /** The spotlight comes first: the first row of titles on a page is the one most on display. */
    private val CYCLE = listOf(ShelfLook.SPOTLIGHT, ShelfLook.STILLS, ShelfLook.POSTERS, ShelfLook.GRID, ShelfLook.SMALL_STILLS)

    /** Below this share of titles with a wide picture, a wide row would be mostly placeholders: posters instead. */
    const val MIN_STILL_SHARE = 0.7

    /** The look for each shelf, in order: Top 10 rows keep their numbers; the others take turns through the cycle. */
    fun forShelves(specs: List<ShelfSpec>): List<ShelfLook> {
        var next = 0
        return specs.map { if (it.ranked) ShelfLook.RANKED else CYCLE[next++ % CYCLE.size] }
    }

    /**
     * A page's shelves in the order they are shown, each with its look. The first ordinary shelf moves to the top, so
     * the row right under the featured title is always the spotlight; a Top 10 row that led the page comes next.
     */
    fun plan(specs: List<ShelfSpec>): List<Pair<ShelfSpec, ShelfLook>> {
        val lead = specs.indexOfFirst { !it.ranked }
        val ordered = if (lead > 0) listOf(specs[lead]) + specs.filterIndexed { i, _ -> i != lead } else specs
        return ordered.zip(forShelves(ordered))
    }

    /** The planned [look], or posters when too few of the row's titles have a wide picture for it. */
    fun resolve(look: ShelfLook, titles: List<Title>): ShelfLook {
        if (look == ShelfLook.POSTERS || look == ShelfLook.RANKED) return look
        if (titles.isEmpty()) return ShelfLook.POSTERS
        if (look == ShelfLook.SPOTLIGHT) return if (spotlightIndex(titles) != null) look else ShelfLook.POSTERS
        val share = titles.count { it.backdropUrl != null }.toDouble() / titles.size
        return if (share >= MIN_STILL_SHARE) look else ShelfLook.POSTERS
    }

    /** Which of a spotlight row's first titles leads it: the first with a wide picture. */
    fun spotlightIndex(titles: List<Title>): Int? = titles.take(3).indexOfFirst { it.backdropUrl != null }.takeIf { it >= 0 }
}
