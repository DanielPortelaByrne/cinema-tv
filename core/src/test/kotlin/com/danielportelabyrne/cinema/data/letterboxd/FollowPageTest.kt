package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowPageTest {
    @Test fun a_typed_name_or_profile_address_gives_the_username() {
        assertEquals("davidehrlich", FollowPage.normalize("davidehrlich"))
        assertEquals("dan", FollowPage.normalize("  @Dan  "))
        assertEquals("dan", FollowPage.normalize("https://letterboxd.com/dan/"))
        assertEquals("dan", FollowPage.normalize("letterboxd.com/dan/films/?x=1"))
    }

    @Test fun what_cannot_be_a_username_is_refused() {
        assertNull(FollowPage.normalize(""))
        assertNull(FollowPage.normalize("two words"))
        assertNull(FollowPage.normalize("name<script>"))
        assertNull(FollowPage.normalize("x".repeat(41)))
    }
}
