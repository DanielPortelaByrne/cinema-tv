package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** The pages the owner can lay out row by row. */
enum class LayoutPage(val key: String, val label: String) {
    HOME("home", "Home"), FILMS("films", "Films"), SERIES("series", "Series"), DOCS("docs", "Documentaries"), LIBRARY("library", "My library")
}

/**
 * What a row is. Catalogue rows ([catalogue]) are asked of JustWatch; the others are the app's own rows (what is
 * part-watched, the library, the service cards…). [arg] picks the variant: which service, genre or kind of title.
 */
enum class SectionType(val catalogue: Boolean) {
    TOP10(true), TRENDING(true), GENRE(true), DOCS(true), POPULAR_ON(true), DOCS_ON_FOLLOWED(true),
    CONTINUE(false), FAVOURITES(false), RECENT(false), PICKS(false), SERVICES(false), CHANNELS(false), GENRE_TILES(false),
    MY_LIBRARY(false), WATCHLIST(false), LIBRARY_LATEST(false), LIBRARY_FILMS(false), LIBRARY_SERIES(false), LIBRARY_GENRES(false)
}

/** One row on a page. Two sections are the same row when their type and argument match. */
data class Section(val type: SectionType, val arg: String = "") {
    val id: String get() = if (arg.isEmpty()) type.name else "${type.name}|$arg"

    companion object {
        fun fromId(id: String): Section? {
            val type = runCatching { SectionType.valueOf(id.substringBefore('|')) }.getOrNull() ?: return null
            return Section(type, if ('|' in id) id.substringAfter('|') else "")
        }
    }
}

/** A row the owner can add, with the words it is found by. */
data class SectionOption(val section: Section, val label: String, val detail: String)

object PageLayouts {
    private val BOTH = listOf(Kind.MOVIE, Kind.SHOW)
    private val FILMS = listOf(Kind.MOVIE)
    private val SERIES = listOf(Kind.SHOW)

    private fun kinds(arg: String) = when (arg) { "films" -> FILMS; "series" -> SERIES; else -> BOTH }
    private fun noun(arg: String) = when (arg) { "films" -> "films"; "series" -> "series"; else -> "" }
    private fun service(code: String) = (Catalog.KNOWN + Catalog.followed).firstOrNull { it.code == code }
    private fun genre(code: String) = Catalog.GENRES.firstOrNull { it.code == code }

    private val PERSONAL = listOf(Section(SectionType.CONTINUE), Section(SectionType.FAVOURITES), Section(SectionType.RECENT))

    /** Each page as the app lays it out until the owner changes it. */
    fun defaults(page: LayoutPage): List<Section> = when (page) {
        LayoutPage.HOME -> listOf(Section(SectionType.TOP10, "all")) + PERSONAL + listOf(
            Section(SectionType.TRENDING, "films"), Section(SectionType.PICKS), Section(SectionType.TRENDING, "series"),
            Section(SectionType.SERVICES), Section(SectionType.CHANNELS), Section(SectionType.DOCS, "all"),
            Section(SectionType.MY_LIBRARY), Section(SectionType.GENRE_TILES), Section(SectionType.POPULAR_ON, "followed"))
        LayoutPage.FILMS -> listOf(Section(SectionType.GENRE, "act:films")) + PERSONAL + listOf(Section(SectionType.TOP10, "films")) +
            Catalog.GENRES.drop(1).map { Section(SectionType.GENRE, "${it.code}:films") }
        LayoutPage.SERIES -> listOf(Section(SectionType.GENRE, "act:series")) + PERSONAL + listOf(Section(SectionType.TOP10, "series")) +
            Catalog.GENRES.drop(1).map { Section(SectionType.GENRE, "${it.code}:series") }
        LayoutPage.DOCS -> listOf(Section(SectionType.DOCS, "films")) + PERSONAL + listOf(
            Section(SectionType.TOP10, "docs"), Section(SectionType.DOCS, "series"), Section(SectionType.DOCS_ON_FOLLOWED))
        LayoutPage.LIBRARY -> PERSONAL + listOf(Section(SectionType.WATCHLIST), Section(SectionType.LIBRARY_LATEST),
            Section(SectionType.LIBRARY_FILMS), Section(SectionType.LIBRARY_SERIES), Section(SectionType.LIBRARY_GENRES))
    }

