package com.danielportelabyrne.cinema.ui

import android.app.Activity
import com.danielportelabyrne.cinema.data.pairing.Pairing
import com.danielportelabyrne.cinema.data.pairing.PairingRequest
import com.danielportelabyrne.cinema.data.pairing.PairingResponse
import com.danielportelabyrne.cinema.data.pairing.PairingServer
import com.danielportelabyrne.cinema.data.pairing.PhoneTextPage
import com.danielportelabyrne.cinema.ui.setup.lanAddress

/**
 * Typing from a phone: a local page with one text box, open for as long as a search is on screen. What is sent is handed
 * to [onText] on the TV's main thread, and the page stays open for the next send. [onConnected] runs once, when the first
 * phone opens the page, so the TV can take its QR code down. Stops on [stop].
 */
class PhoneTyping(
    private val activity: Activity,
    private val onText: (String) -> Unit,
    private val onConnected: () -> Unit = {},
) {
    private var server: PairingServer? = null
    private var connected = false

    /** Opens the page and returns its address for the QR code, or null when the TV has no network address. */
    fun start(): String? {
        stop()
        val host = lanAddress() ?: return null
        val s = PairingServer(Pairing(ttlMillis = SESSION_MILLIS, reusable = true), routes = ::route)
        server = s
        return s.urlFor(host, TYPE_ROUTE)
    }

    private fun route(request: PairingRequest): PairingResponse {
        if (request.route != TYPE_ROUTE) return PairingResponse.text(404, "Not found")
        if (!connected) {
            connected = true
            activity.runOnUiThread { onConnected() }
        }
        if (request.method == "GET") return PairingResponse.html(200, PhoneTextPage.form())
        val text = PhoneTextPage.textFrom(request.form)
            // Refused with a non-2xx status: the session is not spent by an empty send.
            ?: return PairingResponse.html(400, PhoneTextPage.form(problem = "Type something first."))
        activity.runOnUiThread { onText(text) }
        return PairingResponse.html(200, PhoneTextPage.form(sent = text))
    }

    fun stop() {
        server?.close()
        server = null
        connected = false
    }

    private companion object {
        const val TYPE_ROUTE = "type"
        /** Long enough for a search to stay open for a long while; it is stopped when the search closes. */
        const val SESSION_MILLIS = 8 * 60 * 60 * 1000L
    }
}
