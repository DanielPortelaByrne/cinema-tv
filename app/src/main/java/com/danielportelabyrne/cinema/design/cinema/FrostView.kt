package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewOutlineProvider
import com.danielportelabyrne.cinema.design.Cinema

/**
 * Frosted glass: a blurred copy of whatever lies beneath it ([sources], drawn in this view's parent's coordinates),
 * tinted with [tint], inside a pill of [radiusPx]. No edge line: the blur alone sets it apart.
 *
 * Android Views have no backdrop blur. On API 31+ the strip of the sources under this view is drawn, at a quarter of
 * the size, into a small bitmap ([SCALE]); a RenderNode blurs that bitmap on the GPU and it is drawn back scaled up.
 * A 44 px blur hides the lower resolution entirely. The bitmap is a snapshot, not a live copy of the page, so the
 * blur costs nothing while nothing moves and the page's own frames never re-render it; [refresh] takes a new
 * snapshot at most every [MIN_INTERVAL_MS] while things move beneath, and once more when they stop. Older systems
 * get the tint alone, made nearly opaque so text behind stays out of the way.
 */
class FrostView(context: Context, private val sources: List<View>, private val radiusPx: Float) : View(context) {
    private val node: RenderNode? = if (Build.VERSION.SDK_INT >= 31) RenderNode("frost").apply {
        setRenderEffect(RenderEffect.createBlurEffect(Cinema.pxf(context, 44) * SCALE, Cinema.pxf(context, 44) * SCALE, Shader.TileMode.CLAMP))
    } else null
    private val fill = Paint()
    private var snap: Bitmap? = null
    private var snapCanvas: Canvas? = null
    private var hasSnap = false
    private var lastSnapAt = 0L
    private var settleUntil = 0L
    private var queued = false

    /** The tint over the blur. Without blur it is forced close to opaque. */
    var tint: Int = Cinema.RAIL_FILL
        set(value) { field = value; invalidate() }

    init {
        isFocusable = false; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, radiusPx)) }
        }
        clipToOutline = true
    }

    /**
     * Something beneath moved: take a new snapshot soon (now, or when [MIN_INTERVAL_MS] has passed since the last), and
     * keep taking them for [forMs] while something beneath animates. The last one always lands after the movement.
     */
    fun refresh(forMs: Long = 0L) {
        if (node == null) { invalidate(); return }
        val now = SystemClock.uptimeMillis()
        settleUntil = maxOf(settleUntil, now + forMs)
        schedule(now)
    }

    private fun schedule(now: Long) {
        if (queued) return
        queued = true
        postDelayed(snapshotTask, (lastSnapAt + MIN_INTERVAL_MS - now).coerceAtLeast(0L))
    }

    private val snapshotTask = Runnable {
        queued = false
        if (!isAttachedToWindow) return@Runnable
        takeSnapshot()
        // Still animating beneath (a card growing, a row gliding): one more after the interval, and a last one after it ends.
        val now = SystemClock.uptimeMillis()
        if (now < settleUntil + MIN_INTERVAL_MS) schedule(now)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        snap?.recycle(); snap = null; snapCanvas = null; hasSnap = false
        if (node != null && w > 0 && h > 0) refresh()
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(snapshotTask); queued = false }

    /** Draws the strip of the sources beneath this view, at [SCALE], into the snapshot, and hands it to the blur. */
    private fun takeSnapshot() {
        val n = node ?: return
        if (width <= 0 || height <= 0) return
        lastSnapAt = SystemClock.uptimeMillis()
        val started = SystemClock.uptimeMillis()
        val bw = Math.ceil(width * SCALE.toDouble()).toInt().coerceAtLeast(1); val bh = Math.ceil(height * SCALE.toDouble()).toInt().coerceAtLeast(1)
        val bmp = snap?.takeIf { it.width == bw && it.height == bh } ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { snap?.recycle(); snap = it; snapCanvas = Canvas(it) }
        val c = snapCanvas ?: Canvas(bmp).also { snapCanvas = it }
        bmp.eraseColor(Cinema.BG)
        val save = c.save()
        c.scale(SCALE, SCALE)
        c.clipRect(0, 0, width, height)
        c.translate(-left.toFloat(), -top.toFloat())
        sources.forEach { s ->
            if (s.visibility != VISIBLE) return@forEach
            // Only the source's position here: drawUnder applies each view's scroll to its children itself, so taking the
            // scroll off here too would draw a scrolled page shifted up by its scroll, not directly beneath the glass.
            val inner = c.save()
            c.translate(s.left.toFloat(), s.top.toFloat())
            try { drawUnder(s, c) } catch (_: Exception) { }   // a source that cannot draw in software is left out, not fatal
            c.restoreToCount(inner)
        }
        c.restoreToCount(save)
        n.setPosition(0, 0, bw, bh)
        val rec = n.beginRecording()
        rec.drawBitmap(bmp, 0f, 0f, null)
        n.endRecording()
        hasSnap = true
        com.danielportelabyrne.cinema.ui.Perf.took("frost.snapshot", SystemClock.uptimeMillis() - started)
        invalidate()
    }

    /**
     * Draws [v] into the snapshot, visiting only the children that reach into the canvas's clip (the strip under the
     * rail). A plain View.draw() of the page would paint every row and card in software, ~45 ms on the TV; this paints
     * the handful under the rail. Backgrounds and pictures are drawn; outlines and shadows are not (the blur hides them).
     */
    private fun drawUnder(v: View, c: Canvas) {
        // Card pictures are hardware bitmaps, which a software canvas cannot draw: under the blur their card's own colour
        // stands in for them, which costs nothing and reads the same once blurred.
        if (v is android.widget.ImageView && ((v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.config == android.graphics.Bitmap.Config.HARDWARE)) return
        if (v !is android.view.ViewGroup) { v.draw(c); return }
        v.background?.let { bg -> bg.setBounds(0, 0, v.width, v.height); bg.draw(c) }
        for (i in 0 until v.childCount) {
            val child = v.getChildAt(i)
            if (child.visibility != VISIBLE || child.alpha <= 0f) continue
            val save = c.save()
            c.translate((child.left - v.scrollX).toFloat(), (child.top - v.scrollY).toFloat())
            if (!child.matrix.isIdentity) c.concat(child.matrix)
            if (!c.quickReject(0f, 0f, child.width.toFloat(), child.height.toFloat())) {
                if (child.alpha < 1f) c.saveLayerAlpha(0f, 0f, child.width.toFloat(), child.height.toFloat(), (child.alpha * 255).toInt())
                drawUnder(child, c)
            }
            c.restoreToCount(save)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val n = node
        val blurred = n != null && hasSnap && canvas.isHardwareAccelerated
        if (blurred && Build.VERSION.SDK_INT >= 31) {
            val save = canvas.save()
            canvas.scale(1f / SCALE, 1f / SCALE)
            canvas.drawRenderNode(n!!)
            canvas.restoreToCount(save)
        }
        fill.color = if (blurred) tint else Cinema.alpha(tint, 0.94f)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
    }

    private companion object {
        const val SCALE = 0.25f
        const val MIN_INTERVAL_MS = 120L
    }
}
