package com.reloj.app.core.alarm

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reloj.app.R
import com.reloj.app.core.data.local.SoundPreferences
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.RelojTheme

class AlertActivity : ComponentActivity() {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val soundSettings = SoundPreferences.get(this).settings.value
        if (soundSettings.volumeButtonsEnabled && AlarmAlertService.activeAlert.value != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        AlarmAlertService.stopActive(this)
                        finish()
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        AlarmAlertService.snoozeActive(this)
                        finish()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val themePrefs = ThemePreferences.get(this)
        val soundPrefs = SoundPreferences.get(this)

        setContent {
            val appearance by themePrefs.settings.collectAsState()
            val soundSettings by soundPrefs.settings.collectAsState()
            val alert by AlarmAlertService.activeAlert.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val resolvedDark = appearance.themeMode.isDark(systemDark)

            LaunchedEffect(alert) {
                if (alert == null) {
                    finish()
                }
            }

            LaunchedEffect(resolvedDark) {
                val barStyle = if (resolvedDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    )
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            RelojTheme(
                themeMode = appearance.themeMode,
                accent = appearance.accent,
            ) {
                val active = alert ?: return@RelojTheme
                val colors = AppTheme.colors
                val view = LocalView.current

                val pulseTransition = rememberInfiniteTransition(label = "alertPulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.18f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "alertPulseScale",
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (active.isAlarm) "ALARMA" else "TEMPORIZADOR",
                        style = AppTypography.sectionTitle,
                        color = colors.accent,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated),
                            )
                            Surface(
                                modifier = Modifier.size(88.dp),
                                shape = CircleShape,
                                color = colors.accent,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(
                                            if (active.isAlarm) R.drawable.ic_alarm else R.drawable.ic_timer,
                                        ),
                                        contentDescription = null,
                                        tint = colors.onAccent,
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = active.title,
                            style = AppTypography.screenTitle,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )

                        if (active.subtitle.isNotBlank()) {
                            Text(
                                text = active.subtitle,
                                style = AppTypography.title,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (active.isSilent) colors.surfaceElevated else colors.surface,
                            border = BorderStroke(1.dp, colors.outline),
                        ) {
                            Text(
                                text = if (active.isSilent) {
                                    "Sin sonido · ${active.soundLabel}"
                                } else {
                                    "♪ ${active.soundLabel}"
                                },
                                style = AppTypography.label,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    AlarmAlertService.stopActive(this@AlertActivity)
                                    finish()
                                },
                            shape = RoundedCornerShape(22.dp),
                            color = colors.accent,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Detener",
                                    style = AppTypography.title,
                                    color = colors.onAccent,
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    AlarmAlertService.snoozeActive(this@AlertActivity)
                                    finish()
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (active.isAlarm) {
                                        "Posponer ${soundSettings.snoozeMinutes} min"
                                    } else {
                                        "+1 min"
                                    },
                                    style = AppTypography.title,
                                    color = colors.textPrimary,
                                )
                            }
                        }

                        if (soundSettings.volumeButtonsEnabled) {
                            Text(
                                text = "Subir volumen: posponer · Bajar volumen: detener",
                                style = AppTypography.label,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
