package com.danielportelabyrne.cinema.data.pairing

import org.junit.Assert.*
import org.junit.Test

class PairingPagesTest {
    @Test fun the_setup_form_posts_back_to_the_same_code() {
        val html = PairingPages.setup("428193")
        assertTrue(html.contains("action=\"/setup/428193\""))
        assertTrue(html.contains("name=\"jellyfinUrl\""))
        assertTrue(html.contains("name=\"password\""))
        assertTrue(html.contains("name=\"seerrUrl\""))
    }

    @Test fun the_setup_page_offers_quick_connect_on_the_phone() {
        val html = PairingPages.setup("428193")
        assertTrue(html.contains("id=\"qc-finish\""))
        assertTrue("Finish must skip the browser's required-field checks after Quick Connect",
            html.contains("id=\"qc-finish\" formnovalidate"))
        assertFalse("Quick Connect must not submit by itself: the optional sections need a chance to be filled",
            html.contains("form.submit()"))
        assertTrue(html.contains("id=\"qc-start\""))
        assertTrue(html.contains("/qc-start/"))
        assertTrue(html.contains("/qc-check/"))
    }

    @Test fun servers_the_tv_found_are_tap_to_fill_and_escaped() {
        val html = PairingPages.setup("428193", SetupForm(
            found = listOf(ServerChoice("steamdeck", "http://192.168.1.233:8096"), ServerChoice("<b>x</b>", "http://evil:8096")),
            seerrSuggestion = "http://192.168.1.233:5055",
        ))
        assertTrue(html.contains("data-fill=\"jellyfinUrl\" data-value=\"http://192.168.1.233:8096\""))
        assertTrue(html.contains("data-fill=\"seerrUrl\" data-value=\"http://192.168.1.233:5055\""))
        assertFalse(html.contains("<b>x</b>"))
    }

    @Test fun typed_values_are_kept_on_a_retry_but_the_password_is_not() {
        val html = PairingPages.setup("428193", SetupForm(jellyfinUrl = "http://192.168.1.233:8096", username = "admin", error = "no"))
        assertTrue(html.contains("value=\"http://192.168.1.233:8096\""))
        assertTrue(html.contains("value=\"admin\""))
        assertTrue(html.contains("name=\"password\" type=\"password\" autocomplete=\"current-password\" class=\"\""))
    }

    @Test fun the_field_with_the_problem_is_outlined() {
        val html = PairingPages.setup("428193", SetupForm(error = "Wrong password", errorField = "password"))
        assertTrue(html.contains("name=\"password\" type=\"password\" autocomplete=\"current-password\" class=\"bad\""))
        assertTrue(html.contains("Wrong password"))
    }

    @Test fun music_sonarr_radarr_and_stremio_are_optional_sections_on_the_phone() {
        val html = PairingPages.setup("428193")
        assertTrue(html.contains("name=\"musicUrl\""))
        assertTrue(html.contains("name=\"musicApiKey\""))
        assertTrue(html.contains("name=\"sonarrApiKey\""))
        assertTrue(html.contains("name=\"radarrApiKey\""))
        assertTrue(html.contains("id=\"stremio-start\""))
        assertTrue(html.contains("/stremio-check/"))
        assertEquals(4, Regex("<details class=\"card optional\"").findAll(html).count())
    }

    @Test fun the_music_address_is_offered_as_a_tap_to_fill_and_the_username_copies_from_jellyfin() {
        val html = PairingPages.setup("428193", SetupForm(musicSuggestion = "http://192.168.1.233:4533"))
        assertTrue(html.contains("data-fill=\"musicUrl\" data-value=\"http://192.168.1.233:4533\""))
        assertTrue(html.contains("id=\"musicUrl\""))
        assertTrue(html.contains("id=\"musicUsername\""))
        assertTrue(html.contains("getElementById('musicUsername')"))
    }

    @Test fun an_optional_section_with_the_problem_opens_by_itself() {
        val html = PairingPages.setup("428193", SetupForm(error = "x", errorField = "musicPassword"))
        assertTrue(html.contains("<details class=\"card optional\" open>"))
        assertEquals(1, Regex("<details class=\"card optional\" open>").findAll(html).count())
    }

    @Test fun the_torrent_client_step_has_a_sign_in_per_client_and_a_skip() {
        val html = PairingPages.clients("428193", listOf(ClientNeed("qBittorrent", "192.168.1.9"), ClientNeed("Deluge", "192.168.1.10")),
            error = "Enter the sign-in", errorField = "clientPass1")
        assertTrue(html.contains("action=\"/setup/428193\""))
        assertTrue(html.contains("name=\"phase\" value=\"clients\""))
        assertTrue(html.contains("name=\"clientUser0\"") && html.contains("name=\"clientPass1\""))
        assertTrue(html.contains("name=\"skipClients\" value=\"1\""))
        assertTrue(html.contains("class=\"bad\""))
    }

    @Test fun the_save_page_asks_for_a_six_digit_numeric_pin() {
        val html = PairingPages.save("428193")
        assertTrue(html.contains("action=\"/save/428193\""))
        assertTrue(html.contains("inputmode=\"numeric\""))
        assertTrue(html.contains("maxlength=\"6\""))
        assertEquals(6, slotCount(html))
    }

    @Test fun the_restore_page_uses_the_same_six_slot_pin() {
        val html = PairingPages.restore("428193")
        assertTrue(html.contains("class=\"slots\""))
        assertEquals(6, slotCount(html))
    }

    /** The PIN slots are the empty spans in the slots row; nothing else in the page is one. */
    private fun slotCount(html: String): Int =
        html.substringAfter("class=\"slots\"").substringBefore("</div>").split("<span></span>").size - 1

    @Test fun the_restore_page_reads_the_file_in_the_browser_and_sends_base64() {
        val html = PairingPages.restore("428193")
        assertTrue(html.contains("action=\"/restore/428193\""))
        assertTrue(html.contains("readAsDataURL"))
        assertTrue(html.contains("name=\"bundle\""))
    }

    @Test fun the_pages_carry_the_cinema_name_and_roundel() {
        val html = PairingPages.setup("428193")
        assertTrue(html.contains("class=\"wordmark\">Cinema</span>"))
        assertTrue(html.contains("<svg"))
        assertTrue(html.contains("#0B0807"))
    }

    @Test fun errors_from_the_tv_are_escaped() {
        val html = PairingPages.setup("428193", SetupForm(error = "<script>alert('x')</script>"))
        assertFalse(html.contains("<script>alert"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"))
    }

    @Test fun every_page_is_mobile_width_and_declares_utf8() {
        listOf(PairingPages.setup("1"), PairingPages.save("1"), PairingPages.restore("1")).forEach {
            assertTrue(it.contains("width=device-width"))
            assertTrue(it.contains("charset=\"utf-8\""))
        }
    }
}
