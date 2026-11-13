package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

enum class Kind { MOVIE, SHOW }

/** One film or series as the Movies page shows it, wherever it came from. */
data class Title(
    val key: String,
    val kind: Kind,
    val name: String,
    val year: Int?,
    val overview: String?,
    val runtimeMin: Int?,
    val genres: List<String>,
    val age: String?,
    val imdbScore: Double?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbId: String?,
    val tmdbId: String?,
    /** Services it streams on in the UK without renting ("Netflix", "HBO Max"), at most three. */
    val services: List<String> = emptyList()
) {
    /** "2019 · 15 · 1h 41m · Horror, Thriller": only what is known. */
    fun metaLine(): String = listOfNotNull(
        year?.toString(), age?.takeIf { it.isNotBlank() },
        runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" },
        genres.take(3).joinToString(", ").takeIf { it.isNotBlank() }
    ).joinToString("  ·  ")

    /** "Streaming on Netflix and HBO Max", or null when it is not on any service here. */
    fun streamingLine(): String? = services.takeIf { it.isNotEmpty() }?.let { "Streaming on " + it.joinToString(", ").replace(Regex(", ([^,]*)$"), " and $1") }
}

/** A row on the page: a heading and the titles in it. */
data class Shelf(val heading: String, val titles: List<Title>)

/**
 * What to ask JustWatch for, one shelf at a time. [ranked] draws big numbers beside the posters, like a "Top 10".
 * [madeIn] keeps only titles produced in one of those countries ("GB", "IE"). [country] asks in that country's
 * catalogue rather than the viewer's: a national broadcaster is only listed at home.
 */
data class ShelfSpec(
    val heading: String, val packages: List<String>, val kinds: List<Kind>,
    val genre: String? = null, val sort: String = "TRENDING", val ranked: Boolean = false, val query: String? = null,
    val madeIn: List<String> = emptyList(), val country: String? = null
)

/**
 * A streaming service, with the short name JustWatch uses for it and its app icon on JustWatch's image host.
 * A national broadcaster also has the one [country] it runs in (its shelves are asked for there, wherever the viewer
 * is), the countries whose productions are its home-grown titles ([madeIn]) and what a heading calls those
 * ([homeName], "British & Irish"). [sparse] marks one JustWatch lists only a handful of titles for: its home-grown
 * rows then draw on everything listed in its country, not just the service.
 */
data class Service(
    val code: String, val label: String, val icon: String? = null,
    val country: String? = null, val madeIn: List<String> = emptyList(), val homeName: String = "", val sparse: Boolean = false
)

data class Genre(val code: String, val label: String)

/**
 * The plain-Kotlin half of the catalogue: how a shelf is requested from JustWatch's public GraphQL endpoint and how
 * its answer is read. JustWatch needs no sign-in and already knows IMDb and TMDB ids, which is what links a title on
 * a streaming service to the same film in the owner's own library.
 */
object Catalog {
    /** Two-letter country whose streaming catalogue is shown. Set from the owner's settings when the app starts. */
    @Volatile var country: String = "US"

    val NETFLIX = Service("nfx", "Netflix", "/icon/207360008/s166/netflix.png")
    val HBO_MAX = Service("mxx", "HBO Max", "/icon/332884837/s166/max.png")
    val PARAMOUNT = Service("pmp", "Paramount+", "/icon/242706661/s166/paramountplus.png")
    val PRIME = Service("amp", "Prime Video", "/icon/322992749/s166/amazonprime.png")
    val DISNEY = Service("dnp", "Disney+", "/icon/313118777/s166/disneyplus.png")
    val APPLE = Service("atp", "Apple TV+", "/icon/338367329/s166/appletvplus.png")
    /** The services found in most countries. The mixed rows (Top 10, Trending, the genres) draw on these. */
    val STREAMERS = listOf(NETFLIX, HBO_MAX, PARAMOUNT, PRIME, DISNEY, APPLE)

