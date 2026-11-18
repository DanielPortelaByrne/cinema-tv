package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class TitleLogosTest {
    @Test fun an_imdb_id_gives_the_logo_address() {
        assertEquals("https://images.metahub.space/logo/medium/tt0111161/img", TitleLogos.url("tt0111161"))
        assertEquals("https://images.metahub.space/logo/medium/tt0111161/img", TitleLogos.url(" tt0111161 "))
    }

    @Test fun a_title_with_no_usable_id_gives_none() {
        assertNull(TitleLogos.url(null))
        assertNull(TitleLogos.url(""))
        assertNull(TitleLogos.url("12345"))
        assertNull(TitleLogos.url("tt12"))
        assertNull(TitleLogos.url("tt0111161/../x"))
    }

    @Test fun a_wide_logo_is_fitted_by_its_width() {
        // 1000x200 in a 600x300 box is drawn 600x120: tall enough.
        assertTrue(TitleLogos.usable(1000, 200, 600, 300, 60))
    }

    @Test fun a_tall_logo_is_fitted_by_its_height() {
        assertTrue(TitleLogos.usable(300, 600, 800, 200, 60))
    }

    @Test fun a_hairline_logo_is_too_small_to_read() {
        // 2000x40 in a 600-wide box is drawn 600x12.
        assertFalse(TitleLogos.usable(2000, 40, 600, 300, 60))
    }

    @Test fun a_missing_or_empty_picture_is_not_usable() {
        assertFalse(TitleLogos.usable(0, 100, 600, 300, 60))
        assertFalse(TitleLogos.usable(100, 0, 600, 300, 60))
        assertFalse(TitleLogos.usable(100, 100, 0, 300, 60))
    }
}
