package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.ui.room.InfoSheet

/**
 * A short list of choices in the Cinema style, in place of a system dialog: the same dark glass card as [CinemaConfirm],
 * an eyebrow, a serif title and one button per choice. The first choice has focus. Shown through [InfoSheet], so Back
 * dismisses it and focus goes back to where it was. A choice picked closes the sheet, then runs.
 */
object CinemaChoiceSheet {
    class Choice(val label: String, val danger: Boolean = false, val run: () -> Unit)

    fun show(sheet: InfoSheet, context: Context, eyebrow: String, title: String, choices: List<Choice>) {
        if (choices.isEmpty()) return
        val width = Cinema.px(context, 560)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Cinema.glass(context, 28f, 0xF2120D0A.toInt())
            val padX = Cinema.px(context, 44)
            setPadding(padX, Cinema.px(context, 40), padX, Cinema.px(context, 40))
        }
        card.addView(Cinema.eyebrow(context, listOf(eyebrow), mark = false))
        card.addView(Cinema.title(context, title, 48, maxLines = 2), LinearLayout.LayoutParams(width, -2).apply { topMargin = Cinema.px(context, 18) })
        var first: CinemaButton? = null
        choices.forEachIndexed { i, choice ->
            val button = CinemaButton(context, if (choice.danger) CinemaButton.Kind.DANGER else CinemaButton.Kind.GLASS, choice.label)
            button.setOnClickListener { sheet.hide(); choice.run() }
            if (first == null) first = button
            card.addView(button, LinearLayout.LayoutParams(width, -2).apply { topMargin = Cinema.px(context, if (i == 0) 28 else 12) })
        }
        sheet.showCustom(card, focus = first)
    }
}
