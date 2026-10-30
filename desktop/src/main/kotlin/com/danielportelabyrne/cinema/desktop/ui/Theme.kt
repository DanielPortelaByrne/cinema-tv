package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The TV app's Cinema look (app/.../design/Cinema.kt), so the desktop is the same Cinema: espresso ground, cream text,
 * translucent cream glass, and the 70s stripe set. Every size in the desktop UI is in the mockups' pixels (a
 * 1920×1080 frame, docs/ui-review/movies-reimagined), the same numbers the TV code uses; [DeckScale] fits that frame
 * to the window.
 */
object Cin {
    val BG = Color(0xFF0B0807)
    val CREAM = Color(0xFFF6ECDB)
    val MUTED = Color(0xFFB6A896)
    val DIM = Color(0xFF7F7366)
    val READING = Color(0xFFD7CBB9)
    val CARD_TITLE = Color(0xFFCDBFAD)
    val RAIL_ICON = Color(0xFFA99B89)
    val INK = Color(0xFF1B120C)
    val SAFFRON = Color(0xFFEDBE56)
    val TERRA = Color(0xFFE27A49)
    val DANGER_TEXT = Color(0xFFF0A48A)
    val TEAL = Color(0xFF3AA597)
    val PLACEHOLDER = Color(0xFF221A15)
    val KEY_TEXT = Color(0xFFE6DAC8)
    val STATUS = Color(0xFFE8C27A)

    val STRIPE_SAFFRON = Color(0xFFF6BF33)
    val STRIPE_ORANGE = Color(0xFFF08A25)
    val STRIPE_TERRA = Color(0xFFE2562B)
    val STRIPE_RED = Color(0xFFC2281D)
    val STRIPE_MAROON = Color(0xFF6B1A10)
    val STRIPES = listOf(STRIPE_SAFFRON, STRIPE_ORANGE, STRIPE_TERRA, STRIPE_RED, STRIPE_MAROON)

    val GLASS = Color(0x13FFF2DE)
    val GLASS_STRONG = Color(0x21FFF2DE)
    val HAIRLINE = Color(0x24FFF2DE)
    val RAIL_FILL = Color(0x9E120D0A)
    val RAIL_FILL_OPEN = Color(0xFF120D0A)

    /** Setup's fields: brown pills that warm up when focused. */
    val FIELD = Color(0xFF5C4531)
    val FIELD_FOCUS = Color(0xFF7A5C42)
    val SHEET = Color(0xFF30231A)

    /** "Queue download" and setup's main button: terracotta, lit from the top left. */
    val terraGradient = Brush.linearGradient(listOf(Color(0xFFEB8A56), Color(0xFFD4623A)))
}

/**
 * Fraunces and Inter are variable fonts. As on the TV, each size gets its own instance with the optical size and
 * weight baked in (optical size follows the text size), so a 128px title is the fine, high-contrast cut and a 23px
 * caption the sturdy one. Instances are rounded to steps of 4 and cached.
 */
object Fonts {
    private val bytes = HashMap<String, ByteArray?>()
    private val families = HashMap<String, FontFamily>()

    /** How much the design frame is scaled on this screen (set by DeckScale): optical size follows the size drawn. */
    @Volatile var renderScale: Float = 1f

    private fun data(name: String): ByteArray? = bytes.getOrPut(name) { Fonts::class.java.getResourceAsStream("/fonts/$name")?.use { it.readBytes() } }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    fun family(serif: Boolean, sizePx: Float, weight: Int, scale: Float = renderScale): FontFamily {
        val drawn = sizePx * scale
        val opsz = ((if (serif) drawn.coerceIn(9f, 144f) else drawn.coerceIn(14f, 32f)) / 4f).roundToInt() * 4
        val key = "$serif|$opsz|$weight"
        return families.getOrPut(key) {
            val file = if (serif) "fraunces.ttf" else "inter.ttf"
            val d = data(file) ?: return@getOrPut if (serif) FontFamily.Serif else FontFamily.SansSerif
            FontFamily(
                Font(
                    "$file-$opsz-$weight", d, FontWeight(weight), FontStyle.Normal,
                    FontVariation.Settings(FontVariation.Setting("opsz", opsz.toFloat()), FontVariation.weight(weight)),
                )
            )
        }
    }
}

/** A text style in mockup pixels, as `Cinema.text(context, value, sizePx, color, serif, weight, tracking)` on the TV. */
fun ts(sizePx: Number, color: Color = Cin.CREAM, serif: Boolean = false, weight: Int = 400, tracking: Float = 0f, lineHeight: Float? = null): TextStyle {
    val size = sizePx.toFloat()
    return TextStyle(
        fontFamily = Fonts.family(serif, size, weight),
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        letterSpacing = tracking.em,
        lineHeight = lineHeight?.let { (size * it).sp } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        color = color,
    )
}

/** A Fraunces display title (`.title`): regular weight, slightly tightened, tight leading. */
fun titleStyle(sizePx: Number) = ts(sizePx, Cin.CREAM, serif = true, weight = 400, tracking = -0.02f, lineHeight = 1.0f)

/** Reading text (`.syn`): Inter light, generous leading. */
fun synopsisStyle(sizePx: Number = 26) = ts(sizePx, Cin.READING, weight = 300, lineHeight = 1.5f)
