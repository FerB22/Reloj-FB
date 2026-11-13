package com.reloj.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Estilos propios; los dígitos usan ancho tabular para que el contador no vibre. */
object AppTypography {
    val timerDisplay = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    )
    val timerCard = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    )
    val screenTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp,
    )
    val sectionTitle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.8.sp,
    )
    val title = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    )
    val body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )
    val label = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    )
}
