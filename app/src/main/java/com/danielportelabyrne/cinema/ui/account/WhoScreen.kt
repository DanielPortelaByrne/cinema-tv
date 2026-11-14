package com.danielportelabyrne.cinema.ui.account

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.account.AccountStore
import com.danielportelabyrne.cinema.data.account.CinemaAccount
import com.danielportelabyrne.cinema.data.account.LauncherPerson
import com.danielportelabyrne.cinema.data.account.ProfileScope
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.CinemaButton
import com.danielportelabyrne.cinema.design.cinema.QwertyKeyboard
import com.danielportelabyrne.cinema.ui.room.InfoSheet

/**
 * "Who's watching?": everyone on Cinema (each a Jellyfin user), a pencil under each to choose their character, and
 * adding someone, which makes them a Jellyfin user. Shown when Cinema opens and the TV's launcher has not said who is
 * watching, or said someone Cinema has not met yet ([launcher]): picking then remembers which Cinema person that
 * launcher account is. Everything happens on this screen itself (nothing opens behind it); Back steps back out.
 */
class WhoScreen(
    private val activity: Activity,
    private val host: ViewGroup,
    private val accounts: AccountStore,
    private val jellyfin: JellyfinClient,
    @Suppress("unused") private val infoSheet: InfoSheet,
    private val launcher: LauncherPerson?,
    /** Finds a person's favourite-title characters off the main thread and hands them back on it (see FavouriteCharacters). */
    private val favourites: (CinemaAccount, (List<Pair<String, String>>) -> Unit) -> Unit = { _, _ -> },
    private val onChosen: (CinemaAccount) -> Unit
) {
    /** What Back does from here: back to the people, or nothing (leave Cinema) when already there. */
    private var backTo: (() -> Unit)? = null
    private var picker: CharacterPicker? = null

    private val root = object : FrameLayout(activity) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && picker?.isOpen != true) {
                val back = backTo ?: return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP) back()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }.apply { setBackgroundColor(Cinema.BG); isClickable = true; elevation = Cinema.pxf(activity, 80) }
    private fun p(v: Number) = Cinema.px(activity, v)
    @Volatile private var closed = false

    fun show() {
        host.addView(root, FrameLayout.LayoutParams(-1, -1))
        people()
    }

    fun close() { closed = true; (root.parent as? ViewGroup)?.removeView(root) }

    /** Keeps the screen as it is but deaf to keys and clicks, while the next thing is made ready underneath. */
    fun hold() {
        backTo = null
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.requestFocus()
        root.setOnKeyListener { _, _, _ -> true }
        root.isFocusableInTouchMode = true
    }

    /** Fades away over what is under it, so the change is a dissolve rather than a blink. */
    fun fadeOut() {
        closed = true
        root.animate().alpha(0f).setDuration(260).withEndAction { (root.parent as? ViewGroup)?.removeView(root) }.start()
    }

    /** A short message alone on the screen, while something happens. */
    fun busy(text: String) {
        backTo = null
        root.removeAllViews()
        root.addView(Cinema.text(activity, text, 40, Cinema.CREAM, serif = true), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }

    /** Something went wrong: what, and a button back to the people. */
    private fun problem(title: String, text: String) {
        root.removeAllViews()
        backTo = { people() }
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        column.addView(Cinema.text(activity, title, 56, Cinema.CREAM, serif = true))
        column.addView(Cinema.text(activity, text, 28, Cinema.MUTED).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(p(1200), -2).apply { topMargin = p(24) })
        val ok = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Back")
        ok.setOnClickListener { people() }
        column.addView(ok, LinearLayout.LayoutParams(-2, p(76)).apply { topMargin = p(44) })
        root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        ok.post { ok.requestFocus() }
    }

    private fun people(focusUser: String? = null, onPencil: Boolean = false) {
        backTo = null
        root.removeAllViews()
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        column.addView(Cinema.text(activity, "Who's watching?", 84, Cinema.CREAM, serif = true, tracking = -0.02f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2))
        val unmet = launcher?.name?.takeIf { accounts.forLauncher(launcher) == null }
        if (unmet != null) column.addView(Cinema.text(activity, "Who is $unmet on Cinema? Cinema will remember.", 28, Cinema.MUTED).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = p(18) })
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; clipChildren = false; clipToPadding = false }
        var first: View? = null
        val active = focusUser ?: accounts.active()?.userId
        accounts.all().forEach { a ->
            val (box, tile, pencil) = person(a)
            row.addView(box, LinearLayout.LayoutParams(p(300), -2))
            if (first == null || a.userId == active) first = if (onPencil && a.userId == focusUser) pencil else tile
        }
        val (addBox, add, _) = person(null)
        row.addView(addBox, LinearLayout.LayoutParams(p(300), -2))
        column.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(90) })
        root.addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        (first ?: add).let { t -> t.post { t.requestFocus() } }
    }

    /**
     * One person's column: their round tile (their character, or their initial), their name, and a pencil to choose
     * their character. [a] null is "Add someone", with no pencil. Returns the column, the tile and the pencil.
     */
    private fun person(a: CinemaAccount?): Triple<View, View, View?> {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val letter = a?.initial ?: "+"
        val name = a?.name ?: "Add someone"
        val rest = if (a == null) Cinema.DIM else Cinema.MUTED
        val label = Cinema.text(activity, name, 30, rest, weight = 500).apply { maxLines = 1 }
        val tile = object : View(activity) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Cinema.text(activity, "", 10, serif = true).typeface }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = width / 2f - Cinema.pxf(activity, 24)
                val lit = isFocused
                if (lit) { val step = Cinema.pxf(activity, 6); for (i in 4 downTo 1) { paint.color = Cinema.STRIPES[i - 1]; c.drawCircle(cx, cy, r + step * i, paint) } }
                if (a != null) { Avatars.draw(c, a.avatar, cx, cy, r, letter, serif = text.typeface, view = this); return }
                // "+", centred on its own ink in the circle.
                Avatars.letter(c, letter, cx, cy, r, if (lit) Cinema.CREAM else Cinema.alpha(Cinema.CREAM, 0.06f), null,
                    ink = if (lit) Cinema.INK else Cinema.alpha(Cinema.CREAM, 0.75f), size = 0.8f)
            }
        }.apply {
            isFocusable = true; isClickable = true; contentDescription = name
            setOnFocusChangeListener { v, f -> v.invalidate(); label.setTextColor(if (f) Cinema.CREAM else rest)
                v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(160).start() }
            setOnClickListener { if (a != null) onChosen(a) else nameEntry() }
        }
        box.addView(tile, LinearLayout.LayoutParams(p(240), p(240)))
        box.addView(label, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(14) })
        var pencil: View? = null
        if (a != null) {
            // The pencil: just its outline under the name, dim at rest; focused, saffron and a little larger.
            val icon = Cinema.icon(activity, R.drawable.ic_cin_pencil, Cinema.alpha(Cinema.CREAM, 0.6f))
            val pen = FrameLayout(activity).apply {
                isFocusable = true; isClickable = true; contentDescription = "Choose ${a.name}'s character"
                addView(icon, FrameLayout.LayoutParams(p(28), p(28), Gravity.CENTER))
                setOnFocusChangeListener { v, f ->
                    icon.setColorFilter(if (f) Cinema.SAFFRON else Cinema.alpha(Cinema.CREAM, 0.6f))
                    v.animate().scaleX(if (f) 1.3f else 1f).scaleY(if (f) 1.3f else 1f).setDuration(140).start()
                }
                setOnClickListener { chooseCharacter(a) }
            }
            box.addView(pen, LinearLayout.LayoutParams(p(52), p(52)).apply { topMargin = p(12) })
            pencil = pen
        }
        return Triple(box, tile, pencil)
    }

    /** The character picker for [a]; their pick is kept with them (and with their own things, so their other TVs show it). */
    private fun chooseCharacter(a: CinemaAccount) {
        picker = CharacterPicker(activity, root, a.name, a.avatar, favourites = { give -> favourites(a, give) }, onPick = { id ->
            accounts.setAvatar(a.userId, id)
            activity.getSharedPreferences(ProfileScope.fileNameFor("cinema_personal", a.userId), android.content.Context.MODE_PRIVATE)
                .edit().putString("avatar", id ?: "").apply()
        }, onClose = { picker = null; people(a.userId, onPencil = true) }).also { it.show() }
    }

    // --- Someone new: their name, typed on the keyboard, and a Jellyfin user made for them ---------------------

    private fun nameEntry(startText: String = "", error: String? = null) {
        root.removeAllViews()
        backTo = { people() }
        var typed = startText
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        column.addView(Cinema.text(activity, "Add someone", 64, Cinema.CREAM, serif = true))
        column.addView(Cinema.text(activity, "Type their name. Cinema makes them a user on your Jellyfin server, with their own watchlist, history and pages.", 24, Cinema.MUTED),
            LinearLayout.LayoutParams(p(980), -2).apply { topMargin = p(12) })
        val field = Cinema.text(activity, typed, 48, Cinema.CREAM, weight = 300).apply { maxLines = 1 }
        column.addView(field, LinearLayout.LayoutParams(p(980), -2).apply { topMargin = p(30) })
        column.addView(View(activity).apply { setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.3f)) }, LinearLayout.LayoutParams(p(980), p(2)).apply { topMargin = p(6) })
        val keyboard = QwertyKeyboard(activity) { key -> typed = Qwerty.type(typed, key).take(24); field.text = typed }
        keyboard.onLeave = { people() }
        column.addView(keyboard, LinearLayout.LayoutParams(p(980), p(476)).apply { topMargin = p(36) })
        val note = Cinema.text(activity, error.orEmpty(), 24, Cinema.TERRA).apply { visibility = if (error == null) View.GONE else View.VISIBLE }
        column.addView(note, LinearLayout.LayoutParams(p(980), -2).apply { topMargin = p(12) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val make = CinemaButton(activity, CinemaButton.Kind.PRIMARY, "Add them", R.drawable.ic_cin_check)
        val qc = CinemaButton(activity, CinemaButton.Kind.GLASS, "Already on Jellyfin? Quick Connect")
        buttons.addView(make, LinearLayout.LayoutParams(-2, p(76)))
        buttons.addView(qc, LinearLayout.LayoutParams(-2, p(76)).apply { marginStart = p(24) })
        column.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(26) })
        make.setOnClickListener {
            val name = typed.trim()
            if (name.isEmpty()) { note.text = "Type their name first"; note.visibility = View.VISIBLE; return@setOnClickListener }
            if (accounts.all().any { it.name.equals(name, ignoreCase = true) }) { note.text = "$name is already on Cinema"; note.visibility = View.VISIBLE; return@setOnClickListener }
            busy("Adding $name…")
            Thread {
                val (session, why) = jellyfin.createUser(name)
                android.util.Log.w("Accounts", "add someone: ${if (session != null) "made" else "failed: $why"}")
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    if (session == null) nameEntry(name, why ?: "Something went wrong. Try again.")
                    else added(session, name)
                }
            }.start()
        }
        qc.setOnClickListener { quickConnect() }
        root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        (if (error != null) make else keyboard.keys.firstOrNull())?.let { v -> v.post { v.requestFocus() } }
    }

    // --- Someone already on Jellyfin: Quick Connect ---------------------------------------------------------

    private fun quickConnect() {
        busy("Asking Jellyfin for a code…")
        Thread {
            var why: String? = null
            val ticket = jellyfin.startQuickConnect(jellyfin.server) { why = it }
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                if (ticket == null) { problem("Quick Connect didn't start", why ?: "Try again in a moment."); return@runOnUiThread }
                root.removeAllViews()
                backTo = { people() }
                val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                column.addView(Cinema.text(activity, "Approve with this code", 56, Cinema.CREAM, serif = true))
                column.addView(Cinema.text(activity, ticket.code, 140, Cinema.SAFFRON, weight = 600, tracking = 0.2f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(30) })
                column.addView(Cinema.text(activity, "Signed in to Jellyfin as them on a phone or computer: Settings, Quick Connect, enter the code.", 26, Cinema.MUTED),
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(30) })
                val cancel = CinemaButton(activity, CinemaButton.Kind.GLASS, "Cancel")
                cancel.setOnClickListener { people() }
                column.addView(cancel, LinearLayout.LayoutParams(-2, p(76)).apply { topMargin = p(50) })
                root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                cancel.post { cancel.requestFocus() }
                Thread {
                    val until = System.currentTimeMillis() + 5 * 60_000L
                    while (!closed && cancel.isAttachedToWindow && System.currentTimeMillis() < until) {
                        if (jellyfin.isApproved(ticket)) {
                            val session = jellyfin.quickConnectSession(ticket)
                            val name = session?.let { jellyfin.userName(it) }
                            activity.runOnUiThread { if (!closed) { if (session == null) problem("Couldn't sign them in", "Try Quick Connect again.") else added(session, name ?: "Someone") } }
                            return@Thread
                        }
                        Thread.sleep(2500)
                    }
                }.start()
            }
        }.start()
    }

    private fun added(session: JellyfinSession, name: String) {
        accounts.upsert(CinemaAccount(session.userId, name), session.token)
        accounts.all().firstOrNull { it.userId == session.userId }?.let(onChosen)
    }
}
