package com.danielportelabyrne.cinema.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioLanguagesTest {
    @Test fun language_tag_variants_all_mean_english() {
        listOf("en", "eng", "en-US", "en-GB", "EN_gb", " en ").forEach { assertEquals(it, "en", AudioLanguages.normalize(it)) }
    }

    @Test fun blank_and_undetermined_tags_are_ignored() {
        assertNull(AudioLanguages.normalize(null)); assertNull(AudioLanguages.normalize("")); assertNull(AudioLanguages.normalize("und"))
    }

    @Test fun english_is_always_asked_for_as_the_fallback() {
        assertEquals(listOf("en"), AudioLanguages.preferred(emptyList()))
        assertEquals(listOf("en"), AudioLanguages.preferred(listOf("en-IE", "en")))
    }

    @Test fun device_languages_come_first_without_repeats() {
        assertEquals(listOf("it", "en"), AudioLanguages.preferred(listOf("it-IT", "ita", "en")))
    }
}
