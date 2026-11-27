package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val PairlyDarkColorScheme = darkColorScheme(
    primary = PairlyPurple,
    onPrimary = Color.White,
    primaryContainer = PairlyPurpleContainerDark,
    onPrimaryContainer = PairlyDarkTextPrimary,
    secondary = PairlyPink,
    onSecondary = Color.White,
    secondaryContainer = PairlyPinkContainerDark,
    onSecondaryContainer = PairlyDarkTextPrimary,
    tertiary = SuperLikeVioletGold,
    onTertiary = PairlyDarkBackground,
    background = PairlyDarkBackground,
    onBackground = PairlyDarkTextPrimary,
    surface = PairlyDarkSurface,
    onSurface = PairlyDarkTextPrimary,
    surfaceVariant = PairlyDarkSurfaceElevated,
    onSurfaceVariant = PairlyDarkTextSecondary,
    surfaceContainerHigh = PairlyDarkSurfaceElevated,
    surfaceContainerHighest = PairlyDarkSurfaceHighest,
    outline = PairlyDarkOutline,
    outlineVariant = PairlyDarkOutline,
    error = ErrorCrimson,
    onError = Color.White
)

private val PairlyLightColorScheme = lightColorScheme(
    primary = PairlyPurple,
    onPrimary = Color.White,
    primaryContainer = PairlyPurpleContainerLight,
    onPrimaryContainer = PairlyPurple,
    secondary = PairlyPink,
    onSecondary = Color.White,
    secondaryContainer = PairlyPinkContainerLight,
    onSecondaryContainer = PairlyPink,
    tertiary = SuperLikeVioletGold,
    onTertiary = PairlyLightTextPrimary,
    background = PairlyLightBackground,
    onBackground = PairlyLightTextPrimary,
    surface = PairlyLightSurface,
    onSurface = PairlyLightTextPrimary,
    surfaceVariant = PairlyLightSurfaceElevated,
    onSurfaceVariant = PairlyLightTextSecondary,
    surfaceContainerHigh = PairlyLightSurface,
    surfaceContainerHighest = PairlyLightSurfaceElevated,
    outline = PairlyLightOutline,
    outlineVariant = PairlyLightOutline,
    error = ErrorCrimson,
    onError = Color.White
)

val PairlyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PairlyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) PairlyDarkColorScheme else PairlyLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PairlyShapes,
        content = content
    )
}