    private val BRITAIN = listOf("GB", "IE")
    val BBC = Service("bbc", "BBC iPlayer", "/icon/694553/s166/bbc.png", "GB", BRITAIN, "British & Irish")
    val ITVX = Service("itv", "ITVX", "/icon/302120301/s166/itv.png", "GB", BRITAIN, "British & Irish")
    val CHANNEL_4 = Service("al4", "Channel 4", "/icon/306402519/s166/all4.png", "GB", BRITAIN, "British & Irish")
    val CHANNEL_5 = Service("myf", "Channel 5", "/icon/331955847/s166/my5.png", "GB", BRITAIN, "British & Irish")
    val NOW = Service("ntv", "NOW", "/icon/243753092/s166/nowtv.png", "GB", BRITAIN, "British & Irish")
    val RTE = Service("rte", "RTÉ Player", "/icon/344165708/s166/rteplayer.png", "IE", listOf("IE"), "Irish", sparse = true)
    /** The British and Irish broadcasters. Each has a card and a page of its own; the mixed rows leave them out. */
    val BROADCASTERS = listOf(BBC, ITVX, CHANNEL_4, CHANNEL_5, NOW, RTE)

    /** The services the app has its own definition of: the six streamers and the British and Irish broadcasters. */
    val KNOWN = STREAMERS + BROADCASTERS

    /**
     * The services the owner follows, in the order the cards are shown: the original six until they choose (see
     * [FollowedServices]). Set from their settings when the app starts and whenever they follow or unfollow one.
     */
    @Volatile var followed: List<Service> = STREAMERS

    /** Every service with a "Browse by service" card. */
    val SERVICES: List<Service> get() = followed

    /**
     * The followed services the mixed rows (Top 10, Trending, the genres) draw on: those that run in the viewer's own
     * country, so one query can name them together. A national broadcaster has pages of its own and stays out.
     */
    private val MIXED: List<Service> get() = followed.filter { it.country == null && it.madeIn.isEmpty() }.ifEmpty { STREAMERS }
    private val MAJOR: List<String> get() = MIXED.map { it.code }

    val GENRES = listOf(
        Genre("act", "Action"), Genre("cmy", "Comedy"), Genre("crm", "Crime"), Genre("drm", "Drama"), Genre("hrr", "Horror"),
        Genre("scf", "Sci-Fi"), Genre("trl", "Thriller"), Genre("ani", "Animation"), Genre("rma", "Romance"),
        Genre("fnt", "Fantasy"), Genre("fml", "Family"), Genre("hst", "History")
    )

    private val BOTH = listOf(Kind.MOVIE, Kind.SHOW)
    private val FILMS = listOf(Kind.MOVIE)
    private val SERIES = listOf(Kind.SHOW)

    private fun genreRows(kinds: List<Kind>, noun: String, genres: List<Genre> = GENRES, packages: List<String> = MAJOR, sort: String = "POPULAR", country: String? = null) =
        genres.map { ShelfSpec(if (noun.isEmpty()) it.label else "${it.label} $noun", packages, kinds, genre = it.code, sort = sort, country = country) }

    /** Rows named for single services stop here: more would make Home endless. */
    private const val MAX_SERVICE_ROWS = 8

    /** Home: what is trending, the services side by side, and documentaries. */
    fun homePage(): List<ShelfSpec> = listOf(
        ShelfSpec("Top 10 today", MAJOR, BOTH, ranked = true),
        ShelfSpec("Trending films", MAJOR, FILMS),
        ShelfSpec("Trending series", MAJOR, SERIES),
        ShelfSpec("Documentaries", MAJOR, BOTH, genre = "doc")
    ) + followed.take(MAX_SERVICE_ROWS).map { popularOn(it) }

    private fun popularOn(service: Service) = ShelfSpec("Popular on ${service.label}", listOf(service.code), BOTH, sort = "POPULAR", country = service.country)

    /**
     * The shelf a service's card fans its three posters from. Always one that is asked for anyway (the service's Home
     * row, or the row that leads its own page), so the card costs no fetch of its own.
     */
    fun cardShelf(service: Service): ShelfSpec = if (service.madeIn.isEmpty()) popularOn(service) else servicePage(service).first { !it.ranked }

    fun filmsPage(): List<ShelfSpec> = listOf(ShelfSpec("Top 10 films", MAJOR, FILMS, ranked = true)) + genreRows(FILMS, "films")

    fun seriesPage(): List<ShelfSpec> = listOf(ShelfSpec("Top 10 series", MAJOR, SERIES, ranked = true)) + genreRows(SERIES, "series")

    fun documentariesPage(): List<ShelfSpec> = listOf(
        ShelfSpec("Top 10 documentaries", MAJOR, BOTH, genre = "doc", ranked = true),
        ShelfSpec("Documentary films", MAJOR, FILMS, genre = "doc", sort = "POPULAR"),
        ShelfSpec("Documentary series", MAJOR, SERIES, genre = "doc", sort = "POPULAR")
    ) + MIXED.take(4).map { ShelfSpec("Documentaries on ${it.label}", listOf(it.code), BOTH, genre = "doc", sort = "POPULAR") }

