package com.reloj.app.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.reloj.app.R

enum class ThemeMode(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    ICE(R.string.theme_ice),
    MIDNIGHT(R.string.theme_midnight),
    FOREST(R.string.theme_forest);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT, ICE -> false
        DARK, MIDNIGHT, FOREST -> true
    }
}

enum class AccentPreset(
    @param:StringRes val labelRes: Int,
    val darkColor: Color,
    val onDarkColor: Color,
    val lightColor: Color,
    val onLightColor: Color,
) {
    AMBER(
        labelRes = R.string.accent_amber,
        darkColor = Color(0xFFE8A33D),
        onDarkColor = Color(0xFF121316),
        lightColor = Color(0xFFB86E00),
        onLightColor = Color(0xFFF6F3EB),
    ),
    TERRACOTTA(
        labelRes = R.string.accent_terracotta,
        darkColor = Color(0xFFE07A5F),
        onDarkColor = Color(0xFF121316),
        lightColor = Color(0xFFB54D32),
        onLightColor = Color(0xFFF6F3EB),
    ),
    SAGE(
        labelRes = R.string.accent_sage,
        darkColor = Color(0xFF81B29A),
        onDarkColor = Color(0xFF121316),
        lightColor = Color(0xFF3E7A5D),
        onLightColor = Color(0xFFF6F3EB),
    ),
    SLATE(
        labelRes = R.string.accent_slate,
        darkColor = Color(0xFF7B9ACC),
        onDarkColor = Color(0xFF121316),
        lightColor = Color(0xFF3A5F99),
        onLightColor = Color(0xFFF6F3EB),
    ),
    MONOCHROME(
        labelRes = R.string.accent_monochrome,
        darkColor = Color(0xFFEDEFEF),
        onDarkColor = Color(0xFF121316),
        lightColor = Color(0xFF1C1B1A),
        onLightColor = Color(0xFFF6F3EB),
    ),
    COBALT(
        labelRes = R.string.accent_cobalt,
        darkColor = Color(0xFF60A5FA),
        onDarkColor = Color(0xFF0E172A),
        lightColor = Color(0xFF1D4ED8),
        onLightColor = Color(0xFFF8FAFC),
    ),
    CRIMSON(
        labelRes = R.string.accent_crimson,
        darkColor = Color(0xFFF87171),
        onDarkColor = Color(0xFF1A1012),
        lightColor = Color(0xFFC83232),
        onLightColor = Color(0xFFFFF8F8),
    ),
    EMERALD(
        labelRes = R.string.accent_emerald,
        darkColor = Color(0xFF34D399),
        onDarkColor = Color(0xFF0D1B16),
        lightColor = Color(0xFF0F7654),
        onLightColor = Color(0xFFF4FBF8),
    ),
    VIOLET(
        labelRes = R.string.accent_violet,
        darkColor = Color(0xFFA78BFA),
        onDarkColor = Color(0xFF151224),
        lightColor = Color(0xFF6D3FD1),
        onLightColor = Color(0xFFFAF8FF),
    ),
    ROSE(
        labelRes = R.string.accent_rose,
        darkColor = Color(0xFFF472B6),
        onDarkColor = Color(0xFF1E1118),
        lightColor = Color(0xFFBE296F),
        onLightColor = Color(0xFFFFF7FA),
    ),
    GOLD(
        labelRes = R.string.accent_gold,
        darkColor = Color(0xFFFBBF24),
        onDarkColor = Color(0xFF18140C),
        lightColor = Color(0xFF9A6B00),
        onLightColor = Color(0xFFFFFBF2),
    ),
    TEAL(
        labelRes = R.string.accent_teal,
        darkColor = Color(0xFF2DD4BF),
        onDarkColor = Color(0xFF0C1B1A),
        lightColor = Color(0xFF0D7A73),
        onLightColor = Color(0xFFF4FBFB),
    ),
}

