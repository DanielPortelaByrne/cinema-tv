package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.danielportelabyrne.cinema.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The opening's sound, "the velvet skip": an upright-bass pluck, two quick felt-piano notes, a held breath, then a
 * late bass-and-piano landing (A2 pluck; C4 sighing to A3; silence; a slide up to D2 under a rolled, low Dm9). Shipped as
 * `res/raw/splash_jingle.wav`, rendered ahead of time by `SplashJingleSynth` (test sources) from physical models of the
 * two instruments. [Timeline] is the one score the sound and [SplashView] share, and the picture reads its clock from
 * the sound as it actually leaves the speaker ([Voice.positionMs]), so the two cannot drift apart.
 */
object SplashJingle {
    /** Milliseconds from the start of the sound file. Change these and the sound together: re-run `SplashJingleRender`. */
    object Timeline {
        const val BUP = 150               // the bass pluck: the three outer discs land
        const val DI = 355                // first piano note (C4): the orange disc lands
        const val DIT = 447               // second piano note (A3), a hair early: the saffron disc lands
        const val DUM = 1040              // the late landing: bass slides to D2, the chord rolls, the stack snaps into register
        const val END = 1640              // the sound file ends (the chord has been let go)

        const val DROP = 130              // how long the outer discs fall before they land on BUP
        const val PIANO_DROP = 85         // the same for each small disc before its note
        const val SNAP = 110              // the stack slides into register over this, arriving exactly on DUM
        const val TRIANGLE = DUM + 34     // the play triangle prints with the chord's top note
        const val LOCKUP = DUM + 230      // the mark slides left and the wordmark comes out from behind it
        const val LOCKUP_LENGTH = 620
        const val FADE_START = DUM + 1300 // the picture holds, then fades over the finished page
        const val FADE_LENGTH = 800
    }

    const val RATE = 44100
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** The one voice allowed to sound at a time, so two openings can never play over each other. */
    @Volatile private var current: Voice? = null

    /**
     * The opening's sound, loaded into a static [AudioTrack] and ready to [Voice.play]; null if the device cannot play it
     * (the picture then runs on its own clock). Stops any earlier voice that is still sounding.
     */
    fun prepare(context: Context): Voice? {
        current?.stop()
        return try {
            val pcm = readWav(context)
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            // A static track only becomes INITIALIZED once its data is written (until then it is NO_STATIC_DATA).
            if (track.write(pcm, 0, pcm.size) != pcm.size || track.state != AudioTrack.STATE_INITIALIZED) {
                track.release(); null
            } else Voice(track, pcm.size / 2).also { current = it }
        } catch (e: Exception) { android.util.Log.w("Opening", "no sound for the opening", e); null }
    }

    /** 16-bit stereo samples from the shipped WAV's data chunk. */
    private fun readWav(context: Context): ShortArray {
        val bytes = context.resources.openRawResource(R.raw.splash_jingle).use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = b.getInt(at + 4)
            if (id == "data") {
                val out = ShortArray(minOf(size, bytes.size - at - 8) / 2)
                b.position(at + 8); b.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
                return out
            }
            at += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    class Voice internal constructor(private val track: AudioTrack, private val frames: Int) {
        private val stamp = AudioTimestamp()
        @Volatile private var released = false

        fun play() { if (!released) runCatching { track.play() } }

        /**
         * Where the sound is, in [Timeline] milliseconds, as heard: from the output's own timestamp (which allows for
         * the device's output latency), else from the playback head. Null before it starts sounding and once it has
         * finished, when the picture carries on by itself.
         */
        fun positionMs(): Double? {
            if (released) return null
            return try {
                val head = track.playbackHeadPosition
                if (head >= frames) return null
                val frame = if (track.getTimestamp(stamp) && stamp.framePosition > 0)
                    stamp.framePosition + (System.nanoTime() - stamp.nanoTime) * RATE / 1e9
                else head.toDouble()
                if (frame <= 0.0) null else frame * 1000.0 / RATE
            } catch (_: Exception) { null }
        }

        /** Stops the sound without a click: the mixer ramps the volume down, then the track is released. */
        fun stop() {
            if (released) return
            released = true
            if (current === this) current = null
            runCatching { track.setVolume(0f) }
            main.postDelayed({ runCatching { track.stop() }; runCatching { track.release() } }, 60)
        }
    }
}

/**
 * The opening's clock. It holds at zero until the sound is actually coming out (or, if no sound arrives within
 * [waitMs], runs on its own), then follows the sound's position, never stepping backwards; once the sound has ended
 * it carries on in real time from where it was. Pure arithmetic, so it is tested off a device.
 */
class IdentClock(private val waitMs: Double = 450.0) {
    var ms = 0.0
        private set
    private var firstNs = Long.MIN_VALUE
    private var lastNs = 0L
    private var following = false
    private var alone = false

    /** True once the clock runs without the sound (none came, or it has finished). */
    val freeRunning: Boolean get() = alone

    fun tick(nowNs: Long, audioMs: Double?): Double {
        if (firstNs == Long.MIN_VALUE) { firstNs = nowNs; lastNs = nowNs }
        val dt = (nowNs - lastNs) / 1e6
        lastNs = nowNs
        if (!following && !alone) {
            when {
                audioMs != null -> following = true
                (nowNs - firstNs) / 1e6 > waitMs -> { alone = true; return ms }   // start from here, not from the wait
                else -> return ms
            }
        }
        if (following && audioMs == null) { following = false; alone = true }
        ms = if (following && audioMs != null) maxOf(ms, audioMs) else ms + dt
        return ms
    }
}
