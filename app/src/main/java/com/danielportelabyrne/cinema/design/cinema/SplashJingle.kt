package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.danielportelabyrne.cinema.R

/**
 * The opening sting: a 70s analog-synth cue (a filter-swept saw under the stripes, a sub-bass hit and a dark Am(maj7)9 pad as the
 * mark closes, a pulse-wave lead as the wordmark appears) shipped as `res/raw/splash_jingle.wav`. It is rendered ahead
 * of time by `SplashJingleRender` (test sources) because a TV's CPU takes many seconds to synthesise it. [Timeline] is
 * the one clock the sound and [SplashView] share, so the picture and the notes stay in step.
 */
object SplashJingle {
    /** Milliseconds from the start of the splash. Change these and the sound together: re-run `SplashJingleRender`. */
    object Timeline {
        const val SLIDE_START = 250       // the stripes begin to glide in from the left
        const val SLIDE_STEP = 50         // each stripe follows the last by this much
        const val SLIDE_LENGTH = 400
        const val WRAP_START = 650        // the first stripe starts curling into its ring
        const val WRAP_STEP = 50
        const val WRAP_LENGTH = 700
        const val IMPACT = 1550           // the last ring closes: sub hit, chord, bloom
        const val TRIANGLE = 1450         // the play triangle pops in just before it
        const val LOCKUP = 1800           // the mark slides left and the wordmark appears
        const val LOCKUP_LENGTH = 650
        const val FADE_START = 3000       // the picture holds, the glow filling the screen, then eases away
        const val FADE_LENGTH = 800       // over the finished page
        const val END = 3500              // the sound ends here (the pad rings on under the fade)
    }

    /** The sting, loaded and ready to [MediaPlayer.start]; null if the device refuses to play it. */
    fun prepare(context: Context): MediaPlayer? = try {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        MediaPlayer.create(context, R.raw.splash_jingle, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
    } catch (_: Exception) { null }
}
