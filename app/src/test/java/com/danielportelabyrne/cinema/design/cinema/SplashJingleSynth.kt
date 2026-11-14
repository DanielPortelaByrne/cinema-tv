package com.danielportelabyrne.cinema.design.cinema

import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the opening's sound, "the velvet skip". It is not shipped: `SplashJingleRender` writes it once to
 * `res/raw/splash_jingle.wav`, which is what the app plays. Run the render test again after changing the score or
 * [Timeline].
 *
 * Two instruments, both physical models rather than oscillators:
 *  - an upright bass, plucked: each harmonic of the string as two slightly different decaying vibrations (the string
 *    swings in two planes, which gives a real pluck its uneven bloom), shaped by where the finger pulls the string and
 *    how soft the fingertip is, sharp for an instant as the string is stretched and then settling, through the
 *    resonances of a bass's wooden body, with the finger's snap and the string's knock on the fingerboard;
 *  - a felt piano: three strings to a note, a hair out of tune with each other, with a piano's stretched (inharmonic)
 *    overtones, a hammer muffled by felt so the top end barely speaks, the thump of the key, and the dampers' soft
 *    landing when each note is let go.
 * Then a small, dry room, a little air between the players. Everything is quiet: a -8 dBFS ceiling, about -20 LUFS at its loudest.
 *
 * The score (milliseconds from [Timeline]): the bass plucks A2 on BUP and rings a moment; the piano crushes the
 * third over it, C4-G4 bending to C#4-G4 (DI, DIT: minor to major, the seventh held above); then nothing; then, late, the bass slides up into D2 under a low,
 * smoky D minor 9 (F3 C4 E4, no root) rolled up from just after it: the A resolving home, in the minor.
 */
object SplashJingleSynth {
    const val RATE = SplashJingle.RATE

    /** The loudest the sound gets, as a fraction of full scale (-8 dBFS). */
    const val CEILING = 0.4

    @Volatile private var cached: ShortArray? = null

