package com.danielportelabyrne.cinema.ui.room

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R

/**
 * Naming a channel, new or renamed, in Cinema's own sheet (the same card as the sign-in sheets) instead of a system
 * dialog. Typed with the remote and the TV's keyboard; the keyboard's Done saves. A blank name stays on the sheet with a
 * line under the field; Back and Cancel leave everything as it was.
 */
class ChannelNameSheet(private val activity: android.app.Activity, private val sheet: InfoSheet) {
    private fun px(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    fun show(initial: String, save: (String) -> Unit) {
        val ivory = ContextCompat.getColor(activity, R.color.ivory)
        val dim = ContextCompat.getColor(activity, R.color.ivory_text_dim)
        val ink = ContextCompat.getColor(activity, R.color.ink)
        val renaming = initial.isNotEmpty()

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(px(34), px(28), px(34), px(26))
            background = GradientDrawable().apply { cornerRadius = px(30).toFloat(); setColor(ContextCompat.getColor(activity, R.color.menu_bg)) }
        }
        fun label(t: String, sp: Float, c: Int, serif: Boolean = false) = TextView(activity).apply {
            text = t; textSize = sp; setTextColor(c); typeface = Typeface.create(if (serif) "serif" else "sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(label("CHANNELS", 11f, ContextCompat.getColor(activity, R.color.butter)).apply { letterSpacing = 0.16f })
        card.addView(label(if (renaming) "Rename channel" else "New channel", 26f, ivory, serif = true).apply { setPadding(0, px(4), 0, px(16)) })

        val field = EditText(activity).apply {
            hint = "Channel name"; com.danielportelabyrne.cinema.design.Type.apply(this, com.danielportelabyrne.cinema.design.Type.Style.BODY); setHintTextColor(dim)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(initial); setSelectAllOnFocus(true)
            setPadding(px(22), 0, px(22), 0)
            background = GradientDrawable().apply { cornerRadius = px(26).toFloat(); setColor(ContextCompat.getColor(activity, R.color.btn_brown_on)) }
            setOnFocusChangeListener { v, focused -> (v.background as GradientDrawable).setColor(if (focused) 0xFF7A5C42.toInt() else ContextCompat.getColor(activity, R.color.btn_brown_on)) }
        }
        card.addView(field, LinearLayout.LayoutParams(px(492), px(52)))

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
        val saveButton = pill("Save", true)
        val cancel = pill("Cancel", false).apply { setOnClickListener { sheet.hide() } }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        row.addView(saveButton, LinearLayout.LayoutParams(px(190), px(52)))
        row.addView(cancel, LinearLayout.LayoutParams(px(130), px(52)).apply { marginStart = px(10) })
        card.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(16) })

        var saved = false
        fun submit() {
            if (saved) return
            val name = field.text.toString().trim()
            if (name.isEmpty()) { status.text = "Enter a name for the channel."; return }
            saved = true; saveButton.isEnabled = false
            sheet.hide()
            save(name)
        }
        saveButton.setOnClickListener { submit() }
        field.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }

        val wrap = LinearLayout(activity).apply { addView(card, LinearLayout.LayoutParams(px(560), -2)) }
        sheet.showCustom(wrap, field)
    }
}
