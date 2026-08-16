package jp.infold.news.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalInfoldColors = staticCompositionLocalOf { DarkInfoldColors }

@Composable
fun InfoldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkInfoldColors else LightInfoldColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.primary.copy(alpha = 0.2f),
            onPrimaryContainer = colors.primary,
            secondary = colors.primary2,
            tertiary = colors.accent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.backgroundSoft,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.warn,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.primary.copy(alpha = 0.15f),
            onPrimaryContainer = colors.primary,
            secondary = colors.primary2,
            tertiary = colors.accent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.backgroundSoft,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.warn,
        )
    }

    CompositionLocalProvider(LocalInfoldColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
