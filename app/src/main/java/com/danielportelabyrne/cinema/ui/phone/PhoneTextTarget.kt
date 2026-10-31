package com.danielportelabyrne.cinema.ui.phone

/**
 * A text field on the TV that a phone can type into. Whatever screen owns the field makes one of these and tells
 * [PhoneInput] when the field gains or loses focus, so the phone page always shows (and types into) the focused field.
 */
interface PhoneTextTarget {
    /** Stable for a field on a page, so the phone can tell when its text belongs to another field. */
    val fieldId: String
    /** What the phone page calls the field ("Server address", "Search"). */
    val label: String
    /** A password or key: its text is never sent to the phone. */
    val secret: Boolean get() = false
    val maxLength: Int get() = 200

    /** The field's text now. Read on the main thread. */
    fun currentText(): String

    /** The phone's full text for the field. Replaces what is there. Called on the main thread. */
    fun applyFromPhone(text: String)

    /** The phone's Done: what the TV's own Done or Next key would do. Called on the main thread. */
    fun phoneDone() {}
}
