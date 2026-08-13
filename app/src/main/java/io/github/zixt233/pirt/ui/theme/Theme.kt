package io.github.zixt233.pirt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color(0xFF003921),
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = Color(0xFFC1F1D0),
    secondary = Color(0xFFB4CCB7),
    onSecondary = Color(0xFF203527),
    secondaryContainer = Color(0xFF374B3C),
    onSecondaryContainer = Color(0xFFD0E8D2),
    tertiary = Color(0xFFABCDBB),
    onTertiary = Color(0xFF163729),
    tertiaryContainer = Color(0xFF2E4E3E),
    onTertiaryContainer = Color(0xFFC6E9D4),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = Color(0xFFC2CBC3),
    surfaceDim = Color(0xFF0C110E),
    surfaceBright = Color(0xFF2C342E),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF141A16),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceContainer,
    surfaceContainerHighest = Color(0xFF2B342D),
    surfaceTint = ForestGreenLight,
    outline = DarkOutline,
    outlineVariant = Color(0xFF3E4941),
    inverseSurface = Color(0xFFE1E8E1),
    inverseOnSurface = Color(0xFF29302B),
    inversePrimary = ForestGreen,
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = ForestContainer,
    onPrimaryContainer = OnForestContainer,
    secondary = MossGreen,
    onSecondary = Color.White,
    secondaryContainer = MossContainer,
    onSecondaryContainer = Color(0xFF223428),
    tertiary = ForestGreenDark,
    onTertiary = Color.White,
    tertiaryContainer = ForestContainer,
    onTertiaryContainer = OnForestContainer,
    background = WarmBackground,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceContainer,
    onSurfaceVariant = InkMuted,
    surfaceDim = Color(0xFFE1DBD0),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF6EF),
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = Color(0xFFECE5DB),
    surfaceContainerHighest = Color(0xFFE5DED4),
    surfaceTint = ForestGreen,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE3DBD0),
    inverseSurface = Color(0xFF34312B),
    inverseOnSurface = Color(0xFFF7F1E8),
    inversePrimary = ForestGreenLight,
)

@Composable
fun PIRTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
