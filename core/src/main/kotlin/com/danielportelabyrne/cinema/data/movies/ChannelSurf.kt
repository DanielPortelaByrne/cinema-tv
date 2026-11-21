package com.danielportelabyrne.cinema.data.movies

/**
 * Which channel CH+ and CH- tune to: the next or previous one in channel-number order (the order the channels are listed in),
 * wrapping at the ends. Pure, so the wrap and the edge cases can be tested without a player.
 */
object ChannelSurf {
    /** The id to tune to from [current] among [ids] (in channel-number order); [up] is CH+. Null when there is nowhere else to go. */
    fun destination(ids: List<String>, current: String, up: Boolean): String? {
        if (ids.size < 2) return null
        val at = ids.indexOf(current)
        if (at < 0) return if (up) ids.first() else ids.last()
        val step = if (up) 1 else ids.size - 1
        return ids[(at + step) % ids.size]
    }
}
