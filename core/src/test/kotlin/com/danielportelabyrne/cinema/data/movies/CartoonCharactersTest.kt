package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartoonCharactersTest {
    @Test fun `a picture is found through a tidied title and a redirect, and a page without one is left out`() {
        val json = """{"query":{
            "normalized":[{"from":"bugs bunny","to":"Bugs Bunny"}],
            "redirects":[{"from":"Stimpy","to":"Ren and Stimpy (characters)"}],
            "pages":{
              "1":{"title":"Bugs Bunny","thumbnail":{"source":"https://upload.wikimedia.org/bugs.png"}},
              "2":{"title":"Ren and Stimpy (characters)","thumbnail":{"source":"https://upload.wikimedia.org/stimpy.png"}},
              "3":{"title":"Marge Simpson"},
              "-1":{"title":"Nobody","missing":""}}}}"""
        val found = CharacterArt.parsePictures(json, listOf("bugs bunny", "Stimpy", "Marge Simpson", "Nobody"))
        assertEquals(mapOf("bugs bunny" to "https://upload.wikimedia.org/bugs.png", "Stimpy" to "https://upload.wikimedia.org/stimpy.png"), found)
    }

    @Test fun `no character is listed twice, and none has a name that would break a picture id`() {
        val all = CartoonCharacters.SHOWS.flatMap { it.characters }
        assertEquals(all.size, all.map { it.second }.distinct().size)
        assertEquals(all.size, all.map { it.first }.distinct().size)
        assertTrue(all.none { '|' in it.first })
    }
}
