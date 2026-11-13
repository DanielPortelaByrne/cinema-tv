package com.danielportelabyrne.cinema.data.movies

/**
 * A row built from parts: in what order ([order]), which [kind] of title, a category ([genre]) and a subcategory within
 * it ([sub]), on one service or all the owner's ([service]), and where it was made ([made]). "Top 10 crime documentaries
 * on Netflix", "Popular British comedy series", "Trending music films". Kept in a section's argument as "o=..;k=..".
 */
data class Mix(
    val order: String = "trending", val kind: String = "all", val genre: String? = null, val sub: String? = null,
    val service: String? = null, val made: String? = null
) {
    fun encode(): String = listOfNotNull("o=$order", "k=$kind", genre?.let { "g=$it" }, sub?.let { "s=$it" }, service?.let { "v=$it" }, made?.let { "m=$it" }).joinToString(";")

    companion object {
        fun decode(arg: String): Mix {
            val m = arg.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return Mix(m["o"] ?: "trending", m["k"] ?: "all", m["g"], m["s"], m["v"], m["m"])
        }
    }
}

object Mixes {
    /** Order: what the row is sorted by. Top 10 draws the big numbers. */
    val ORDERS = listOf("trending" to "Trending", "popular" to "Popular", "top10" to "Top 10")
    val KINDS = listOf("all" to "Films & series", "films" to "Films", "series" to "Series")

    /** Every JustWatch genre, by its code: (code, adjective as a heading word, name in a list). */
    val GENRES = listOf(
        Triple("act", "action", "Action & adventure"), Triple("ani", "animated", "Animation"), Triple("cmy", "comedy", "Comedy"),
        Triple("crm", "crime", "Crime"), Triple("doc", "documentary", "Documentary"), Triple("drm", "drama", "Drama"),
        Triple("fml", "family", "Kids & family"), Triple("fnt", "fantasy", "Fantasy"), Triple("hst", "history", "History"),
        Triple("hrr", "horror", "Horror"), Triple("msc", "music", "Music & musicals"), Triple("rly", "reality", "Reality TV"),
        Triple("rma", "romance", "Romance"), Triple("scf", "sci-fi", "Sci-fi"), Triple("spt", "sport", "Sport"),
        Triple("trl", "thriller", "Mystery & thriller"), Triple("war", "war", "War & military"), Triple("wsn", "western", "Western")
    )

    /** Where a title was made: (key, adjective, the production countries asked for). */
    val MADE = listOf(
        Triple("gb", "British & Irish", listOf("GB", "IE")), Triple("us", "American", listOf("US")), Triple("kr", "Korean", listOf("KR")),
        Triple("jp", "Japanese", listOf("JP")), Triple("fr", "French", listOf("FR")), Triple("es", "Spanish", listOf("ES")),
        Triple("nordic", "Nordic", listOf("DK", "SE", "NO", "FI", "IS")), Triple("in", "Indian", listOf("IN")),
        Triple("de", "German", listOf("DE")), Triple("it", "Italian", listOf("IT")), Triple("au", "Australian", listOf("AU"))
    )

    private fun service(code: String?) = code?.let { c -> (Catalog.followed + Catalog.KNOWN).firstOrNull { it.code == c } }

    /** The JustWatch request for [m]: the category is asked for, and the subcategory kept from what comes back. */
    fun spec(m: Mix): ShelfSpec? {
        val svc = service(m.service)
        if (m.service != null && svc == null) return null
        val kinds = when (m.kind) { "films" -> listOf(Kind.MOVIE); "series" -> listOf(Kind.SHOW); else -> listOf(Kind.MOVIE, Kind.SHOW) }
        // A documentary pair is asked as documentaries and kept to the other genre, so the row (and its "More" page) is
        // always one of documentaries, never of crime films that happen to be documentaries too.
        val genres = listOfNotNull(m.genre, m.sub).distinct().sortedBy { if (it == "doc") 0 else 1 }
        val made = MADE.firstOrNull { it.first == m.made }?.third.orEmpty()
        val packages = svc?.let { listOf(it.code) } ?: if (made.isNotEmpty()) emptyList() else Catalog.MAJOR
        return ShelfSpec(label(m), packages, kinds, genre = genres.firstOrNull(), sort = if (m.order == "popular") "POPULAR" else "TRENDING",
            ranked = m.order == "top10", madeIn = made, country = svc?.country, alsoGenres = genres.drop(1))
    }

    /** "Top 10 crime documentaries on Netflix", "Popular British comedy series", "Trending music films". */
    fun label(m: Mix): String {
        val order = ORDERS.firstOrNull { it.first == m.order }?.second ?: "Trending"
        val words = listOfNotNull(m.genre, m.sub).distinct().mapNotNull { c -> GENRES.firstOrNull { it.first == c } }
        val doc = words.any { it.first == "doc" }
        val adjectives = words.filter { it.first != "doc" }.map { it.second }
        val noun = when {
            doc && m.kind == "films" -> "documentary films"
            doc && m.kind == "series" -> "documentary series"
            doc -> "documentaries"
            m.kind == "films" -> "films"
            m.kind == "series" -> "series"
            else -> if (adjectives.isEmpty()) "titles" else "films & series"
        }
        val made = MADE.firstOrNull { it.first == m.made }?.second
        val on = service(m.service)?.let { " on ${it.label}" }.orEmpty()
        val body = (listOfNotNull(made) + adjectives + noun).joinToString(" ")
        // "British" and the like keep their capital; genre words follow "Top 10"/"Trending" in lower case.
        return "$order $body$on".replace("  ", " ")
    }

    /** "Comedy series", "Crime documentaries", "Music films": what a genre page of these parts is called. */
    fun title(genre: String, sub: String?, kind: String): String =
        label(Mix("trending", kind, genre, sub)).removePrefix("Trending ").replaceFirstChar { it.uppercase() }.replace(" films & series", "")

    /**
     * A genre page narrowed to one [kind] ("films", "series") and/or a [sub] genre: its Top 10, what is popular, films
     * and series apart when it is both, then service by service. The rows the "More" card at the end of such a row opens.
     */
    fun page(genre: String, sub: String?, kind: String): List<ShelfSpec> {
        val base = Mix("popular", kind, genre, sub)
        val mixes = mutableListOf(base.copy(order = "top10"), base.copy(order = "trending"), base)
        if (kind == "all") { mixes += base.copy(kind = "films"); mixes += base.copy(kind = "series") }
        mixes += Catalog.MIXED.take(8).map { base.copy(service = it.code) }
        return mixes.mapNotNull { spec(it) }
    }

    /** The mixes offered in the search: every order, kind, category and subcategory together (services are in the builder). */
    fun searchable(): List<Mix> {
        val out = ArrayList<Mix>()
        for ((o, _) in ORDERS) for ((k, _) in KINDS) {
            out += Mix(o, k)
            for (g in GENRES) {
                out += Mix(o, k, g.first)
                for (s in GENRES) if (s.first != g.first && s.first > g.first) out += Mix(o, k, g.first, s.first)
            }
        }
        return out
    }
}
