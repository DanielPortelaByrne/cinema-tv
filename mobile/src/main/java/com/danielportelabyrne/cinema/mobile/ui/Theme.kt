package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danielportelabyrne.cinema.mobile.R

/** The TV's "Painted Home" palette: espresso grounds, ivory text, butter for the one thing to press. */
object Palette {
    val ivory = Color(0xFFF3E9CF)
    val ivoryMuted = Color(0xC7F3E9CF)
    val espresso = Color(0xFF1E1610)
    val surface = Color(0xFF302319)
    val raised = Color(0xFF3B2C1F)
    val selected = Color(0xFF5C4531)
    val butter = Color(0xFFDDBB57)
    val terracotta = Color(0xFFAF5938)
    val teal = Color(0xFF28766C)
    val letterboxd = Color(0xFFE85D4A)
}

val Fraunces = FontFamily(Font(R.font.fraunces, FontWeight.Normal), Font(R.font.fraunces, FontWeight.SemiBold))
val Inter = FontFamily(Font(R.font.inter, FontWeight.Normal), Font(R.font.inter, FontWeight.Medium), Font(R.font.inter, FontWeight.SemiBold))

private val type = Typography(
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.butter, onPrimary = Palette.espresso,
            secondary = Palette.terracotta, onSecondary = Palette.ivory,
            tertiary = Palette.teal,
            background = Palette.espresso, onBackground = Palette.ivory,
            surface = Palette.surface, onSurface = Palette.ivory,
            surfaceVariant = Palette.raised, onSurfaceVariant = Palette.ivoryMuted,
            surfaceContainer = Palette.surface, surfaceContainerHigh = Palette.raised,
            secondaryContainer = Palette.selected, onSecondaryContainer = Palette.ivory,
            outline = Palette.selected,
        ),
        typography = type,
        content = content,
    )
}