    /** Everything on one service: trending, then films, series, documentaries and a few genres. */
    fun servicePage(service: Service): List<ShelfSpec> {
        if (service.madeIn.isNotEmpty()) return broadcasterPage(service)
        val only = listOf(service.code); val at = service.country
        return listOf(
            ShelfSpec("Top 10 on ${service.label}", only, BOTH, ranked = true, country = at),
            ShelfSpec("Films", only, FILMS, sort = "POPULAR", country = at),
            ShelfSpec("Series", only, SERIES, sort = "POPULAR", country = at),
            ShelfSpec("Documentaries", only, BOTH, genre = "doc", sort = "POPULAR", country = at)
        ) + genreRows(BOTH, "", genres = GENRES.filter { it.code in listOf("act", "cmy", "drm", "trl", "hrr", "scf", "crm") }, packages = only, country = at)
    }

    /** What a heading calls each genre a broadcaster's page has a home-grown row for: "British & Irish comedy". */
    private val HOME_GENRES = listOf("cmy" to "comedy", "drm" to "drama", "crm" to "crime", "trl" to "thrillers")

    /**
     * A national broadcaster's page. These carry a great many American imports, so the page leads with what was made
     * at home (series, films, documentaries, a few genres) and ends with everything on the service. A [Service.sparse]
     * one has too few titles listed to fill rows, so its home-grown rows come from every service in its country, under
     * a Top 10 of the same, and a single row says what the service itself has.
     */
    private fun broadcasterPage(service: Service): List<ShelfSpec> {
        val only = listOf(service.code); val at = service.country; val home = service.homeName
        val from = if (service.sparse) emptyList() else only
        fun grown(heading: String, kinds: List<Kind>, genre: String? = null) =
            ShelfSpec(heading, from, kinds, genre = genre, sort = "POPULAR", madeIn = service.madeIn, country = at)
        val rows = listOf(grown("$home series", SERIES), grown("$home films", FILMS), grown("$home documentaries", BOTH, "doc")) +
            HOME_GENRES.map { (code, noun) -> grown("$home $noun", BOTH, code) }
        return if (service.sparse) listOf(
            ShelfSpec("Top 10 $home titles", emptyList(), BOTH, ranked = true, madeIn = service.madeIn, country = at),
            ShelfSpec("On ${service.label}", only, BOTH, sort = "POPULAR", country = at)
        ) + rows else listOf(ShelfSpec("Top 10 on ${service.label}", only, BOTH, ranked = true, country = at)) + rows + listOf(
            ShelfSpec("All series", only, SERIES, sort = "POPULAR", country = at),
            ShelfSpec("All films", only, FILMS, sort = "POPULAR", country = at)
        )
    }

    /** One genre across all the services, then service by service. */
    fun genrePage(genre: Genre): List<ShelfSpec> = listOf(
        ShelfSpec("Trending in ${genre.label}", MAJOR, BOTH, genre = genre.code, ranked = true),
        ShelfSpec("${genre.label} films", MAJOR, FILMS, genre = genre.code, sort = "POPULAR"),
        ShelfSpec("${genre.label} series", MAJOR, SERIES, genre = genre.code, sort = "POPULAR")
    ) + MIXED.take(MAX_SERVICE_ROWS).map { ShelfSpec("${genre.label} on ${it.label}", listOf(it.code), BOTH, genre = genre.code, sort = "POPULAR") }

    /** Search needs no service filter: it finds anything JustWatch knows, streaming here or not. */
    fun searchSpecs(query: String): List<ShelfSpec> = listOf(
        ShelfSpec("Films", emptyList(), FILMS, sort = "POPULAR", query = query),
        ShelfSpec("Series", emptyList(), SERIES, sort = "POPULAR", query = query)
    )

    private const val IMAGES = "https://images.justwatch.com"

    /** The service's square app icon (166 px), or null when it has none on record. */
    fun logoUrl(service: Service): String? = service.icon?.let { IMAGES + it }

    private const val QUERY = "query(\$f: TitleFilter!, \$c: Country!, \$l: Language!, \$n: Int!) { popularTitles(country: \$c, filter: \$f, first: \$n, sortBy: %SORT%) { edges { node { id objectType " +
        "content(country: \$c, language: \$l) { title originalReleaseYear shortDescription runtime genres { translation(language: \$l) } ageCertification posterUrl " +
        "backdrops(format: JPG, profile: S1440) { backdropUrl } externalIds { imdbId tmdbId } scoring { imdbScore } } " +
        "offers(country: \$c, platform: WEB) { monetizationType package { clearName } } } } } }"

