package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The search bar every search screen uses, so they all look and behave the same: a lead (an icon, or a page's own mark),
 * the query with its caret, a small hint at the right, a phone to type from and the microphone to speak with. While the
 * microphone listens the hint says LISTENING, in the same place and style everywhere.
 *
 * [prompt] is the grey text shown while nothing is typed; [hintText] is the small text at the right (empty for none).
 */
class SearchField(
    context: Context,
    lead: View,
    private val prompt: String,
    private val hintText: String,
) : LinearLayout(context) {
    /** The lead: the search icon, or a page's own mark such as the Letterboxd dots and "Lists". */
    val icon: View = lead
    val caret = View(context).apply { setBackgroundColor(Cinema.SAFFRON) }
    val query: TextView = Cinema.text(context, "", 36, Cinema.CREAM, weight = 300).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.START }
    val hint: TextView = Cinema.text(context, hintText, 18, Cinema.MUTED, tracking = 0.04f)
    val mic = MicButton(context).apply { contentDescription = "Voice search" }
    /** Types from a phone: the page's text box is filled with what the phone sends. */
    val phone: View = phoneButton(context)

    /** Pressed the microphone (hold or press it, as the page decides). */
    var onMic: (() -> Unit)? = null
    /** Pressed the phone: the page opens its phone typing. */
    var onPhone: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false
        setPadding(Cinema.px(context, 42), 0, Cinema.px(context, 14), 0)
        background = Cinema.glass(context, 56f, 0x17FFF2DE)
        // A single icon is a 40 px square; a lead with several parts (the Letterboxd dots and "Lists") takes its own size.
        addView(icon, if (icon is android.widget.ImageView) LayoutParams(Cinema.px(context, 40), Cinema.px(context, 40)) else LayoutParams(-2, -2))
        // The query and its caret share a line; the caret sits after the typed text, or before the grey prompt while empty.
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(line, LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(context, 22) })
        line.addView(caret, LayoutParams(Cinema.px(context, 3), Cinema.px(context, 48)))
        line.addView(query, LayoutParams(-2, -2).apply { marginStart = Cinema.px(context, 22) })
        line.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) { query.maxWidth = (v.width - Cinema.px(context, 40)).coerceAtLeast(0); query.post { query.requestLayout() } }
        }
        addView(hint, LayoutParams(-2, -2).apply { marginEnd = Cinema.px(context, 20) })
        addView(phone, LayoutParams(Cinema.px(context, 84), Cinema.px(context, 84)).apply { marginStart = Cinema.px(context, 8) })
        addView(mic, LayoutParams(Cinema.px(context, 84), Cinema.px(context, 84)).apply { marginStart = Cinema.px(context, 8) })
        mic.setOnClickListener { onMic?.invoke() }
        phone.setOnClickListener { onPhone?.invoke() }
        render("", null)
    }

    /**
     * Shows [text] as the query, or, while the microphone listens, [partial] (the words so far, the last one dimmer).
     * [partial] null means not listening.
     */
    fun render(text: String, partial: String?) {
        val listening = partial != null
        query.text = when {
            listening && partial!!.isNotEmpty() -> SpannableStringBuilder(partial).apply {
                val cut = partial.lastIndexOf(' ')
                if (cut > 0) setSpan(ForegroundColorSpan(Cinema.DIM), cut, partial.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            listening -> ""
            text.isEmpty() -> prompt
            else -> text
        }
        val idle = !listening && text.isEmpty()
        query.setTextColor(if (idle) 0xFF8F8272.toInt() else Cinema.CREAM)
        Cinema.weight(query, 36, serif = false, weight = if (idle) 300 else 400)
        caret.visibility = if (listening) View.GONE else View.VISIBLE
        placeCaret(after = !listening && text.isNotEmpty())
        (icon as? android.widget.ImageView)?.setColorFilter(if (listening) Cinema.TERRA else Cinema.SAFFRON)
        hint.visibility = if (listening || hintText.isNotEmpty()) View.VISIBLE else View.GONE
        hint.text = if (listening) "LISTENING" else hintText
        Cinema.style(hint, 18, if (listening) Cinema.TERRA else Cinema.MUTED, weight = if (listening) 600 else 400, tracking = if (listening) 0.14f else 0.04f)
        background = Cinema.glass(context, 56f, if (listening) Cinema.alpha(Cinema.STRIPE_TERRA, 0.16f) else 0x17FFF2DE)
    }

    private fun placeCaret(after: Boolean) {
        val line = caret.parent as? LinearLayout ?: return
        val caretFirst = line.indexOfChild(caret) == 0
        if (caretFirst == !after) return
        line.removeView(caret)
        line.addView(caret, if (after) 1 else 0)
        (query.layoutParams as LinearLayout.LayoutParams).marginStart = if (after) 0 else Cinema.px(context, 22)
        // Right after the last letter, touching it: a gap there reads as a typed space.
        (caret.layoutParams as LinearLayout.LayoutParams).marginStart = 0
        line.requestLayout()
    }

    companion object {
        /** A round glass button with the phone icon, cream when focused. */
        fun phoneButton(context: Context): View {
            val icon = Cinema.icon(context, R.drawable.ic_cin_phone, Cinema.CREAM)
            return android.widget.FrameLayout(context).apply {
                background = Cinema.glass(context, 42f, Cinema.GLASS_STRONG)
                isFocusable = true; isClickable = true; contentDescription = "Type on your phone"
                addView(icon, android.widget.FrameLayout.LayoutParams(Cinema.px(context, 40), Cinema.px(context, 40), Gravity.CENTER))
                setOnFocusChangeListener { _, focused ->
                    background = Cinema.glass(context, 42f, if (focused) Cinema.CREAM else Cinema.GLASS_STRONG)
                    icon.setColorFilter(if (focused) Cinema.INK else Cinema.CREAM)
                }
            }
        }
    }
}
