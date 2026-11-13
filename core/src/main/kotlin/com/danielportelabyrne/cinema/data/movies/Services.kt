package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The services the owner follows: the cards in "Browse by service" on Home. Until they choose, it is the original six.
 * Pure, so it can be tested: [parse] and [toJson] are what is kept on the TV, and a service the app knows by name
 * (BBC iPlayer, say) always comes back as the app's own definition of it, with its home-grown rows.
 */
object FollowedServices {
    val DEFAULT: List<Service> get() = Catalog.STREAMERS

    fun toJson(list: List<Service>): String = JSONArray().also { a ->
        list.forEach { s ->
            a.put(JSONObject().put("code", s.code).put("label", s.label).put("icon", s.icon ?: "").put("country", s.country ?: ""))
        }
    }.toString()

    /** The followed services kept as [json], or null when nothing was ever kept (the original six apply). */
    fun parse(json: String?): List<Service>? = try {
        if (json == null) null else {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("code").ifBlank { return@mapNotNull null }
                Catalog.KNOWN.firstOrNull { it.code == code }
                    ?: Service(code, o.optString("label").ifBlank { code }, o.optString("icon").ifBlank { null }, o.optString("country").ifBlank { null })
            }.distinctBy { it.code }
        }
    } catch (_: Exception) { null }

    fun follow(list: List<Service>, s: Service): List<Service> = if (list.any { it.code == s.code }) list else list + s

    fun unfollow(list: List<Service>, code: String): List<Service> = list.filterNot { it.code == code }

    fun isFollowing(list: List<Service>, code: String) = list.any { it.code == code }
}

/** One service as JustWatch lists it in one country. */
data class ListedService(val code: String, val name: String, val icon: String?, val country: String)

/**
 * Finding a service to follow among everything JustWatch knows: its streaming apps and catch-up players in about fifty
 * countries (a service is only listed where it runs). Pure parsing and ranking; [ServiceDirectoryRepository] fetches.
 */
object ServiceDirectory {
    /**
     * The countries asked, in the order a service that runs in several is credited to one: Britain and Ireland first,
     * then the big English-speaking markets, then the rest of the world.
     */
    val COUNTRIES = listOf(
        "GB", "IE", "US", "CA", "AU", "NZ", "DE", "FR", "ES", "IT", "NL", "BE", "SE", "NO", "DK", "FI", "PL", "PT", "AT", "CH",
        "CZ", "GR", "HU", "RO", "TR", "UA", "RU", "IL", "AE", "SA", "EG", "ZA", "NG", "KE", "BR", "MX", "AR", "CL", "CO", "PE",
        "JP", "KR", "CN", "HK", "TW", "IN", "SG", "MY", "TH", "PH", "ID"
    )

    private val WATCHABLE = setOf("FLATRATE", "FREE", "ADS", "FAST")

    /** Names people type for a service that JustWatch files under another name. */
    private val ALIASES = mapOf(
        "e4" to "al4", "film4" to "al4", "more4" to "al4", "4seven" to "al4", "all4" to "al4",
        "itv" to "itv", "itv2" to "itv", "itv3" to "itv", "itv4" to "itv", "citv" to "itv",
        "iplayer" to "bbc", "bbc one" to "bbc", "bbc two" to "bbc", "bbc three" to "bbc", "cbbc" to "bbc", "cbeebies" to "bbc",
        "my5" to "myf", "channel five" to "myf",
        "now tv" to "ntv", "sky atlantic" to "ntv", "sky cinema" to "ntv",
        "rte" to "rte", "rte2" to "rte",
        "max" to "mxx", "hbo" to "mxx", "disney" to "dnp", "prime" to "amp", "amazon" to "amp", "apple" to "atp",
        "paramount" to "pmp", "uktv" to "ukt", "dave" to "ukt", "yesterday" to "ukt",
        "stv" to "stp", "s4c" to "sfl", "welsh" to "sfl", "irish" to "rte", "gaeilge" to "tgf"
    )

