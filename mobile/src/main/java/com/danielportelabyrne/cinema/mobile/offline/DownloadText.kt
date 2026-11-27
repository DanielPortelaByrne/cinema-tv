package com.danielportelabyrne.cinema.mobile.offline

import android.app.DownloadManager

/** What to say about a download that has not finished, from DownloadManager's status and reason codes. */
object DownloadText {
    /** Null while it is simply downloading: the progress says that. */
    fun of(status: Int, reason: Int): String? = when (status) {
        DownloadManager.STATUS_PENDING -> "Waiting to start. Needs Wi-Fi."
        DownloadManager.STATUS_PAUSED -> when (reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI, DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection dropped. Will try again."
            else -> "Paused"
        }
        DownloadManager.STATUS_FAILED -> when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Download failed: not enough space on this phone."
            // A server's refusal arrives as its HTTP status.
            in 400..599 -> "Download failed: Jellyfin answered $reason. Delete it and try again."
            else -> "Download failed. Delete it and try again."
        }
        else -> null
    }

    /** "45% downloaded", or "Downloading" until there is progress to report. */
    fun percent(progress: Float): String =
        if (progress > 0f) "${(progress * 100).toInt().coerceIn(0, 99)}% downloaded" else "Downloading"

    /** The line under an unfinished download's bar: how far it is, and how big the whole file is. */
    fun line(progress: Float, size: String?): String = listOfNotNull(percent(progress), size).joinToString(" · ")
}