    /** Interleaved 16-bit stereo at [RATE], [Timeline.END] long. Pure maths, so it can be checked off a device. */
    fun samples(): ShortArray {
        cached?.let { return it }
        val frames = RATE * Timeline.END / 1000
        val left = DoubleArray(frames)
        val right = DoubleArray(frames)
        val dry = Mix(left, right)

        // Bup: a round, soft A2, ringing into the piano's first chord before the hand stops it.
        bass(dry, Timeline.BUP.toDouble(), 110.0, velocity = 0.80, holdSec = 0.20, dampTau = 0.04, seed = 11)
        // Di-dit: the blues pianist's crushed third. C4-G4 (the blue minor third under the seventh), lifted quickly, then the
        // same hand with only the C bent up to C#: C#4-G4, the A7. One note moves, the G stays put, so the ear reads it at once.
        piano(dry, Timeline.DI.toDouble(), 261.63, velocity = 0.46, holdSec = 0.045, seed = 21)
        piano(dry, Timeline.DI + 3.0, 392.0, velocity = 0.38, holdSec = 0.045, seed = 26)
        piano(dry, Timeline.DIT.toDouble(), 277.18, velocity = 0.54, holdSec = 0.11, seed = 22)
        piano(dry, Timeline.DIT + 3.0, 392.0, velocity = 0.42, holdSec = 0.11, seed = 27)
        // Dum: the bassist slides up into D2 from a semitone under; the chord rolls up from just behind, top note last.
        bass(dry, Timeline.DUM.toDouble(), 73.42, velocity = 0.80, holdSec = 0.42, dampTau = 0.09, seed = 12, slideCents = -100.0)
        piano(dry, Timeline.DUM + 9.0, 174.61, velocity = 0.44, holdSec = 0.41, seed = 23)
        piano(dry, Timeline.DUM + 21.0, 261.63, velocity = 0.40, holdSec = 0.40, seed = 24)
        piano(dry, Timeline.DUM + 36.0, 329.63, velocity = 0.47, holdSec = 0.39, seed = 25)

        room(left, right)

        // Master: clear the rumble a TV cannot play anyway, set the ceiling, and close the last moment to silence.
        for (ch in listOf(left, right)) {
            val hp1 = Biquad.highpass(42.0, 0.707); val hp2 = Biquad.highpass(42.0, 0.707)
            for (i in ch.indices) ch[i] = hp2.run(hp1.run(ch[i]))
        }
        val peak = max(max(left.maxOf { abs(it) }, right.maxOf { abs(it) }), 1e-9)
        val gain = CEILING / peak
        val tail = (RATE * 0.06).toInt()
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val fade = if (i >= frames - tail) 0.5 - 0.5 * cos(PI * (frames - 1 - i) / tail) else 1.0
            out[2 * i] = (left[i] * gain * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            out[2 * i + 1] = (right[i] * gain * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out.also { cached = it }
    }

    private class Mix(val left: DoubleArray, val right: DoubleArray) {
        /** Adds a mono [voice] starting at frame [start], panned (-1 left .. 1 right, constant power). */
        fun add(voice: DoubleArray, start: Int, pan: Double) {
            val a = (pan + 1) * PI / 4
            val gl = cos(a); val gr = sin(a)
            for (i in voice.indices) {
                val j = start + i
                if (j < 0 || j >= left.size) continue
                left[j] += voice[i] * gl; right[j] += voice[i] * gr
            }
        }
    }

    private fun frame(ms: Double) = (ms * RATE / 1000).toInt()

    // ---- upright bass, pizzicato --------------------------------------------------------------------------------------

    private fun bass(mix: Mix, atMs: Double, hz: Double, velocity: Double, holdSec: Double, dampTau: Double, seed: Long, slideCents: Double = 0.0) {
        val length = (RATE * (holdSec + dampTau * 9)).toInt()
        val string = DoubleArray(length)
        val pluckAt = 0.23                         // where the finger pulls, as a fraction of the string from the bridge
        val finger = 480.0 + 420.0 * velocity      // a fingertip is soft: it rounds off the top of the spectrum
        val inharmonic = 6e-5
        var n = 1
        while (true) {
            val f = n * hz * sqrt(1 + inharmonic * n * n)
            if (f > 4500 || n > 48) break
            val shape = sin(n * PI * pluckAt) / n * exp(-(f / finger).pow(2))
            val t60 = 1.3 / (1 + (f / 200).pow(1.45))
            // Two planes of vibration: the second slightly sharp, quieter and slower to die, so the tone blooms unevenly.
            partial(string, f, shape * 0.72, t60, holdSec, dampTau, slideCents)
            partial(string, f * 1.00035, shape * 0.28, t60 * 1.8, holdSec, dampTau, slideCents)
            n++
        }
        // The wooden body: resonances that give the pluck its hollow, woody bloom.
        val body = DoubleArray(length)
        val modes = arrayOf(doubleArrayOf(58.0, 9.0, 0.28), doubleArrayOf(99.0, 12.0, 0.65), doubleArrayOf(143.0, 13.0, 0.45),
            doubleArrayOf(197.0, 15.0, 0.38), doubleArrayOf(268.0, 14.0, 0.30), doubleArrayOf(384.0, 12.0, 0.20),
            doubleArrayOf(565.0, 10.0, 0.12), doubleArrayOf(830.0, 8.0, 0.06))
        for (m in modes) {
            val bp = Biquad.bandpass(m[0], m[1])
            for (i in 0 until length) body[i] += bp.run(string[i]) * m[2]
        }
        val lp = Biquad.lowpass(2800.0, 0.6)
        for (i in 0 until length) body[i] = lp.run(string[i] * 0.55 + body[i] * 1.6)
        // The finger: a brief snap as it leaves the string, and the string's knock against the fingerboard.
        val rnd = Random(seed)
        val snap = Biquad.bandpass(2300.0, 1.1); val knock = Biquad.lowpass(520.0, 0.7)
        for (i in 0 until min(length, RATE / 20)) {
            val t = i / RATE.toDouble()
            val noise = rnd.nextGaussian()
            body[i] += snap.run(noise) * exp(-t / 0.0035) * 0.018 * velocity + knock.run(noise) * exp(-t / 0.009) * 0.09 * velocity
        }
        for (i in 0 until length) body[i] *= velocity
        mix.add(body, frame(atMs), -0.08)
    }

    // ---- felt piano --------------------------------------------------------------------------------------------------

    private fun piano(mix: Mix, atMs: Double, hz: Double, velocity: Double, holdSec: Double, seed: Long) {
        val length = (RATE * (holdSec + 0.5)).toInt()
        val note = DoubleArray(length)
        val rnd = Random(seed)
        val struckAt = 1 / 8.3                         // the hammer hits an eighth of the way along the string
        val felt = 400.0 + 650.0 * velocity            // a felt-muffled hammer: soft, dark, more so when played gently
        val inharmonic = 0.00042 * (hz / 440.0)
        val strings = doubleArrayOf(-0.8, 0.15, 0.95) // cents: a unison never quite in tune, so the note breathes
        val share = doubleArrayOf(0.36, 0.34, 0.30)
        val life = doubleArrayOf(0.85, 1.0, 1.3)
        var n = 1
        while (true) {
            val f = n * hz * sqrt(1 + inharmonic * n * n)
            if (f > 7000 || n > 24) break
            val shape = abs(sin(n * PI * struckAt)) / n.toDouble().pow(1.3) / sqrt(1 + (f / felt).pow(4))
            val t60 = 6.5 / (1 + (f / 520).pow(1.5))
            val damp = 0.034 / (1 + f / 2000)
            for (k in strings.indices) {
                val c = strings[k] + rnd.nextGaussian() * 0.15
                partial(note, f * 2.0.pow(c / 1200), shape * share[k], t60 * life[k], holdSec, damp, pluck = false,
                    phase = rnd.nextDouble() * 2 * PI, attackSec = 0.004 + 0.004 * (1 - velocity))
            }
            n++
        }
        // The action: the key's low thump and the felt's puff at the strike, and the damper settling when it is let go.
        val thump = Biquad.lowpass(260.0, 0.8); val puff = Biquad.bandpass(1300.0, 0.9); val damper = Biquad.lowpass(700.0, 0.7)
        val off = (holdSec * RATE).toInt()
        for (i in 0 until length) {
            val t = i / RATE.toDouble()
            val noise = rnd.nextGaussian()
            val strike = thump.run(noise) * exp(-t / 0.011) * 0.05 + puff.run(noise) * exp(-t / 0.0028) * 0.018
            val release = damper.run(noise) * if (i >= off) exp(-(i - off) / RATE.toDouble() / 0.012) * 0.006 else 0.0
            note[i] += strike + release
        }
        for (i in 0 until length) note[i] *= velocity * 1.5
        mix.add(note, frame(atMs), ((hz - 470) / 500).coerceIn(-0.35, 0.35))
    }

    /**
     * Adds one decaying vibration at [hz] to [bus]: a soft attack, a decay to -60 dB over [t60] seconds, and after
     * [holdSec] the extra damping of a hand or a damper (time constant [dampTau]). A [pluck] starts a few cents sharp and
     * settles, as a plucked string does when the stretch of the pluck lets go; [slideCents] (negative: from below) slides
     * into the note as a finger moving up the fingerboard does.
     */
    private fun partial(bus: DoubleArray, hz: Double, amp: Double, t60: Double, holdSec: Double, dampTau: Double,
                        slideCents: Double = 0.0, pluck: Boolean = true, phase: Double = 0.0, attackSec: Double = 0.004) {
        if (abs(amp) < 1e-5) return
        var p = phase
        val decay = 6.9078 / t60
        for (i in bus.indices) {
            val t = i / RATE.toDouble()
            val cents = (if (pluck) 16.0 * exp(-t / 0.035) else 0.0) + slideCents * exp(-(t / 0.03).pow(1.6))
            val f = hz * 2.0.pow(cents / 1200)
            p += 2 * PI * f / RATE
            var env = exp(-decay * t)
            if (t < attackSec) env *= 0.5 - 0.5 * cos(PI * t / attackSec)
            if (t > holdSec) env *= exp(-(t - holdSec) / dampTau)
            if (env < 1e-6 && t > holdSec) break
            bus[i] += amp * env * sin(p)
        }
    }

    // ---- room ---------------------------------------------------------------------------------------------------------

    /** A small, dry room: a few early reflections either side and a short, dark tail, all well under the players. */
    private fun room(left: DoubleArray, right: DoubleArray) {
        val n = left.size
        val send = DoubleArray(n)
        val tone = Biquad.lowpass(3800.0, 0.7)
        for (i in 0 until n) send[i] = tone.run((left[i] + right[i]) * 0.5)
        val earlyL = DoubleArray(n); val earlyR = DoubleArray(n)
        val tapsL = doubleArrayOf(7.1, 13.3, 19.7, 29.1); val tapsR = doubleArrayOf(8.9, 15.2, 22.6, 31.4)
        val gains = doubleArrayOf(0.13, 0.09, 0.065, 0.045)
        for (k in tapsL.indices) {
            val dl = frame(tapsL[k]); val dr = frame(tapsR[k])
            for (i in 0 until n) {
                if (i >= dl) earlyL[i] += send[i - dl] * gains[k]
                if (i >= dr) earlyR[i] += send[i - dr] * gains[k]
            }
        }
        // Tail: four delay lines mixed through a Hadamard matrix, each losing what a 0.42 s decay needs, and its highs.
        val lengths = doubleArrayOf(29.7, 37.1, 41.1, 43.7).map { frame(it) }
        val lines = lengths.map { DoubleArray(it) }
        val pos = IntArray(4)
        val g = lengths.map { 10.0.pow(-3.0 * it / RATE / 0.42) }
        val low = DoubleArray(4)
        val o = DoubleArray(4)
        for (i in 0 until n) {
            for (k in 0 until 4) { low[k] += 0.45 * (lines[k][pos[k]] - low[k]); o[k] = low[k] }
            val h0 = o[0] + o[1] + o[2] + o[3]; val h1 = o[0] - o[1] + o[2] - o[3]
            val h2 = o[0] + o[1] - o[2] - o[3]; val h3 = o[0] - o[1] - o[2] + o[3]
            val h = doubleArrayOf(h0, h1, h2, h3)
            for (k in 0 until 4) {
                lines[k][pos[k]] = send[i] * 0.22 + 0.5 * h[k] * g[k]
                pos[k] = (pos[k] + 1) % lengths[k]
            }
            left[i] += earlyL[i] + (o[0] + o[2]) * 0.075
            right[i] += earlyR[i] + (o[1] + o[3]) * 0.075
        }
    }

    /** A second-order filter (RBJ cookbook), transposed direct form II. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var z1 = 0.0
        private var z2 = 0.0
        fun run(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        companion object {
            private fun make(hz: Double, q: Double, coeffs: (cosw: Double, alpha: Double) -> DoubleArray): Biquad {
                val w = 2 * PI * hz / RATE
                val c = coeffs(cos(w), sin(w) / (2 * q))
                return Biquad(c[0] / c[3], c[1] / c[3], c[2] / c[3], c[4] / c[3], c[5] / c[3])
            }
            fun lowpass(hz: Double, q: Double) = make(hz, q) { cw, al -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + al, -2 * cw, 1 - al) }
            fun highpass(hz: Double, q: Double) = make(hz, q) { cw, al -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + al, -2 * cw, 1 - al) }
            /** Band-pass with 0 dB at its peak. */
            fun bandpass(hz: Double, q: Double) = make(hz, q) { cw, al -> doubleArrayOf(al, 0.0, -al, 1 + al, -2 * cw, 1 - al) }
        }
    }
}
