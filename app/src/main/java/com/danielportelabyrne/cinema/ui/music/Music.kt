package com.danielportelabyrne.cinema.ui.music

import com.danielportelabyrne.cinema.data.music.MusicTrack
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.ui.RemoteImages

/**
 * The music server for the whole process: one client per saved config, and the queue handed to the now-playing
 * screen. Cover art is keyed `subsonic:/rest/getCoverArt?...` and signed only when fetched.
 */
object Music {
    @Volatile var client: SubsonicClient? = null
        private set

    /** The client for [config], or null when Music is off. */
    @Synchronized fun use(config: SubsonicConfig?): SubsonicClient? {
        if (config == null) { client = null; RemoteImages.signers.remove(RemoteImages.MUSIC); return null }
        client?.takeIf { it.config == config }?.let { return it }
        val c = SubsonicClient(config)
        client = c
        RemoteImages.signers[RemoteImages.MUSIC] = { path -> client?.signed(path) }
        return c
    }

    /** The image key for [coverArt] at [size] pixels. */
    fun cover(coverArt: String?, size: Int): String? {
        val c = client ?: return null
        return RemoteImages.MUSIC + ":" + c.coverPath(coverArt ?: return null, size)
    }

    /** What the now-playing screen opens with. */
    class Queue(val tracks: List<MusicTrack>, val start: Int)

    @Volatile var queued: Queue? = null

    /** Show lyrics when a track has them; Up or Down on now playing flips it. */
    @Volatile var lyricsOn = true
}
