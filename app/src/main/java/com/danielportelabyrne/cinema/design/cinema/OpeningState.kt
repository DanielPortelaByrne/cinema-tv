package com.danielportelabyrne.cinema.design.cinema

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * When the branded opening is due. It plays on a fresh start of the app (the app was closed, or the device rebooted), and
 * again after the TV's screen has gone off since it last played. These TVs keep Cinema running through standby, so the
 * switch-off is learned from the screen-off broadcast; a fresh start covers an app that was closed while the TV was off.
 *
 * It is recorded as shown only once the opening has reached its held frame with Cinema in focus, so an opening that
 * started behind the launcher, or was dropped, is still due next time.
 */
object OpeningState {
    private const val PREFS = "opening"
    private const val KEY_SHOWN = "shown"
    private const val KEY_SCREEN_OFF = "screenOff"

    /** Set once the opening has been recorded in this run of the app, so the same run does not repeat it. */
    @Volatile private var shownThisRun = false

    /** Records when the screen goes off. Registered once for the whole app, so it hears it from any screen. */
    fun watchScreen(context: Context) {
        context.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                prefs(c).edit().putLong(KEY_SCREEN_OFF, System.currentTimeMillis()).apply()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    /** True when the opening should play now. */
    fun due(context: Context): Boolean {
        if (!shownThisRun) return true
        val prefs = prefs(context)
        return prefs.getLong(KEY_SCREEN_OFF, 0L) > prefs.getLong(KEY_SHOWN, 0L)
    }

    /** Records that the opening has been seen. */
    fun markShown(context: Context) {
        shownThisRun = true
        prefs(context).edit().putLong(KEY_SHOWN, System.currentTimeMillis()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
