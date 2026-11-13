package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject

/** A page that carries the personal shelves (Continue watching, Favourites, Recently watched). */
sealed class PersonalPage {
    /** Everything, including titles whose details are not known yet. */
    object Home : PersonalPage() { override fun toString() = "Home" }
    object Films : PersonalPage() { override fun toString() = "Films" }
    object Series : PersonalPage() { override fun toString() = "Series" }
    object Documentaries : PersonalPage() { override fun toString() = "Documentaries" }
    /** A genre's page; [code] is the catalogue's genre code ("hrr"). */
    data class InGenre(val code: String) : PersonalPage()
    /** A service's page; [label] is its name as titles list it ("Netflix", see [Service.label]). */
    data class OnService(val label: String) : PersonalPage()
    /** My library: only what the library is known to hold. */
    object Library : PersonalPage() { override fun toString() = "Library" }
}

/** Whether a title belongs on a page. [UNKNOWN] while the details that decide it are not known. */
enum class Membership { IN, OUT, UNKNOWN }

/**
 * What is known about a film or series beyond its ids: its genres and the services it streams on. Null means not known
 * (an empty genre list is taken as not known too, as some sources leave it empty). [jellyfinId] is only ever set for
 * the library loaded now, never kept, so one server's ids never meet another's.
 */
data class TitleFacts(
    val kind: Kind,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val jellyfinId: String? = null,
    val genres: List<String>? = null,
    val services: List<String>? = null,
    val atMs: Long = 0L
) {
    val hasId get() = !tmdbId.isNullOrBlank() || !imdbId.isNullOrBlank() || !jellyfinId.isNullOrBlank()

    companion object {
        /** From a catalogue title: JustWatch lists its genres and (UK) services, so both are known. */
        fun of(t: Title, atMs: Long): TitleFacts = TitleFacts(t.kind, t.tmdbId, t.imdbId, null, t.genres.ifEmpty { null }, t.services, atMs)

        /** From the library loaded now: its genres only (Jellyfin does not know the services). */
        fun of(i: JellyfinItem): TitleFacts? {
            if (!i.isMovie && !i.isSeries) return null
            return TitleFacts(if (i.isSeries) Kind.SHOW else Kind.MOVIE, i.tmdbId, i.imdbId, ContinueEngine.jellyfinKey(i.id), i.genres.ifEmpty { null }, null, 0L)
        }
    }
}

/**
 * Which of the viewer's own titles belong on which page. Pure: it reads only what it is given and never fetches. Titles
 * are matched by id only (TMDB, IMDb, and the current library's Jellyfin ids), never by name; an episode is judged by its
 * series.
 */
object PersonalShelves {
    /** The genre names each catalogue genre code covers, as JustWatch, TMDB, Jellyfin and Cinemeta write them. */
    private val GENRE_NAMES = mapOf(
        "act" to setOf("action", "adventure", "action & adventure", "action and adventure"),
        "ani" to setOf("animation", "anime"),
        "cmy" to setOf("comedy"),
        "crm" to setOf("crime"),
        "doc" to setOf("documentary", "documentaries"),
        "drm" to setOf("drama"),
        "fml" to setOf("family", "kids", "kids & family", "children"),
        "fnt" to setOf("fantasy", "sci-fi & fantasy"),
        "hrr" to setOf("horror"),
        "hst" to setOf("history"),
        "rma" to setOf("romance"),
        "scf" to setOf("science fiction", "science-fiction", "sci-fi", "sci-fi & fantasy"),
        "trl" to setOf("thriller", "mystery", "mystery & thriller", "suspense")
    )

    /** True when one of [genres] is the genre [code]. */
    fun hasGenre(code: String, genres: List<String>): Boolean {
        val names = GENRE_NAMES[code] ?: return false
        return genres.any { it.trim().lowercase() in names }
    }

