package com.danielportelabyrne.cinema.ui.phone

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.danielportelabyrne.cinema.data.pairing.Pairing
import com.danielportelabyrne.cinema.data.pairing.PairingRequest
import com.danielportelabyrne.cinema.data.pairing.PairingResponse
import com.danielportelabyrne.cinema.data.pairing.PairingServer
import com.danielportelabyrne.cinema.data.pairing.PhoneInputProtocol
import com.danielportelabyrne.cinema.data.pairing.PhoneInputProtocol.FromPhone
import com.danielportelabyrne.cinema.data.pairing.PhoneSocket
import com.danielportelabyrne.cinema.data.pairing.PhoneSocketListener
import com.danielportelabyrne.cinema.data.pairing.PhoneTextPage
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import com.danielportelabyrne.cinema.ui.setup.PhoneQr
import com.danielportelabyrne.cinema.ui.setup.lanAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Typing from a phone, for every text field in the app. One session lives for as long as the app is open: one address,
 * one code, one WebSocket from the phone. Each text field tells it when it gains or loses focus ([focus], [blur]); the
 * phone page then shows that field and types into it live. Moving between fields never needs a new scan.
 *
 * The session slides: it ends after [IDLE_MILLIS] without the phone or the TV touching it, or [MAX_MILLIS] after it
 * began, or when the app is destroyed. A phone that drops reconnects by itself. Passwords are never sent to the phone.
 */
object PhoneInput {
    private const val PAGE_ROUTE = "type"
    private const val PLAIN_ROUTE = "plain"
    private const val IDLE_MILLIS = 30 * 60 * 1000L
    private const val MAX_MILLIS = 8 * 60 * 60 * 1000L
    private const val PUSH_DELAY_MILLIS = 120L
    private const val CARD_LINGER_MILLIS = 3500L

    private val main = Handler(Looper.getMainLooper())
    /** Socket writes must not run on the main thread. */
    private val sender = Executors.newSingleThreadExecutor { task -> Thread(task, "cinema-phone-send").apply { isDaemon = true } }
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private var activity: Activity? = null
    private var pairing: Pairing? = null
    private var server: PairingServer? = null
    @Volatile private var socket: PhoneSocket? = null
    private var target: PhoneTextTarget? = null
    private var card: View? = null
    private var pushPending: Runnable? = null

    /** Whether a phone is connected right now. */
    @Volatile var isConnected: Boolean = false
        private set

    fun attach(activity: Activity) { this.activity = activity }

    /** The app is going away: end the session. */
    fun detach(activity: Activity) {
        if (this.activity !== activity) return
        close()
        this.activity = null
    }

    fun addConnectionListener(listener: (Boolean) -> Unit) { listeners += listener }
    fun removeConnectionListener(listener: (Boolean) -> Unit) { listeners -= listener }

    // --- Fields ---------------------------------------------------------------------------------------------------

    /** [field] now has focus: the phone types into it. */
    fun focus(field: PhoneTextTarget) {
        target = field
        pairing?.touch()
        pushNow()
    }

    /** [field] lost focus or left the screen. If it was the target, the phone is told nothing is selected. */
    fun blur(field: PhoneTextTarget) {
        if (target !== field) return
        target = null
        pushNow()
        if (!isConnected) hideQr()
    }

    /** The text of [field] changed on the TV (its keyboard): tell the phone, if [field] is the one in use. */
    fun textChanged(field: PhoneTextTarget) {
        if (target === field) pushSoon()
    }

    fun isCurrent(field: PhoneTextTarget): Boolean = target === field

    // --- Session --------------------------------------------------------------------------------------------------

    /** The address a phone opens, starting the session if there is none or it has ended. Null without a network. */
    @Synchronized
    private fun address(): String? {
        val host = lanAddress() ?: return null
        val live = server?.takeIf { it.isOpen && pairing?.isExpired == false }
        if (live == null) {
            socket?.close(1001)
            val p = Pairing(ttlMillis = IDLE_MILLIS, idleMillis = IDLE_MILLIS, maxMillis = MAX_MILLIS, reusable = true)
            pairing = p
            server = PairingServer(p, routes = ::route, listener = listener)
        }
        return server?.urlFor(host, PAGE_ROUTE)
    }

    private fun route(request: PairingRequest): PairingResponse {
        pairing?.touch()
        return when (request.route) {
            PAGE_ROUTE -> if (request.method == "GET") PairingResponse.html(200, PhoneTextPage.live(pairing?.code.orEmpty()))
                else PairingResponse.text(405, "Method not allowed")
            // For a browser without WebSocket: one box and Send, handing the whole text to the field in use.
            PLAIN_ROUTE -> if (request.method == "GET") PairingResponse.html(200, PhoneTextPage.form()) else {
                val text = PhoneTextPage.textFrom(request.form)
                    ?: return PairingResponse.html(400, PhoneTextPage.form(problem = "Type something first."))
                main.post { target?.let { it.applyFromPhone(PhoneInputProtocol.clampToMax(text, it.maxLength)) } }
                PairingResponse.html(200, PhoneTextPage.form(sent = text))
            }
            else -> PairingResponse.text(404, "Not found")
        }
    }

