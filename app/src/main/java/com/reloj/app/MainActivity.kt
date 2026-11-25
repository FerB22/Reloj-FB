package com.reloj.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.reloj.app.core.alarm.NotificationHelper
import com.reloj.app.core.data.local.AppIconManager
import com.reloj.app.core.data.local.AppLocaleManager
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.data.repository.AlarmRepository
import com.reloj.app.core.data.repository.TimerRepository
import com.reloj.app.core.designsystem.AppTheme
import com.reloj.app.core.designsystem.AppTypography
import com.reloj.app.core.designsystem.RelojTheme
import com.reloj.app.core.designsystem.ThemeMode
import com.reloj.app.core.designsystem.buildAppColorScheme
import com.reloj.app.feature.alarm.AlarmScreen
import com.reloj.app.feature.settings.SettingsScreen
import com.reloj.app.feature.timer.TimerHomeScreen
import com.reloj.app.feature.widget.ClockWidget
import com.reloj.app.feature.widget.TimerWidget
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class AppTab(@param:StringRes val labelRes: Int, val iconRes: Int) {
    ALARMS(R.string.tab_alarms, R.drawable.ic_alarm),
    TIMERS(R.string.tab_timers, R.drawable.ic_timer),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

class MainActivity : ComponentActivity() {

    private var requestedTab by mutableStateOf<AppTab?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = parseTabExtra(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            ThemePreferences.get(applicationContext).syncSystemNightModeIfNeeded(applicationContext)
            AppIconManager.applyPendingIconIfNeeded(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePrefs = ThemePreferences.get(this)
        val initialAppearance = themePrefs.settings.value
        val systemDarkAtBoot =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialDark = initialAppearance.themeMode.isDark(systemDarkAtBoot)
        val initialScheme = buildAppColorScheme(
            themeMode = initialAppearance.themeMode,
            systemDark = systemDarkAtBoot,
            accent = initialAppearance.accent,
        )
        setTheme(themeStyleFor(initialAppearance.themeMode, systemDarkAtBoot))

        super.onCreate(savedInstanceState)
        applyEdgeToEdgeBars(initialDark)
        window.setBackgroundDrawable(ColorDrawable(initialScheme.background.toArgb()))

        val initialTab = parseTabExtra(intent) ?: AppTab.TIMERS

        setContent {
            val appearance by themePrefs.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val resolvedDark = appearance.themeMode.isDark(systemDark)
            val resolvedScheme = remember(appearance.themeMode, systemDark, appearance.accent) {
                buildAppColorScheme(
                    themeMode = appearance.themeMode,
                    systemDark = systemDark,
                    accent = appearance.accent,
                )
            }
            var appliedThemeKey by remember { mutableStateOf(initialDark to initialScheme.background) }

            LaunchedEffect(resolvedDark, resolvedScheme.background) {
                val targetKey = resolvedDark to resolvedScheme.background
                if (appliedThemeKey != targetKey) {
                    appliedThemeKey = targetKey
                    applyEdgeToEdgeBars(resolvedDark)
                    window.setBackgroundDrawable(ColorDrawable(resolvedScheme.background.toArgb()))
                }
            }

            RelojTheme(
                themeMode = appearance.themeMode,
                accent = appearance.accent,
            ) {
                val timerRepo = remember(this) { TimerRepository.get(this) }
                val alarmRepo = remember(this) { AlarmRepository.get(this) }
                val timers by timerRepo.observeAll().collectAsState(initial = null)
                val alarms by alarmRepo.observeAll().collectAsState(initial = null)

                LaunchedEffect(Unit) {
                    alarmRepo.rescheduleEnabled()
                }

                LaunchedEffect(timers) {
                    val currentTimers = timers ?: return@LaunchedEffect
                    NotificationHelper.syncRunningTimerNotifications(this@MainActivity, currentTimers)
                }

                val pagerState = rememberPagerState(
                    initialPage = initialTab.ordinal,
                    pageCount = { AppTab.entries.size },
                )
                val scope = rememberCoroutineScope()
                val view = LocalView.current

                LaunchedEffect(requestedTab) {
                    val target = requestedTab ?: return@LaunchedEffect
                    requestedTab = null
                    if (pagerState.currentPage != target.ordinal) {
                        pagerState.scrollToPage(target.ordinal)
                    }
                }

                var lastPage by remember { mutableIntStateOf(pagerState.currentPage) }
                var animatingFromTabClick by remember { mutableStateOf(false) }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != lastPage) {
                        lastPage = pagerState.currentPage
                        if (!animatingFromTabClick) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }

                val colors = AppTheme.colors
                val currentTab = AppTab.entries[pagerState.currentPage]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .statusBarsPadding(),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { page ->
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            ).absoluteValue.coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = lerp(0.96f, 1f, 1f - pageOffset)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = lerp(0.75f, 1f, 1f - pageOffset)
                                },
                        ) {
                            when (AppTab.entries[page]) {
                                AppTab.ALARMS -> AlarmScreen(
                                    alarms = alarms,
                                    repository = alarmRepo,
                                    use24HourFormat = appearance.use24HourFormat,
                                    clockStyle = appearance.clockWidgetStyle,
                                )
                                AppTab.TIMERS -> TimerHomeScreen(
                                    timers = timers,
                                    repository = timerRepo,
                                )
                                AppTab.SETTINGS -> SettingsScreen(
                                    appearance = appearance,
                                    onThemeModeChange = { mode ->
                                        themePrefs.setThemeMode(mode)
                                        scope.launch {
                                            TimerWidget.updateAll(this@MainActivity)
                                            ClockWidget.updateAll(this@MainActivity)
                                        }
                                    },
                                    onAccentChange = { accent ->
                                        themePrefs.setAccent(accent)
                                        scope.launch {
                                            TimerWidget.updateAll(this@MainActivity)
                                            ClockWidget.updateAll(this@MainActivity)
                                        }
                                    },
                                    onTimeFormatChange = { use24Hour ->
                                        themePrefs.setUse24HourFormat(use24Hour)
                                        scope.launch {
                                            ClockWidget.updateAll(this@MainActivity)
                                        }
                                    },
                                    onClockWidgetStyleChange = { style ->
                                        themePrefs.setClockWidgetStyle(style)
                                        scope.launch {
                                            ClockWidget.updateAll(this@MainActivity)
                                        }
                                    },
                                    onLanguageChange = { language ->
                                        val needsRecreate = AppLocaleManager.setLanguage(this@MainActivity, language)
                                        scope.launch {
                                            TimerWidget.updateAll(this@MainActivity)
                                            ClockWidget.updateAll(this@MainActivity)
                                            timerRepo.syncNotifications()
                                        }
                                        if (needsRecreate) {
                                            recreate()
                                        }
                                    },
                                )
                            }
                        }
                    }

                    RelojBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { tab ->
                            if (tab.ordinal != pagerState.currentPage) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                scope.launch {
                                    animatingFromTabClick = true
                                    try {
                                        pagerState.animateScrollToPage(
                                            page = tab.ordinal,
                                            animationSpec = tween(durationMillis = 260),
                                        )
                                    } finally {
                                        animatingFromTabClick = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun applyEdgeToEdgeBars(isDark: Boolean) {
        val barStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
    }

    private fun themeStyleFor(mode: ThemeMode, systemDark: Boolean): Int {
        val resolved = when (mode) {
            ThemeMode.SYSTEM -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
            else -> mode
        }
        return when (resolved) {
            ThemeMode.DARK -> R.style.Theme_Reloj_Dark
            ThemeMode.MIDNIGHT -> R.style.Theme_Reloj_Midnight
            ThemeMode.FOREST -> R.style.Theme_Reloj_Forest
            ThemeMode.ICE -> R.style.Theme_Reloj_Ice
            ThemeMode.LIGHT, ThemeMode.SYSTEM -> R.style.Theme_Reloj_Light
        }
    }

    private fun parseTabExtra(intent: Intent?): AppTab? {
        return when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_ALARMS -> AppTab.ALARMS
            else -> null
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_ALARMS = "ALARMS"
    }
}

@Composable
private fun RelojBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val itemColor by animateColorAsState(
                    targetValue = if (selected) colors.accent else colors.textSecondary,
                    animationSpec = tween(durationMillis = 200),
                    label = "tabContentColor",
                )
                val pillBackground by animateColorAsState(
                    targetValue = if (selected) colors.surfaceElevated else colors.surface,
                    animationSpec = tween(durationMillis = 200),
                    label = "tabPillColor",
                )
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val tabLabel = stringResource(tab.labelRes)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBackground)
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = tabLabel,
                            tint = itemColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = tabLabel,
                        style = AppTypography.label,
                        color = itemColor,
                    )
                }
            }
        }
    }
}
