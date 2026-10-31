package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.data.FilmPage
import com.danielportelabyrne.cinema.data.LetterboxdParser

/** A film as a Letterboxd page lists it. [rating] is in half stars (1..10), as Letterboxd stores it. */
data class LbFilm(val slug: String, val name: String, val year: Int?, val rating: Int? = null, val liked: Boolean = false) : java.io.Serializable {
    val stars: String? get() = rating?.let { Lb.stars(it) }
}

/** One diary line: what was watched, on which day ("2026-10-16"), and how it was rated. */
data class LbDiaryEntry(val film: LbFilm, val date: String?, val rating: Int?, val liked: Boolean, val rewatch: Boolean) : java.io.Serializable

/** A review, by [author] (the username) shown as [authorName]. [likes] is how many people liked it. */
data class LbReview(
    val film: LbFilm?, val author: String, val authorName: String, val avatar: String?, val rating: Int?, val liked: Boolean,
    val text: String, val date: String?, val likes: Int?
) : java.io.Serializable

/** A person on a film's page: where their Letterboxd page is ([path], "/actor/al-pacino/"), their name and, for the cast, the character. */
data class LbPerson(val path: String, val name: String, val role: String?) : java.io.Serializable

/** One crew role (Director, Writer…) and who did it. */
data class LbCrew(val role: String, val people: List<LbPerson>) : java.io.Serializable

/** A film's cast and crew, from the Cast and Crew tabs of its Letterboxd page. */
data class LbCredits(val cast: List<LbPerson>, val crew: List<LbCrew>) : java.io.Serializable

/** A list: where it lives ([path], "/someone/list/2026/"), whose it is, how big, and the first few posters. */
data class LbList(
    val path: String, val name: String, val owner: String, val ownerName: String?, val count: Int?, val likes: String?,
    val description: String?, val previews: List<LbFilm>
) : java.io.Serializable

/** A member's profile page: name, picture, numbers, four favourites and what they watched last. */
data class LbProfile(
    val user: String, val name: String, val avatar: String?, val bio: String?, val stats: List<Pair<String, String>>,
    val favourites: List<LbFilm>, val recent: List<LbFilm>
) : java.io.Serializable {
    fun stat(label: String): String? = stats.firstOrNull { it.second.equals(label, ignoreCase = true) }?.first
}

/** A film's Letterboxd page, as the film page and the paused screen use it. [histogram] counts ratings from ½ to ★★★★★. */
data class LbFilmInfo(
    val slug: String, val average: Double?, val ratings: Long?, val reviews: Long?, val tmdbId: Long?, val isTv: Boolean,
    val backdrop: String?, val page: FilmPage, val histogram: List<Long> = emptyList(), val imdb: String? = null
)

/** What one member has logged for one film: their rating, whether they liked it, their review, and whether they watched it at all. */
data class LbMemberFilm(val user: String, val rating: Int?, val liked: Boolean, val review: String?, val watched: Boolean = true) : java.io.Serializable

/** Someone a member follows, for suggestions on the "follow someone" page. */
data class LbMember(val user: String, val name: String, val avatar: String?, val films: String?) : java.io.Serializable

/**
 * Reads Letterboxd's public pages. Pure, so it can be tested; every reader is forgiving and returns what it can find.
 * The pages are built around `LazyPoster` placeholders, which carry each film's slug and name; a film's rating and
 * like sit just after its placeholder.
 */
object Lb {
    private val patterns = java.util.concurrent.ConcurrentHashMap<Pair<String, Set<RegexOption>>, Regex>()
    private fun regex(pattern: String, options: Set<RegexOption> = emptySet()): Regex =
        patterns.getOrPut(pattern to options) { Regex(pattern, options) }
    private val dot = setOf(RegexOption.DOT_MATCHES_ALL)
    private const val POSTER = "data-component-class=\"LazyPoster\""

    fun stars(halves: Int): String = "★".repeat(halves / 2) + if (halves % 2 == 1) "½" else ""

    /** "★★★½" (or an aria-label holding it) as half stars. */
    fun halves(stars: String): Int? = stars.trim().takeIf { it.isNotEmpty() && it.all { c -> c == '★' || c == '½' } }
        ?.let { s -> s.count { it == '★' } * 2 + s.count { it == '½' } }?.takeIf { it in 1..10 }

    fun text(raw: String): String = LetterboxdParser.clean(raw)

