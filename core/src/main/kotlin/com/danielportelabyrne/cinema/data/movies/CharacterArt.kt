package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A drawing of an animated character, from the character's own Wikipedia article ("Bender (Futurama)", "Toph
 * Beifong"): the picture at the top of the article. Only articles Wikipedia describes as a character are taken, so a
 * film or an episode with the same name is never used. Blocking; null when there is none.
 */
object CharacterArt {
    private const val API = "https://en.wikipedia.org/w/api.php"

    fun find(character: String, show: String): String? {
        val short = show.substringBefore(':').trim()
        val first = character.split(' ').first()
        // Exact titles first, the way Wikipedia names character articles; then a search.
        val titles = listOf("$character ($show)", "$character ($short)", "$first ($show)", "$first ($short)", character).distinct()
        query("action=query&titles=" + enc(titles.joinToString("|")))?.firstOrNull { it.third }?.let { return it.second }
        val searched = query("action=query&generator=search&gsrlimit=5&gsrsearch=" + enc("$character $short character")) ?: return null
        return searched.firstOrNull { it.third && it.first.contains(first, ignoreCase = true) }?.second
    }

    /** Blocking: the picture at the top of each of these articles, by the title as given; those without one are left out. */
    fun pictures(articles: List<String>): Map<String, String> {
        val out = HashMap<String, String>()
        articles.distinct().chunked(50).forEach { batch ->   // Wikipedia answers for at most 50 titles at a time
            try {
                val c = URL("$API?action=query&titles=" + enc(batch.joinToString("|")) + "&format=json&redirects=1&prop=pageimages&piprop=thumbnail&pithumbsize=300&pilicense=any").openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 10000
                c.setRequestProperty("User-Agent", "CinemaTV/1.0 (home TV app)")
                c.exchange {
                    if (c.responseCode == 200) out += parsePictures(c.inputStream.bufferedReader().use { it.readText() }, batch)
                }
            } catch (_: Exception) { }
        }
        return out
    }

    /** Wikipedia may tidy a title or send it on to another article: each asked title is followed to the page that answers. */
    internal fun parsePictures(json: String, asked: List<String>): Map<String, String> {
        val q = JSONObject(json).optJSONObject("query") ?: return emptyMap()
        val moved = HashMap<String, String>()
        for (key in listOf("normalized", "redirects")) q.optJSONArray(key)?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { moved[it.optString("from")] = it.optString("to") }
        }
        val pages = q.optJSONObject("pages") ?: return emptyMap()
        val byTitle = HashMap<String, String>()
        pages.keys().forEach { k ->
            val o = pages.getJSONObject(k)
            o.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("https://") }?.let { byTitle[o.optString("title")] = it }
        }
        val out = HashMap<String, String>()
        asked.forEach { title ->
            var t = title
            repeat(3) { t = moved[t] ?: t }
            byTitle[t]?.let { out[title] = it }
        }
        return out
    }

    /** (title, picture, is a character with a picture) for each page of an answer, in search order. */
    private fun query(params: String): List<Triple<String, String, Boolean>>? = try {
        val c = URL("$API?$params&format=json&redirects=1&prop=pageimages|description&piprop=thumbnail&pithumbsize=300&pilicense=any").openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "CinemaTV/1.0 (home TV app)")
        c.exchange {
            if (c.responseCode != 200) null else {
                val pages = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONObject("query")?.optJSONObject("pages") ?: return@exchange emptyList()
                pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index", 99) }.mapNotNull { o ->
                    val thumb = o.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    Triple(o.optString("title"), thumb, o.optString("description").contains("character", ignoreCase = true))
                }.toList()
            }
        }
    } catch (_: Exception) { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
