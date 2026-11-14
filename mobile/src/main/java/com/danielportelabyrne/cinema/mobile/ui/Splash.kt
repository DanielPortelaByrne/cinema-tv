package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The branded opening, the TV's "velvet skip" without the sound. The mark is built like a cut-paper print being
 * registered: its five discs fall onto the screen as a slipped stack (the three outer ones together, then orange, then
 * saffron, landing a little high and settling back). The stack hangs there, slowly slipping, until it snaps into register:
 * the discs become the flat Cinema mark, the play triangle prints, the rings ripple outwards outer ring first, and the
 * wordmark slides out from behind the mark. Every frame is a pure function of one clock. Tap to skip.
 */
@Composable
fun Splash(onDone: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(TOTAL, tween((TOTAL * 1000).toInt(), easing = LinearEasing))
        onDone()
    }
    val measurer = rememberTextMeasurer()
    val word = remember { TextStyle(fontFamily = fraunces(500, 40f), fontSize = 44.sp, letterSpacing = (-0.9).sp, color = Palette.ivory) }
    val wordSize = remember { measurer.measure("Cinema", word).size }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fadeOut(clock.value) }.background(Palette.bg)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }) {
        Ground(Grounds.HOME, Modifier.alpha(0.55f))
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val side = min(size.width, size.height) * 0.40f
            val u = side / 56f
            val gap = side * 18f / 56f
            val reveal = easeInOut((t - T_WORD) / 0.7f)
            // The pair re-centres as the word arrives.
            val pair = side + (gap + wordSize.width) * reveal
            val left = (size.width - pair) / 2f
            val top = size.height / 2f - side / 2f
            val markTopLeft = Offset(left, top)
            val centre = markTopLeft + Offset(side / 2f, side / 2f)

            // The word, first, so the mark covers where it starts.
            if (reveal > 0f) {
                val x = lerp(left + side * 0.5f, left + side + gap, reveal)
                clipRect(left + side * 0.5f, 0f, size.width, size.height) {
                    drawText(measurer, "Cinema", Offset(x, size.height / 2f - wordSize.height / 2f), word.copy(color = Palette.ivory.copy(alpha = reveal)))
                }
            }

            if (t < T_SNAP) {
                stack(t, markTopLeft, u)
            } else {
                val settle = easeOut((t - T_SNAP) / 0.18f)
                for (i in 0 until 5) drawCircle(COLOURS[i], RADII[i] * u, centre + Offset((SLIP[i] - 6.9f) * (1f - settle) * u, 0f))
                val print = easeOut((t - T_SNAP - 0.06f) / 0.2f)
                if (print > 0f) {
                    val tri = Path().apply {
                        moveTo(markTopLeft.x + 25.2f * u, markTopLeft.y + 24.2f * u); lineTo(markTopLeft.x + 25.2f * u, markTopLeft.y + 31.8f * u)
                        lineTo(markTopLeft.x + 33.6f * u, markTopLeft.y + 28f * u); close()
                    }
                    scale(0.6f + 0.4f * print, centre) { drawPath(tri, Color(0xFF1B0D06).copy(alpha = print)) }
                }
                // Rings burst outwards, the outer ring first.
                RADII.forEachIndexed { k, r ->
                    val p = (t - T_SNAP - 0.04f - 0.07f * k) / 0.7f
                    if (p in 0f..1f) drawCircle(COLOURS[k].copy(alpha = (1f - p) * 0.9f), r * u * (1f + 1.1f * easeOut(p)), centre, style = Stroke(width = (6f - 4f * p) * u * 0.5f))
                }
            }
        }
    }
}

private const val T_SNAP = 1.95f
private const val T_WORD = 2.3f
private const val TOTAL = 3.5f

private fun fadeOut(t: Float): Float = if (t < 3.1f) 1f else (1f - (t - 3.1f) / 0.4f).coerceIn(0f, 1f)

private val RADII = floatArrayOf(27f, 22f, 17f, 12f, 7.2f)
private val COLOURS = listOf(Palette.stripeMaroon, Palette.stripeRed, Palette.stripeTerra, Palette.stripeOrange, Palette.stripeSaffron)
/** How far along the slip each disc lies in the stack, in mark units: authored, slightly uneven steps. */
private val SLIP = floatArrayOf(0f, 3.0f, 6.6f, 9.9f, 13.8f)
/** When each disc lands: the three outer together, then orange, then saffron. */
private val LAND = floatArrayOf(0.55f, 0.55f, 0.55f, 0.95f, 1.35f)
private const val FALL = 0.45f

private fun DrawScope.stack(t: Float, markTopLeft: Offset, u: Float) {
    val centreX = markTopLeft.x + 28f * u
    val centreY = markTopLeft.y + 28f * u
    // Hanging in the silence, the stack slips a little, off register.
    val drift = sin(t * 2.2f * PI.toFloat()) * 0.8f * easeInOut((t - 1.4f) / 0.5f)
    val fromAbove = (centreY / u) + 32f
    for (i in 0 until 5) {
        val p = (t - (LAND[i] - FALL)) / FALL
        if (p <= 0f) continue
        val y = if (p < 1f) -fromAbove * (1f - p * p)
                else if (i >= 3) -3f * (1f - easeOut((t - LAND[i]) / 0.25f)) else 0f
        val x = (SLIP[i] - 6.9f) + drift * (i - 2) * 0.5f
        drawCircle(COLOURS[i], RADII[i] * u, Offset(centreX + x * u, centreY + y * u))
    }
}
