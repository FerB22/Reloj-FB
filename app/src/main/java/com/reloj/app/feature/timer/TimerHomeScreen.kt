package com.reloj.app.feature.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.reloj.app.R
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.core.data.local.TimerEntity
import com.reloj.app.core.data.local.TimerState
import com.reloj.app.core.data.repository.TimerRepository
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.ConfirmDeleteButton
import com.reloj.app.core.designsystem.EmptyStateCard
import com.reloj.app.core.designsystem.NumberStepper
import com.reloj.app.core.designsystem.PrimaryPillButton
import com.reloj.app.core.designsystem.SoundPickerSection
import com.reloj.app.core.designsystem.StableActionRow
import com.reloj.app.core.designsystem.resolveSoundLabel
import com.reloj.app.feature.widget.TimerWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_PRESET_MINUTES = listOf(5, 10, 15, 25, 45)

@Composable
fun TimerHomeScreen(
    timers: List<TimerEntity>?,
    repository: TimerRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTimer by remember { mutableStateOf<TimerEntity?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val hasRunning = timers?.any { it.state == TimerState.RUNNING } == true
    LaunchedEffect(hasRunning) {
        if (hasRunning) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(200)
            }
        }
    }

    fun runAndSyncWidget(block: suspend () -> Unit) {
        scope.launch {
            block()
            TimerWidget.updateAll(context)
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
                text = "Temporizadores",
                style = AppTypography.screenTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_PRESET_MINUTES.forEach { minutes ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                runAndSyncWidget {
                                    repository.create(
                                        label = "$minutes min",
                                        durationMillis = minutes * 60_000L,
                                    )
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline),
                    ) {
                        Text(
                            text = "+ $minutes min",
                            style = AppTypography.label,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (timers != null) {
                if (timers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            iconRes = R.drawable.ic_timer,
                            title = "No hay temporizadores",
                            description = "Añade un temporizador personalizado o usa un acceso rápido arriba.",
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
                        items(timers, key = { it.id }) { timer ->
                            TimerCard(
                                timer = timer,
                                nowMillis = nowMillis,
                                onClick = { editingTimer = timer },
                                onStart = {
                                    nowMillis = System.currentTimeMillis()
                                    runAndSyncWidget { repository.start(timer.id, nowMillis) }
                                },
                                onPause = {
                                    nowMillis = System.currentTimeMillis()
                                    runAndSyncWidget { repository.pause(timer.id, nowMillis) }
                                },
                                onAddMinute = {
                                    nowMillis = System.currentTimeMillis()
                                    runAndSyncWidget { repository.addMinute(timer.id, now = nowMillis) }
                                },
                                onReset = {
                                    runAndSyncWidget { repository.reset(timer.id) }
                                },
                                onDelete = {
                                    runAndSyncWidget { repository.delete(timer) }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!timers.isNullOrEmpty()) {
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
        TimerEditorDialog(
            initial = null,
            onDismiss = {
                showCreateDialog = false
                scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
            },
            onSave = { label, durationMillis, soundPreset, customPath, customName ->
                showCreateDialog = false
                runAndSyncWidget {
                    repository.create(
                        label = label,
                        durationMillis = durationMillis,
                        soundPreset = soundPreset,
                        customSoundPath = customPath,
                        customSoundName = customName,
                    )
                }
            },
        )
    }

    editingTimer?.let { target ->
        TimerEditorDialog(
            initial = target,
            onDismiss = {
                editingTimer = null
                scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
            },
            onSave = { label, durationMillis, soundPreset, customPath, customName ->
                editingTimer = null
                runAndSyncWidget {
                    repository.update(
                        id = target.id,
                        label = label,
                        durationMillis = durationMillis,
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
private fun TimerCard(
    timer: TimerEntity,
    nowMillis: Long,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onAddMinute: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    val displayMillis = when (timer.state) {
        TimerState.RUNNING -> ((timer.endAtMillis ?: nowMillis) - nowMillis).coerceAtLeast(0L)
        TimerState.PAUSED -> (timer.remainingMillis ?: timer.durationMillis).coerceAtLeast(0L)
        TimerState.IDLE -> timer.durationMillis
        TimerState.FINISHED -> 0L
    }

    val isRunning = timer.state == TimerState.RUNNING && displayMillis > 0L
    val isFinished = timer.state == TimerState.FINISHED ||
        (timer.state == TimerState.RUNNING && displayMillis == 0L)

    val statusText = when {
        isFinished -> "Finalizado"
        isRunning -> "En curso"
        timer.state == TimerState.PAUSED -> "Pausado"
        else -> "Listo"
    }

    val soundLabel = remember(timer.soundPreset, timer.customSoundPath, timer.customSoundName) {
        resolveSoundLabel(
            isAlarm = false,
            soundPreset = timer.soundPreset,
            customSoundPath = timer.customSoundPath,
            customSoundName = timer.customSoundName,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (isRunning || isFinished) 1.5.dp else 1.dp,
            color = if (isRunning || isFinished) colors.accent else colors.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = timer.label,
                        style = AppTypography.title,
                        color = colors.textPrimary,
                    )
                    if (soundLabel != null) {
                        Text(
                            text = "♪ $soundLabel",
                            style = AppTypography.label,
                            color = colors.accent,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceElevated,
                ) {
                    Text(
                        text = statusText,
                        style = AppTypography.label,
                        color = if (isRunning || isFinished) colors.accent else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            var confirmingDelete by remember(timer.id) { mutableStateOf(false) }

            StableActionRow(
                reservedDeleteWidth = if (timer.state == TimerState.IDLE) 42.dp else 156.dp,
                spacing = 8.dp,
                leftContent = {
                    Text(
                        text = formatMillis(displayMillis),
                        style = AppTypography.timerCard,
                        color = if (isFinished) colors.accent else colors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                deleteAction = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(
                            visible = !confirmingDelete && (isRunning || timer.state == TimerState.PAUSED),
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onAddMinute),
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, colors.outline),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "+1 min",
                                        style = AppTypography.label,
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !confirmingDelete && timer.state != TimerState.IDLE,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            SmallIconButton(
                                iconRes = R.drawable.ic_reset,
                                contentDescription = "Reiniciar",
                                containerColor = colors.surfaceElevated,
                                iconColor = colors.textPrimary,
                                onClick = onReset,
                            )
                        }

                        ConfirmDeleteButton(
                            confirming = confirmingDelete,
                            onRequestConfirm = { confirmingDelete = true },
                            onConfirm = {
                                confirmingDelete = false
                                onDelete()
                            },
                            onCancelConfirm = { confirmingDelete = false },
                            contentDescription = "Eliminar",
                            buttonSize = 42.dp,
                        )
                    }
                },
                trailingAction = {
                    if (!isFinished) {
                        SmallIconButton(
                            iconRes = if (isRunning) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isRunning) "Pausar" else "Iniciar",
                            containerColor = colors.accent,
                            iconColor = colors.onAccent,
                            onClick = if (isRunning) onPause else onStart,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SmallIconButton(
    iconRes: Int,
    contentDescription: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TimerEditorDialog(
    initial: TimerEntity?,
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        durationMillis: Long,
        soundPreset: String?,
        customPath: String?,
        customName: String?,
    ) -> Unit,
) {
    val colors = AppTheme.colors
    val initialSeconds = ((initial?.durationMillis ?: (5 * 60_000L)) / 1000L).coerceAtLeast(0L)
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var hours by remember(initial) { mutableIntStateOf((initialSeconds / 3600L).toInt().coerceIn(0, 99)) }
    var minutes by remember(initial) { mutableIntStateOf(((initialSeconds % 3600L) / 60L).toInt()) }
    var seconds by remember(initial) { mutableIntStateOf((initialSeconds % 60L).toInt()) }
    var soundPreset by remember(initial) { mutableStateOf(initial?.soundPreset) }
    var customPath by remember(initial) { mutableStateOf(initial?.customSoundPath) }
    var customName by remember(initial) { mutableStateOf(initial?.customSoundName) }

    val totalMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L

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
                    text = if (initial == null) "Nuevo temporizador" else "Editar temporizador",
                    style = AppTypography.title,
                    color = colors.textPrimary,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.background,
                    border = BorderStroke(1.dp, colors.outline),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (label.isEmpty()) {
                            Text(
                                text = "Nombre (opcional, ej. Café)",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberStepper(
                        title = "Horas",
                        value = hours,
                        maxValue = 99,
                        onValueChange = { hours = it },
                        onDecrement = { hours = (hours - 1).coerceAtLeast(0) },
                        onIncrement = { hours = (hours + 1).coerceAtMost(99) },
                    )
                    Text(
                        text = ":",
                        style = AppTypography.timerCard,
                        color = colors.textSecondary,
                    )
                    NumberStepper(
                        title = "Minutos",
                        value = minutes,
                        maxValue = 59,
                        onValueChange = { minutes = it },
                        onDecrement = { minutes = if (minutes > 0) minutes - 1 else 59 },
                        onIncrement = { minutes = (minutes + 1) % 60 },
                    )
                    Text(
                        text = ":",
                        style = AppTypography.timerCard,
                        color = colors.textSecondary,
                    )
                    NumberStepper(
                        title = "Segundos",
                        value = seconds,
                        maxValue = 59,
                        onValueChange = { seconds = it },
                        onDecrement = { seconds = if (seconds > 0) seconds - 1 else 59 },
                        onIncrement = { seconds = (seconds + 1) % 60 },
                    )
                }

                SoundPickerSection(
                    isAlarm = false,
                    selectedPreset = soundPreset,
                    customSoundPath = customPath,
                    customSoundName = customName,
                    onSelectDefault = {
                        soundPreset = null
                        customPath = null
                        customName = null
                    },
                    onSelectPreset = { presetName ->
                        soundPreset = presetName
                        customPath = null
                        customName = null
                    },
                    onCustomAudioImported = { path, name ->
                        soundPreset = "CUSTOM"
                        customPath = path
                        customName = name
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
                            .clickable(enabled = totalMillis > 0L) {
                                val resolvedLabel = label.trim().ifEmpty {
                                    if (hours == 0 && seconds == 0) {
                                        "$minutes min"
                                    } else {
                                        formatMillis(totalMillis)
                                    }
                                }
                                onSave(
                                    resolvedLabel,
                                    totalMillis,
                                    soundPreset,
                                    customPath,
                                    customName,
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (totalMillis > 0L) colors.accent else colors.outline,
                    ) {
                        Text(
                            text = "Guardar",
                            style = AppTypography.label,
                            color = if (totalMillis > 0L) colors.onAccent else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
