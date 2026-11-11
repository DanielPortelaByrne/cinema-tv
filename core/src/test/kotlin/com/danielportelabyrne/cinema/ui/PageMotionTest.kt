package com.danielportelabyrne.cinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMotionTest {
    // A 1000 px screen, 24 px clear above, 56 below: 920 px of room. Content scrolls up to 5000.
    private fun target(scroll: Int, top: Int, bottom: Int, heading: Int? = null, max: Int = 5000) =
        PageScroll.target(scroll, 1000, max, top, bottom, heading, 24, 56)

    @Test fun aCardAlreadyInViewDoesNotMoveThePage() {
        assertEquals(800, target(800, 1000, 1400, heading = 950))
    }

    @Test fun downMovesOnlyFarEnoughToShowTheCardAndItsBottomClearance() {
        // Card bottom 2100 + 56 clear must reach 800 + 1000: the page moves to 1156, not to the card's top.
        assertEquals(1156, target(800, 1700, 2100, heading = 1640))
    }

    @Test fun upShowsTheRowsHeadingWithItsCards() {
        assertEquals(1616, target(2000, 1700, 2100, heading = 1640))
    }

    @Test fun aRowTallerThanTheScreenShowsTheFocusedCardFirst() {
        // Heading 1000, card 1500..2300: together 1300 px, more than the 920 of room. The card leads.
        assertEquals(1476, target(3000, 1500, 2300, heading = 1000))
        assertEquals(1356, target(0, 1500, 2300, heading = 1000))
    }

    @Test fun aCardTallerThanTheRoomShowsItsTop() {
        assertEquals(976, target(0, 1000, 2000))
        assertEquals(976, target(3000, 1000, 2000))
    }

    @Test fun targetsStayInsideTheRealScrollRange() {
        assertEquals(300, target(100, 1200, 1250, max = 300))
        assertEquals(0, target(400, 10, 200))
    }

    @Test fun movingUpCloseToTheTopGoesAllTheWay() {
        assertEquals(0, target(600, 40, 400))     // would rest at 16
        assertEquals(16, target(0, 300, 960))      // moving down by a little is just that
    }

    @Test fun aGlideIsNeverRestartedForTheSameTarget() {
        assertFalse(PageScroll.shouldStart(500, 900, 900))
        assertTrue(PageScroll.shouldStart(500, 900, 700))       // retarget from where it is
        assertFalse(PageScroll.shouldStart(700, null, 700))
        assertTrue(PageScroll.shouldStart(700, null, 650))
    }

    @Test fun sidewaysAlongARowLeavesThePageStill() {
        assertTrue(PageScroll.sideways(true, 1200, 1200))
        assertTrue(PageScroll.sideways(true, 1200, 1201))
        assertFalse(PageScroll.sideways(true, 1200, 1500))      // a grid row's lower card
        assertFalse(PageScroll.sideways(false, 1200, 1200))     // another row at the same height
        assertFalse(PageScroll.sideways(true, null, 1200))
    }

    // --- The rail's gate

    @Test fun leftInsideThePageIsAnOrdinaryMove() {
        assertEquals(LeftAction.MOVE, NavGate.left(toRail = false, rowScrollX = 900, fresh = false))
    }

    @Test fun aFreshLeftOnASettledFirstCardEntersTheRail() {
        assertEquals(LeftAction.ENTER_RAIL, NavGate.left(true, 0, true))
        assertEquals(LeftAction.ENTER_RAIL, NavGate.left(true, 1, true))
    }

    @Test fun aHeldLeftNeverSpillsIntoTheRail() {
        assertEquals(LeftAction.HOLD, NavGate.left(true, 0, false))
        assertEquals(LeftAction.HOLD, NavGate.left(true, null, false))
    }

    @Test fun aRowStillScrolledFinishesBeforeTheRail() {
        assertEquals(LeftAction.SETTLE_ROW, NavGate.left(true, 2, true))
        assertEquals(LeftAction.SETTLE_ROW, NavGate.left(true, 340, false))
    }

    @Test fun aControlInNoRowNeedsOnlyAFreshPress() {
        assertEquals(LeftAction.ENTER_RAIL, NavGate.left(true, null, true))
    }

    // --- The rail's motion

    @Test fun durationsScaleWithWhatIsLeft() {
        assertEquals(240L, RailMotion.duration(0f, 1f))
        assertEquals(200L, RailMotion.duration(1f, 0f))
        assertEquals(60L, RailMotion.duration(0.75f, 1f))
        assertEquals(150L, RailMotion.duration(0.75f, 0f))     // turned round three quarters open
        assertEquals(0L, RailMotion.duration(1f, 1f))
    }

    @Test fun everythingFollowsOneProgressAndClosedIsExactlyZero() {
        assertEquals(0f, RailMotion.push(0f, 92f, 420f), 0f)
        assertEquals(328f, RailMotion.push(1f, 92f, 420f), 0f)
        assertEquals(164f, RailMotion.push(0.5f, 92f, 420f), 0.001f)
        assertEquals(0.03f, RailMotion.dim(0.5f), 0.0001f)
        assertEquals(0.06f, RailMotion.dim(1f), 0f)
        assertEquals(0f, RailMotion.push(0.0004f, 92f, 420f), 0f)
        assertEquals(0f, RailMotion.dim(0.0004f), 0f)
        assertEquals(328f, RailMotion.push(1.3f, 92f, 420f), 0f)
    }
}
