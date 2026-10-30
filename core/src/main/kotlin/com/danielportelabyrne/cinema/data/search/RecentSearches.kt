package com.danielportelabyrne.cinema.data.search

/** The two search keyboards, each with its own recent searches. */
enum class SearchKeyboard(val key: String) { GLOBAL("recent_searches"), LISTS("recent_list_searches") }

/**
 * The searches the owner ran lately on one keyboard, most recent first. Pure, so it can be tested.
 */
object RecentSearches {
    /** How many are kept. */
    const val MAX = 8

    /** How many the search page shows. */
    const val SHOWN = 5

    /** One search per line: a query never holds a line break. */
    fun parse(saved: String?): List<String> = saved?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /** Saved form of [terms]; null when there are none, so the saved value is cleared. */
    fun encode(terms: List<String>): String? = terms.takeIf { it.isNotEmpty() }?.joinToString("\n")

    /**
     * [terms] with [term] put first. A query still being typed replaces the one before it (one is a start of the other),
     * so typing a title does not leave each of its starts behind. Queries under two letters are not kept.
     */
    fun add(terms: List<String>, term: String, max: Int = MAX): List<String> {
        val t = term.trim()
        if (t.length < 2) return terms
        val latest = terms.firstOrNull()
        val rest = if (latest != null && (latest.startsWith(t, ignoreCase = true) || t.startsWith(latest, ignoreCase = true))) terms.drop(1) else terms
        return (listOf(t) + rest.filterNot { it.equals(t, ignoreCase = true) }).take(max)
    }
}
