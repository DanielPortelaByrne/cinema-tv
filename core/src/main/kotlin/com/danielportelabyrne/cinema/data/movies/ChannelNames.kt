package com.danielportelabyrne.cinema.data.movies

import java.text.Normalizer
import java.util.Locale

/**
 * What people call the television networks a viewer in the UK or Ireland looks for, so that a search for "disney" or
 * "tinypop" also asks Wikidata for "Disney Channel" and "Tiny Pop" by name. Names only, no ids: Wikidata still decides
 * what exists, and a network is still only offered when it has shows to browse. Pure.
 */
object ChannelNames {
    /** A network's names: the first is what it is called, the rest are what people type for it. */
    private val FAMILIES: List<List<String>> = listOf(
        listOf("Pop", "pop tv"),
        listOf("Pop Girl", "popgirl"),
        listOf("Tiny Pop", "tinypop", "tiny pop"),
        listOf("CITV", "children's itv", "childrens itv"),
        listOf("CBBC", "children's bbc", "childrens bbc"),
        listOf("CBeebies", "cbeebies", "bbc cbeebies"),
        listOf("Disney Channel", "disney ch"),
        listOf("Disney XD", "disneyxd"),
        listOf("Disney Junior", "disney jr", "playhouse disney"),
        listOf("Playhouse Disney"),
        listOf("Nickelodeon", "nick"),
        listOf("Nick Jr.", "nick jr", "nickjr"),
        listOf("Nicktoons", "nick toons"),
        listOf("Cartoon Network", "cartoon net", "cn"),
        listOf("Boomerang"),
        listOf("Jetix"),
        listOf("Fox Kids"),
        listOf("RTÉ2", "rte2", "rte 2", "network 2"),
        listOf("RTÉ One", "rte one", "rte1", "rte 1"),
        listOf("RTÉjr", "rtejr", "rte jr"),
        listOf("TG4"),
        listOf("Virgin Media One", "tv3")
    )

    /** At most this many networks are searched for one query: Wikidata is asked once for each. */
    const val MAX = 6

    private fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT).replace("+", " plus ").replace(Regex("[^a-z0-9]+"), "")

    /** How well a channel called [name] answers [query]: its own name first, then what people call the network (75), 0 for not at all. */
    fun score(name: String, query: String): Int {
        val own = ServiceDirectory.score(name, query)
        if (own > 0) return own
        val flat = fold(name)
        return if (expand(query).any { fold(it) == flat }) 75 else 0
    }

    /**
     * The names to search Wikidata for, besides what was typed: every network the query is the start of (by name, by an
     * alias or by a word of the name, spaces and punctuation ignored), exact matches first. Empty for a short query.
     */
    fun expand(query: String): List<String> {
        val q = fold(query)
        if (q.length < 2) return emptyList()
        val hits = FAMILIES.mapNotNull { names ->
            val keys = names.map(::fold)
            val words = names.first().split(' ').map(::fold)
            val rank = when {
                keys.any { it == q } -> 0
                keys.any { it.startsWith(q) } -> 1
                q.length >= 3 && words.any { it.startsWith(q) } -> 2
                else -> return@mapNotNull null
            }
            rank to names.first()
        }
        return hits.sortedBy { it.first }.map { it.second }.distinct().take(MAX)
    }
}
