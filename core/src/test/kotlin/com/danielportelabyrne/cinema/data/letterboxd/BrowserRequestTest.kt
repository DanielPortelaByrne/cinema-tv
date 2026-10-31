package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRequestTest {
    @Test fun `Letterboxd and the browser check's hosts are loaded`() {
        assertFalse(LetterboxdPages.notNeededByBrowser("letterboxd.com", "/static/js/app.js"))
        assertFalse(LetterboxdPages.notNeededByBrowser("a.ltrbxd.com", "/x/y"))
        assertFalse(LetterboxdPages.notNeededByBrowser("challenges.cloudflare.com", "/turnstile/v0/api.js"))
    }

    @Test fun `other hosts are refused`() {
        assertTrue(LetterboxdPages.notNeededByBrowser("googletagmanager.com", "/gtm.js"))
        assertTrue(LetterboxdPages.notNeededByBrowser("fundingchoicesmessages.google.com", "/x"))
        assertTrue(LetterboxdPages.notNeededByBrowser(null, "/"))
    }

    @Test fun `styles fonts and pictures are refused, even from Letterboxd`() {
        assertTrue(LetterboxdPages.notNeededByBrowser("letterboxd.com", "/static/css/site.css"))
        assertTrue(LetterboxdPages.notNeededByBrowser("s.ltrbxd.com", "/font/inter.woff2?v=3"))
        assertTrue(LetterboxdPages.notNeededByBrowser("a.ltrbxd.com", "/poster.JPG"))
    }

    @Test fun `a suffix match needs a dot before the host`() {
        assertTrue(LetterboxdPages.notNeededByBrowser("evilletterboxd.com", "/x.js"))
    }
}
