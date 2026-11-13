package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject

/** One of a title's characters: who they are, the actor, and the actor's TMDB photo path ("/abc.jpg"). */
data class CastMember(val character: String, val actor: String, val profilePath: String) {
    companion object {
        /** The billed cast from a Seerr title answer (its `credits.cast`), those with a photo and a character, in billing order. */
        fun parse(json: String, limit: Int = 8): List<CastMember> = try {
            val cast = JSONObject(json).optJSONObject("credits")?.optJSONArray("cast")
            (0 until (cast?.length() ?: 0)).mapNotNull { i ->
                val o = cast!!.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("profilePath").takeIf { it.startsWith("/") } ?: return@mapNotNull null
                // "Walter White / Heisenberg (voice)": the first name the character goes by.
                val character = o.optString("character").substringBefore(" / ").replace(Regex("""\s*\(.*?\)"""), "").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                CastMember(character, o.optString("name"), path)
            }.distinctBy { it.character }.take(limit)
        } catch (_: Exception) { emptyList() }
    }
}
