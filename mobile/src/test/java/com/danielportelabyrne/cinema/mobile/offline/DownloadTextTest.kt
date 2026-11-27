package com.danielportelabyrne.cinema.mobile.offline

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTextTest {
    @Test fun runningSaysNothingExtra() = assertNull(DownloadText.of(DownloadManager.STATUS_RUNNING, 0))

    @Test fun waitingForWifiIsSaid() {
        assertEquals("Waiting for Wi-Fi", DownloadText.of(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI))
        assertEquals("Waiting for Wi-Fi", DownloadText.of(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK))
        assertEquals("Waiting to start. Needs Wi-Fi.", DownloadText.of(DownloadManager.STATUS_PENDING, 0))
    }

    @Test fun failuresGiveTheirReason() {
        assertEquals("Download failed: not enough space on this phone.",
            DownloadText.of(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE))
        assertEquals("Download failed: Jellyfin answered 401. Delete it and try again.", DownloadText.of(DownloadManager.STATUS_FAILED, 401))
        assertEquals("Download failed. Delete it and try again.",
            DownloadText.of(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN))
    }

    @Test fun percentNeverClaimsToBeDone() {
        assertEquals("Downloading", DownloadText.percent(0f))
        assertEquals("45% downloaded", DownloadText.percent(0.456f))
        assertEquals("99% downloaded", DownloadText.percent(1f))
    }

    @Test fun lineGivesTheSizeWhenKnown() {
        assertEquals("Downloading · 4.2 GB", DownloadText.line(0f, "4.2 GB"))
        assertEquals("45% downloaded · 4.2 GB", DownloadText.line(0.45f, "4.2 GB"))
        assertEquals("45% downloaded", DownloadText.line(0.45f, null))
    }
}
