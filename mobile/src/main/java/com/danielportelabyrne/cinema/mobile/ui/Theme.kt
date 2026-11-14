package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.mobile.R

/**
 * The TV's palette (design/Cinema.kt): a near-black warm ground, cream type, and the 70s stripe set (saffron, orange,
 * terracotta, red, maroon). Surfaces are glass: cream at a few percent, so the painted ground shows through.
 */
object Palette {
    val bg = Color(0xFF0B0807)
    val ink = Color(0xFF1B120C)
    val ivory = Color(0xFFF6ECDB)          // cream
    val ivoryMuted = Color(0xFFB6A896)
    val dim = Color(0xFF7F7366)
    val reading = Color(0xFFD7CBB9)        // synopsis text
    val butter = Color(0xFFEDBE56)         // saffron
    val terracotta = Color(0xFFE27A49)
    val teal = Color(0xFF3AA597)
    val danger = Color(0xFFF0A48A)
    val placeholder = Color(0xFF221A15)
    val letterboxd = Color(0xFFE85D4A)

    val stripeSaffron = Color(0xFFF6BF33)
    val stripeOrange = Color(0xFFF08A25)
    val stripeTerra = Color(0xFFE2562B)
    val stripeRed = Color(0xFFC2281D)
    val stripeMaroon = Color(0xFF6B1A10)
    val maroon = Color(0xFF5D1710)
    /** Saffron to maroon: the eyebrow pill, the row marker, the stripe shadow. */
    val stripes = listOf(stripeSaffron, stripeOrange, stripeTerra, stripeRed, stripeMaroon)
    /** Widest first: maroon outside to saffron inside (ribbons, the mark's rings). */
    val rings = listOf(maroon, stripeRed, stripeTerra, stripeOrange, stripeSaffron)

    val glass = Color(0x13FFF2DE)
    val glassStrong = Color(0x21FFF2DE)
    val hairline = Color(0x24FFF2DE)
    val railFill = Color(0xF0120D0A)

    // Legacy role names used across the screens.
    val surface = glass
    val raised = glassStrong
    val selected = Color(0x38FFF2DE)
    val espresso = bg
}

/** Fraunces (display) at a weight and optical size, the way the TV sets it. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun fraunces(weight: Int = 500, opticalSize: Float = 36f) = FontFamily(
    Font(R.font.fraunces, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize.coerceIn(9f, 144f))))
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun inter(weight: Int = 400, opticalSize: Float = 16f) = FontFamily(
    Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize.coerceIn(14f, 32f))))
)

val Fraunces = fraunces(500, 40f)
val Inter = inter(400)

private val display = fraunces(500, 56f)
private val heading = fraunces(500, 28f)
private val sans = inter(400)
private val sansMedium = inter(500)
private val sansSemi = inter(600)

private val type = Typography(
    displayLarge = TextStyle(fontFamily = display, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.9).sp),
    displayMedium = TextStyle(fontFamily = display, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = display, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = heading, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = heading, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = heading, fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = sansSemi, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = sansMedium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = inter(300), fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = sans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = sansSemi, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = sansMedium, fontSize = 12.sp),
)

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.butter, onPrimary = Palette.ink,
            secondary = Palette.terracotta, onSecondary = Palette.ink,
            tertiary = Palette.teal,
            background = Palette.bg, onBackground = Palette.ivory,
            surface = Color(0xFF1A130E), onSurface = Palette.ivory,
            surfaceVariant = Color(0xFF241A13), onSurfaceVariant = Palette.ivoryMuted,
            surfaceContainer = Color(0xFF1A130E), surfaceContainerHigh = Color(0xFF241A13),
            secondaryContainer = Color(0xFF3A2B20), onSecondaryContainer = Palette.ivory,
            outline = Palette.hairline,
        ),
        typography = type,
        content = content,
    )
}
