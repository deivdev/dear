package dev.deiv.dear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// One warm accent (tertiary) on black, white and greys; red is kept for errors and deletions.
private val AmberDark = Color(0xFFF2A93B)
private val AmberLight = Color(0xFF9A5B00)
private val ErrorDark = Color(0xFFE5484D)
private val ErrorLight = Color(0xFFCE2C31)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFF8C8C8C),
    onSecondary = Color(0xFF000000),
    tertiary = AmberDark,
    onTertiary = Color(0xFF1A1100),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF8C8C8C),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF222222),
    error = ErrorDark,
    onError = Color(0xFFFFFFFF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D0D0D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = Color(0xFF6B6B6B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = AmberLight,
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2F2),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFF2F2F2),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE3E3E3),
    outline = Color(0xFFCFCFCF),
    outlineVariant = Color(0xFFE0E0E0),
    error = ErrorLight,
    onError = Color(0xFFFFFFFF),
)

private val DearShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun DearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DearTypography,
        shapes = DearShapes,
        content = content,
    )
}
