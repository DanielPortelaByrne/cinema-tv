package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListFocusTest {
    private val old = listOf<String?>(null, "a", "b", "c", "cancel")

    @Test fun focusFollowsTheSameRowWhenItMoves() =
        assertEquals(1, ListFocus.after(old, 3, listOf("a", "c", "cancel")))

    @Test fun deletedMiddleRowMovesFocusToTheNextOne() =
        assertEquals(2, ListFocus.after(old, 2, listOf(null, "a", "c", "cancel")))

    @Test fun deletedLastRowMovesFocusToThePreviousOne() =
        assertEquals(1, ListFocus.after(listOf(null, "a", "b"), 2, listOf(null, "a")))

    @Test fun deletedFirstRowMovesFocusToTheNewFirst() =
        assertEquals(0, ListFocus.after(listOf("a", "b"), 0, listOf("b")))

    @Test fun nothingLeftGivesNoAnswer() {
        assertNull(ListFocus.after(listOf("a"), 0, emptyList()))
        assertNull(ListFocus.after(old, 9, listOf("a")))
    }
}