    private val listener = object : PhoneSocketListener {
        override fun onOpen(socket: PhoneSocket) {
            this@PhoneInput.socket = socket
            setConnected(true)
            main.post { pushNow() }
        }

        override fun onMessage(socket: PhoneSocket, text: String) {
            when (val message = PhoneInputProtocol.parseFromPhone(text)) {
                is FromPhone.Text -> main.post { applyMessage(message) }
                FromPhone.Done -> main.post { target?.phoneDone() }
                FromPhone.KeepAlive, null -> Unit
            }
        }

        override fun onClose(socket: PhoneSocket) {
            if (this@PhoneInput.socket === socket) this@PhoneInput.socket = null
            if (this@PhoneInput.socket == null) setConnected(false)
        }
    }

    private fun applyMessage(message: FromPhone.Text) {
        val field = target ?: return
        // Text for a field the TV has since left belongs to nothing now.
        if (field.fieldId != message.fieldId) return
        field.applyFromPhone(PhoneInputProtocol.clampToMax(PhoneInputProtocol.sanitise(message.text), field.maxLength))
    }

    private fun setConnected(on: Boolean) {
        if (isConnected == on) return
        isConnected = on
        main.post {
            listeners.forEach { it(on) }
            if (on) {
                showConnectedOnCard()
                CinemaToast.show(activity ?: return@post, "Phone connected. Type on it and the box on the TV follows.", Toast.LENGTH_SHORT)
            }
        }
    }

    /** Sends the focused field (or "nothing selected") to the phone at once. Main thread. */
    private fun pushNow() {
        pushPending?.let { main.removeCallbacks(it) }
        pushPending = null
        val s = socket ?: return
        val field = target
        val message = if (field == null) PhoneInputProtocol.formatNone()
            else PhoneInputProtocol.formatFocus(field.fieldId, field.label, field.secret, field.maxLength, field.currentText())
        sender.execute { s.send(message) }
    }

    private fun pushSoon() {
        if (socket == null) return
        pushPending?.let { main.removeCallbacks(it) }
        pushPending = Runnable { pushPending = null; pushNow() }.also { main.postDelayed(it, PUSH_DELAY_MILLIS) }
    }

    /** Ends the session and drops the phone. */
    fun close() {
        hideQr()
        pushPending?.let { main.removeCallbacks(it) }
        pushPending = null
        target = null
        socket = null
        server?.close()
        server = null
        pairing = null
        if (isConnected) { isConnected = false; listeners.forEach { it(false) } }
    }

    // --- The code a phone scans -----------------------------------------------------------------------------------

    /**
     * The phone button was pressed. Not connected yet: show the QR code to scan (pressing again takes it away).
     * Already connected: say so, since the phone is already typing into the field in use.
     */
    fun toggleQr() {
        val a = activity ?: return
        if (isConnected) {
            CinemaToast.show(a, "Your phone is connected. What you type on it lands in the box you have selected.", Toast.LENGTH_LONG)
            return
        }
        if (card != null) { hideQr(); return }
        val address = address()
        if (address == null) { CinemaToast.show(a, "Connect the TV to Wi-Fi to type from your phone", Toast.LENGTH_LONG); return }
        val panel = FrameLayout(a).apply { isFocusable = false; isClickable = false; clipChildren = false; elevation = 400f }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = Cinema.glass(a, 28f, 0xF2120D0A.toInt())
            setPadding(Cinema.px(a, 26), Cinema.px(a, 22), Cinema.px(a, 26), Cinema.px(a, 22))
            isFocusable = false; isClickable = false
            tag = "phone-card"
        }
        box.addView(Cinema.eyebrow(a, listOf("Type from your phone"), 15))
        val code = ImageView(a).apply { setImageBitmap(PhoneQr.bitmap(address, 8)); tag = "phone-qr" }
        box.addView(code, LinearLayout.LayoutParams(Cinema.px(a, 260), Cinema.px(a, 260)).apply { topMargin = Cinema.px(a, 16) })
        val status = Cinema.text(a, "Scan the code with your phone's camera. It stays connected while the app is open.", 19, Cinema.MUTED).apply { tag = "phone-status" }
        box.addView(status, LinearLayout.LayoutParams(Cinema.px(a, 380), -2).apply { topMargin = Cinema.px(a, 14) })
        box.addView(Cinema.text(a, "Or type $address into your phone's browser.", 15, Cinema.DIM).apply { tag = "phone-address" },
            LinearLayout.LayoutParams(Cinema.px(a, 380), -2).apply { topMargin = Cinema.px(a, 8) })
        panel.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { marginEnd = Cinema.px(a, 60); bottomMargin = Cinema.px(a, 60) })
        a.findViewById<ViewGroup>(android.R.id.content).addView(panel, FrameLayout.LayoutParams(-1, -1))
        card = panel
    }

    /** Takes the QR card away. True when there was one, so a Back press can be used up on it. */
    fun hideQr(): Boolean {
        val shown = card ?: return false
        (shown.parent as? ViewGroup)?.removeView(shown)
        card = null
        return true
    }

    private fun showConnectedOnCard() {
        val shown = card ?: return
        shown.findViewWithTag<View>("phone-qr")?.visibility = View.GONE
        shown.findViewWithTag<View>("phone-address")?.visibility = View.GONE
        shown.findViewWithTag<TextView>("phone-status")?.text = "Phone connected. What you type on it lands in the box you have selected."
        shown.postDelayed({ if (card === shown) hideQr() }, CARD_LINGER_MILLIS)
    }
}
