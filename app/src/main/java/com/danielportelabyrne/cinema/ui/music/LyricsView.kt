package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.data.music.Lyrics
import com.danielportelabyrne.cinema.design.Cinema

/**
 * Lyrics as Apple Music shows them: large bold lines, the one being sung bright and the rest faint, gliding up so
 * the current line sits a third of the way down. Untimed lyrics scroll with the track instead. The list fades out at
 * the top and bottom edges.
 */
class LyricsView(context: Context) : FrameLayout(context) {
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var lyrics: Lyrics? = null
    private var current = NONE
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    init {
        clipChildren = true
        setWillNotDraw(false)
        addView(list, LayoutParams(-1, -2))
    }

    val hasLyrics: Boolean get() = lyrics != null

    fun set(l: Lyrics?) {
        lyrics = l
        current = NONE
        list.removeAllViews()
        list.animate().cancel()
        l?.lines?.forEach { line ->
            val gap = line.text.isEmpty()
            val v = Cinema.text(context, if (gap) "\u2022  \u2022  \u2022" else line.text, if (gap) 34 else 52, 0xFFFFFFFF.toInt(), weight = 700)
            v.setLineSpacing(0f, 1.08f)
            v.alpha = if (l.synced) FAINT else UNTIMED
            v.pivotX = 0f
            list.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Cinema.px(context, 38) })
        }
        list.translationY = anchor()
    }

    /** Follows playback: [positionMs] picks the timed line; untimed lyrics move with [fraction] of the track. */
    fun position(positionMs: Long, fraction: Float) {
        val l = lyrics ?: return
        if (height == 0 || list.height == 0) return
        if (!l.synced) {
            val travel = (list.height - height * 0.5f).coerceAtLeast(0f)
            list.translationY = anchor() - travel * fraction.coerceIn(0f, 1f)
            return
        }
        val at = l.lineAt(positionMs)
        if (at == current) return
        current = at
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            val on = i == at
            v.animate().alpha(if (on) 1f else if (i < at) PAST else FAINT).scaleX(if (on) 1f else SMALL).scaleY(if (on) 1f else SMALL)
                .setDuration(380).setInterpolator(EASE).start()
        }
        val top = list.getChildAt(at.coerceAtLeast(0))?.top ?: 0
        list.animate().translationY(anchor() - top).setDuration(700).setInterpolator(EASE).start()
    }

    private fun anchor() = height * 0.32f

    /** The list is as tall as all its lines, not the window, or lines past the first screen are never laid out. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        list.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mask.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0, -0x1000000, -0x1000000, 0), floatArrayOf(0f, 0.14f, 0.8f, 1f), Shader.TileMode.CLAMP)
        current = NONE
        list.translationY = anchor()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
        canvas.restoreToCount(layer)
    }

    companion object {
        private const val NONE = -2
        private const val FAINT = 0.34f
        private const val PAST = 0.24f
        private const val UNTIMED = 0.85f
        private const val SMALL = 0.96f
        private val EASE = PathInterpolator(0.3f, 0f, 0.2f, 1f)
    }
}