    /** The catalogue rows a section asks for: none for the app's own rows, or for a service or genre no longer known. */
    fun specs(s: Section): List<ShelfSpec> = when (s.type) {
        SectionType.TOP10 -> listOfNotNull(when {
            s.arg == "all" -> ShelfSpec("Top 10 today", Catalog.MAJOR, BOTH, ranked = true)
            s.arg == "films" -> ShelfSpec("Top 10 films", Catalog.MAJOR, FILMS, ranked = true)
            s.arg == "series" -> ShelfSpec("Top 10 series", Catalog.MAJOR, SERIES, ranked = true)
            s.arg == "docs" -> ShelfSpec("Top 10 documentaries", Catalog.MAJOR, BOTH, genre = "doc", ranked = true)
            s.arg.startsWith("svc:") -> service(s.arg.removePrefix("svc:"))?.let { ShelfSpec("Top 10 on ${it.label}", listOf(it.code), BOTH, ranked = true, country = it.country) }
            s.arg.startsWith("genre:") -> genre(s.arg.removePrefix("genre:"))?.let { ShelfSpec("Top 10 in ${it.label}", Catalog.MAJOR, BOTH, genre = it.code, ranked = true) }
            else -> null
        })
        SectionType.TRENDING -> listOf(ShelfSpec(when (s.arg) { "films" -> "Trending films"; "series" -> "Trending series"; else -> "Trending now" }, Catalog.MAJOR, kinds(s.arg)))
        SectionType.GENRE -> {
            val g = genre(s.arg.substringBefore(':')); val k = s.arg.substringAfter(':', "all")
            listOfNotNull(g?.let { ShelfSpec(listOf(it.label, noun(k)).filter { w -> w.isNotEmpty() }.joinToString(" "), Catalog.MAJOR, kinds(k), genre = it.code, sort = "POPULAR") })
        }
        SectionType.DOCS -> listOf(ShelfSpec(when (s.arg) { "films" -> "Documentary films"; "series" -> "Documentary series"; else -> "Documentaries" },
            Catalog.MAJOR, kinds(s.arg), genre = "doc", sort = if (s.arg == "all") "TRENDING" else "POPULAR"))
        SectionType.POPULAR_ON -> if (s.arg == "followed") Catalog.followed.take(8).map { popularOn(it) } else listOfNotNull(service(s.arg)?.let { popularOn(it) })
        SectionType.DOCS_ON_FOLLOWED -> Catalog.MIXED.take(4).map { ShelfSpec("Documentaries on ${it.label}", listOf(it.code), BOTH, genre = "doc", sort = "POPULAR") }
        else -> emptyList()
    }

    private fun popularOn(service: Service) = ShelfSpec("Popular on ${service.label}", listOf(service.code), BOTH, sort = "POPULAR", country = service.country)

    /** What a section is called in the editor. */
    fun label(s: Section): String = when (s.type) {
        SectionType.CONTINUE -> "Continue watching"
        SectionType.FAVOURITES -> "Your favourites"
        SectionType.RECENT -> "Recently watched"
        SectionType.PICKS -> "Picked for you"
        SectionType.SERVICES -> "Browse by service"
        SectionType.CHANNELS -> "Browse by channel"
        SectionType.GENRE_TILES -> "Browse by genre"
        SectionType.MY_LIBRARY -> "My library"
        SectionType.WATCHLIST -> "Watchlist"
        SectionType.LIBRARY_LATEST -> "Library: latest added"
        SectionType.LIBRARY_FILMS -> "Library: films"
        SectionType.LIBRARY_SERIES -> "Library: series"
        SectionType.LIBRARY_GENRES -> "Library: by genre"
        SectionType.POPULAR_ON -> if (s.arg == "followed") "Popular on each service you follow" else specs(s).firstOrNull()?.heading ?: "Popular on a service"
        SectionType.DOCS_ON_FOLLOWED -> "Documentaries on each service you follow"
        else -> specs(s).firstOrNull()?.heading ?: s.id
    }

