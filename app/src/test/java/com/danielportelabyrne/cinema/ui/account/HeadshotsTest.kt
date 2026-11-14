package com.danielportelabyrne.cinema.ui.account

import com.danielportelabyrne.cinema.ui.account.Headshots.Face
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which cast photos count as headshots (TMDB's h632 portraits are 421 x 632). */
class HeadshotsTest {
    private val w = 421; private val h = 632

    @Test fun a_portrait_with_the_face_near_the_top_is_a_headshot() {
        assertTrue(Headshots.usable(Face(210f, 200f, 90f, 0.5f), w, h))
    }

    @Test fun a_distant_full_length_photo_is_not() {
        assertFalse(Headshots.usable(Face(210f, 150f, 30f, 0.5f), w, h))   // the face is a small part of the photo
    }

    @Test fun a_face_cut_off_at_the_top_or_low_in_the_frame_is_not() {
        assertFalse(Headshots.usable(Face(210f, 40f, 90f, 0.5f), w, h))    // no room above the eyes for the head
        assertFalse(Headshots.usable(Face(210f, 560f, 90f, 0.5f), w, h))   // a face down at the bottom of the photo
    }

    @Test fun the_best_framed_comes_first() {
        val centredAndClose = Headshots.score(Face(210f, 220f, 110f, 0.52f), w)
        val offToTheSide = Headshots.score(Face(90f, 220f, 110f, 0.52f), w)
        val further = Headshots.score(Face(210f, 220f, 65f, 0.52f), w)
        assertTrue(centredAndClose > offToTheSide)
        assertTrue(centredAndClose > further)
    }
}
