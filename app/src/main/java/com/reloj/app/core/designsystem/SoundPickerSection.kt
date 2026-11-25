package com.reloj.app.core.designsystem

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reloj.app.R
import com.reloj.app.core.data.local.AlarmSoundPreset
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.core.data.local.SoundPreviewPlayer
import com.reloj.app.core.data.local.TimerSoundPreset
import com.reloj.app.core.data.local.isSilentSoundPreset
import kotlinx.coroutines.launch

/**
 * Selector compacto de sonido reutilizable para alarmas y temporizadores.
 * Permite elegir el predeterminado de Ajustes, tonos integrados con preescucha,
 * importar un audio externo o silenciar la alarma dejando únicamente vibración.
 */
@Composable
fun SoundPickerSection(
    isAlarm: Boolean,
    selectedPreset: String?,
    customSoundPath: String?,
    customSoundName: String?,
    onSelectDefault: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onCustomAudioImported: (path: String, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.sound_title),
    includeDefaultOption: Boolean = true,
    allowSilentToggle: Boolean = false,
    onToggleSoundEnabled: ((Boolean) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val isSilent = isSilentSoundPreset(selectedPreset)
    val soundEnabled = !allowSilentToggle || !isSilent

    var expanded by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var playingKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(soundEnabled) {
        if (!soundEnabled) {
            expanded = false
            playingKey = null
            SoundPreviewPlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { SoundPreviewPlayer.stop() }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importError = null
        scope.launch {
            CustomSoundManager.importAudio(context, uri)
                .onSuccess { imported ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onCustomAudioImported(imported.path, imported.displayName)
                }
                .onFailure { err ->
                    importError = err.message ?: context.getString(R.string.sound_import_error)
                }
            importing = false
        }
    }

    val currentLabel = resolveSoundLabel(
        context = context,
        isAlarm = isAlarm,
        soundPreset = selectedPreset,
        customSoundPath = customSoundPath,
        customSoundName = customSoundName,
    ) ?: if (includeDefaultOption) {
        stringResource(R.string.sound_default_settings)
    } else if (isAlarm) {
        stringResource(AlarmSoundPreset.SUNRISE.labelRes)
    } else {
        stringResource(TimerSoundPreset.CHIME.labelRes)
    }

    val cardColor by animateColorAsState(
        targetValue = if (soundEnabled) colors.background else colors.surfaceElevated,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "soundCardBg",
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (soundEnabled) colors.outline else colors.outline.copy(alpha = 0.7f),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "soundCardBorder",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, cardBorderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = soundEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allowSilentToggle && onToggleSoundEnabled != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            style = AppTypography.body,
                            color = if (soundEnabled) colors.textPrimary else colors.textSecondary,
                        )
                        Text(
                            text = if (soundEnabled) {
                                "♪ $currentLabel"
                            } else {
                                stringResource(R.string.sound_silent_badge)
                            },
                            style = AppTypography.label,
                            color = if (soundEnabled) colors.accent else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (soundEnabled) {
                            Text(
                                text = if (expanded) "˄" else "˅",
                                style = AppTypography.label,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { enabled ->
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleSoundEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.outline,
                            ),
                        )
                    }
                } else {
                    Text(
                        text = title,
                        style = AppTypography.body,
                        color = colors.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            text = "♪ $currentLabel",
                            style = AppTypography.label,
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (expanded) "˄" else "˅",
                            style = AppTypography.label,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && soundEnabled,
                enter = expandVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(160)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (includeDefaultOption) {
                        SoundOptionRow(
                            label = stringResource(R.string.sound_default_settings),
                            selected = selectedPreset == null && customSoundPath == null,
                            previewKey = null,
                            isPlaying = false,
                            onSelect = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelectDefault()
                            },
                            onTogglePreview = null,
                        )
                    }

                    if (isAlarm) {
                        AlarmSoundPreset.entries.forEach { preset ->
                            val key = "dlg_alarm_${preset.name}"
                            val isSelected = customSoundPath == null && selectedPreset == preset.name
                            SoundOptionRow(
                                label = stringResource(preset.labelRes),
                                selected = isSelected,
                                previewKey = key,
                                isPlaying = playingKey == key,
                                onSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelectPreset(preset.name)
                                },
                                onTogglePreview = {
                                    if (playingKey == key) {
                                        SoundPreviewPlayer.stop()
                                        playingKey = null
                                    } else {
                                        playingKey = key
                                        SoundPreviewPlayer.playRaw(context, preset.rawResId, key) {
                                            if (playingKey == key) playingKey = null
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        TimerSoundPreset.entries.forEach { preset ->
                            val key = "dlg_timer_${preset.name}"
                            val isSelected = customSoundPath == null && selectedPreset == preset.name
                            SoundOptionRow(
                                label = stringResource(preset.labelRes),
                                selected = isSelected,
                                previewKey = key,
                                isPlaying = playingKey == key,
                                onSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelectPreset(preset.name)
                                },
                                onTogglePreview = {
                                    if (playingKey == key) {
                                        SoundPreviewPlayer.stop()
                                        playingKey = null
                                    } else {
                                        playingKey = key
                                        SoundPreviewPlayer.playRaw(context, preset.rawResId, key) {
                                            if (playingKey == key) playingKey = null
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (!customSoundPath.isNullOrBlank()) {
                        val key = "dlg_custom"
                        SoundOptionRow(
                            label = "♪ ${customSoundName ?: stringResource(R.string.sound_device_audio)}",
                            selected = true,
                            previewKey = key,
                            isPlaying = playingKey == key,
                            onSelect = {},
                            onTogglePreview = {
                                if (playingKey == key) {
                                    SoundPreviewPlayer.stop()
                                    playingKey = null
                                } else {
                                    playingKey = key
                                    SoundPreviewPlayer.playFile(customSoundPath, key) {
                                        if (playingKey == key) playingKey = null
                                    }
                                }
                            },
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !importing) {
                                audioPicker.launch(arrayOf("audio/*"))
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.outline),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when {
                                    importing -> stringResource(R.string.sound_importing)
                                    customSoundPath != null -> stringResource(R.string.sound_change_device_audio)
                                    else -> stringResource(R.string.sound_pick_device_audio)
                                },
                                style = AppTypography.label,
                                color = colors.accent,
                            )
                        }
                    }

                    if (importError != null) {
                        Text(
                            text = importError.orEmpty(),
                            style = AppTypography.label,
                            color = colors.danger,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOptionRow(
    label: String,
    selected: Boolean,
    previewKey: String?,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.surface else colors.background,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.accent else colors.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTypography.label,
                color = if (selected) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            if (previewKey != null && onTogglePreview != null) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePreview),
                    shape = CircleShape,
                    color = if (isPlaying) colors.accent else colors.surfaceElevated,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isPlaying) "■" else "▶",
                            style = AppTypography.label,
                            color = if (isPlaying) colors.onAccent else colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

fun resolveSoundLabel(
    context: Context,
    isAlarm: Boolean,
    soundPreset: String?,
    customSoundPath: String?,
    customSoundName: String?,
): String? {
    if (isSilentSoundPreset(soundPreset)) {
        return context.getString(R.string.sound_vibration_only)
    }
    if (!customSoundPath.isNullOrBlank()) {
        return customSoundName?.ifBlank { null } ?: context.getString(R.string.sound_custom_audio)
    }
    if (soundPreset.isNullOrBlank()) return null
    return if (isAlarm) {
        AlarmSoundPreset.entries.firstOrNull { it.name == soundPreset }
            ?.let { context.getString(it.labelRes) }
    } else {
        TimerSoundPreset.entries.firstOrNull { it.name == soundPreset }
            ?.let { context.getString(it.labelRes) }
    }
}
