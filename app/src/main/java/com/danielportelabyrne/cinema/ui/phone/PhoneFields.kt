package com.danielportelabyrne.cinema.ui.phone

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.cinema.SearchField

/** A system [EditText] as a phone target: the phone's text replaces the box, and typing on the TV is passed on. */
private class EditTextTarget(
    private val edit: EditText,
    override val fieldId: String,
    override val label: String,
    override val secret: Boolean,
    override val maxLength: Int,
) : PhoneTextTarget, TextWatcher {
    /** Set while the phone's own text is going in, so it is not sent straight back. */
    private var fromPhone = false

    override fun currentText(): String = edit.text.toString()

    override fun applyFromPhone(text: String) {
        fromPhone = true
        try {
            edit.setText(text)
            edit.setSelection(edit.text.length)
        } finally {
            fromPhone = false
        }
    }

    /** The box's own Done or Next, so a phone's Done behaves exactly like the TV's (for a sign-in, it signs in). */
    override fun phoneDone() {
        edit.onEditorAction(edit.imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) { if (!fromPhone) PhoneInput.textChanged(this) }
}

/**
 * Makes [edit] a phone target. Call it after the box is set up (its own focus listener is kept). The phone follows the
 * box whenever it has focus, and lets go when it loses focus or leaves the screen.
 */
fun bindPhone(edit: EditText, fieldId: String, label: String, secret: Boolean = false, maxLength: Int = 200) {
    val target = EditTextTarget(edit, fieldId, label, secret, maxLength)
    val previous = edit.onFocusChangeListener
    edit.setOnFocusChangeListener { v, focused ->
        previous?.onFocusChange(v, focused)
        if (focused) PhoneInput.focus(target) else PhoneInput.blur(target)
    }
    edit.addTextChangedListener(target)
    edit.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { if (v.hasFocus()) PhoneInput.focus(target) }
        override fun onViewDetachedFromWindow(v: View) { PhoneInput.blur(target) }
    })
    if (edit.hasFocus()) PhoneInput.focus(target)
}

/** A line of [edit] and the phone button, sized to match. The phone button opens the scan-to-connect card. */
fun phoneRow(context: Context, edit: EditText, editWidth: Int, height: Int, gap: Int): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false
        addView(edit, LinearLayout.LayoutParams(editWidth, height))
        val button = SearchField.phoneButton(context).apply { setOnClickListener { PhoneInput.toggleQr() } }
        addView(button, LinearLayout.LayoutParams(height, height).apply { marginStart = gap })
    }

/** An id for a field made from its hint, so the phone can tell one box from another ("Server address" becomes "server-address"). */
fun fieldIdFor(hint: String): String = hint.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
