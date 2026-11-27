package com.danielportelabyrne.cinema.mobile.offline

import com.danielportelabyrne.cinema.data.movies.ByteSize

/** What to say about a download that has not finished. */
object DownloadText {
    /** "45% downloaded", or "Downloading" until there is progress to report. */
    fun percent(progress: Float): String = if (progress > 0f) "${whole(progress)}% downloaded" else "Downloading"

    /** "45% · 1.2 GB of 2.7 GB", or what there is to say when the size is not known. */
    fun amount(done: Long, total: Long?): String =
        if (total == null || total <= 0) "${ByteSize.format(done)} so far"
        else "${whole(done.toFloat() / total)}% · ${ByteSize.format(done)} of ${ByteSize.format(total)}"

    /** "8.4 MB/s · 3 min left", or null before any speed has been measured. */
    fun pace(bytesPerSecond: Long, done: Long, total: Long?): String? {
        if (bytesPerSecond <= 0) return null
        val speed = String.format(java.util.Locale.UK, "%.1f MB/s", bytesPerSecond / 1e6)
        val left = total?.takeIf { it > done }?.let { timeLeft((it - done) / bytesPerSecond) }
        return listOfNotNull(speed, left).joinToString(" · ")
    }

    fun timeLeft(seconds: Long): String {
        val minutes = (seconds + 59) / 60
        return when {
            seconds < 60 -> "under a minute left"
            minutes < 60 -> "$minutes min left"
            else -> "${minutes / 60} h ${minutes % 60} min left"
        }
    }

    /** Never 100 until the file is finished. */
    private fun whole(fraction: Float) = (fraction * 100).toInt().coerceIn(0, 99)
}
