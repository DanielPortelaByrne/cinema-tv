package com.danielportelabyrne.cinema.ui.room

import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.QuickConnectTicket
import kotlin.concurrent.thread

/**
 * One-time sign-in to the owner's Jellyfin with Quick Connect: the TV shows a six-digit code, the owner approves
 * it from Jellyfin on a phone or computer, and the TV is signed in. No password is typed here or stored.
 */
class JellyfinConnectSheet(
    private val activity: android.app.Activity, private val sheet: InfoSheet,
    /** When set, signing in is handed to this instead (the app's own sign-in step, with every way to sign in). */
    private val redirect: ((onDone: () -> Unit) -> Unit)? = null
) {
    private fun px(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
    private val handler = Handler(Looper.getMainLooper())

    fun show(client: JellyfinClient, onConnected: () -> Unit) {
        redirect?.let { it(onConnected); return }
        val ivory = ContextCompat.getColor(activity, R.color.ivory)
        val dim = ContextCompat.getColor(activity, R.color.ivory_text_dim)
        val ink = ContextCompat.getColor(activity, R.color.ink)
        val butter = ContextCompat.getColor(activity, R.color.butter)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(px(34), px(28), px(34), px(26))
            background = GradientDrawable().apply { cornerRadius = px(30).toFloat(); setColor(ContextCompat.getColor(activity, R.color.menu_bg)) }
        }
        fun label(t: String, sp: Float, c: Int, serif: Boolean = false) = TextView(activity).apply {
            text = t; textSize = sp; setTextColor(c); typeface = Typeface.create(if (serif) "serif" else "sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(label("CONNECT JELLYFIN", 11f, butter).apply { letterSpacing = 0.16f })
        card.addView(label("Approve this TV", 26f, ivory, serif = true).apply { setPadding(0, px(4), 0, 0) })
        val steps = label("Opening Quick Connect…", 14f, dim).apply { setPadding(0, px(8), 0, px(12)) }
        card.addView(steps)
        val code = label("", 44f, ivory).apply { letterSpacing = 0.12f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        card.addView(code)
        val status = label("", 13f, butter).apply { setPadding(0, px(10), 0, 0) }
        card.addView(status)

        val rest = GradientDrawable().apply { cornerRadius = px(26).toFloat(); setColor(ContextCompat.getColor(activity, R.color.btn_brown_on)) }
        val lit = GradientDrawable().apply { cornerRadius = px(26).toFloat(); setColor(ivory) }
        val cancel = label("Cancel", 16f, ivory).apply {
            gravity = Gravity.CENTER; isFocusable = true; isClickable = true; background = rest
            setOnFocusChangeListener { v, f -> v.background = if (f) lit else rest; (v as TextView).setTextColor(if (f) ink else ivory) }
        }
        card.addView(cancel, LinearLayout.LayoutParams(px(150), px(52)).apply { topMargin = px(16) })

        var open = true
        var ticket: QuickConnectTicket? = null
        fun stop() { open = false; handler.removeCallbacksAndMessages(null) }
        cancel.setOnClickListener { stop(); sheet.hide() }

        // Ask for a code, then check every two seconds whether it has been approved (the code lives about five minutes).
        fun begin() {
            thread {
                var failure: String? = null
                val t = client.startQuickConnect(client.server) { failure = it }
                activity.runOnUiThread {
                    if (!open) return@runOnUiThread
                    if (t == null) { steps.text = failure ?: "Couldn't start Quick Connect"; return@runOnUiThread }
                    ticket = t
                    code.text = t.code.chunked(3).joinToString(" ")
                    steps.text = "On a phone or computer, open Jellyfin and sign in. Then open the user menu, choose Quick Connect and type this code."
                    status.text = "Waiting for approval…"
                    val poll = object : Runnable {
                        override fun run() {
                            // Back closes the sheet without telling us, so an invisible sheet means the attempt is over.
                            if (!open || !sheet.isVisible) { stop(); return }
                            thread {
                                val approved = client.isApproved(t)
                                val done = approved && client.finishQuickConnect(t)
                                activity.runOnUiThread {
                                    if (!open) return@runOnUiThread
                                    if (done) { stop(); sheet.hide(); CinemaToast.show(activity, "Jellyfin connected", Toast.LENGTH_SHORT); onConnected() }
                                    else handler.postDelayed(this, 2000)
                                }
                            }
                        }
                    }
                    handler.postDelayed(poll, 2000)
                }
            }
        }
        begin()

        val wrap = LinearLayout(activity).apply { addView(card, LinearLayout.LayoutParams(px(560), -2)) }
        sheet.showCustom(wrap, cancel)
    }
}
