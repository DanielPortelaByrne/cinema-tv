package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema

/** The round microphone button every search uses: saffron to terracotta, with a halo that grows when it is focused. */
class MicButton(context: Context) : View(context) {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = context.getDrawable(R.drawable.ic_cin_mic)!!.mutate().apply { setTint(0xFF1D0D05.toInt()) }

    init { isFocusable = true; isClickable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        disc.shader = RadialGradient(w / 2f, h * 0.4f, w / 2f, Cinema.STRIPE_SAFFRON, Cinema.STRIPE_TERRA, Shader.TileMode.CLAMP)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val s = if (gainFocus) 1.1f else 1f
        animate().scaleX(s).scaleY(s).setDuration(160).start(); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        ring.color = Cinema.alpha(Cinema.STRIPE_ORANGE, if (isFocused) 0.3f else 0.12f); canvas.drawCircle(c, c, c + Cinema.pxf(context, 15), ring)
        ring.color = Cinema.alpha(Cinema.STRIPE_TERRA, if (isFocused) 0.45f else 0.25f); canvas.drawCircle(c, c, c + Cinema.pxf(context, 7), ring)
        canvas.drawCircle(c, c, c, disc)
        val i = Cinema.px(context, 19)
        icon.setBounds(i, i, width - i, height - i); icon.draw(canvas)
    }
}
