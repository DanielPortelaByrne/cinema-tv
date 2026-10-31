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

    @Test fun the_live_page_opens_a_websocket_to_the_code_and_downloads_nothing() {
        val html = PhoneTextPage.live("428193")
        assertTrue(html.contains("data-code=\"428193\""))
        assertTrue(html.contains("new WebSocket"))
        assertTrue(html.contains("'/ws/'"))
        assertFalse(html.contains("src="))
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
    }

    @Test fun the_live_page_only_ever_embeds_digits_of_the_code() {
        val html = PhoneTextPage.live("12\"><script>alert(x)</script>34")
        assertTrue(html.contains("data-code=\"1234\""))
        assertFalse(html.contains("alert(x)"))
    }

    @Test fun the_live_page_reconnects_and_keeps_alive() {
        val html = PhoneTextPage.live("123456")
        assertTrue(html.contains("setInterval"))
        assertTrue(html.contains("visibilitychange"))
        assertTrue(html.contains("Scan the code on the TV again."))
    }
}
