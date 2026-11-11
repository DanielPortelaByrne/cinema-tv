package com.danielportelabyrne.cinema.design

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.provider.Settings.Global
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import kotlin.math.abs
import kotlin.math.max

/**
 * The app's one motion language: slow, soft and slightly lagging, like a Mid-Century room that
 * happens to move. Everything animates through these curves and durations so the feel stays consistent.
 * If the system's animation scale is 0 (accessibility "remove animations") everything becomes instant.
 */
object Motion {
    /** Long soft tail: it leaves quickly, then drifts to rest. Used for panning the page and rails. */
    val SWOOSH = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Gentle ease-out with no bounce. Used for focus, fades and sheets. */
    val SETTLE = PathInterpolator(0f, 0f, 0.2f, 1f)

    /**
     * Quick to start, long soft landing, no overshoot: (0.22, 1.0, 0.36, 1.0). The Cinema page's rows moving up and down
     * (see CinemaContentScrollView) and the navigation rail opening and closing share it.
     */
    val GLIDE = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** Master switch: false makes every scroll, focus and select change instant. */
    const val ENABLED = true

    const val FOCUS_IN_MS = 160L
    const val FOCUS_OUT_MS = 120L
    const val FADE_MS = 200L

    /** Sheets: the page steps back to this scale as a layer arrives over it. */
    const val PAGE_RECEDE = 0.975f
    const val SHEET_IN_MS = 260L
    const val SHEET_OUT_MS = 200L
    /** A poster travelling from the page into a sheet (and back). */
    const val FLIGHT_MS = 380L

    private class Running(val animator: ValueAnimator, val target: Int)

