package com.danielportelabyrne.cinema.data.letterboxd

/** A Letterboxd username from what was typed, for following someone. Pure, so it can be tested. */
object FollowPage {
    private val USERNAME = Regex("[a-z0-9_-]{1,40}")

    /**
     * The Letterboxd username in what was typed: a bare name, one with an "@", or a letterboxd.com address (its first part
     * is the name). Null when what is left cannot be a username.
     */
    fun normalize(typed: String): String? {
        val withoutAt = typed.trim().removePrefix("@")
        val afterSite = withoutAt.substringAfter("letterboxd.com/", withoutAt)
        return afterSite.substringBefore('/').substringBefore('?').trim().lowercase().takeIf { USERNAME.matches(it) }
    }
}
