package com.danielportelabyrne.cinema.ui.room

import com.danielportelabyrne.cinema.design.cinema.CinemaToast
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.movies.ClientKind
import com.danielportelabyrne.cinema.ui.phone.bindPhone
import com.danielportelabyrne.cinema.ui.phone.phoneRow
import kotlin.concurrent.thread

/**
 * One-time sign-in to the torrent client, for when Sonarr/Radarr keep its password hidden. The login is kept on this TV,
 * encrypted, so the download controls keep working; Deluge asks for a password only.
 */
class DownloadClientSignInSheet(private val activity: android.app.Activity, private val sheet: InfoSheet) {
    private fun px(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    /** [trySignIn] runs off the main thread and says whether the login worked (and was kept). */
    fun show(kind: ClientKind, trySignIn: (username: String, password: String) -> Boolean, onSignedIn: () -> Unit) {
        val ivory = ContextCompat.getColor(activity, R.color.ivory)
        val dim = ContextCompat.getColor(activity, R.color.ivory_text_dim)
        val ink = ContextCompat.getColor(activity, R.color.ink)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(px(34), px(28), px(34), px(26))
            background = GradientDrawable().apply { cornerRadius = px(30).toFloat(); setColor(ContextCompat.getColor(activity, R.color.menu_bg)) }
        }
        fun label(t: String, sp: Float, c: Int, serif: Boolean = false) = TextView(activity).apply {
            text = t; textSize = sp; setTextColor(c); typeface = Typeface.create(if (serif) "serif" else "sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(label("DOWNLOAD CONTROLS", 11f, ContextCompat.getColor(activity, R.color.butter)).apply { letterSpacing = 0.16f })
        card.addView(label("Sign in to ${kind.label}", 26f, ivory, serif = true).apply { setPadding(0, px(4), 0, 0) })
        val what = if (kind.needsUsername) "your ${kind.label} Web UI username and password" else "your ${kind.label} Web UI password"
        card.addView(label("Sonarr and Radarr keep this hidden, so enter $what once. It is kept on this TV, encrypted.", 14f, dim).apply { setPadding(0, px(8), 0, px(16)) })

        fun field(hint: String, password: Boolean) = EditText(activity).apply {
            this.hint = hint; com.danielportelabyrne.cinema.design.Type.apply(this, com.danielportelabyrne.cinema.design.Type.Style.BODY); setHintTextColor(dim)
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            maxLines = 1; isSingleLine = true
            imeOptions = if (password) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            setPadding(px(22), 0, px(22), 0)
            background = GradientDrawable().apply { cornerRadius = px(26).toFloat(); setColor(ContextCompat.getColor(activity, R.color.btn_brown_on)) }
            setOnFocusChangeListener { v, focused -> (v.background as GradientDrawable).setColor(if (focused) 0xFF7A5C42.toInt() else ContextCompat.getColor(activity, R.color.btn_brown_on)) }
        }
        val user = field("${kind.label} username", false)
        val password = field("Password", true)
        // The card is 492 px of content wide: the box, a gap, and the phone button that is as tall as the box is.
        bindPhone(user, "client-sheet-username", "${kind.label} username")
        bindPhone(password, "client-sheet-password", "Password", secret = true)
        if (kind.needsUsername) card.addView(phoneRow(activity, user, px(430), px(52), px(10)), LinearLayout.LayoutParams(-2, -2))
        card.addView(phoneRow(activity, password, px(430), px(52), px(10)), LinearLayout.LayoutParams(-2, -2).apply { if (kind.needsUsername) topMargin = px(10) })

        val status = label("", 13f, ContextCompat.getColor(activity, R.color.butter)).apply { setPadding(0, px(10), 0, 0) }
        card.addView(status)

        fun pill(text: String, accent: Boolean): TextView {
            val radius = px(26).toFloat()
            val rest = GradientDrawable().apply { cornerRadius = radius; setColor(if (accent) ContextCompat.getColor(activity, R.color.terracotta) else ContextCompat.getColor(activity, R.color.btn_brown_on)) }
            val lit = GradientDrawable().apply { cornerRadius = radius; setColor(ivory) }
            return label(text, 16f, ivory).apply {
                gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                typeface = Typeface.create("sans-serif-medium", if (accent) Typeface.BOLD else Typeface.NORMAL)
                background = rest
                setOnFocusChangeListener { v, f -> v.background = if (f) lit else rest; (v as TextView).setTextColor(if (f) ink else ivory) }
            }
        }
        val signIn = pill("Sign in", true)
        val cancel = pill("Cancel", false).apply { setOnClickListener { sheet.hide() } }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        row.addView(signIn, LinearLayout.LayoutParams(px(190), px(52)))
        row.addView(cancel, LinearLayout.LayoutParams(px(130), px(52)).apply { marginStart = px(10) })
        card.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(16) })

        fun submit() {
            val u = user.text.toString().trim(); val p = password.text.toString()
            if ((kind.needsUsername && u.isEmpty()) || p.isEmpty()) { status.text = "Enter your ${kind.label} ${if (kind.needsUsername) "username and password" else "password"}."; return }
            signIn.isEnabled = false; status.text = "Signing in…"
            thread {
                val error = if (trySignIn(u, p)) null else "Couldn't sign in. Check the ${if (kind.needsUsername) "username, " else ""}password and connection."
                activity.runOnUiThread {
                    if (error == null) { password.text.clear(); sheet.hide(); CinemaToast.show(activity, "Signed in", Toast.LENGTH_SHORT); onSignedIn() }
                    else { status.text = error; signIn.isEnabled = true }
                }
            }
        }
        signIn.setOnClickListener { submit() }
        password.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }

        val wrap = LinearLayout(activity).apply { addView(card, LinearLayout.LayoutParams(px(560), -2)) }
        sheet.showCustom(wrap, if (kind.needsUsername) user else password)
    }
}
