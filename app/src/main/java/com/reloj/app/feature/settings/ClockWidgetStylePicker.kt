package com.reloj.app.feature.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reloj.app.R
import com.reloj.app.core.designsystem.AppColorScheme
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.ClockWidgetStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ClockWidgetStyleCard(
    selectedStyle: ClockWidgetStyle,
    use24HourFormat: Boolean,
    onSelectStyle: (ClockWidgetStyle) -> Unit,
    expandIcon: @Composable (rotationDegrees: Float) -> Unit,
) {
    val colors = AppTheme.colors
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "widgetStyleArrowRotation",
    )
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val headerInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerInteraction,
                        indication = null,
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Estilo del widget",
                    style = AppTypography.title,
                    color = colors.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = selectedStyle.label,
                        style = AppTypography.label,
                        color = colors.accent,
                    )
                    expandIcon(arrowRotation)
                }
            }

            ClockWidgetLivePreview(
                style = selectedStyle,
                colors = colors,
                nowMillis = nowMillis,
                use24HourFormat = use24HourFormat,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    expanded = !expanded
                },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClockWidgetStyle.entries.chunked(5).forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowStyles.forEach { style ->
                                ClockWidgetStyleTile(
                                    style = style,
                                    selected = selectedStyle == style,
                                    colors = colors,
                                    onClick = {
                                        if (selectedStyle != style) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onSelectStyle(style)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = if (expanded) {
                    "Toca cualquier diseño para aplicarlo al widget y al reloj en Alarmas."
                } else {
                    "Toca la vista previa para elegir entre los 10 estilos disponibles."
                },
                style = AppTypography.label,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ClockWidgetLivePreview(
    style: ClockWidgetStyle,
    colors: AppColorScheme,
    nowMillis: Long,
    use24HourFormat: Boolean,
    onClick: () -> Unit,
) {
    val spanishLocale = remember { Locale.forLanguageTag("es") }
    val datePattern = remember(spanishLocale) { SimpleDateFormat("EEE, d 'de' MMM", spanishLocale) }
    val timePattern = remember(use24HourFormat) {
        SimpleDateFormat(if (use24HourFormat) "HH:mm" else "h:mm", Locale.US)
    }
    val hourPattern = remember(use24HourFormat) {
        SimpleDateFormat(if (use24HourFormat) "HH" else "hh", Locale.US)
    }
    val minutePattern = remember { SimpleDateFormat("mm", Locale.US) }
    val periodPattern = remember { SimpleDateFormat("a", Locale.US) }

    val nowDate = remember(nowMillis) { Date(nowMillis) }
    val timeText = remember(nowMillis, use24HourFormat) { timePattern.format(nowDate) }
    val hoursText = remember(nowMillis, use24HourFormat) { hourPattern.format(nowDate) }
    val minutesText = remember(nowMillis) { minutePattern.format(nowDate) }
    val periodText = remember(nowMillis, use24HourFormat) {
        if (use24HourFormat) null else periodPattern.format(nowDate).uppercase(Locale.US)
    }
    val dateText = remember(nowMillis / 60_000L) {
        datePattern.format(nowDate).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(spanishLocale) else ch.toString()
        }
    }

    val wallpaperGradient = if (colors.isDark) {
        listOf(Color(0xFF1D2430), Color(0xFF121720))
    } else {
        listOf(Color(0xFFDAD4C8), Color(0xFFC8C0B2))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(wallpaperGradient))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val isAccentHero = style == ClockWidgetStyle.ACCENT_HERO
        val isMinimal = style == ClockWidgetStyle.MINIMAL_CLEAR
        val cardColor = when {
            isMinimal -> Color.Transparent
            isAccentHero -> colors.accent
            else -> colors.background
        }
        val cardBorder = if (style == ClockWidgetStyle.TECH_MONO) {
            BorderStroke(1.5.dp, colors.accent)
        } else {
            null
        }
        val cardShape = if (style == ClockWidgetStyle.CAPSULE_SPLIT) {
            RoundedCornerShape(24.dp)
        } else {
            RoundedCornerShape(18.dp)
        }

        val primaryText = if (isAccentHero) colors.onAccent else colors.textPrimary
        val secondaryText = if (isAccentHero) colors.onAccent.copy(alpha = 0.86f) else colors.textSecondary
        val accentText = if (isAccentHero) colors.onAccent else colors.accent

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = cardColor,
            border = cardBorder,
        ) {
            when (style) {
                ClockWidgetStyle.COMPACT_ROW -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewTimeRow(timeText, periodText, FontFamily.Default, FontWeight.Light, 34, primaryText, accentText)
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                            PreviewAlarmPill(colors, isFilled = true, textColor = accentText, iconTint = accentText)
                        }
                    }
                }

                ClockWidgetStyle.STACKED_DUO -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = hoursText,
                                style = AppTypography.timerCard.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 32.sp,
                                    lineHeight = 32.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = primaryText,
                            )
                            Text(
                                text = minutesText,
                                style = AppTypography.timerCard.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 32.sp,
                                    lineHeight = 32.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = accentText,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (periodText != null) {
                                Text(periodText, style = AppTypography.label, color = accentText)
                            }
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                            PreviewAlarmPill(colors, isFilled = true, textColor = accentText, iconTint = accentText)
                        }
                    }
                }

                ClockWidgetStyle.SPLIT_TILES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(hoursText to primaryText, minutesText to accentText).forEachIndexed { idx, (part, color) ->
                                if (idx > 0) Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceElevated,
                                ) {
                                    Text(
                                        text = part,
                                        style = AppTypography.timerCard.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 28.sp,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = color,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (periodText != null) {
                                Text(
                                    text = periodText,
                                    style = AppTypography.label,
                                    color = accentText,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                            PreviewAlarmPill(colors, isFilled = false, textColor = accentText, iconTint = accentText)
                        }
                    }
                }

                ClockWidgetStyle.CAPSULE_SPLIT -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PreviewTimeRow(timeText, periodText, FontFamily.Default, FontWeight.Light, 38, primaryText, accentText)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                            PreviewAlarmPill(colors, isFilled = false, textColor = accentText, iconTint = accentText)
                        }
                    }
                }

                ClockWidgetStyle.BOLD_EDGE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(68.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreviewTimeRow(timeText, periodText, FontFamily.Default, FontWeight.Bold, 38, primaryText, accentText)
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                            PreviewAlarmPill(colors, isFilled = false, textColor = accentText, iconTint = accentText)
                        }
                    }
                }

                ClockWidgetStyle.EDITORIAL_SERIF -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PreviewTimeRow(timeText, periodText, FontFamily.Serif, FontWeight.Normal, 36, primaryText, accentText)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(2.dp)
                                .background(colors.accent),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dateText,
                            style = AppTypography.label.copy(fontFamily = FontFamily.Serif),
                            color = secondaryText,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PreviewAlarmPill(
                            colors = colors,
                            isFilled = false,
                            textColor = secondaryText,
                            iconTint = accentText,
                            fontFamily = FontFamily.Serif,
                            italic = true,
                        )
                    }
                }

                ClockWidgetStyle.CLASSIC,
                ClockWidgetStyle.MINIMAL_CLEAR,
                ClockWidgetStyle.ACCENT_HERO,
                ClockWidgetStyle.TECH_MONO,
                -> {
                    val fontFamily = if (style == ClockWidgetStyle.TECH_MONO) FontFamily.Monospace else FontFamily.Default
                    val fontWeight = when (style) {
                        ClockWidgetStyle.ACCENT_HERO, ClockWidgetStyle.TECH_MONO -> FontWeight.Bold
                        ClockWidgetStyle.MINIMAL_CLEAR -> FontWeight.ExtraLight
                        else -> FontWeight.Light
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (style == ClockWidgetStyle.TECH_MONO) {
                            Text(
                                text = dateText.uppercase(spanishLocale),
                                style = AppTypography.label.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
                                color = secondaryText,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        PreviewTimeRow(timeText, periodText, fontFamily, fontWeight, 38, primaryText, accentText)
                        if (style != ClockWidgetStyle.TECH_MONO) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(dateText, style = AppTypography.label, color = secondaryText)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        PreviewAlarmPill(
                            colors = colors,
                            isFilled = style == ClockWidgetStyle.CLASSIC || style == ClockWidgetStyle.TECH_MONO,
                            textColor = accentText,
                            iconTint = accentText,
                            fontFamily = fontFamily,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTimeRow(
    timeText: String,
    periodText: String?,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSizeSp: Int,
    primaryText: Color,
    accentText: Color,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = timeText,
            style = AppTypography.timerCard.copy(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 2).sp,
                fontFeatureSettings = "tnum",
            ),
            color = primaryText,
        )
        if (periodText != null) {
            Text(
                text = periodText,
                style = AppTypography.label.copy(fontFamily = fontFamily, fontSize = 11.sp),
                color = accentText,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun PreviewAlarmPill(
    colors: AppColorScheme,
    isFilled: Boolean,
    textColor: Color,
    iconTint: Color,
    fontFamily: FontFamily = FontFamily.Default,
    italic: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isFilled) 8.dp else 0.dp,
                vertical = if (isFilled) 4.dp else 0.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_alarm),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = "Lun · 07:30",
                style = AppTypography.label.copy(
                    fontFamily = fontFamily,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 11.sp,
                ),
                color = textColor,
            )
        }
    }

    if (isFilled) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.surface,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun ClockWidgetStyleTile(
    style: ClockWidgetStyle,
    selected: Boolean,
    colors: AppColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val outerBorderColor = if (selected) colors.accent else colors.outline
        val isAccentHero = style == ClockWidgetStyle.ACCENT_HERO
        val isMinimal = style == ClockWidgetStyle.MINIMAL_CLEAR
        val tileBg = when {
            isMinimal -> Color.Transparent
            isAccentHero -> colors.accent
            else -> colors.background
        }
        val fgPrimary = if (isAccentHero) colors.onAccent else colors.textPrimary
        val fgAccent = if (isAccentHero) colors.onAccent else colors.accent

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = if (isMinimal) colors.surfaceElevated else tileBg,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (style == ClockWidgetStyle.TECH_MONO && !selected) colors.accent.copy(alpha = 0.6f) else outerBorderColor,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (style) {
                    ClockWidgetStyle.CLASSIC -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("08:30", fontSize = 11.sp, fontWeight = FontWeight.Light, color = fgPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.5f)),
                        )
                    }

                    ClockWidgetStyle.MINIMAL_CLEAR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("08:30", fontSize = 11.sp, fontWeight = FontWeight.ExtraLight, color = colors.textPrimary)
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(2.dp)
                                .background(colors.textSecondary.copy(alpha = 0.45f)),
                        )
                    }

                    ClockWidgetStyle.COMPACT_ROW -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("08:30", fontSize = 9.sp, fontWeight = FontWeight.Light, color = fgPrimary)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                            Box(modifier = Modifier.width(10.dp).height(2.dp).background(colors.textSecondary))
                            Box(modifier = Modifier.width(8.dp).height(2.dp).background(colors.accent))
                        }
                    }

                    ClockWidgetStyle.STACKED_DUO -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("08", fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, color = fgPrimary)
                            Text("30", fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, color = fgAccent)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.width(10.dp).height(2.dp).background(colors.textSecondary))
                    }

                    ClockWidgetStyle.SPLIT_TILES -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf("08" to fgPrimary, "30" to fgAccent).forEach { (num, color) ->
                            Surface(shape = RoundedCornerShape(4.dp), color = colors.surfaceElevated) {
                                Text(
                                    text = num,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = color,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    ClockWidgetStyle.ACCENT_HERO -> Text(
                        text = "08:30",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onAccent,
                    )

                    ClockWidgetStyle.TECH_MONO -> Text(
                        text = "08:30",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = fgPrimary,
                    )

                    ClockWidgetStyle.CAPSULE_SPLIT -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text("08:30", fontSize = 10.sp, fontWeight = FontWeight.Light, color = fgPrimary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(colors.surface),
                        )
                    }

                    ClockWidgetStyle.BOLD_EDGE -> Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("08:30", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fgPrimary)
                    }

                    ClockWidgetStyle.EDITORIAL_SERIF -> Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("08:30", fontSize = 10.sp, fontFamily = FontFamily.Serif, color = fgPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(colors.accent))
                    }
                }
            }
        }

        Text(
            text = style.label,
            style = AppTypography.label.copy(fontSize = 10.sp),
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