    fun animationsOn(view: View): Boolean = ENABLED && try {
        Global.getFloat(view.context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (_: Exception) { true }

    /** How long a pan of [distancePx] takes: never abrupt, never tedious. */
    fun panDuration(distancePx: Int): Long = (300 + abs(distancePx) * 0.06f).toLong().coerceIn(320, 380)

    // ---- panning -----------------------------------------------------------------------------------

    fun scrollVerticalTo(sv: ScrollView, target: Int) {
        val content = sv.getChildAt(0) ?: return
        val max = max(0, content.height - (sv.height - sv.paddingTop - sv.paddingBottom))
        pan(sv, target.coerceIn(0, max), sv.scrollY) { sv.scrollTo(sv.scrollX, it) }
    }

    fun scrollHorizontalTo(sv: HorizontalScrollView, target: Int) {
        val content = sv.getChildAt(0) ?: return
        val max = max(0, content.width - (sv.width - sv.paddingLeft - sv.paddingRight))
        pan(sv, target.coerceIn(0, max), sv.scrollX) { sv.scrollTo(it, sv.scrollY) }
    }

    private fun pan(view: View, to: Int, from: Int, apply: (Int) -> Unit) {
        val running = view.getTag(R.id.motion_pan) as? Running
        if (running != null && running.target == to && running.animator.isRunning) return
        running?.animator?.cancel()
        if (to == from) return
        if (!animationsOn(view)) { apply(to); return }
        // Restart from where the view is right now, so quick D-pad presses glide on instead of jerking.
        val animator = ValueAnimator.ofInt(from, to).apply {
            duration = panDuration(to - from)
            interpolator = SWOOSH
            addUpdateListener { apply(it.animatedValue as Int) }
        }
        view.setTag(R.id.motion_pan, Running(animator, to))
        animator.start()
    }

    // ---- small pieces ------------------------------------------------------------------------------

    /** Fades a drawable's alpha; [onEnd] runs when it finishes (not if cancelled). */
    fun fadeDrawable(view: View, drawable: Drawable, from: Int, to: Int, ms: Long, onEnd: (() -> Unit)? = null) {
        val old = view.getTag(R.id.motion_fade) as? Animator
        old?.cancel()
        if (!animationsOn(view)) { drawable.alpha = to; onEnd?.invoke(); return }
        val a = ObjectAnimator.ofInt(drawable, "alpha", from, to).apply {
            duration = ms; interpolator = SETTLE
            if (onEnd != null) addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) onEnd() }
            })
        }
        view.setTag(R.id.motion_fade, a)
        a.start()
    }

    /** Glides a label's colour instead of snapping it. */
    fun tweenTextColor(tv: TextView, to: Int, ms: Long = 80L) {
        (tv.getTag(R.id.motion_text) as? Animator)?.cancel()
        val from = tv.currentTextColor
        if (from == to) return
        if (!animationsOn(tv)) { tv.setTextColor(to); return }
        val a = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = ms; interpolator = SETTLE
            addUpdateListener { tv.setTextColor(it.animatedValue as Int) }
        }
        tv.setTag(R.id.motion_text, a)
        a.start()
    }

    /**
     * Focus, press, release: a quick dip below the focused size, then a slower settle back up, so OK feels
     * like pressing an object rather than toggling a state. The dip is relative to [restScale].
     */
    fun press(view: View, restScale: Float) {
        if (!animationsOn(view)) return
        view.animate().cancel()
        val dip = restScale * PRESS_DIP
        view.animate().scaleX(dip).scaleY(dip).setDuration(PRESS_DOWN_MS).setInterpolator(SETTLE).withEndAction {
            view.animate().scaleX(restScale).scaleY(restScale).setDuration(PRESS_UP_MS).setInterpolator(SETTLE).start()
        }.start()
    }

    // ---- layered focus -----------------------------------------------------------------------------

    const val CARD_SCALE = 1.045f
    const val CARD_INNER_SCALE = 1.025f
    const val PRESS_DIP = 0.955f
    const val PRESS_DOWN_MS = 70L
    const val PRESS_UP_MS = 190L
    /** Resting alpha of a card's caption; focus brings it to full strength a beat after the image lifts. */
    const val CAPTION_REST_ALPHA = 0.82f
    private const val INNER_LAG_MS = 70L
    private const val CAPTION_DELAY_MS = 40L

    /**
     * Focus for an image object, in layers: the clipped [frame] lifts and scales, the picture [inner] inside
     * its clip scales a little more slowly (so the image seems to sit behind glass), and the [caption]
     * brightens and follows the lift a beat later. The ivory mat is faded by the caller, as before.
     */
    fun focusImageCard(frame: View, inner: View?, caption: View?, focused: Boolean) {
        val d = frame.resources.displayMetrics.density
        val scale = if (focused) CARD_SCALE else 1f
        val lift = if (focused) -3f * d else 0f
        val innerScale = if (focused) CARD_INNER_SCALE else 1f
        val captionAlpha = if (focused) 1f else CAPTION_REST_ALPHA
        if (!animationsOn(frame)) {
            frame.scaleX = scale; frame.scaleY = scale; frame.translationY = lift; frame.translationZ = if (focused) 18f else 0f
            inner?.scaleX = innerScale; inner?.scaleY = innerScale
            caption?.alpha = captionAlpha; caption?.translationY = lift
            return
        }
        val ms = if (focused) FOCUS_IN_MS else FOCUS_OUT_MS
        frame.animate().scaleX(scale).scaleY(scale).translationY(lift).translationZ(if (focused) 18f else 0f)
            .setStartDelay(0).setDuration(ms).setInterpolator(SETTLE).start()
        inner?.animate()?.scaleX(innerScale)?.scaleY(innerScale)?.setDuration(ms + INNER_LAG_MS)?.setInterpolator(SETTLE)?.start()
        caption?.animate()?.alpha(captionAlpha)?.translationY(lift)
            ?.setStartDelay(if (focused) CAPTION_DELAY_MS else 0)?.setDuration(ms)?.setInterpolator(SETTLE)?.start()
    }

    /** A round app icon's extra: the smallest upward lift, so the disc rises off the shelf as its halo lights. */
    fun liftIcon(ring: View, focused: Boolean) {
        val lift = if (focused) -3f * ring.resources.displayMetrics.density else 0f
        if (!animationsOn(ring)) { ring.translationY = lift; return }
        ring.animate().translationY(lift).setDuration(if (focused) FOCUS_IN_MS + 40 else FOCUS_OUT_MS).setInterpolator(SETTLE).start()
    }

    // ---- printed stills -----------------------------------------------------------------------------

    /**
     * App stills (neon thumbnails, bright key art) rest a little quieter and warmer, like prints in a warm
     * room, and come to full colour when focused. [amount] 0 = resting, 1 = full colour.
     */
    fun printFilter(amount: Float): android.graphics.ColorFilter? {
        if (amount >= 1f) return null
        val m = android.graphics.ColorMatrix().apply { setSaturation(PRINT_SATURATION + (1f - PRINT_SATURATION) * amount) }
        val warm = 1f - amount
        val v = 1f - 0.1f * warm
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            v * (1f + 0.03f * warm), 0f, 0f, 0f, 0f,
            0f, v, 0f, 0f, 0f,
            0f, 0f, v * (1f - 0.06f * warm), 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
        return android.graphics.ColorMatrixColorFilter(m)
    }

    /** Brings [image] to full colour on focus and back to its printed rest after. */
    fun printFocus(image: android.widget.ImageView, focused: Boolean) {
        (image.getTag(R.id.motion_fade) as? Animator)?.cancel()
        val from = (image.getTag(R.id.motion_print) as? Float) ?: 0f
        val to = if (focused) 1f else 0f
        if (!animationsOn(image)) { image.setTag(R.id.motion_print, to); image.colorFilter = printFilter(to); return }
        val a = ValueAnimator.ofFloat(from, to).apply {
            duration = if (focused) 200L else FOCUS_OUT_MS + 40; interpolator = SETTLE
            addUpdateListener { val f = it.animatedValue as Float; image.setTag(R.id.motion_print, f); image.colorFilter = printFilter(f) }
        }
        image.setTag(R.id.motion_fade, a)
        a.start()
    }

    private const val PRINT_SATURATION = 0.55f

    // ---- content replacement -----------------------------------------------------------------------

    const val SWAP_MS = 240L

    private class Swap(val animator: ValueAnimator, var target: CharSequence, var applied: Boolean)

    /**
     * Replaces a label's text with a short fade-through (out, swap, back in). Calls that arrive mid-swap
     * just retarget it, so rapid changes never stack or leave the label half-faded. Off-screen labels,
     * unchanged text and "remove animations" all set the text directly.
     */
    fun swapText(tv: TextView, text: CharSequence) {
        val running = tv.getTag(R.id.motion_swap) as? Swap
        if (running != null && running.animator.isRunning) {
            if (!running.applied) { running.target = text; return }
            if (tv.text.toString() == text.toString()) return    // already fading in the right text
            running.animator.cancel()                            // fading in stale text: turn round from here
        } else if (tv.text.toString() == text.toString()) return
        if (!animationsOn(tv) || !tv.isShown) { tv.text = text; tv.alpha = 1f; return }
        val from = tv.alpha
        val swap = Swap(ValueAnimator.ofFloat(0f, 1f), text, false)
        swap.animator.apply {
            duration = SWAP_MS; interpolator = null
            addUpdateListener {
                val f = it.animatedFraction
                if (f < 0.4f) tv.alpha = from * (1f - SETTLE.getInterpolation(f / 0.4f))
                else {
                    if (!swap.applied) { tv.text = swap.target; swap.applied = true }
                    tv.alpha = SETTLE.getInterpolation((f - 0.4f) / 0.6f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    if (!swap.applied) tv.text = swap.target
                    tv.alpha = 1f
                }
            })
        }
        tv.setTag(R.id.motion_swap, swap)
        swap.animator.start()
    }

    /** Old picture recedes (a touch smaller, fading), new one settles in: for posters and sleeves. */
    fun swapImage(view: View, apply: () -> Unit) {
        if (!animationsOn(view) || !view.isShown) { apply(); view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f; return }
        view.animate().cancel()
        view.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(110).setInterpolator(SETTLE).withEndAction {
            apply()
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(SWOOSH).start()
        }.start()
    }

    /** Fades [container] in and lifts [card] a short way into place (no scale), for sheets and menus. */
    fun enter(container: View, card: View?, risePx: Float) {
        if (!animationsOn(container)) return
        container.animate().cancel()
        container.alpha = 0f
        // A full-screen fade is drawn through one offscreen texture rather than re-drawing every child each frame;
        // withLayer() puts the hardware layer on for this animation only and restores the previous layer type at its end.
        container.animate().alpha(1f).setDuration(SHEET_IN_MS).setInterpolator(SETTLE).withLayer().start()
        card?.let {
            val rise = kotlin.math.min(abs(risePx), 12f * container.resources.displayMetrics.density) * (if (risePx < 0) -1f else 1f)
            it.animate().cancel()
            it.translationY = rise
            it.scaleX = 0.985f; it.scaleY = 0.985f
            it.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(SHEET_IN_MS + 40).setInterpolator(SWOOSH).start()
        }
    }

    /** The reverse of [enter]: the card sinks back a little as the layer fades, then [onEnd] runs (always). */
    fun exit(container: View, card: View?, onEnd: () -> Unit) {
        if (!animationsOn(container)) { onEnd(); return }
        val sink = 8f * container.resources.displayMetrics.density
        card?.animate()?.translationY(sink)?.scaleX(0.985f)?.scaleY(0.985f)?.setDuration(SHEET_OUT_MS)?.setInterpolator(SETTLE)?.start()
        container.animate().cancel()
        container.animate().alpha(0f).setDuration(SHEET_OUT_MS).setInterpolator(SETTLE).withEndAction(onEnd).start()
    }
}
