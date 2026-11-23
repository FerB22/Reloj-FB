package com.reloj.app.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reloj.app.core.alarm.NotificationHelper
import com.reloj.app.core.data.local.AlarmSoundPreset
import com.reloj.app.core.data.local.AppIconManager
import com.reloj.app.core.data.local.AppIconPreset
import com.reloj.app.core.data.local.AppearanceSettings
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.core.data.local.SoundPreferences
import com.reloj.app.core.data.local.SoundPreviewPlayer
import com.reloj.app.core.data.local.TimerSoundPreset
import com.reloj.app.core.designsystem.AccentPreset
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.ClockWidgetStyle
import com.reloj.app.core.designsystem.SoundPickerSection
import com.reloj.app.core.designsystem.ThemeMode
import com.reloj.app.core.designsystem.buildAppColorScheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onTimeFormatChange: (Boolean) -> Unit,
    onClockWidgetStyleChange: (ClockWidgetStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val soundPrefs = remember(context) { SoundPreferences.get(context) }
    val soundSettings by soundPrefs.settings.collectAsState()

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarmsGranted by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var fullScreenGranted by remember { mutableStateOf(NotificationHelper.canUseFullScreenIntent(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var activeIcon by remember { mutableStateOf(AppIconManager.getSelectedIcon(context)) }
    var hasPendingIconChange by remember { mutableStateOf(AppIconManager.hasPendingChange(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = hasNotificationPermission(context)
                exactAlarmsGranted = canScheduleExactAlarms(context)
                fullScreenGranted = NotificationHelper.canUseFullScreenIntent(context)
                overlayGranted = Settings.canDrawOverlays(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                activeIcon = AppIconManager.getSelectedIcon(context)
                hasPendingIconChange = AppIconManager.hasPendingChange(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SoundPreviewPlayer.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Ajustes",
            style = AppTypography.screenTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader("APARIENCIA")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val systemDark = isSystemInDarkTheme()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tema de fondo",
                        style = AppTypography.title,
                        color = colors.textPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.chunked(3).forEach { rowModes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowModes.forEach { mode ->
                                    val selected = appearance.themeMode == mode
                                    val previewScheme = remember(mode, systemDark, appearance.accent) {
                                        buildAppColorScheme(
                                            themeMode = mode,
                                            systemDark = systemDark,
                                            accent = appearance.accent,
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (appearance.themeMode != mode) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    onThemeModeChange(mode)
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = previewScheme.background,
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) colors.accent else previewScheme.outline,
                                        ),
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = mode.label,
                                                style = AppTypography.label,
                                                color = previewScheme.textPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var accentExpanded by remember { mutableStateOf(false) }
                val arrowRotation by animateFloatAsState(
                    targetValue = if (accentExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "accentArrowRotation",
                )
                val accentRows = remember { AccentPreset.entries.chunked(4) }

                Column {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Color de acento",
                                style = AppTypography.title,
                                color = colors.textPrimary,
                            )
                            val headerToggleInteraction = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier.clickable(
                                    interactionSource = headerToggleInteraction,
                                    indication = null,
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    accentExpanded = !accentExpanded
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                )
                                Text(
                                    text = appearance.accent.label,
                                    style = AppTypography.label,
                                    color = colors.accent,
                                )
                                AccentExpandArrow(
                                    rotationDegrees = arrowRotation,
                                    tint = colors.textSecondary,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            accentRows.first().forEach { preset ->
                                AccentSwatch(
                                    preset = preset,
                                    isDark = colors.isDark,
                                    selected = appearance.accent == preset,
                                    outlineColor = colors.outline,
                                    selectedBorderColor = colors.textPrimary,
                                    onClick = {
                                        if (appearance.accent != preset) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onAccentChange(preset)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = accentExpanded,
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
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            accentRows.drop(1).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    rowPresets.forEach { preset ->
                                        AccentSwatch(
                                            preset = preset,
                                            isDark = colors.isDark,
                                            selected = appearance.accent == preset,
                                            outlineColor = colors.outline,
                                            selectedBorderColor = colors.textPrimary,
                                            onClick = {
                                                if (appearance.accent != preset) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    onAccentChange(preset)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Formato horario",
                            style = AppTypography.title,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (appearance.use24HourFormat) "24 h (19:30)" else "12 h (7:30 PM)",
                            style = AppTypography.label,
                            color = colors.textSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("24 h" to true, "12 h" to false).forEach { (fmtLabel, is24h) ->
                            val selected = appearance.use24HourFormat == is24h
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!selected) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onTimeFormatChange(is24h)
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colors.accent else colors.surfaceElevated,
                                border = if (selected) null else BorderStroke(1.dp, colors.outline),
                            ) {
                                Text(
                                    text = fmtLabel,
                                    style = AppTypography.label,
                                    color = if (selected) colors.onAccent else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionHeader("WIDGET DE RELOJ")

        ClockWidgetStyleCard(
            selectedStyle = appearance.clockWidgetStyle,
            use24HourFormat = appearance.use24HourFormat,
            onSelectStyle = onClockWidgetStyleChange,
            expandIcon = { rotation ->
                AccentExpandArrow(
                    rotationDegrees = rotation,
                    tint = colors.textSecondary,
                )
            },
        )

        SectionHeader("ICONO DE LA APLICACIÓN")

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Estilo del icono",
                        style = AppTypography.title,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = activeIcon.label,
                        style = AppTypography.label,
                        color = colors.accent,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIconPreset.entries.chunked(5).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowPresets.forEach { preset ->
                                AppIconTile(
                                    preset = preset,
                                    selected = activeIcon == preset,
                                    onClick = {
                                        if (activeIcon != preset) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            AppIconManager.scheduleIconChange(context, preset)
                                            activeIcon = preset
                                            hasPendingIconChange = AppIconManager.hasPendingChange(context)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (hasPendingIconChange) {
                        "Icono seleccionado. Se aplicará automáticamente al salir de la aplicación."
                    } else {
                        "El cambio de icono se aplica al salir de la aplicación para no cerrarla."
                    },
                    style = AppTypography.label,
                    color = if (hasPendingIconChange) colors.accent else colors.textSecondary,
                )
            }
        }

        SectionHeader("SONIDO Y ALERTAS")

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
                SoundPickerSection(
                    title = "Tono de alarma",
                    isAlarm = true,
                    includeDefaultOption = false,
                    selectedPreset = if (soundSettings.customAlarmPath != null) "CUSTOM" else soundSettings.alarmSound.name,
                    customSoundPath = soundSettings.customAlarmPath,
                    customSoundName = soundSettings.customAlarmName,
                    onSelectDefault = {},
                    onSelectPreset = { name ->
                        AlarmSoundPreset.entries.firstOrNull { it.name == name }?.let { preset ->
                            soundPrefs.setAlarmPreset(preset)
                            scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
                        }
                    },
                    onCustomAudioImported = { path, name ->
                        soundPrefs.setCustomAlarmAudio(path, name)
                        scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
                    },
                )

                SoundPickerSection(
                    title = "Tono de temporizador",
                    isAlarm = false,
                    includeDefaultOption = false,
                    selectedPreset = if (soundSettings.customTimerPath != null) "CUSTOM" else soundSettings.timerSound.name,
                    customSoundPath = soundSettings.customTimerPath,
                    customSoundName = soundSettings.customTimerName,
                    onSelectDefault = {},
                    onSelectPreset = { name ->
                        TimerSoundPreset.entries.firstOrNull { it.name == name }?.let { preset ->
                            soundPrefs.setTimerPreset(preset)
                            scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
                        }
                    },
                    onCustomAudioImported = { path, name ->
                        soundPrefs.setCustomTimerAudio(path, name)
                        scope.launch { CustomSoundManager.pruneUnusedSounds(context) }
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                SettingSwitchRow(
                    title = "Vibrar al sonar",
                    subtitle = "Patrón diferenciado para alarmas y temporizadores",
                    checked = soundSettings.vibrateEnabled,
                    onCheckedChange = { enabled ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        soundPrefs.setVibrateEnabled(enabled)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                SettingSwitchRow(
                    title = "Volumen gradual en alarmas",
                    subtitle = "Sube de forma progresiva durante los primeros 6 s",
                    checked = soundSettings.gradualVolume,
                    onCheckedChange = { enabled ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        soundPrefs.setGradualVolume(enabled)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                SettingSwitchRow(
                    title = "Modo despertador intenso",
                    subtitle = "Sube temporalmente el volumen al 90 %, refuerza el altavoz e intensifica la vibración",
                    checked = soundSettings.intenseWakeMode,
                    onCheckedChange = { enabled ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        soundPrefs.setIntenseWakeMode(enabled)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                SettingSwitchRow(
                    title = "Botones de volumen al sonar",
                    subtitle = "Subir pospone · Bajar detiene la alarma",
                    checked = soundSettings.volumeButtonsEnabled,
                    onCheckedChange = { enabled ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        soundPrefs.setVolumeButtonsEnabled(enabled)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Duración de posponer",
                            style = AppTypography.title,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Tiempo añadido al posponer una alarma",
                            style = AppTypography.label,
                            color = colors.textSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15).forEach { mins ->
                            val selected = soundSettings.snoozeMinutes == mins
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!selected) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            soundPrefs.setSnoozeMinutes(mins)
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colors.accent else colors.surfaceElevated,
                                border = if (selected) null else BorderStroke(1.dp, colors.outline),
                            ) {
                                Text(
                                    text = "$mins min",
                                    style = AppTypography.label,
                                    color = if (selected) colors.onAccent else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionHeader("PERMISOS DEL SISTEMA")

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
                PermissionRow(
                    title = "Notificaciones",
                    subtitle = "Necesarias para avisar cuando termine un temporizador",
                    granted = notificationsGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )
                PermissionRow(
                    title = "Alarmas exactas",
                    subtitle = "Garantiza precisión aunque la pantalla esté apagada",
                    granted = exactAlarmsGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                        }
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.outline),
                    )
                    PermissionRow(
                        title = "Alertas con pantalla bloqueada",
                        subtitle = "Permite mostrar Detener y Posponer al despertar el móvil",
                        granted = fullScreenGranted,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )
                PermissionRow(
                    title = "Mostrar sobre otras apps",
                    subtitle = "Muestra el banner flotante personalizado al estar en otra aplicación",
                    granted = overlayGranted,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline),
                )
                PermissionRow(
                    title = "Sin restricciones de batería",
                    subtitle = "Evita que el sistema suspenda las alarmas con la pantalla apagada",
                    granted = batteryUnrestricted,
                    onClick = {
                        runCatching {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    isDark: Boolean,
    selected: Boolean,
    outlineColor: Color,
    selectedBorderColor: Color,
    onClick: () -> Unit,
) {
    val swatchColor = if (isDark) preset.darkColor else preset.lightColor
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) selectedBorderColor else outlineColor,
                shape = CircleShape,
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun AccentExpandArrow(
    rotationDegrees: Float,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = rotationDegrees },
        ) {
            val chevron = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.65f)
                lineTo(size.width * 0.5f, size.height * 0.33f)
                lineTo(size.width * 0.82f, size.height * 0.65f)
            }
            drawPath(
                path = chevron,
                color = tint,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = AppTypography.sectionTitle,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AppIconTile(
    preset: AppIconPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) colors.accent else colors.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(3.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = preset.bgColor)
            val strokePx = size.minDimension * (5f / 108f)
            val ringRadius = size.minDimension * (26f / 108f)
            drawCircle(
                color = preset.ringColor,
                radius = ringRadius,
                style = Stroke(width = strokePx),
            )
            val handsPath = Path().apply {
                moveTo(size.width * (54f / 108f), size.height * (38f / 108f))
                lineTo(size.width * (54f / 108f), size.height * (54f / 108f))
                lineTo(size.width * (65f / 108f), size.height * (61f / 108f))
            }
            drawPath(
                path = handsPath,
                color = preset.handsColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = AppTypography.body,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = AppTypography.label,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = AppTypography.body,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = AppTypography.label,
                color = colors.textSecondary,
            )
        }
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !granted, onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            color = if (granted) colors.surfaceElevated else colors.accent,
        ) {
            Text(
                text = if (granted) "Activo" else "Activar",
                style = AppTypography.label,
                color = if (granted) colors.textSecondary else colors.onAccent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