    private fun attr(chunk: String, name: String) = regex("$name=\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)?.let { text(it) }

    /** "Hundreds of Beavers (2022)" -> ("Hundreds of Beavers", 2022). */
    fun nameAndYear(full: String): Pair<String, Int?> {
        val m = regex("""^(.*?)\s*\((\d{4})\)\s*$""").find(full) ?: return full to null
        return m.groupValues[1] to m.groupValues[2].toInt()
    }

    /** Every poster in [html], in order, each with the rating and like that follow it. Duplicates are kept once. */
    fun films(html: String): List<LbFilm> {
        val starts = regex(Regex.escape(POSTER)).findAll(html).map { it.range.first }.toList()
        return starts.mapIndexedNotNull { i, at ->
            val chunk = html.substring(at, if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, at + 4000))
            val slug = attr(chunk, "data-item-slug") ?: return@mapIndexedNotNull null
            val (name, year) = nameAndYear(attr(chunk, "data-item-name") ?: slug)
            val rating = regex("""rated-(\d+)""").find(chunk)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..10 }
            LbFilm(slug, name, year, rating, chunk.contains("icon-liked") || chunk.contains("inline-liked"))
        }.distinctBy { it.slug }
    }

    /** The part of a profile page between `id="name"` and the next section. */
    private fun section(html: String, id: String): String {
        val at = html.indexOf("id=\"$id\"").takeIf { it >= 0 } ?: return ""
        val end = html.indexOf("<section", at + 10).takeIf { it > 0 } ?: html.length
        return html.substring(at, end)
    }

    fun profile(html: String, user: String): LbProfile? {
        val header = html.substringAfter("profile-header", "").takeIf { it.isNotEmpty() } ?: return null
        val name = regex("""class="displayname[^"]*"[^>]*>\s*<span class="label">(.*?)</span>""", dot).find(header)?.groupValues?.get(1)?.let { text(it) } ?: user
        val avatar = regex("""profile-avatar.*?<img src="([^"]+)"""", dot).find(header)?.groupValues?.get(1)?.replace("&amp;", "&")
        val stats = regex("""<span class="value">([^<]+)</span><span class="definition[^"]*">([^<]+)</span>""").findAll(header.take(20_000))
            .map { text(it.groupValues[1]) to text(it.groupValues[2]) }.toList()
        val bio = regex("""js-bio-content[^>]*>(.*?)</div>""", dot).find(html)?.groupValues?.get(1)?.let { text(it) }?.ifBlank { null }
        return LbProfile(user, name, avatar, bio, stats, films(section(html, "favourites")).take(4), films(section(html, "recent-activity")))
    }

    /** Diary rows, newest first. A row only names its month when the month changes, so it is carried over. */
    fun diary(html: String): List<LbDiaryEntry> {
        val rows = html.split("<tr class=\"diary-entry-row").drop(1)
        return rows.mapNotNull { row0 ->
            val row = row0.substringBefore("</tr>")
            val film = films(row).firstOrNull() ?: return@mapNotNull null
            val day = regex("""href="/[^"]+/diary/films/for/(\d{4})/(\d{2})/(\d{2})/"""").find(row)?.groupValues
            val date = day?.let { "${it[1]}-${it[2]}-${it[3]}" }
            val rating = regex("""rated-(\d+)""").find(row)?.groupValues?.get(1)?.toIntOrNull()
            val likeCell = regex("""class="col-like[^"]*">(.*?)</td>""", dot).find(row)?.groupValues?.get(1).orEmpty()
            val rewatch = regex("""class="col-rewatch[^"]*"""").find(row)?.value?.contains("icon-status-off") == false
            LbDiaryEntry(film.copy(rating = rating), date, rating, likeCell.contains("liked"), rewatch)
        }
    }

    /** Reviews, from a member's review page or a film's. */
    fun reviews(html: String): List<LbReview> = html.split("<article class=\"production-viewing").drop(1).mapNotNull { a0 ->
        val a = a0.substringBefore("</article>")
        val film = films(a).firstOrNull()
        val author = regex("""href="/([^/"]+)/film/""").find(a)?.groupValues?.get(1) ?: attr(a, "data-owner") ?: return@mapNotNull null
        val authorName = regex("""<strong class="displayname">(.*?)</strong>""", dot).find(a)?.groupValues?.get(1)?.let { text(it) } ?: author
        val avatar = regex("""class="avatar[^"]*"[^>]*>\s*<img src="([^"]+)"""", dot).find(a)?.groupValues?.get(1)?.replace("&amp;", "&")
        val reactions = regex("""content-reactions-strip -viewing(.*?)</div>""", dot).find(a)?.groupValues?.get(1).orEmpty()
        val rating = regex("""aria-label="([★½]+)"""").find(reactions)?.groupValues?.get(1)?.let { halves(it) }
            ?: regex("""rated-(\d+)""").find(reactions)?.groupValues?.get(1)?.toIntOrNull()
        val body = regex("""js-review-body[^>]*>(.*?)</div>""", dot).find(a)?.groupValues?.get(1)
            ?.let { b -> regex("<p[^>]*>(.*?)</p>", dot).findAll(b).map { text(it.groupValues[1]) }.filter { it.isNotBlank() }.joinToString("\n\n") }
            .orEmpty()
        if (body.isBlank()) return@mapNotNull null
        LbReview(film, author, authorName, avatar, rating, reactions.contains("Liked"), body,
            regex("""datetime="([0-9-]+)"""").find(a)?.groupValues?.get(1),
            regex("""data-count="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull())
    }

    /** List summaries, from a member's lists page or a list search. */
    fun lists(html: String): List<LbList> = html.split("<article class=\"list-summary").drop(1).mapNotNull { a0 ->
        val a = a0.substringBefore("</article>")
        val path = regex("""href="(/[^/"]+/list/[^/"]+/)"""").find(a)?.groupValues?.get(1) ?: return@mapNotNull null
        val name = regex("""<h2 class="name[^"]*">\s*<a[^>]*>(.*?)</a>""", dot).find(a)?.groupValues?.get(1)?.let { text(it) } ?: return@mapNotNull null
        val owner = attr(a, "data-person") ?: path.split('/')[1]
        LbList(
            path, name, owner.lowercase(),
            regex("""<strong class="displayname">(.*?)</strong>""", dot).find(a)?.groupValues?.get(1)?.let { text(it) },
            regex("""(\d[\d,]*)(?:\s|&nbsp;| )+films?""").find(a)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            regex("""/likes/" class="metadata">.*?<span class="label">([^<]+)</span>""", dot).find(a)?.groupValues?.get(1)?.trim(),
            regex("""<div class="body-text[^"]*"[^>]*>(.*?)</div>""", dot).find(a)?.groupValues?.get(1)?.let { text(it) }?.ifBlank { null },
            films(a).take(5)
        )
    }

    /** A list's own page: its name, description and films (one page of up to 100). */
    fun listPage(html: String, path: String): LbList {
        val name = regex("""<meta property="og:title" content="([^"]*)"""").find(html)?.groupValues?.get(1)?.let { text(it) }
            ?: regex("""<h1 class="title-1[^"]*">(.*?)</h1>""", dot).find(html)?.groupValues?.get(1)?.let { text(it) } ?: "List"
        val grid = html.substringAfter("<ul class=\"poster-list", html).substringBefore("</ul>")
        val count = regex("""(\d[\d,]*)(?:\s|&nbsp;| )+films""").find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val description = regex("""<div class="body-text -prose[^"]*"[^>]*>(.*?)</div>""", dot).find(html)?.groupValues?.get(1)?.let { text(it) }?.ifBlank { null }
        val owner = path.split('/').getOrNull(1).orEmpty()
        val films = films(grid)
        return LbList(path, name, owner, null, count ?: films.size, null, description, films)
    }

    /** A film's page: the average, how many have rated and reviewed it, its TMDB id, and the credits. */
    fun film(html: String): LbFilmInfo? {
        val slug = regex("""<meta property="og:url" content="https://letterboxd\.com/film/([^/"]+)/"""").find(html)?.groupValues?.get(1) ?: return null
        val tmdb = regex("""themoviedb\.org/(movie|tv)/(\d+)""").find(html)
        return LbFilmInfo(
            slug,
            regex(""""ratingValue":\s*([\d.]+)""").find(html)?.groupValues?.get(1)?.toDoubleOrNull(),
            regex(""""ratingCount":\s*(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull(),
            regex(""""reviewCount":\s*(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull(),
            tmdb?.groupValues?.get(2)?.toLongOrNull(), tmdb?.groupValues?.get(1) == "tv",
            regex("""data-backdrop="([^"]+)"""").find(html)?.groupValues?.get(1)?.replace("&amp;", "&"),
            LetterboxdParser.parse(html),
            imdb = regex("""imdb\.com/title/(tt\d+)""").find(html)?.groupValues?.get(1)
        )
    }

    /**
     * A film's average rating, out of five, worked out from its rating chart: [histogram] counts the ratings from ½ to
     * ★★★★★. The chart is a small page, so this is far quicker to fetch than the film's own page. Null with no ratings.
     */
    fun average(histogram: List<Long>): Double? {
        val total = histogram.sum()
        if (histogram.size != 10 || total == 0L) return null
        return histogram.withIndex().sumOf { (i, n) -> (i + 1) / 2.0 * n } / total
    }

    /** The text of one tab panel of a film's page (`cast`, `crew`…): from its id to the next tab's. Empty when absent. */
    private fun panel(html: String, name: String): String {
        val start = html.indexOf("id=\"tab-panel-$name\"").takeIf { it >= 0 } ?: return ""
        val end = html.indexOf("id=\"tab-panel-", start + 16).takeIf { it > 0 } ?: html.length
        return html.substring(start, end)
    }

    /** A film's cast and crew, each with the path of their page, so their picture can be found. */
    fun credits(html: String): LbCredits {
        val cast = regex("""<a title="([^"]*)" href="(/[^"]+/)" class="text-slug[^"]*">([^<]+)</a>""").findAll(panel(html, "cast")).map {
            LbPerson(it.groupValues[2], text(it.groupValues[3]), text(it.groupValues[1]).ifBlank { null })
        }.toList()
        val crew = regex("""<span class="crewrole -full">([^<]+)</span>.*?<div class="text-sluglist">(.*?)</div>""", dot).findAll(panel(html, "crew")).map { role ->
            LbCrew(text(role.groupValues[1]), regex("""<a href="(/[^"]+/)" class="text-slug">([^<]+)</a>""").findAll(role.groupValues[2]).map {
                LbPerson(it.groupValues[1], text(it.groupValues[2]), null)
            }.toList())
        }.filter { it.people.isNotEmpty() }.toList()
        return LbCredits(cast, crew)
    }

    /** A person's picture, from their Letterboxd page: TMDb's headshot of them. Null when the page has none. */
    fun headshot(html: String): String? = regex("""data-image="(https://image\.tmdb\.org/[^"]+)"""").find(html)?.groupValues?.get(1)

    /** The rating chart fragment: how many gave each of the ten ratings, from ½ up. */
    fun histogram(html: String): List<Long> {
        val bars = regex("""class="barcolumn[^"]*"\s+title="([\d,]+)\s""").findAll(html)
            .map { it.groupValues[1].replace(",", "").toLongOrNull() ?: 0L }.toList()
        return if (bars.size == 10) bars else emptyList()
    }

    /** A member's page for one film: their own rating, like and review (other people's reviews on the page are skipped). */
    fun memberFilm(html: String, user: String): LbMemberFilm? {
        if (html.contains("<title>Letterboxd - Not Found</title>")) return null
        val own = regex("""content-reactions-strip -viewing(.*?)</div>""", dot).find(html)?.groupValues?.get(1)
        val rating = own?.let { regex("""aria-label="([★½]+)"""").find(it)?.groupValues?.get(1)?.let { s -> halves(s) } }
            ?: regex("""rated-(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val review = regex("""js-review-body[^>]*>(.*?)</div>""", dot).find(html)?.groupValues?.get(1)
            ?.let { b -> regex("<p[^>]*>(.*?)</p>", dot).findAll(b).map { text(it.groupValues[1]) }.joinToString("\n\n") }?.ifBlank { null }
        return LbMemberFilm(user, rating, own?.contains("Liked") == true, review)
    }

    /**
     * A member's page for one film, as the film and list pages read it, with whether they have watched it. Not Found
     * means they never logged it. A page that is neither (a browser check, say) gives null rather than a guess.
     */
    fun member(html: String, user: String): LbMemberFilm? = when {
        html.contains("<title>Letterboxd - Not Found</title>") -> LbMemberFilm(user, null, false, null, watched = false)
        !html.contains("og:url") -> null
        else -> memberFilm(html, user)
    }

    /** People a member follows: username, display name, picture and films watched. */
    fun members(html: String): List<LbMember> = html.split("<div class=\"person-summary").drop(1).mapNotNull { p0 ->
        val p = p0.substringBefore("</td>")
        val user = regex("""href="/([^/"]+)/" class="name"""").find(p)?.groupValues?.get(1) ?: return@mapNotNull null
        LbMember(user, regex("""class="name">(.*?)</a>""", dot).find(p)?.groupValues?.get(1)?.let { text(it) } ?: user,
            regex("""<img src="([^"]+)"""").find(p)?.groupValues?.get(1)?.replace("&amp;", "&"), null)
    }
}
