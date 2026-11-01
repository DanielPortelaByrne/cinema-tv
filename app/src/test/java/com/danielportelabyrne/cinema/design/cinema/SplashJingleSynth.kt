package com.danielportelabyrne.cinema.design.cinema

import com.danielportelabyrne.cinema.design.cinema.SplashJingle.Timeline
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Renders the opening sting. It is not shipped: a TV's CPU takes many seconds to synthesise it, so `SplashJingleRender`
 * writes it once to `res/raw/splash_jingle.wav`, which is what the app plays. Run the render test to regenerate it
 * after changing the sound or [Timeline]. It is a small 70s analog-synth voice: a filter-swept sawtooth rises under the stripes as they feed into the ring, a sub-bass hit and a warm Am9 pad land
 * with the mark, and a pulse-wave lead answers as the wordmark appears. [Timeline] is the one clock the sound and
 * [SplashView] share, so the picture and the notes stay in step.
 */
object SplashJingleSynth {
    const val RATE = 44100

    @Volatile private var cached: ShortArray? = null

    private enum class Wave { SAW, PULSE }

    /** Mono 16-bit PCM at [RATE], [Timeline.END] long. Pure maths, so it can be checked off a device. */
    fun samples(): ShortArray {
        cached?.let { return it }
        val total = RATE * Timeline.END / 1000
        val bass = DoubleArray(total)
        val pad = DoubleArray(total)   // gets the chorus
        fun sec(ms: Int) = ms / 1000.0

        // The charge-up: it starts the moment the first stripe moves, already audible (a TV speaker cannot play the low
        // notes, so an octave voice carries it), and climbs from A1 to A2 as a low-pass filter opens, swelling to the hit.
        val sweepStart = sec(Timeline.SLIDE_START)
        val sweepLength = sec(Timeline.IMPACT - Timeline.SLIDE_START)
        fun climb(t: Double) = (t / sweepLength).coerceIn(0.0, 1.0)
        fun swell(t: Double) = min(1.0, t / 0.05) * (0.40 + 0.60 * climb(t).pow(1.3))
        voice(bass, sweepStart, sweepLength, Wave.SAW, doubleArrayOf(-12.0, 12.0), 0.62,
            hz = { t -> 55.0 * 2.0.pow(climb(t).pow(2.0)) },
            cutoff = { t -> 260.0 * 10.0.pow(climb(t)) },
            level = 0.30, env = ::swell)
        voice(bass, sweepStart, sweepLength, Wave.SAW, doubleArrayOf(-9.0, 9.0), 0.55,
            hz = { t -> 110.0 * 2.0.pow(climb(t).pow(2.0)) },
            cutoff = { t -> 500.0 * 6.0.pow(climb(t)) },
            level = 0.16, env = ::swell)

        // The hit: a sine that falls from 170 Hz to 38 Hz with a short noise click, then the sustained A1 sub-bass.
        val impact = sec(Timeline.IMPACT)
        var phase = 0.0
        var seed = 12345
        for (i in 0 until (RATE * 0.9).toInt()) {
            val t = i / RATE.toDouble()
            phase += 2 * PI * (38.0 + 132.0 * exp(-t * 20.0)) / RATE
            seed = seed * 1103515245 + 12345
            val noise = ((seed ushr 16) and 0x7fff) / 16384.0 - 1.0
            val idx = ((impact + t) * RATE).toInt()
            if (idx < total) bass[idx] += sin(phase) * exp(-t * 5.0) * 1.05 + noise * exp(-t * 220.0) * 0.30
        }
        voice(bass, impact, 1.7, Wave.SAW, doubleArrayOf(-5.0, 5.0), 0.75,
            hz = { 55.0 }, cutoff = { t -> 160.0 + 360.0 * exp(-t * 3.0) }, level = 0.55,
            env = { t -> min(1.0, t / 0.01) * exp(-t / 0.9) })
        voice(bass, impact, 1.7, Wave.SAW, doubleArrayOf(0.0), 0.8,
            hz = { 27.5 }, cutoff = { 120.0 }, level = 0.50,
            env = { t -> min(1.0, t / 0.01) * exp(-t / 0.8) })

        // The chords, three detuned saws a note, strummed a hair apart, the filter closing darker and slower than a bright pad
        // would. The hit is Am(maj7)9 (A E G# B C), the major seventh rubbing against the minor third; when the wordmark lands
        // the pad sinks to Dm9 over the same A bass (D F A C E), so the lead's B is the aching sixth and its E5 the ninth.
        val lockupAt = sec(Timeline.LOCKUP)
        doubleArrayOf(110.0, 164.81, 207.65, 246.94, 261.63, 329.63).forEachIndexed { n, hz ->
            voice(pad, impact + n * 0.014, lockupAt - impact + 0.05, Wave.SAW, doubleArrayOf(-9.0, 0.0, 9.0), 0.7,
                hz = { hz }, cutoff = { t -> 520.0 + 1700.0 * exp(-t * 2.6) }, level = 0.085,
                env = { t -> min(1.0, t / 0.015) * exp(-t / 1.1) })
        }
        doubleArrayOf(146.83, 174.61, 220.0, 261.63, 329.63, 440.0).forEachIndexed { n, hz ->
            voice(pad, lockupAt + n * 0.02, 1.7, Wave.SAW, doubleArrayOf(-9.0, 0.0, 9.0), 0.7,
                hz = { hz }, cutoff = { t -> 560.0 + 1500.0 * exp(-t * 2.0) }, level = 0.085,
                env = { t -> min(1.0, t / 0.07) * exp(-t / 1.0) })
        }

        // The answer as the wordmark appears: a pulse-wave lead, B4 then E5, with a little late vibrato.
        val lockup = lockupAt
        listOf(493.88 to 0.0 to 0.36, 659.25 to 0.2 to 1.3).forEach { (note, length) ->
            val (hz, offset) = note
            voice(pad, lockup + offset, length, Wave.PULSE, doubleArrayOf(-6.0, 6.0), 0.6,
                hz = { t -> hz * 2.0.pow(sin(2 * PI * 5.6 * t) * 7.0 * min(1.0, t / 0.4) / 1200.0) },
                cutoff = { t -> 1200.0 + 2200.0 * exp(-t * 4.0) }, level = 0.11,
                env = { t -> min(1.0, t / 0.03) * exp(-t / 0.7) })
        }

        // Ensemble chorus on the pad and lead (two slowly modulated delays), the classic string-machine shimmer.
        val mix = DoubleArray(total)
        for (i in 0 until total) {
            val t = i / RATE.toDouble()
            val d1 = (0.009 + 0.003 * sin(2 * PI * 0.31 * t)) * RATE
            val d2 = (0.014 + 0.004 * sin(2 * PI * 0.47 * t + 2.1)) * RATE
            mix[i] = bass[i] + pad[i] + 0.55 * tap(pad, i - d1) + 0.55 * tap(pad, i - d2)
        }

        // One slap-back echo for the room, a soft saturation to glue the layers, then the last 200 ms fade out.
        val delay = (RATE * 0.21).toInt()
        for (i in delay until total) mix[i] += mix[i - delay] * 0.24
        for (i in 0 until total) mix[i] = tanh(mix[i] * 1.15)
        val tail = (RATE * 0.2).toInt()
        for (i in 0 until tail) mix[total - 1 - i] *= i / tail.toDouble()
        val peak = max(mix.maxOf { abs(it) }, 1e-6)
        val gain = 0.85 / peak
        return ShortArray(total) { (mix[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }.also { cached = it }
    }

    private fun tap(buffer: DoubleArray, position: Double): Double {
        if (position < 0) return 0.0
        val i = floor(position).toInt()
        val frac = position - i
        return buffer[i] * (1 - frac) + buffer.getOrElse(i + 1) { 0.0 } * frac
    }

    /**
     * One analog-style voice: detuned oscillators ([detuneCents] one per oscillator) through a resonant two-pole
     * low-pass. [hz], [cutoff] and [env] are functions of seconds since the voice started. [damping] is the filter's
     * 1/Q: lower is more resonant. Adds into [bus] from [startSec] for [lengthSec].
     */
    private fun voice(
        bus: DoubleArray, startSec: Double, lengthSec: Double, wave: Wave, detuneCents: DoubleArray, damping: Double,
        hz: (Double) -> Double, cutoff: (Double) -> Double, level: Double, env: (Double) -> Double
    ) {
        val start = (startSec * RATE).toInt()
        val length = min((lengthSec * RATE).toInt(), bus.size - start)
        val phases = DoubleArray(detuneCents.size)
        var low = 0.0
        var band = 0.0
        for (i in 0 until length) {
            val t = i / RATE.toDouble()
            val base = hz(t)
            var osc = 0.0
            for (k in phases.indices) {
                phases[k] += base * 2.0.pow(detuneCents[k] / 1200.0) / RATE
                val p = phases[k] - floor(phases[k])
                osc += if (wave == Wave.SAW) 2 * p - 1 else if (p < 0.32) 1.0 else -1.0
            }
            osc /= phases.size
            val f = 2 * sin(PI * min(cutoff(t), RATE / 6.0) / RATE)
            low += f * band
            val high = osc - low - damping * band
            band += f * high
            val release = min(1.0, (lengthSec - t) / 0.04)
            bus[start + i] += low * env(t) * level * release
        }
    }

}
