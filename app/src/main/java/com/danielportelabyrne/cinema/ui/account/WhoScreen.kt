package com.danielportelabyrne.cinema.ui.account

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.account.AccountStore
import com.danielportelabyrne.cinema.data.account.CinemaAccount
import com.danielportelabyrne.cinema.data.account.GoldProfiles
import com.danielportelabyrne.cinema.data.account.LauncherPerson
import com.danielportelabyrne.cinema.data.account.ProfileScope
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinSession
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
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
    /** Who has found Gold Mode (hold OK on a face for three seconds) and who has it on. */
    private val gold: GoldProfiles = GoldProfiles.session,
    private val onChosen: (CinemaAccount) -> Unit
) {
    /** Forgets a three-second hold in progress (Back, or the face losing focus). */
    private var cancelHold: (() -> Unit)? = null
    private var goldCaption: TextView? = null
    private val hideCaption = Runnable { goldCaption?.animate()?.alpha(0f)?.setDuration(400)?.start() }
    /** What Back does from here: back to the people, or nothing (leave Cinema) when already there. */
    private var backTo: (() -> Unit)? = null
    private var picker: CharacterPicker? = null

    private val root = object : FrameLayout(activity) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && picker?.isOpen != true) {
                cancelHold?.invoke()
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

    /** Listens to keys again after [hold]. */
    private fun unhold() {
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        root.setOnKeyListener(null)
    }

    /**
     * [person]'s saved sign-in no longer works (Jellyfin forgets it when they are signed in again on this TV): Quick
     * Connect, approved as them, signs them back in and carries on as them.
     */
    fun reconnect(person: CinemaAccount) {
        closed = false
        root.alpha = 1f
        if (root.parent == null) host.addView(root, FrameLayout.LayoutParams(-1, -1))
        unhold()
        quickConnect(person)
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
        // Where Gold Mode speaks: low on the screen, so the row of faces never moves.
        goldCaption = Cinema.text(activity, "", 26, Cinema.SAFFRON, weight = 500, tracking = 0.12f).apply { alpha = 0f; gravity = Gravity.CENTER }
            .also { root.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = p(120) }) }
        (first ?: add).let { t -> t.post { t.requestFocus() } }
    }

    /** A quiet line for a moment, then gone; also read out by the TV's screen reader. */
    private fun say(text: String) {
        val c = goldCaption ?: return
        c.removeCallbacks(hideCaption)
        c.text = text
        if (Motion.animationsOn(c)) c.animate().alpha(1f).setDuration(300).start() else c.alpha = 1f
        c.announceForAccessibility(text)
        c.postDelayed(hideCaption, 3500)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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

            // --- Gold Mode: hold OK three seconds on a face ---
            val champagne = mix(Cinema.SAFFRON, Cinema.CREAM, 0.4f)
            val gesture = HoldGesture()
            val gild = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            val gildFill = Paint(Paint.ANTI_ALIAS_FLAG)
            val oval = RectF()
            val star = Path()
            var sweep = -1f      // 0..1 while the unlock sweep runs
            var starPop = 1f
            val stepper = Runnable { step() }

            fun describe() = name + if (a != null && gold.isEnabled(a.userId)) ", Gold Mode on" else ""

            fun step() {
                if (!gesture.holding) { invalidate(); return }
                if (a != null && gesture.tick(SystemClock.uptimeMillis())) {
                    val held = gold.held(a.userId)
                    contentDescription = describe()
                    say(when (held) {
                        GoldProfiles.Held.UNLOCKED -> "GOLD MODE UNLOCKED — Only the finest."
                        GoldProfiles.Held.ENABLED -> "Gold Mode on"
                        GoldProfiles.Held.DISABLED -> "Gold Mode off"
                    })
                    if (held == GoldProfiles.Held.UNLOCKED && Motion.animationsOn(this)) {
                        ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 900
                            addUpdateListener { sweep = it.animatedValue as Float; starPop = OvershootInterpolator().getInterpolation((sweep * 2f).coerceAtMost(1f)); invalidate() }
                            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(an: Animator) { sweep = -1f; starPop = 1f; invalidate() } })
                        }.start()
                    }
                }
                invalidate()
                if (gesture.holding) postOnAnimation(stepper)
            }

            fun key(code: Int, e: KeyEvent): Boolean {
                if (a == null || (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER)) return false
                when (e.action) {
                    KeyEvent.ACTION_DOWN -> if (gesture.down(SystemClock.uptimeMillis())) {
                        cancelHold = { gesture.cancel(); invalidate() }
                        postOnAnimation(stepper)
                    }
                    KeyEvent.ACTION_UP -> { val end = gesture.up(SystemClock.uptimeMillis()); invalidate(); if (end == HoldGesture.End.CLICK) performClick() }
                    else -> return false
                }
                return true
            }

            fun cancelHoldNow() { gesture.cancel(); removeCallbacks(stepper); invalidate() }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (!hasWindowFocus) cancelHoldNow() }
            override fun onDetachedFromWindow() { cancelHoldNow(); super.onDetachedFromWindow() }

            /** The progress arc, the thin gold outline and its star: drawn on the face's own edge, so its size never changes. */
            fun mark(c: Canvas, cx: Float, cy: Float, r: Float) {
                val u = Cinema.pxf(activity, 1)
                val on = a != null && gold.isEnabled(a.userId)
                val p = gesture.progress(SystemClock.uptimeMillis())
                if (on) { gild.strokeWidth = 3 * u; gild.color = champagne; c.drawCircle(cx, cy, r - 1.5f * u, gild) }
                if (gesture.holding && p > 0.08f) {
                    val ri = r - 2.5f * u
                    oval.set(cx - ri, cy - ri, cx + ri, cy + ri)
                    gild.strokeWidth = 5 * u; gild.color = Cinema.alpha(Cinema.CREAM, 0.25f); c.drawCircle(cx, cy, ri, gild)
                    gild.color = champagne; c.drawArc(oval, -90f, 360f * p, false, gild)
                }
                if (sweep >= 0f) {
                    val ri = r - 3f * u
                    oval.set(cx - ri, cy - ri, cx + ri, cy + ri)
                    gild.strokeWidth = 6 * u; gild.color = Cinema.alpha(Cinema.CREAM, 0.9f * (1f - sweep))
                    c.drawArc(oval,-90f + 360f * sweep, 70f, false, gild)
                }
                if (on) {
                    // A five-point star just outside the portrait, upper right.
                    val sx = cx + (r + 13 * u) * 0.7071f; val sy = cy - (r + 13 * u) * 0.7071f
                    val outer = 9 * u * starPop; val inner = outer * 0.42f
                    star.rewind()
                    for (i in 0 until 10) {
                        val ang = Math.toRadians(-90.0 + i * 36.0); val rad = if (i % 2 == 0) outer else inner
                        val x = sx + (rad * Math.cos(ang)).toFloat(); val y = sy + (rad * Math.sin(ang)).toFloat()
                        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                    }
                    star.close()
                    gild.strokeWidth = 2 * u; gild.color = Cinema.BG; c.drawPath(star, gild)
                    gildFill.color = champagne; c.drawPath(star, gildFill)
                }
            }

            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = width / 2f - Cinema.pxf(activity, 24)
                val lit = isFocused
                if (lit) { val step = Cinema.pxf(activity, 6); for (i in 4 downTo 1) { paint.color = Cinema.STRIPES[i - 1]; c.drawCircle(cx, cy, r + step * i, paint) } }
                if (a != null) { Avatars.draw(c, a.avatar, cx, cy, r, letter, serif = text.typeface, view = this); mark(c, cx, cy, r); return }
                // "+", centred on its own ink in the circle.
                Avatars.letter(c, letter, cx, cy, r, if (lit) Cinema.CREAM else Cinema.alpha(Cinema.CREAM, 0.06f), null,
                    ink = if (lit) Cinema.INK else Cinema.alpha(Cinema.CREAM, 0.75f), size = 0.8f)
            }
        }.apply {
            isFocusable = true; isClickable = true; contentDescription = describe()
            setOnKeyListener { _, code, e -> key(code, e) }
            setOnFocusChangeListener { v, f -> if (!f) cancelHoldNow(); v.invalidate(); label.setTextColor(if (f) Cinema.CREAM else rest)
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

    private fun quickConnect(forPerson: CinemaAccount? = null) {
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
                column.addView(Cinema.text(activity, forPerson?.let { "Sign in as ${it.name} again" } ?: "Approve with this code", 56, Cinema.CREAM, serif = true))
                column.addView(Cinema.text(activity, ticket.code, 140, Cinema.SAFFRON, weight = 600, tracking = 0.2f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p(30) })
                column.addView(Cinema.text(activity, (if (forPerson != null) "${forPerson.name}'s sign-in on this TV has run out. " else "") +
                    "Signed in to Jellyfin as ${forPerson?.name ?: "them"} on a phone or computer: Settings, Quick Connect, enter the code.", 26, Cinema.MUTED),
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
                            activity.runOnUiThread {
                                if (closed) return@runOnUiThread
                                when {
                                    session == null -> problem("Couldn't sign them in", "Try Quick Connect again.")
                                    forPerson != null && session.userId != forPerson.userId ->
                                        problem("That was ${name ?: "someone else"}", "Approve the code while signed in to Jellyfin as ${forPerson.name}.")
                                    else -> added(session, name ?: "Someone")
                                }
                            }
                            return@Thread
                        }
                        Thread.sleep(2500)
                    }
                }.start()
            }
        }.start()
    }

    private fun added(session: JellyfinSession, name: String) {
        // Someone already on Cinema signing in again keeps their name and character; only their sign-in is new.
        val known = accounts.all().firstOrNull { it.userId == session.userId }
        accounts.upsert(known ?: CinemaAccount(session.userId, name), session.token)
        accounts.all().firstOrNull { it.userId == session.userId }?.let(onChosen)
    }
}
