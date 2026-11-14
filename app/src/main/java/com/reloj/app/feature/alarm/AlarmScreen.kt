package com.reloj.app.feature.alarm

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.reloj.app.R
import com.reloj.app.core.data.local.AlarmEntity
import com.reloj.app.core.data.local.AlarmRepeatUtils
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.core.data.local.SILENT_SOUND_PRESET
import com.reloj.app.core.data.local.isSilentSoundPreset
import com.reloj.app.core.data.repository.AlarmRepository
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.ClockWidgetStyle
import com.reloj.app.core.designsystem.ConfirmDeleteButton
import com.reloj.app.core.designsystem.EmptyStateCard
import com.reloj.app.core.designsystem.NumberStepper
import com.reloj.app.core.designsystem.PrimaryPillButton
import com.reloj.app.core.designsystem.SoundPickerSection
import com.reloj.app.core.designsystem.StableActionRow
import com.reloj.app.core.designsystem.resolveSoundLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlarmScreen(
    alarms: List<AlarmEntity>?,
    repository: AlarmRepository,
    use24HourFormat: Boolean,
    clockStyle: ClockWidgetStyle = ClockWidgetStyle.CLASSIC,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val nextAlarmSummary = remember(alarms, nowMillis, use24HourFormat) {
        alarms
            ?.asSequence()
            ?.filter { it.enabled }
            ?.map { alarm ->
                alarm to AlarmRepeatUtils.nextTriggerMillis(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    repeatDaysMask = alarm.repeatDaysMask,
                    nowMillis = nowMillis,
                )
            }
            ?.minByOrNull { it.second }
            ?.let { (alarm, triggerMillis) ->
                val time = AlarmRepeatUtils.formatAlarmTime(alarm.hour, alarm.minute, use24HourFormat)
                val remaining = AlarmRepeatUtils.formatRemainingUntil(triggerMillis, nowMillis)
                "Próxima · $time · $remaining"
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
        ) {
            Text(
                text = "Alarmas",
                style = AppTypography.screenTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (alarms != null) {
                if (alarms.isEmpty()) {
                    LiveClockHeader(
                        nowMillis = nowMillis,
                        use24HourFormat = use24HourFormat,
                        clockStyle = clockStyle,
                        nextAlarmText = null,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            iconRes = R.drawable.ic_alarm,
                            title = "Sin alarmas configuradas",
                            description = "Crea una alarma para programar tu próximo aviso.",
                            actionText = "Añadir",
                            onAction = { showCreateDialog = true },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "live_clock_header") {
                            LiveClockHeader(
                                nowMillis = nowMillis,
                                use24HourFormat = use24HourFormat,
                                clockStyle = clockStyle,
                                nextAlarmText = nextAlarmSummary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmCard(
                                alarm = alarm,
                                use24HourFormat = use24HourFormat,
                                nowMillis = nowMillis,
                                onClick = { editingAlarm = alarm },
                                onToggle = { enabled ->
                                    nowMillis = System.currentTimeMillis()
                                    scope.launch { repository.setEnabled(alarm.id, enabled) }
                                },
                                onDelete = {
                                    scope.launch { repository.delete(alarm) }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!alarms.isNullOrEmpty()) {
            PrimaryPillButton(
                text = "Nuevo",
                onClick = { showCreateDialog = true },
                shadowElevation = 6.dp,
                large = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 18.dp),
            )
        }
    }

    if (showCreateDialog) {
        AlarmEditorDialog(
            initial = null,
            use24HourFormat = use24HourFormat,
            onDismiss = {
                showCreateDialog = false
                scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
            },
            onSave = { hour, minute, label, repeatMask, soundPreset, customPath, customName ->
                showCreateDialog = false
                nowMillis = System.currentTimeMillis()
                scope.launch {
                    repository.create(
                        hour = hour,
                        minute = minute,
                        label = label,
                        repeatDaysMask = repeatMask,
                        soundPreset = soundPreset,
                        customSoundPath = customPath,
                        customSoundName = customName,
                    )
                }
            },
        )
    }

    editingAlarm?.let { target ->
        AlarmEditorDialog(
            initial = target,
            use24HourFormat = use24HourFormat,
            onDismiss = {
                editingAlarm = null
                scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
            },
            onSave = { hour, minute, label, repeatMask, soundPreset, customPath, customName ->
                editingAlarm = null
                nowMillis = System.currentTimeMillis()
                scope.launch {
                    repository.update(
                        id = target.id,
                        hour = hour,
                        minute = minute,
                        label = label,
                        repeatDaysMask = repeatMask,
                        soundPreset = soundPreset,
                        customSoundPath = customPath,
                        customSoundName = customName,
                    )
                }
            },
        )
    }
}

@Composable
private fun LiveClockHeader(
    nowMillis: Long,
    use24HourFormat: Boolean,
    clockStyle: ClockWidgetStyle,
    nextAlarmText: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spanishLocale = remember { Locale.forLanguageTag("es") }
    val datePattern = remember(spanishLocale) { SimpleDateFormat("EEEE, d 'de' MMMM", spanishLocale) }
    val timePattern = remember(use24HourFormat) {
        SimpleDateFormat(if (use24HourFormat) "HH:mm" else "hh:mm", Locale.US)
    }
    val hourPattern = remember(use24HourFormat) {
        SimpleDateFormat(if (use24HourFormat) "HH" else "hh", Locale.US)
    }
    val minutePattern = remember { SimpleDateFormat("mm", Locale.US) }
    val secondsPattern = remember { SimpleDateFormat(":ss", Locale.US) }
    val periodPattern = remember { SimpleDateFormat("a", Locale.US) }

    val nowDate = remember(nowMillis) { Date(nowMillis) }
    val mainTimeText = remember(nowMillis, use24HourFormat) { timePattern.format(nowDate) }
    val hoursText = remember(nowMillis, use24HourFormat) { hourPattern.format(nowDate) }
    val minutesText = remember(nowMillis) { minutePattern.format(nowDate) }
    val secondsText = remember(nowMillis) { secondsPattern.format(nowDate) }
    val periodText = remember(nowMillis, use24HourFormat) {
        if (use24HourFormat) null else periodPattern.format(nowDate).uppercase(Locale.US)
    }
    val rawDateText = remember(nowMillis / 60_000L) {
        datePattern.format(nowDate).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(spanishLocale) else ch.toString()
        }
    }
    val dateText = if (clockStyle == ClockWidgetStyle.TECH_MONO) {
        rawDateText.uppercase(spanishLocale)
    } else {
        rawDateText
    }

    val clockFontFamily = when (clockStyle) {
        ClockWidgetStyle.TECH_MONO -> FontFamily.Monospace
        ClockWidgetStyle.EDITORIAL_SERIF -> FontFamily.Serif
        else -> FontFamily.Default
    }
    val clockFontWeight = when (clockStyle) {
        ClockWidgetStyle.MINIMAL_CLEAR -> FontWeight.ExtraLight
        ClockWidgetStyle.ACCENT_HERO, ClockWidgetStyle.BOLD_EDGE -> FontWeight.Bold
        ClockWidgetStyle.STACKED_DUO, ClockWidgetStyle.SPLIT_TILES -> FontWeight.Medium
        ClockWidgetStyle.TECH_MONO -> FontWeight.SemiBold
        else -> FontWeight.Light
    }

    when (clockStyle) {
        ClockWidgetStyle.COMPACT_ROW -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                InlineClockDigits(
                    mainTimeText = mainTimeText,
                    secondsText = secondsText,
                    periodText = periodText,
                    fontFamily = clockFontFamily,
                    fontWeight = clockFontWeight,
                    fontSizeSp = 44,
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = dateText,
                        style = AppTypography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                    if (nextAlarmText != null) {
                        NextAlarmBadge(nextAlarmText = nextAlarmText)
                    }
                }
            }
        }

        ClockWidgetStyle.STACKED_DUO -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hoursText,
                        style = AppTypography.timerCard.copy(
                            fontFamily = clockFontFamily,
                            fontWeight = clockFontWeight,
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = minutesText,
                        style = AppTypography.timerCard.copy(
                            fontFamily = clockFontFamily,
                            fontWeight = clockFontWeight,
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = secondsText,
                            style = AppTypography.timerCard.copy(
                                fontFamily = clockFontFamily,
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textSecondary,
                        )
                        if (periodText != null) {
                            Text(
                                text = periodText,
                                style = AppTypography.label,
                                color = colors.accent,
                            )
                        }
                    }
                    Text(
                        text = dateText,
                        style = AppTypography.body,
                        color = colors.textSecondary,
                    )
                    if (nextAlarmText != null) {
                        NextAlarmBadge(nextAlarmText = nextAlarmText)
                    }
                }
            }
        }

        ClockWidgetStyle.SPLIT_TILES -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    listOf(hoursText, minutesText).forEachIndexed { idx, part ->
                        if (idx > 0) Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.outline),
                        ) {
                            Text(
                                text = part,
                                style = AppTypography.timerCard.copy(
                                    fontFamily = clockFontFamily,
                                    fontWeight = clockFontWeight,
                                    fontSize = 40.sp,
                                    lineHeight = 44.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = secondsText,
                            style = AppTypography.timerCard.copy(
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textSecondary,
                        )
                        if (periodText != null) {
                            Text(
                                text = periodText,
                                style = AppTypography.label,
                                color = colors.accent,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = dateText,
                    style = AppTypography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                if (nextAlarmText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NextAlarmBadge(nextAlarmText = nextAlarmText)
                }
            }
        }

        ClockWidgetStyle.CAPSULE_SPLIT -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InlineClockDigits(
                            mainTimeText = mainTimeText,
                            secondsText = secondsText,
                            periodText = periodText,
                            fontFamily = clockFontFamily,
                            fontWeight = clockFontWeight,
                            fontSizeSp = 48,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dateText,
                            style = AppTypography.label,
                            color = colors.textSecondary,
                        )
                        if (nextAlarmText != null) {
                            NextAlarmBadge(nextAlarmText = nextAlarmText, compact = true)
                        }
                    }
                }
            }
        }

        ClockWidgetStyle.BOLD_EDGE -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(78.dp)
                        .clip(CircleShape)
                        .background(colors.outline),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineClockDigits(
                        mainTimeText = mainTimeText,
                        secondsText = secondsText,
                        periodText = periodText,
                        fontFamily = clockFontFamily,
                        fontWeight = clockFontWeight,
                        fontSizeSp = 50,
                        letterSpacingSp = -1.5f,
                    )
                    Text(
                        text = dateText,
                        style = AppTypography.body,
                        color = colors.textSecondary,
                    )
                    if (nextAlarmText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        NextAlarmBadge(nextAlarmText = nextAlarmText)
                    }
                }
            }
        }

        ClockWidgetStyle.EDITORIAL_SERIF -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InlineClockDigits(
                    mainTimeText = mainTimeText,
                    secondsText = secondsText,
                    periodText = periodText,
                    fontFamily = clockFontFamily,
                    fontWeight = clockFontWeight,
                    fontSizeSp = 50,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(2.dp)
                        .background(colors.outline),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dateText,
                    style = AppTypography.body.copy(fontFamily = FontFamily.Serif),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                if (nextAlarmText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NextAlarmBadge(nextAlarmText = nextAlarmText)
                }
            }
        }

        ClockWidgetStyle.CLASSIC,
        ClockWidgetStyle.MINIMAL_CLEAR,
        ClockWidgetStyle.ACCENT_HERO,
        ClockWidgetStyle.TECH_MONO,
        -> {
            val containerModifier = if (clockStyle == ClockWidgetStyle.TECH_MONO) {
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(vertical = 14.dp, horizontal = 16.dp)
            } else {
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            }
            Column(
                modifier = containerModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (clockStyle == ClockWidgetStyle.TECH_MONO) {
                    Text(
                        text = dateText,
                        style = AppTypography.label.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                        ),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                InlineClockDigits(
                    mainTimeText = mainTimeText,
                    secondsText = secondsText,
                    periodText = periodText,
                    fontFamily = clockFontFamily,
                    fontWeight = clockFontWeight,
                    fontSizeSp = when (clockStyle) {
                        ClockWidgetStyle.MINIMAL_CLEAR -> 54
                        ClockWidgetStyle.TECH_MONO -> 44
                        else -> 52
                    },
                )
                if (clockStyle != ClockWidgetStyle.TECH_MONO) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateText,
                        style = AppTypography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (nextAlarmText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    NextAlarmBadge(
                        nextAlarmText = nextAlarmText,
                        monospace = clockStyle == ClockWidgetStyle.TECH_MONO,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineClockDigits(
    mainTimeText: String,
    secondsText: String,
    periodText: String?,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSizeSp: Int,
    letterSpacingSp: Float = -0.5f,
) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = mainTimeText,
            style = AppTypography.timerCard.copy(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 4).sp,
                letterSpacing = letterSpacingSp.sp,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textPrimary,
        )
        Text(
            text = secondsText,
            style = AppTypography.timerCard.copy(
                fontFamily = fontFamily,
                fontSize = (fontSizeSp * 0.38f).toInt().sp,
                lineHeight = ((fontSizeSp * 0.38f).toInt() + 4).sp,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        if (periodText != null) {
            Text(
                text = periodText,
                style = AppTypography.label.copy(
                    fontFamily = fontFamily,
                    fontSize = 13.sp,
                ),
                color = colors.accent,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun NextAlarmBadge(
    nextAlarmText: String,
    compact: Boolean = false,
    monospace: Boolean = false,
) {
    val colors = AppTheme.colors
    Surface(
        shape = if (monospace) RoundedCornerShape(8.dp) else CircleShape,
        color = colors.accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_alarm),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = nextAlarmText,
                style = if (monospace) {
                    AppTypography.label.copy(fontFamily = FontFamily.Monospace)
                } else {
                    AppTypography.label
                },
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    use24HourFormat: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val soundLabel = remember(alarm.soundPreset, alarm.customSoundPath, alarm.customSoundName) {
        resolveSoundLabel(
            isAlarm = true,
            soundPreset = alarm.soundPreset,
            customSoundPath = alarm.customSoundPath,
            customSoundName = alarm.customSoundName,
        )
    }
    val timeText = AlarmRepeatUtils.formatAlarmTime(alarm.hour, alarm.minute, use24HourFormat)
    val repeatSummary = AlarmRepeatUtils.formatRepeatSummary(alarm.repeatDaysMask)
    val remainingText = if (alarm.enabled) {
        val nextMillis = AlarmRepeatUtils.nextTriggerMillis(
            hour = alarm.hour,
            minute = alarm.minute,
            repeatDaysMask = alarm.repeatDaysMask,
            nowMillis = nowMillis,
        )
        AlarmRepeatUtils.formatRemainingUntil(nextMillis, nowMillis)
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (alarm.enabled) 1.5.dp else 1.dp,
            color = if (alarm.enabled) colors.accent else colors.outline,
        ),
    ) {
        val isSilent = isSilentSoundPreset(alarm.soundPreset)
        var confirmingDelete by remember(alarm.id) { mutableStateOf(false) }

        StableActionRow(
            reservedDeleteWidth = 38.dp,
            spacing = 8.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            leftContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = timeText,
                        style = AppTypography.timerCard,
                        color = if (alarm.enabled) colors.textPrimary else colors.textSecondary,
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            style = AppTypography.body,
                            color = colors.textSecondary,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = repeatSummary,
                            style = AppTypography.label,
                            color = colors.textSecondary,
                        )
                        if (remainingText != null) {
                            Text(
                                text = "· $remainingText",
                                style = AppTypography.label,
                                color = colors.accent,
                                maxLines = 1,
                            )
                        }
                    }
                    if (isSilent) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.outline),
                        ) {
                            Text(
                                text = "Sin sonido · Solo vibración",
                                style = AppTypography.label,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    } else if (soundLabel != null) {
                        Text(
                            text = "♪ $soundLabel",
                            style = AppTypography.label,
                            color = if (alarm.enabled) colors.accent else colors.textSecondary,
                        )
                    }
                }
            },
            deleteAction = {
                ConfirmDeleteButton(
                    confirming = confirmingDelete,
                    onRequestConfirm = { confirmingDelete = true },
                    onConfirm = {
                        confirmingDelete = false
                        onDelete()
                    },
                    onCancelConfirm = { confirmingDelete = false },
                    contentDescription = "Eliminar alarma",
                    buttonSize = 38.dp,
                )
            },
            trailingAction = {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceElevated,
                        uncheckedBorderColor = colors.outline,
                    ),
                )
            },
        )
    }
}