    /** The request body for one shelf. */
    fun requestBody(spec: ShelfSpec, count: Int = 20): String {
        val filter = JSONObject().put("objectTypes", JSONArray(spec.kinds.map { if (it == Kind.MOVIE) "MOVIE" else "SHOW" }))
        if (spec.packages.isNotEmpty()) filter.put("packages", JSONArray(spec.packages))
        spec.genre?.let { filter.put("genres", JSONArray().put(it)) }
        spec.query?.let { filter.put("searchQuery", it) }
        if (spec.madeIn.isNotEmpty()) filter.put("productionCountries", JSONArray(spec.madeIn))
        return JSONObject()
            .put("query", QUERY.replace("%SORT%", spec.sort))
            .put("variables", JSONObject().put("f", filter).put("c", spec.country ?: country).put("l", "en").put("n", count))
            .toString()
    }

    fun poster(path: String?): String? = path?.takeIf { it.isNotBlank() }?.replace("{profile}", "s332")?.replace("{format}", "jpg")?.let { IMAGES + it }

    fun backdrop(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { IMAGES + it }

    /** JustWatch's package names, tidied the way the rest of the app writes them. */
    fun serviceName(clearName: String): String = when {
        clearName.startsWith("Amazon Prime Video") || clearName == "Amazon Video" -> "Prime Video"
        clearName == "Disney Plus" -> "Disney+"
        clearName == "Paramount Plus" -> "Paramount+"
        clearName == "Apple TV" || clearName == "Apple TV Plus" -> "Apple TV+"
        clearName == "5" || clearName == "My5" -> "Channel 5"
        clearName == "Now TV" || clearName.startsWith("Now TV ") -> "NOW"
        clearName == "RTE Player" -> "RTÉ Player"
        clearName.endsWith(" with Ads") -> clearName.removeSuffix(" with Ads")
        else -> clearName
    }

    fun parse(json: String): List<Title> = try {
        val edges = JSONObject(json).getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges")
        (0 until edges.length()).mapNotNull { i ->
            val node = edges.getJSONObject(i).getJSONObject("node")
            val c = node.optJSONObject("content") ?: return@mapNotNull null
            val name = c.optString("title").ifBlank { return@mapNotNull null }
            val ids = c.optJSONObject("externalIds")
            val genres = c.optJSONArray("genres")?.let { g -> (0 until g.length()).mapNotNull { g.getJSONObject(it).optString("translation").ifBlank { null } } }.orEmpty()
            val offers = node.optJSONArray("offers")
            val services = (0 until (offers?.length() ?: 0)).mapNotNull { k ->
                val o = offers!!.getJSONObject(k)
                if (o.optString("monetizationType") in setOf("FLATRATE", "FREE", "ADS", "FLATRATE_AND_BUY")) serviceName(o.getJSONObject("package").optString("clearName")) else null
            }.filter { it.isNotBlank() }.distinct().let { all -> all.filter { n -> all.none { m -> m != n && n.startsWith("$m ") } } }.take(3)
            Title(
                key = node.optString("id"),
                kind = if (node.optString("objectType") == "SHOW") Kind.SHOW else Kind.MOVIE,
                name = name,
                year = c.optInt("originalReleaseYear", 0).takeIf { it > 0 },
                overview = c.optString("shortDescription").ifBlank { null },
                runtimeMin = c.optInt("runtime", 0).takeIf { it > 0 },
                genres = genres,
                age = c.optString("ageCertification").ifBlank { null },
                imdbScore = c.optJSONObject("scoring")?.optDouble("imdbScore", -1.0)?.takeIf { it > 0 },
                posterUrl = poster(c.optString("posterUrl")),
                backdropUrl = backdrop(c.optJSONArray("backdrops")?.optJSONObject(0)?.optString("backdropUrl")),
                imdbId = ids?.optString("imdbId")?.ifBlank { null },
                tmdbId = ids?.optString("tmdbId")?.ifBlank { null },
                services = services
            )
        }
    } catch (_: Exception) { emptyList() }

    /** A film is often on several services; the first listing wins. */
    fun dedupe(titles: List<Title>): List<Title> = titles.distinctBy { it.imdbId ?: it.key }
}
