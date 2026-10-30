package com.danielportelabyrne.cinema.data.search

/** The on-screen QWERTY keyboard's layout and what each key does to the query. Pure, so it can be unit tested. */
object Qwerty {
    /** Character rows, top to bottom; row offsets (in key widths) give the staggered look (`kbd()` in the mockup). */
    val ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
    val ROW_OFFSETS = listOf(0f, 0f, 0.5f, 1f)

    /** The symbols layer ("123#"): common punctuation and symbols, four rows, no stagger. */
    val SYMBOL_ROWS = listOf("1234567890", "@#$%&*()-+", "!\"'.,:;/?_", "<>[]{}\\|~^")
    val SYMBOL_OFFSETS = listOf(0f, 0f, 0f, 0f)

    /** The longest query the field accepts; plenty for a title or a name. */
    const val MAX_LENGTH = 60

    sealed class Key {
        data class Char(val value: kotlin.Char) : Key()
        object Space : Key()
        object Delete : Key()
        object Clear : Key()
    }

    /** [text] after pressing [key]: no leading or doubled spaces, and never longer than [MAX_LENGTH]. */
    fun type(text: String, key: Key): String = when (key) {
        is Key.Char -> if (text.length >= MAX_LENGTH) text else text + key.value
        Key.Space -> if (text.isEmpty() || text.endsWith(' ') || text.length >= MAX_LENGTH) text else "$text "
        Key.Delete -> text.dropLast(1)
        Key.Clear -> ""
    }
}

/**
 * Voice search as a small state machine, so the screen only ever shows a state that makes sense: asking for the
 * microphone, listening (with the words heard so far and the loudness), done, or unavailable (and why).
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object Requesting : VoiceState()
    data class Listening(val partial: String = "", val level: Float = 0f) : VoiceState()
    data class Done(val text: String) : VoiceState()
    data class Unavailable(val reason: String) : VoiceState()

    sealed class Event {
        /** The viewer asked to speak. */
        data class Start(val recogniserAvailable: Boolean, val permitted: Boolean) : Event()
        data class Permission(val granted: Boolean) : Event()
        data class Partial(val text: String) : Event()
        /** Loudness from the recogniser, in its dB scale (about -2 to 10). */
        data class Rms(val db: Float) : Event()
        data class Final(val text: String) : Event()
        data class Error(val code: Int) : Event()
        object Cancel : Event()
    }

    fun next(event: Event): VoiceState = when (event) {
        is Event.Start -> when {
            !event.recogniserAvailable -> Unavailable("Voice search isn't available on this TV")
            !event.permitted -> Requesting
            else -> Listening()
        }
        is Event.Permission -> if (this is Requesting) { if (event.granted) Listening() else Unavailable("Voice search needs the microphone. Allow it in Settings.") } else this
        is Event.Partial -> if (this is Listening) copy(partial = event.text.trim()) else this
        is Event.Rms -> if (this is Listening) copy(level = ((event.db + 2f) / 12f).coerceIn(0f, 1f)) else this
        is Event.Final -> if (this is Listening) { val t = event.text.trim().ifEmpty { partial }; if (t.isEmpty()) Unavailable(NO_MATCH) else Done(t) } else this
        is Event.Error -> if (this is Listening || this is Requesting) Unavailable(message(event.code, (this as? Listening)?.partial.orEmpty())) else this
        Event.Cancel -> Idle
    }

    companion object {
        const val NO_MATCH = "Didn't catch that. Try again"

        /** Plain words for SpeechRecognizer's error codes (android.speech.SpeechRecognizer.ERROR_*). */
        fun message(code: Int, heard: String): String = when (code) {
            9 -> "Voice search needs the microphone. Allow it in Settings."   // ERROR_INSUFFICIENT_PERMISSIONS
            1, 2, 4 -> "Voice search needs the internet right now"            // NETWORK_TIMEOUT, NETWORK, SERVER
            6, 7 -> if (heard.isNotEmpty()) "Didn't catch the end of that. Try again" else NO_MATCH   // SPEECH_TIMEOUT, NO_MATCH
            8 -> "The microphone is busy. Try again in a moment"              // RECOGNIZER_BUSY
            else -> "Voice search stopped. Try again"
        }
    }
}
