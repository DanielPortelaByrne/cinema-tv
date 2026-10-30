package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.ui.room.InfoSheet

/**
 * A confirmation in the Cinema style: a dark glass card over the page, with a small eyebrow, a serif title, the
 * explanation, the destructive button and Keep it. Keep it has focus, so a stray OK keeps the thing. Shown through
 * [InfoSheet], so Back dismisses it and focus goes back to where it was.
 */
object CinemaConfirm {
    fun show(sheet: InfoSheet, context: Context, eyebrow: String, title: String, body: String, confirm: String, onConfirm: () -> Unit) {
        val width = Cinema.px(context, 680)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Cinema.glass(context, 28f, 0xF2120D0A.toInt())
            val padX = Cinema.px(context, 44)
            setPadding(padX, Cinema.px(context, 40), padX, Cinema.px(context, 40))
        }
        card.addView(Cinema.eyebrow(context, listOf(eyebrow), color = Cinema.DANGER_TEXT, mark = false))
        card.addView(Cinema.title(context, title, 56, maxLines = 2), LinearLayout.LayoutParams(width, -2).apply { topMargin = Cinema.px(context, 18) })
        card.addView(Cinema.text(context, body, 25, Cinema.MUTED), LinearLayout.LayoutParams(width, -2).apply { topMargin = Cinema.px(context, 22) })

        val keep = CinemaButton(context, CinemaButton.Kind.GLASS, "Keep it")
        val remove = CinemaButton(context, CinemaButton.Kind.DANGER, confirm)
        keep.setOnClickListener { sheet.hide() }
        remove.setOnClickListener { sheet.hide(); onConfirm() }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(keep, LinearLayout.LayoutParams(-2, -2))
        row.addView(remove, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 22) })
        card.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 36) })

        sheet.showCustom(card, focus = keep)
    }
}
