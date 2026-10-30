package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTextPageTest {
    @Test fun the_text_sent_is_trimmed_and_cut_and_empty_is_nothing() {
        assertEquals("heist films", PhoneTextPage.textFrom(mapOf("text" to "  heist films  ")))
        assertNull(PhoneTextPage.textFrom(mapOf("text" to "   ")))
        assertNull(PhoneTextPage.textFrom(emptyMap()))
        assertEquals(PhoneTextPage.MAX_LENGTH, PhoneTextPage.textFrom(mapOf("text" to "x".repeat(500)))!!.length)
    }

    @Test fun the_page_has_a_text_box_and_escapes_what_it_shows() {
        val html = PhoneTextPage.form(sent = "<i>hi</i>", problem = "Type something first")
        assertTrue(html.contains("name=\"text\""))
        assertTrue(html.contains("Sent: &lt;i&gt;hi&lt;/i&gt;"))
        assertTrue(html.contains("Type something first"))
        assertFalse(html.contains("<i>hi</i>"))
    }
}
