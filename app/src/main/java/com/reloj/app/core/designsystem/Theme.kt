package com.reloj.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppColors = staticCompositionLocalOf { CarbonColorScheme }

/** Acceso corto: `AppTheme.colors.background`. */
object AppTheme {
    val colors: AppColorScheme
        @Composable get() = LocalAppColors.current
}

@Composable
fun RelojTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPreset = AccentPreset.AMBER,
    content: @Composable () -> Unit,
) {
    val colors = buildAppColorScheme(
        themeMode = themeMode,
        systemDark = isSystemInDarkTheme(),
        accent = accent,
    )

    val material = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            outline = colors.outline,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            outline = colors.outline,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
