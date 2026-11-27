package com.danielportelabyrne.cinema.mobile.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTextTest {
    @Test fun percentNeverClaimsToBeDone() {
        assertEquals("Downloading", DownloadText.percent(0f))
        assertEquals("45% downloaded", DownloadText.percent(0.456f))
        assertEquals("99% downloaded", DownloadText.percent(1f))
    }

    @Test fun amountSaysHowMuchOfHowMuch() {
        assertEquals("44% · 1.2 GB of 2.7 GB", DownloadText.amount(1_200_000_000, 2_700_000_000))
        assertEquals("0% · 0 MB of 2.7 GB", DownloadText.amount(0, 2_700_000_000))
        assertEquals("350 MB so far", DownloadText.amount(350_000_000, null))
    }

    @Test fun paceGivesSpeedAndTimeLeft() {
        assertNull(DownloadText.pace(0, 0, 2_700_000_000))
        assertEquals("5.0 MB/s · 5 min left", DownloadText.pace(5_000_000, 1_200_000_000, 2_700_000_000))
        assertEquals("5.0 MB/s", DownloadText.pace(5_000_000, 100, null))
    }

    @Test fun timeLeftRoundsUp() {
        assertEquals("under a minute left", DownloadText.timeLeft(59))
        assertEquals("2 min left", DownloadText.timeLeft(61))
        assertEquals("1 h 5 min left", DownloadText.timeLeft(3_900))
    }
}