    /** A service's name boiled down for comparing: tidied, lower case, accents dropped ("RTÉ" is "RTE" to some), no spaces. */
    private fun serviceKey(name: String) = java.text.Normalizer.normalize(Catalog.serviceName(name.trim()), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace("+", "plus").replace(Regex("[^a-z0-9]"), "")

    /** True when one of [services] is the service named [label]. TMDB calls HBO Max "Max". */
    fun hasService(label: String, services: List<String>): Boolean {
        val want = serviceKey(label)
        return services.any { val k = serviceKey(it); k == want || (k == "max" && want == "hbomax") }
    }

    /** Where [kind] with [facts] stands on [page]. [inLibrary] is null when the library is not known (not connected, not loaded). */
    fun membership(page: PersonalPage, kind: Kind, facts: TitleFacts?, inLibrary: Boolean?): Membership = when (page) {
        PersonalPage.Home -> Membership.IN
        PersonalPage.Films -> if (kind == Kind.MOVIE) Membership.IN else Membership.OUT
        PersonalPage.Series -> if (kind == Kind.SHOW) Membership.IN else Membership.OUT
        PersonalPage.Documentaries -> genre("doc", facts)
        is PersonalPage.InGenre -> genre(page.code, facts)
        is PersonalPage.OnService -> facts?.services?.let { if (hasService(page.label, it)) Membership.IN else Membership.OUT } ?: Membership.UNKNOWN
        PersonalPage.Library -> when (inLibrary) { true -> Membership.IN; false -> Membership.OUT; null -> Membership.UNKNOWN }
    }

    private fun genre(code: String, facts: TitleFacts?) =
        facts?.genres?.takeIf { it.isNotEmpty() }?.let { if (hasGenre(code, it)) Membership.IN else Membership.OUT } ?: Membership.UNKNOWN

    // --- Identity

    private fun shares(a: String?, b: String?) = !a.isNullOrBlank() && a == b
    private fun differ(a: String?, b: String?) = !a.isNullOrBlank() && !b.isNullOrBlank() && a != b

    /** True when [f] is the title [ref]: same kind, an id in common, and no id that disagrees. */
    fun matches(kind: Kind, ref: TitleRef, f: TitleFacts): Boolean {
        if (f.kind != kind) return false
        val jf = ContinueEngine.jellyfinKey(ref.jellyfinId); val fjf = ContinueEngine.jellyfinKey(f.jellyfinId)
        if (differ(ref.tmdbId, f.tmdbId) || differ(ref.imdbId, f.imdbId) || differ(jf, fjf)) return false
        return shares(ref.tmdbId, f.tmdbId) || shares(ref.imdbId, f.imdbId) || shares(jf, fjf)
    }

    /** Everything [all] knows about [ref], merged: genres from every source, services from the newest that knows them. */
    fun factsFor(kind: Kind, ref: TitleRef, all: List<TitleFacts>): TitleFacts? {
        val hits = all.filter { matches(kind, ref, it) }
        if (hits.isEmpty()) return null
        val genres = hits.flatMap { it.genres.orEmpty() }.distinct().ifEmpty { null }
        val services = hits.filter { it.services != null }.maxByOrNull { it.atMs }?.services
        return TitleFacts(kind, ref.tmdbId ?: hits.firstNotNullOfOrNull { it.tmdbId }, ref.imdbId ?: hits.firstNotNullOfOrNull { it.imdbId },
            ref.jellyfinId, genres, services, hits.maxOf { it.atMs })
    }

    // --- The three shelves' titles

    fun refOf(f: Favourite) = TitleRef(f.tmdbId, f.imdbId, ContinueEngine.jellyfinKey(f.jellyfinId))

    /** A film's own ids; an episode's series' ids, so it goes wherever its series goes. */
    fun refOf(e: PlayedEntry) = if (e.isEpisode) TitleRef(e.seriesTmdbId, e.seriesImdbId, ContinueEngine.jellyfinKey(e.seriesJellyfinId))
        else TitleRef(e.tmdbId, e.imdbId, ContinueEngine.jellyfinKey(e.jellyfinId))
}

/**
 * One page's filter over the personal shelves. Home keeps everything; any other page keeps only what is known to belong.
 * The titles it could not decide are collected in [unknown], so their details can be asked for in the background.
 */
class PersonalFilter(val page: PersonalPage, facts: List<TitleFacts>, library: List<JellyfinItem>?) {
    /** Facts and library titles by each id they carry, so a lookup reads a few records rather than all of them. */
    private val factsIndex = index(facts.filter { it.hasId })
    private val libraryIndex = library?.let { l -> index(l.filter { !it.missing }.mapNotNull { TitleFacts.of(it) }) }
    /** The titles seen whose place could not be decided, as (kind, ids). In the order met, once each. */
    val unknown = LinkedHashSet<Pair<Kind, TitleRef>>()

    private fun keys(kind: Kind, tmdb: String?, imdb: String?, jf: String?) = listOfNotNull(
        tmdb?.takeIf { it.isNotBlank() }?.let { "$kind|t|$it" }, imdb?.takeIf { it.isNotBlank() }?.let { "$kind|i|$it" },
        ContinueEngine.jellyfinKey(jf)?.let { "$kind|j|$it" })

    private fun index(list: List<TitleFacts>): Map<String, List<TitleFacts>> {
        val m = HashMap<String, MutableList<TitleFacts>>()
        list.forEach { f -> keys(f.kind, f.tmdbId, f.imdbId, f.jellyfinId).forEach { m.getOrPut(it) { ArrayList() } += f } }
        return m
    }

    private fun near(index: Map<String, List<TitleFacts>>, kind: Kind, ref: TitleRef) =
        keys(kind, ref.tmdbId, ref.imdbId, ref.jellyfinId).flatMap { index[it].orEmpty() }.distinct()

