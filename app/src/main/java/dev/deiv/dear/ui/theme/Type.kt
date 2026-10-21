package dev.deiv.dear.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.deiv.dear.R

/** Display serif (Instrument Serif, OFL): headings and the app name. One weight, set large. */
val SerifFamily = FontFamily(Font(R.font.instrument_serif, FontWeight.Normal))

/** Body text (Space Grotesk, OFL). */
val GroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Labels and metadata, always uppercase in the UI (Space Mono, OFL). */
val MonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

private val serif = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal)
private val grotesk = TextStyle(fontFamily = GroteskFamily)
private val mono = TextStyle(fontFamily = MonoFamily, letterSpacing = 0.08.em)

val DearTypography = Typography(
    displayLarge = serif.copy(fontSize = 60.sp, lineHeight = 64.sp),
    displayMedium = serif.copy(fontSize = 48.sp, lineHeight = 52.sp),
    displaySmall = serif.copy(fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = serif.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = serif.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = serif.copy(fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = grotesk.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = grotesk.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = grotesk.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = grotesk.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = grotesk.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = grotesk.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = mono.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = mono.copy(fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = mono.copy(fontSize = 10.sp, lineHeight = 14.sp),
)
