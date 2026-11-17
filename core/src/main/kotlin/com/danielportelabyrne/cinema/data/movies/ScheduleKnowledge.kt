package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import java.text.Normalizer

/**
 * What classic network television ran together: for each show, the seasons it was on air, and for pairs of shows how
 * many seasons they ran directly after one another, or on the same night of the same network. Built by
 * `scripts/schedules/build_dataset.py` from Wikipedia's season-by-season schedule pages (US networks from 1946, Canadian
 * networks 2003 to 2023) and bundled as a resource; the text of Wikipedia is CC BY-SA 4.0, so the data is too.
 *
 * Shows are found by [normalise]d name, so "The Cosby Show", "Cosby Show (TV series)" and "COSBY SHOW" are one show.
 */
class ScheduleKnowledge private constructor(
    /** How many season pages the data came from. */
    val seasons: Int,
    private val shows: List<Show>,
    private val byName: Map<String, Int>,
    private val adjacent: Map<Long, Int>,
    private val sameNight: Map<Long, Int>
) {
    /** A show and the first and last season (by starting year) it was seen in; [seasons] is how many it was seen in. */
    data class Show(val name: String, val firstYear: Int, val lastYear: Int, val seasons: Int)

    /** How many seasons two shows ran [adjacent] (directly after one another) and on the [sameNight]. */
    data class Together(val adjacent: Int, val sameNight: Int) {
        val none: Boolean get() = adjacent == 0 && sameNight == 0
    }

    val showCount: Int get() = shows.size

    /** The show called [name] in the data, or null when it never ran on a network the data covers. */
    fun find(name: String): Show? = byName[normalise(name)]?.let { shows[it] }

    fun together(a: String, b: String): Together {
        val i = byName[normalise(a)] ?: return NEVER
        val j = byName[normalise(b)] ?: return NEVER
        if (i == j) return NEVER
        val key = pair(i, j)
        return Together(adjacent[key] ?: 0, sameNight[key] ?: 0)
    }

    /** How many of [names] the data knows. */
    fun known(names: Iterable<String>): Int = names.count { find(it) != null }

    companion object {
        private val NEVER = Together(0, 0)
        private const val RESOURCE = "/cinema/schedules/classic-schedules.json"

        /**
         * A name as the data keys it: no accents, nothing in brackets, "&" as "and", letters and digits only, lower
         * case, no leading "the". Must stay the same as `normalise` in the build script.
         */
        fun normalise(name: String): String {
            val plain = Normalizer.normalize(name, Normalizer.Form.NFKD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            val s = plain.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ").lowercase().replace("&", " and ")
                .replace(Regex("[^a-z0-9]+"), " ").trim()
            return s.removePrefix("the ")
        }

        private fun pair(i: Int, j: Int): Long = if (i < j) (i.toLong() shl 32) or j.toLong() else (j.toLong() shl 32) or i.toLong()

        /** The data in [json] (the build script's output), or null when it is not that. */
        fun parse(json: String): ScheduleKnowledge? = try {
            val o = JSONObject(json)
            require(o.optInt("v") == 1) { "unknown schedule data version" }
            val showArray = o.getJSONArray("shows")
            val shows = (0 until showArray.length()).map { i ->
                val s = showArray.getJSONArray(i)
                Show(s.getString(0), s.getInt(1), s.getInt(2), s.getInt(3))
            }
            val byName = HashMap<String, Int>(shows.size * 2)
            shows.forEachIndexed { i, s -> byName.putIfAbsent(normalise(s.name), i) }
            fun edges(key: String): Map<Long, Int> {
                val a = o.getJSONArray(key)
                val m = HashMap<Long, Int>(a.length() * 2)
                for (k in 0 until a.length()) { val e = a.getJSONArray(k); m[pair(e.getInt(0), e.getInt(1))] = e.getInt(2) }
                return m
            }
            ScheduleKnowledge(o.optInt("seasons"), shows, byName, edges("adjacent"), edges("sameNight"))
        } catch (_: Exception) { null }

        @Volatile private var loaded: ScheduleKnowledge? = null
        @Volatile private var loading = false

        /** The bundled data, read now (a fraction of a second: not on the main thread), or null if the resource is missing. */
        fun bundled(): ScheduleKnowledge? {
            loaded?.let { return it }
            return synchronized(this) {
                loaded ?: ScheduleKnowledge::class.java.getResourceAsStream(RESOURCE)?.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
                    ?.also { loaded = it }
            }
        }

        /** The bundled data if it has been read already, and never waits. */
        fun bundledIfReady(): ScheduleKnowledge? = loaded

        /** Starts reading the bundled data on another thread, so [bundledIfReady] has it by the time a channel is shown. */
        fun warm() {
            if (loaded != null || loading) return
            loading = true
            Thread({ try { bundled() } finally { loading = false } }, "schedule-data").apply { isDaemon = true }.start()
        }
    }
}