enum class ClockWidgetStyle(@param:StringRes val labelRes: Int) {
    CLASSIC(R.string.widget_style_classic),
    MINIMAL_CLEAR(R.string.widget_style_minimal),
    COMPACT_ROW(R.string.widget_style_compact_row),
    STACKED_DUO(R.string.widget_style_stacked),
    SPLIT_TILES(R.string.widget_style_split_tiles),
    ACCENT_HERO(R.string.widget_style_accent_hero),
    TECH_MONO(R.string.widget_style_tech_mono),
    CAPSULE_SPLIT(R.string.widget_style_capsule),
    BOLD_EDGE(R.string.widget_style_bold_edge),
    EDITORIAL_SERIF(R.string.widget_style_editorial),
}

/**
 * Paleta semántica de la aplicación. Las pantallas leen estos roles desde
 * [LocalAppColors] y nunca colores sueltos, de modo que cambiar de modo o acento
 * se refleja en tiempo real.
 */
@Immutable
data class AppColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
)

fun buildAppColorScheme(
    themeMode: ThemeMode,
    systemDark: Boolean,
    accent: AccentPreset,
): AppColorScheme {
    val resolvedMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
        else -> themeMode
    }
    return when (resolvedMode) {
        ThemeMode.DARK -> AppColorScheme(
            isDark = true,
            background = Color(0xFF121316),
            surface = Color(0xFF1A1C20),
            surfaceElevated = Color(0xFF22252B),
            outline = Color(0xFF2B2E34),
            textPrimary = Color(0xFFEDEFEF),
            textSecondary = Color(0xFF9DA2A8),
            accent = accent.darkColor,
            onAccent = accent.onDarkColor,
            danger = Color(0xFFE06C75),
        )
        ThemeMode.MIDNIGHT -> AppColorScheme(
            isDark = true,
            background = Color(0xFF0B1220),
            surface = Color(0xFF121C2E),
            surfaceElevated = Color(0xFF1A263D),
            outline = Color(0xFF263552),
            textPrimary = Color(0xFFEDF2FA),
            textSecondary = Color(0xFF92A2BD),
            accent = accent.darkColor,
            onAccent = accent.onDarkColor,
            danger = Color(0xFFF07178),
        )
        ThemeMode.FOREST -> AppColorScheme(
            isDark = true,
            background = Color(0xFF0F1A16),
            surface = Color(0xFF172620),
            surfaceElevated = Color(0xFF20332C),
            outline = Color(0xFF2C4239),
            textPrimary = Color(0xFFECF4F0),
            textSecondary = Color(0xFF91A89E),
            accent = accent.darkColor,
            onAccent = accent.onDarkColor,
            danger = Color(0xFFE57373),
        )
        ThemeMode.ICE -> AppColorScheme(
            isDark = false,
            background = Color(0xFFF2F5FA),
            surface = Color(0xFFE6ECF5),
            surfaceElevated = Color(0xFFDCE4F0),
            outline = Color(0xFFCBD5E6),
            textPrimary = Color(0xFF141B29),
            textSecondary = Color(0xFF5A657A),
            accent = accent.lightColor,
            onAccent = accent.onLightColor,
            danger = Color(0xFFC23B3B),
        )
        ThemeMode.LIGHT, ThemeMode.SYSTEM -> AppColorScheme(
            isDark = false,
            background = Color(0xFFF6F3EB),
            surface = Color(0xFFECE7DC),
            surfaceElevated = Color(0xFFE3DDD0),
            outline = Color(0xFFD6CFC0),
            textPrimary = Color(0xFF1C1B1A),
            textSecondary = Color(0xFF68645E),
            accent = accent.lightColor,
            onAccent = accent.onLightColor,
            danger = Color(0xFFB53D3D),
        )
    }
}

fun buildAppColorScheme(isDark: Boolean, accent: AccentPreset): AppColorScheme =
    buildAppColorScheme(
        themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT,
        systemDark = isDark,
        accent = accent,
    )

val CarbonColorScheme = buildAppColorScheme(isDark = true, accent = AccentPreset.AMBER)
val BeigeColorScheme = buildAppColorScheme(isDark = false, accent = AccentPreset.AMBER)