    private fun detail(s: Section): String = when {
        s.type.name.startsWith("LIBRARY") || s.type == SectionType.MY_LIBRARY || s.type == SectionType.WATCHLIST -> "Your library"
        !s.type.catalogue -> "Your rows"
        s.type == SectionType.TOP10 -> "Top 10"
        else -> "Catalogue"
    }

    /** Every row that can be added, Top 10s first. */
    fun options(): List<SectionOption> {
        val list = ArrayList<Section>()
        listOf("all", "films", "series", "docs").forEach { list += Section(SectionType.TOP10, it) }
        (Catalog.followed + Catalog.KNOWN).distinctBy { it.code }.forEach { list += Section(SectionType.TOP10, "svc:${it.code}") }
        Catalog.GENRES.forEach { list += Section(SectionType.TOP10, "genre:${it.code}") }
        listOf("all", "films", "series").forEach { list += Section(SectionType.TRENDING, it) }
        listOf(SectionType.CONTINUE, SectionType.FAVOURITES, SectionType.RECENT, SectionType.PICKS, SectionType.MY_LIBRARY, SectionType.WATCHLIST,
            SectionType.LIBRARY_LATEST, SectionType.LIBRARY_FILMS, SectionType.LIBRARY_SERIES, SectionType.LIBRARY_GENRES,
            SectionType.SERVICES, SectionType.CHANNELS, SectionType.GENRE_TILES).forEach { list += Section(it) }
        list += Section(SectionType.POPULAR_ON, "followed")
        (Catalog.followed + Catalog.KNOWN).distinctBy { it.code }.forEach { list += Section(SectionType.POPULAR_ON, it.code) }
        listOf("all", "films", "series").forEach { list += Section(SectionType.DOCS, it) }
        list += Section(SectionType.DOCS_ON_FOLLOWED)
        Catalog.GENRES.forEach { g -> listOf("all", "films", "series").forEach { list += Section(SectionType.GENRE, "${g.code}:$it") } }
        return list.map { SectionOption(it, label(it), detail(it)) }
    }

    /** The options whose name has every word of [query] (any order), leaving out rows the page already has. */
    fun search(query: String, have: List<Section>): List<SectionOption> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        val taken = have.map { it.id }.toSet()
        return options().filter { it.section.id !in taken }.filter { o ->
            val text = (o.label + " " + o.detail).lowercase().replace("top 10", "top 10 top10 ten")
            words.all { it in text }
        }
    }

    // --- Kept as one JSON object: page key to its list of section ids ---------------------------------------------

    fun parse(json: String?): Map<LayoutPage, List<Section>> = try {
        val o = JSONObject(json ?: "{}")
        LayoutPage.values().mapNotNull { p ->
            val a = o.optJSONArray(p.key) ?: return@mapNotNull null
            p to (0 until a.length()).mapNotNull { Section.fromId(a.optString(it)) }.distinctBy { it.id }
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    fun encode(layouts: Map<LayoutPage, List<Section>>): String {
        val o = JSONObject()
        layouts.forEach { (p, list) -> o.put(p.key, JSONArray(list.map { it.id })) }
        return o.toString()
    }

    /** The page as the owner left it, or its default. */
    fun of(page: LayoutPage, saved: Map<LayoutPage, List<Section>>): List<Section> = saved[page] ?: defaults(page)
}