@Composable
private fun AlarmEditorDialog(
    initial: AlarmEntity?,
    use24HourFormat: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        hour: Int,
        minute: Int,
        label: String,
        repeatDaysMask: Int,
        soundPreset: String?,
        customPath: String?,
        customName: String?,
    ) -> Unit,
) {
    val colors = AppTheme.colors
    val view = LocalView.current
    var hour by remember(initial) { mutableIntStateOf(initial?.hour ?: 7) }
    var minute by remember(initial) { mutableIntStateOf(initial?.minute ?: 30) }
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var repeatDaysMask by remember(initial) { mutableIntStateOf(initial?.repeatDaysMask ?: 0) }
    var soundPreset by remember(initial) { mutableStateOf(initial?.soundPreset) }
    var customPath by remember(initial) { mutableStateOf(initial?.customSoundPath) }
    var customName by remember(initial) { mutableStateOf(initial?.customSoundName) }
    var lastNonSilentPreset by remember(initial) {
        mutableStateOf(initial?.soundPreset?.takeUnless { isSilentSoundPreset(it) })
    }
    var lastCustomPath by remember(initial) {
        mutableStateOf(if (isSilentSoundPreset(initial?.soundPreset)) null else initial?.customSoundPath)
    }
    var lastCustomName by remember(initial) {
        mutableStateOf(if (isSilentSoundPreset(initial?.soundPreset)) null else initial?.customSoundName)
    }

    val isPm = hour >= 12
    val displayHour = if (use24HourFormat) {
        hour
    } else {
        when (val h12 = hour % 12) {
            0 -> 12
            else -> h12
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = if (initial == null) "Nueva alarma" else "Editar alarma",
                    style = AppTypography.title,
                    color = colors.textPrimary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberStepper(
                        title = "Hora",
                        value = displayHour,
                        maxValue = if (use24HourFormat) 23 else 12,
                        onValueChange = { typed ->
                            hour = if (use24HourFormat) {
                                typed
                            } else {
                                val h12 = typed.coerceIn(1, 12)
                                if (isPm) (if (h12 == 12) 12 else h12 + 12) else (if (h12 == 12) 0 else h12)
                            }
                        },
                        onDecrement = {
                            hour = if (use24HourFormat) {
                                if (hour > 0) hour - 1 else 23
                            } else {
                                val prev12 = if (displayHour > 1) displayHour - 1 else 12
                                if (isPm) (if (prev12 == 12) 12 else prev12 + 12) else (if (prev12 == 12) 0 else prev12)
                            }
                        },
                        onIncrement = {
                            hour = if (use24HourFormat) {
                                (hour + 1) % 24
                            } else {
                                val next12 = if (displayHour < 12) displayHour + 1 else 1
                                if (isPm) (if (next12 == 12) 12 else next12 + 12) else (if (next12 == 12) 0 else next12)
                            }
                        },
                    )
                    Text(
                        text = ":",
                        style = AppTypography.timerCard,
                        color = colors.textSecondary,
                    )
                    NumberStepper(
                        title = "Minuto",
                        value = minute,
                        maxValue = 59,
                        onValueChange = { minute = it },
                        onDecrement = { minute = if (minute > 0) minute - 1 else 59 },
                        onIncrement = { minute = (minute + 1) % 60 },
                    )

                    if (!use24HourFormat) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            listOf("AM" to false, "PM" to true).forEach { (periodLabel, targetPm) ->
                                val selected = isPm == targetPm
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (selected) return@clickable
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            hour = if (targetPm) hour + 12 else hour - 12
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) colors.accent else colors.surfaceElevated,
                                    border = if (selected) null else BorderStroke(1.dp, colors.outline),
                                ) {
                                    Text(
                                        text = periodLabel,
                                        style = AppTypography.label,
                                        color = if (selected) colors.onAccent else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Repetir",
                            style = AppTypography.title,
                            color = colors.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "Lun-Vie" to AlarmRepeatUtils.MASK_WEEKDAYS,
                                "Todos" to AlarmRepeatUtils.MASK_EVERYDAY,
                            ).forEach { (presetLabel, presetMask) ->
                                val active = repeatDaysMask == presetMask
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            repeatDaysMask = if (active) AlarmRepeatUtils.MASK_NONE else presetMask
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) colors.accent else colors.surfaceElevated,
                                    border = if (active) null else BorderStroke(1.dp, colors.outline),
                                ) {
                                    Text(
                                        text = presetLabel,
                                        style = AppTypography.label,
                                        color = if (active) colors.onAccent else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AlarmRepeatUtils.DAY_SHORT_LETTERS.forEachIndexed { idx, letter ->
                            val selected = AlarmRepeatUtils.hasDay(repeatDaysMask, idx)
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        repeatDaysMask = AlarmRepeatUtils.toggleDay(repeatDaysMask, idx)
                                    },
                                shape = CircleShape,
                                color = if (selected) colors.accent else colors.background,
                                border = if (selected) null else BorderStroke(1.dp, colors.outline),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter,
                                        style = AppTypography.label,
                                        color = if (selected) colors.onAccent else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    val previewNext = AlarmRepeatUtils.nextTriggerMillis(hour, minute, repeatDaysMask)
                    Text(
                        text = "${AlarmRepeatUtils.formatRepeatSummary(repeatDaysMask)} · ${AlarmRepeatUtils.formatRemainingUntil(previewNext)}",
                        style = AppTypography.label,
                        color = colors.accent,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.background,
                    border = BorderStroke(1.dp, colors.outline),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (label.isEmpty()) {
                            Text(
                                text = "Etiqueta (opcional, ej. Despertar)",
                                style = AppTypography.body,
                                color = colors.textSecondary,
                            )
                        }
                        BasicTextField(
                            value = label,
                            onValueChange = { if (it.length <= 30) label = it },
                            singleLine = true,
                            textStyle = AppTypography.body.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SoundPickerSection(
                    isAlarm = true,
                    selectedPreset = soundPreset,
                    customSoundPath = customPath,
                    customSoundName = customName,
                    allowSilentToggle = true,
                    onToggleSoundEnabled = { enabled ->
                        if (enabled) {
                            soundPreset = lastNonSilentPreset
                            customPath = lastCustomPath
                            customName = lastCustomName
                        } else {
                            if (!isSilentSoundPreset(soundPreset)) {
                                lastNonSilentPreset = soundPreset
                                lastCustomPath = customPath
                                lastCustomName = customName
                            }
                            soundPreset = SILENT_SOUND_PRESET
                            customPath = null
                            customName = null
                        }
                    },
                    onSelectDefault = {
                        soundPreset = null
                        customPath = null
                        customName = null
                        lastNonSilentPreset = null
                        lastCustomPath = null
                        lastCustomName = null
                    },
                    onSelectPreset = { presetName ->
                        soundPreset = presetName
                        customPath = null
                        customName = null
                        lastNonSilentPreset = presetName
                        lastCustomPath = null
                        lastCustomName = null
                    },
                    onCustomAudioImported = { path, name ->
                        soundPreset = "CUSTOM"
                        customPath = path
                        customName = name
                        lastNonSilentPreset = "CUSTOM"
                        lastCustomPath = path
                        lastCustomName = name
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceElevated,
                    ) {
                        Text(
                            text = "Cancelar",
                            style = AppTypography.label,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSave(
                                    hour,
                                    minute,
                                    label.trim(),
                                    repeatDaysMask,
                                    soundPreset,
                                    customPath,
                                    customName,
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accent,
                    ) {
                        Text(
                            text = "Guardar",
                            style = AppTypography.label,
                            color = colors.onAccent,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