    private fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT).replace("+", " plus ").replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The services a package list names that can be watched without renting or buying (cinemas and stores are left out). */
    fun parsePackages(json: String, country: String): List<ListedService> = try {
        val a = JSONObject(json).getJSONObject("data").getJSONArray("packages")
        (0 until a.length()).mapNotNull { i ->
            val p = a.getJSONObject(i)
            val kinds = p.optJSONArray("monetizationTypes")?.let { k -> (0 until k.length()).map { k.optString(it) } }.orEmpty()
            if (kinds.none { it in WATCHABLE }) return@mapNotNull null
            val code = p.optString("shortName").ifBlank { return@mapNotNull null }
            val name = Catalog.serviceName(p.optString("clearName")).ifBlank { return@mapNotNull null }
            val icon = p.optString("icon").ifBlank { null }?.replace("{profile}", "s166")?.replace("{format}", "png")
            ListedService(code, name, icon, country)
        }
    } catch (_: Exception) { emptyList() }

    /**
     * Every service, once. A service the app knows is its own definition; one the viewer's country lists runs there like
     * any other; one that only runs elsewhere keeps the first country (in [COUNTRIES] order) that lists it, which is where
     * its pages are asked for.
     */
    fun merge(byCountry: Map<String, List<ListedService>>, viewer: String): List<Service> {
        val seen = LinkedHashMap<String, Service>()
        val order = listOf(viewer) + COUNTRIES.filter { it != viewer } + byCountry.keys.filter { it !in COUNTRIES && it != viewer }
        val here = byCountry[viewer].orEmpty().map { it.code }.toSet()
        order.forEach { c ->
            byCountry[c].orEmpty().forEach { l ->
                if (l.code in seen) return@forEach
                seen[l.code] = Catalog.KNOWN.firstOrNull { it.code == l.code }
                    ?: Service(l.code, l.name, l.icon, country = if (l.code in here) null else l.country)
            }
        }
        return seen.values.toList()
    }

    /** True when [s] is listed in the viewer's own country. */
    fun runsHere(s: Service, viewer: String) = s.country == null || s.country.equals(viewer, ignoreCase = true)

    /** Where [s] runs, for a line under its name: "Streams in your country" or "Runs in Japan". */
    fun whereLine(s: Service, viewer: String): String = when {
        runsHere(s, viewer) -> "Streams in your country"
        else -> "Runs in " + Locale("", s.country).getDisplayCountry(Locale.ENGLISH).ifBlank { s.country }
    }

    /**
     * How well [name] answers [query], 0 for not at all: exact 100, its alias 95, starts with it 85, every word of the query
     * starts a word of it 70, inside a word 50. Shared by services and channels so one list can be ranked.
     */
    fun score(name: String, query: String, aliasHit: Boolean = false): Int {
        val q = fold(query)
        if (q.isEmpty()) return 0
        val n = fold(name)
        val words = n.split(' ')
        return when {
            n == q -> 100
            aliasHit -> 95
            n.startsWith(q) -> 85
            q.split(' ').all { t -> words.any { it.startsWith(t) } } -> 70
            q.length >= 3 && words.any { it.contains(q.replace(" ", "")) } -> 50   // inside a word, never across two ("tubi tv" is not an "itv")
            else -> 0
        }
    }

    /** [score] of a service for [query], counting what people call it. */
    fun scoreOf(s: Service, query: String): Int = score(s.label, query, aliasHit = ALIASES[fold(query)] == s.code)

    /** The services [query] names, best first: by name, by a word of it, by what people call it. Empty for a blank query. */
    fun search(all: List<Service>, query: String, viewer: String, limit: Int = 30): List<Service> {
        if (fold(query).isEmpty()) return emptyList()
        return all.mapNotNull { s ->
            val score = scoreOf(s, query)
            if (score == 0) null else Triple(s, score, if (runsHere(s, viewer)) 1 else 0)
        }.sortedWith(compareByDescending<Triple<Service, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first.label.length }.thenBy { it.first.label })
            .take(limit).map { it.first }
    }
}

/**
 * Fetches each country's service list from JustWatch (no sign-in) and keeps it on disk for a week, so the search is
 * instant after the first time. Blocking; call it off the main thread.
 */
class ServiceDirectoryRepository(platform: Platform, private val clock: () -> Long = System::currentTimeMillis) {
    private val dir = File(platform.cacheDir, "services")
    @Volatile private var held: Pair<String, List<Service>>? = null

    /** Every service JustWatch lists anywhere, as seen from [viewer]'s country. Empty when nothing could be fetched. */
    fun all(viewer: String): List<Service> {
        held?.takeIf { it.first == viewer }?.let { return it.second }
        synchronized(this) {
            held?.takeIf { it.first == viewer }?.let { return it.second }
            val countries = (listOf(viewer) + ServiceDirectory.COUNTRIES).distinct()
            val pool = Executors.newFixedThreadPool(8) { r -> Thread(r, "service-directory").apply { isDaemon = true } }
            val got = try {
                val jobs = countries.map { c -> c to pool.submit(Callable { listFor(c) }) }
                jobs.mapNotNull { (c, f) -> runCatching { f.get(40, TimeUnit.SECONDS) }.getOrNull()?.let { c to it } }.toMap()
            } finally { pool.shutdownNow() }
            val merged = ServiceDirectory.merge(got, viewer)
            // Only kept when the viewer's own country came back: a half answer would hide what runs at home.
            if (got.containsKey(viewer) && merged.isNotEmpty()) held = viewer to merged
            return merged
        }
    }

    private fun listFor(country: String): List<ListedService> {
        val file = File(dir, "$country.json")
        val fresh = file.takeIf { it.exists() && clock() - it.lastModified() < WEEK_MS }
        val json = fresh?.let { runCatching { it.readText() }.getOrNull() } ?: fetch(country)?.also { text ->
            runCatching { dir.mkdirs(); file.writeText(text); file.setLastModified(clock()) }
        } ?: runCatching { file.readText() }.getOrNull()   // stale beats nothing
        return json?.let { ServiceDirectory.parsePackages(it, country) }.orEmpty()
    }

    private fun fetch(country: String): String? = try {
        val body = JSONObject().put("query", QUERY).put("variables", JSONObject().put("c", country)).toString()
        val c = URL("https://apis.justwatch.com/graphql").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "CinemaTV/1.0 (Android TV)")
        c.exchange {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else { Log.w(TAG, "HTTP ${c.responseCode} for $country"); null }
        }
    } catch (e: Exception) { Log.w(TAG, "fetch failed for $country: ${e.javaClass.simpleName}"); null }

    companion object {
        private const val TAG = "Services"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        private const val QUERY = "query(\$c: Country!) { packages(country: \$c, platform: WEB) { shortName clearName icon monetizationTypes } }"
    }
}
