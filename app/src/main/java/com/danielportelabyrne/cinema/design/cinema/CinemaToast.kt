package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A toast in the Cinema style: a dark glass pill with the message in cream, drawn by the app rather than the system
 * (no icon, no grey slab). It sits at the foot of the screen, where the system toast would.
 */
object CinemaToast {
    fun show(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        val pill = Cinema.text(context, message, 24, Cinema.CREAM, weight = 500).apply {
            background = Cinema.glass(context, Cinema.pxf(context, 40), 0xF2120D0A.toInt())
            gravity = Gravity.CENTER
            setPadding(Cinema.px(context, 40), Cinema.px(context, 22), Cinema.px(context, 40), Cinema.px(context, 22))
        }
        Toast.makeText(context.applicationContext, "", duration).apply {
            view = pill
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, Cinema.px(context, 120))
        }.show()
    }
}
