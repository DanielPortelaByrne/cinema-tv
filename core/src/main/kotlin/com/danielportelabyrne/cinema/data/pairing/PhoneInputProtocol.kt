package com.danielportelabyrne.cinema.data.pairing

/**
 * What the phone and the TV say to each other over the typing WebSocket. Every message is one text frame; fields are
 * separated by a tab, and tab, CR and LF inside a value are turned into spaces so a value can never break the framing.
 *
 * Phone to TV:
 * - `T<fieldId>\t<text>` the full text of the field being typed in (replace, never append)
 * - `D` Done
 * - `P` keep-alive, about every 20 seconds
 *
 * TV to phone:
 * - `F<fieldId>\t<label>\t<secret 0|1>\t<maxLength>\t<text>` the focused field. A secret field sends no text, so a
 *   password never reaches the phone.
 * - `N` no text field is focused
 */
object PhoneInputProtocol {
    sealed class FromPhone {
        data class Text(val fieldId: String, val text: String) : FromPhone()
        object Done : FromPhone()
        object KeepAlive : FromPhone()
    }

    /** Reads a message from the phone, or null when it is not one this protocol knows. */
    fun parseFromPhone(frame: String): FromPhone? = when {
        frame == "D" -> FromPhone.Done
        frame == "P" -> FromPhone.KeepAlive
        frame.startsWith("T") -> {
            val rest = frame.substring(1)
            val tab = rest.indexOf('\t')
            if (tab <= 0) null else FromPhone.Text(rest.substring(0, tab), rest.substring(tab + 1))
        }
        else -> null
    }

    /** Tab, CR and LF become spaces. */
    fun sanitise(value: String): String =
        if (value.none { it == '\t' || it == '\r' || it == '\n' }) value
        else value.map { if (it == '\t' || it == '\r' || it == '\n') ' ' else it }.joinToString("")

    /** [text] cut to [max] characters; a [max] of zero or less means no limit. */
    fun clampToMax(text: String, max: Int): String = if (max > 0) text.take(max) else text

    fun formatFocus(fieldId: String, label: String, secret: Boolean, maxLength: Int, text: String): String =
        "F${sanitise(fieldId)}\t${sanitise(label)}\t${if (secret) 1 else 0}\t$maxLength\t${if (secret) "" else sanitise(text)}"

    fun formatNone(): String = "N"
}
