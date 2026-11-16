package com.danielportelabyrne.cinema.data.movies

/** How a channel is painted: its colour, the shape cut into it, and whether text on it is dark. */
data class ChannelLook(val colour: Int, val motif: Int, val darkText: Boolean)

/**
 * A playlist seen as a TV channel: its number (its place in the list), its look and the plain words about what is on it.
 * Numbers follow the list, so a new channel is always the last number and nothing is renumbered behind the viewer's back
 * except when a channel is deleted.
 */
class ChannelSummary(val number: Int, val playlist: BroadcastPlaylist) {
    val id: String get() = playlist.id
    val name: String get() = playlist.name
    val look: ChannelLook get() = ChannelGuide.look(number)

    private val films get() = playlist.slots.count { it.title.kind == Kind.MOVIE }
    private val series get() = playlist.slots.size - films

    /** "6 programmes", "1 programme", or "Nothing on yet". */
    val programmes: String
        get() = when (val n = playlist.slots.size) {
            0 -> "Nothing on yet"
            1 -> "1 programme"
            else -> "$n programmes"
        }

    /** "4 films, 2 series" (a kind with none is left out). */
    val mix: String
        get() = listOfNotNull(
            films.takeIf { it > 0 }?.let { if (it == 1) "1 film" else "$it films" },
            series.takeIf { it > 0 }?.let { if (it == 1) "1 series" else "$it series" }
        ).joinToString(", ")

    /** "6 programmes · 4 films, 2 series". */
    val detail: String get() = if (playlist.slots.isEmpty()) programmes else "$programmes · $mix"

    /** The series that was left part-way: "Corner Shop · S3 E4", or null when nothing is waiting to be resumed. */
    val resume: String?
        get() = playlist.slots.firstNotNullOfOrNull { slot ->
            slot.pending?.let { "${slot.title.name} · ${ChannelGuide.episode(it)}" }
        }
}

object ChannelGuide {
    private const val TERRA = 0xFFE2562B.toInt()
    private const val TEAL = 0xFF2F6E66.toInt()
    private const val SAFFRON = 0xFFF6BF33.toInt()
    private const val OLIVE = 0xFF4D5B2C.toInt()
    private const val RED = 0xFFC2281D.toInt()
    private const val MAROON = 0xFF5D1710.toInt()
    private val COLOURS = intArrayOf(TERRA, TEAL, SAFFRON, OLIVE, RED, MAROON)

    /** Channel 1 is terracotta, 2 teal, 3 saffron, and so on round, with the shape changing each time round too. */
    fun look(number: Int): ChannelLook {
        val i = (number - 1).coerceAtLeast(0)
        val colour = COLOURS[i % COLOURS.size]
        return ChannelLook(colour, (i / COLOURS.size + i) % 3, darkText = colour == SAFFRON)
    }

    fun summaries(playlists: List<BroadcastPlaylist>): List<ChannelSummary> =
        playlists.mapIndexed { index, playlist -> ChannelSummary(index + 1, playlist) }

    /** "S3 E4": an episode in words. */
    fun episode(e: PlaylistEpisode) = "S${e.season} E${e.number}"
}
