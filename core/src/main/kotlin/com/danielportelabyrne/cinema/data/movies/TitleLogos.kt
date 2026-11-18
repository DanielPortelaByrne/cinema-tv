package com.danielportelabyrne.cinema.data.movies

/**
 * Which title gets a logo in place of its name in type, and whether a logo that came back is fit to show.
 * The picture source is the one the large still cards already use (a transparent PNG keyed by IMDb id); nothing
 * new is asked of anyone. A title with no usable logo keeps its name in type, so a title is never blank.
 */
object TitleLogos {
    private const val SOURCE = "https://images.metahub.space/logo/medium/%s/img"
    private val IMDB = Regex("tt\\d{5,10}")

    /** The address of the title's logo, or null when it has no IMDb id to look one up by. */
    fun url(imdbId: String?): String? = imdbId?.trim()?.takeIf { IMDB.matches(it) }?.let { SOURCE.format(it) }

    /**
     * True when a [w] x [h] picture, fitted (never cropped or stretched) inside [boxW] x [boxH], would still be
     * drawn at least [minHeight] tall: a hairline-thin or tiny logo is not legible from the sofa, so the name stays.
     */
    fun usable(w: Int, h: Int, boxW: Int, boxH: Int, minHeight: Int): Boolean {
        if (w <= 0 || h <= 0 || boxW <= 0 || boxH <= 0) return false
        val scale = minOf(boxW.toDouble() / w, boxH.toDouble() / h)
        return h * scale >= minHeight
    }
}