    fun membership(kind: Kind, ref: TitleRef): Membership {
        if (page == PersonalPage.Home) return Membership.IN
        val library = libraryIndex?.let { idx -> near(idx, kind, ref).any { PersonalShelves.matches(kind, ref, it) } }
        // The library's genres count too: a library title is judged by what both know of it.
        val known = near(factsIndex, kind, ref) + (libraryIndex?.let { near(it, kind, ref) }.orEmpty())
        val m = PersonalShelves.membership(page, kind, PersonalShelves.factsFor(kind, ref, known), library)
        if (m == Membership.UNKNOWN) unknown += kind to ref
        return m
    }

    fun keeps(kind: Kind, ref: TitleRef) = membership(kind, ref) == Membership.IN

    fun keeps(f: Favourite) = keeps(f.kind, PersonalShelves.refOf(f))

    fun keeps(e: PlayedEntry) = keeps(e.kind, PersonalShelves.refOf(e))

    /** A card that plays from the library is a library title, whatever ids it has. */
    fun keeps(e: ContinueEntry): Boolean =
        (page == PersonalPage.Library && e.source is ContinueSource.Library) || keeps(e.kind, e.ids)

    /** The Favourites shelf for this page: [Favourites.shelf], then filtered. */
    fun favourites(list: List<Favourite>): List<Favourite> = Favourites.shelf(list).filter { keeps(it) }

    companion object {
        /** Nothing filtered: Home. */
        val HOME = PersonalFilter(PersonalPage.Home, emptyList(), null)
    }
}

/**
 * Title details learned from the catalogue and from lookups, kept on this TV so a page can sort the viewer's own titles
 * without the network. Ids are TMDB and IMDb only: a Jellyfin id is never kept. At most [CAP] titles, newest first.
 */
class TitleFactsStore(platform: Platform, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = platform.preferences("title_facts")
    private var cache: List<TitleFacts>? = null

    private var dirty = false

    @Synchronized fun all(): List<TitleFacts> = cache ?: parse(prefs.getString(KEY, null)).also { cache = it }

    /**
     * Adds what [learned] says, replacing an older record of the same title. Held in memory at once; written by [flush],
     * so a page loading many rows writes once. True when something changed. [addServices] is for what was learned in
     * another country's catalogue: its services join the ones already known rather than replacing them.
     */
    @Synchronized fun learn(learned: List<TitleFacts>, addServices: Boolean = false): Boolean {
        val now = all()
        val next = merge(now, learned.map { it.copy(jellyfinId = null, atMs = if (it.atMs > 0) it.atMs else clock()) }.filter { it.hasId }, addServices)
        if (next == now) return false
        cache = next; dirty = true
        return true
    }

    /** Writes what was learned since the last write, if anything. */
    @Synchronized fun flush() {
        if (!dirty) return
        dirty = false
        prefs.edit().putString(KEY, toJson(all())).apply()
    }

    companion object {
        const val CAP = 1000
        private const val KEY = "all"

        /**
         * [list] with [learned] folded in: a record matching a learned one by id (with none disagreeing) is replaced, keeping
         * what it knew that the new one does not. With [addServices], the services it knew are kept beside the new ones:
         * a title on RTÉ Player in Ireland is still on BBC iPlayer in Britain.
         */
        fun merge(list: List<TitleFacts>, learned: List<TitleFacts>, addServices: Boolean = false): List<TitleFacts> {
            var out = list
            for (f in learned) {
                val ref = TitleRef(f.tmdbId, f.imdbId)
                val old = out.filter { PersonalShelves.matches(f.kind, ref, it) }
                val merged = old.fold(f) { acc, o -> acc.copy(tmdbId = acc.tmdbId ?: o.tmdbId, imdbId = acc.imdbId ?: o.imdbId,
                    genres = acc.genres ?: o.genres,
                    services = if (addServices && acc.services != null && o.services != null) (o.services + acc.services).distinct() else acc.services ?: o.services) }
                if (old.size == 1 && old[0].copy(atMs = merged.atMs) == merged) continue   // nothing new
                out = listOf(merged) + out.filterNot { o -> old.any { it === o } }
            }
            return out.sortedByDescending { it.atMs }.take(CAP)
        }

        fun toJson(list: List<TitleFacts>): String = JSONArray().also { a ->
            list.forEach { f ->
                val o = JSONObject().put("kind", f.kind.name).put("tmdb", f.tmdbId ?: "").put("imdb", f.imdbId ?: "").put("at", f.atMs)
                f.genres?.let { o.put("g", JSONArray(it)) }
                f.services?.let { o.put("s", JSONArray(it)) }
                a.put(o)
            }
        }.toString()

        fun parse(json: String?): List<TitleFacts> = try {
            val a = JSONArray(json ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                fun list(k: String) = o.optJSONArray(k)?.let { g -> (0 until g.length()).map { g.optString(it) }.filter { it.isNotBlank() } }
                TitleFacts(kind, o.optString("tmdb").ifBlank { null }, o.optString("imdb").ifBlank { null }, null,
                    list("g")?.ifEmpty { null }, list("s"), o.optLong("at")).takeIf { it.hasId }
            }
        } catch (_: Exception) { emptyList() }
    }
}
