package com.danielportelabyrne.cinema.data

/** A trailing season and episode on a title ("Ruby Gloom · S1 E4", "Ruby Gloom S01E04 - Missing Buns"). */
private val EPISODE_SUFFIX = Regex("""\s*[·•:\-–—]?\s*S\d{1,3}\s*E\d{1,4}\b.*$""", RegexOption.IGNORE_CASE)

/** What a title is called with its episode taken off, lower-cased: the thing Continue watching shows once. */
internal fun seriesKey(title: String): String = title.replace(EPISODE_SUFFIX, "").trim().lowercase()

/**
 * Continue watching shows each title once: for a series that is the episode watched most recently, not every episode that
 * was left part-way. Newest first. A stream saves its title with the episode on the end ("Ruby Gloom · S1 E4") while the
 * library entry is just "Ruby Gloom", so titles are compared with the episode taken off; a stream and the library copy of
 * the same series then count as one.
 */
fun <T> latestPerTitle(items: List<T>, title: (T) -> String, lastMs: (T) -> Long): List<T> =
    items.sortedByDescending(lastMs).distinctBy { seriesKey(title(it)) }
