package jp.infold.news.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.infold.news.ui.components.InfoldBackdrop
import jp.infold.news.ui.components.LocalInfoldHaze

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
            onPrimary = colors.onPrimary,
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
            onPrimary = colors.onPrimary,
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

    // Web 版の固定背景（グロー + グリッド）を全画面のブラー元として共有する。
    val hazeState = rememberHazeState()
    CompositionLocalProvider(
        LocalInfoldColors provides colors,
        LocalInfoldHaze provides hazeState,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                InfoldBackdrop(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                )
                content()
            }
        }
    }
}
