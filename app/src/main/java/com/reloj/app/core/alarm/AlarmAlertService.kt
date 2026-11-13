package com.reloj.app.core.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.reloj.app.R
import com.reloj.app.core.data.local.AlarmSoundPreset
import com.reloj.app.core.data.local.SoundPreferences
import com.reloj.app.core.data.local.SoundPreviewPlayer
import com.reloj.app.core.data.local.TimerSoundPreset
import com.reloj.app.core.data.repository.TimerRepository
import com.reloj.app.feature.widget.TimerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ActiveAlertState(
    val isAlarm: Boolean,
    val itemId: Long,
    val title: String,
    val subtitle: String,
    val soundLabel: String,
)

class AlarmAlertService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var floatingBanner: TimerFloatingBanner? = null
    private var volumeJob: Job? = null
    private var timeoutJob: Job? = null
    private var currentAlert: ActiveAlertState? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlertAndSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val active = currentAlert
                stopPlaybackResources()
                if (active != null) {
                    if (active.isAlarm) {
                        AlarmScheduler(this).scheduleSnoozeAlarm(active.itemId)
                        stopAlertAndSelf()
                    } else {
                        serviceScope.launch {
                            TimerRepository.get(this@AlarmAlertService).extendSnooze(active.itemId)
                            TimerWidget.updateAll(this@AlarmAlertService)
                            stopAlertAndSelf()
                        }
                    }
                } else {
                    stopAlertAndSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, true)
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: if (isAlarm) "Alarma" else "Temporizador"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
                val soundPreset = intent.getStringExtra(EXTRA_SOUND_PRESET)
                val customPath = intent.getStringExtra(EXTRA_CUSTOM_PATH)
                val customName = intent.getStringExtra(EXTRA_CUSTOM_NAME)

                val resolved = resolveAudioSource(
                    isAlarm = isAlarm,
                    itemPreset = soundPreset,
                    itemCustomPath = customPath,
                    itemCustomName = customName,
                )

                val state = ActiveAlertState(
                    isAlarm = isAlarm,
                    itemId = itemId,
                    title = title,
                    subtitle = subtitle,
                    soundLabel = resolved.displayLabel,
                )

                val useFullScreen = shouldLaunchFullScreen()
                val canShowOverlayBanner = !useFullScreen && TimerFloatingBanner(this).canShow()

                // Arrancar primer plano de inmediato antes de iniciar audio o tareas asíncronas.
                val notification = NotificationHelper.buildAlertNotification(
                    context = this,
                    title = title,
                    body = subtitle.ifBlank { resolved.displayLabel },
                    isAlarm = isAlarm,
                    useFullScreen = useFullScreen,
                    suppressHeadsUp = canShowOverlayBanner,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                startAlertPlayback(state, resolved, useFullScreen, canShowOverlayBanner)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlaybackResources()
        _activeAlert.value = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldLaunchFullScreen(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return !pm.isInteractive || (km?.isKeyguardLocked == true)
    }

    private fun startAlertPlayback(
        state: ActiveAlertState,
        source: ResolvedAudio,
        useFullScreen: Boolean,
        canShowOverlayBanner: Boolean,
    ) {
        SoundPreviewPlayer.stop()
        stopPlaybackResources()

        currentAlert = state
        _activeAlert.value = state

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reloj:AlertWakeLock").apply {
            acquire(6 * 60_000L)
        }
        if (useFullScreen) {
            @Suppress("DEPRECATION")
            screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Reloj:ScreenWakeLock",
            ).apply {
                acquire(8_000L)
            }
            launchFullScreenAlert()
        } else if (canShowOverlayBanner) {
            val banner = TimerFloatingBanner(this)
            val shown = banner.show(
                title = state.title,
                subtitle = state.subtitle.ifBlank { state.soundLabel },
                isAlarm = state.isAlarm,
                onOpenFullScreen = { launchFullScreenAlert() },
                onStop = { stopAlertAndSelf() },
            )
            if (shown) {
                floatingBanner = banner
            } else {
                // Si el sistema bloqueó el overlay, promover a notificación emergente estándar.
                val fallback = NotificationHelper.buildAlertNotification(
                    context = this,
                    title = state.title,
                    body = state.subtitle.ifBlank { state.soundLabel },
                    isAlarm = state.isAlarm,
                    useFullScreen = false,
                    suppressHeadsUp = false,
                )
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, fallback)
            }
        }

        val soundSettings = SoundPreferences.get(this).settings.value
        val useGradual = state.isAlarm && soundSettings.gradualVolume
        startMediaPlayer(source, useGradual)

        if (soundSettings.vibrateEnabled) {
            startVibration(state.isAlarm)
        }

        val maxMillis = if (state.isAlarm) 5 * 60_000L else 2 * 60_000L
        timeoutJob = serviceScope.launch {
            delay(maxMillis)
            stopAlertAndSelf()
        }
    }

    private fun launchFullScreenAlert() {
        val alertIntent = Intent(this, AlertActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            )
        }
        runCatching { startActivity(alertIntent) }
    }

    private fun startMediaPlayer(source: ResolvedAudio, gradualVolume: Boolean) {
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val initialVol = if (gradualVolume) 0.15f else 1.0f

        val createdFromFile = source.filePath?.let { path ->
            val file = File(path)
            if (!file.exists()) return@let null
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAttrs)
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVolume(initialVol, initialVol)
                    prepare()
                    start()
                }
            }.getOrNull()
        }

        val player = createdFromFile ?: runCatching {
            val afd = resources.openRawResourceFd(source.fallbackRawRes)
            MediaPlayer().apply {
                setAudioAttributes(alarmAttrs)
                afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = true
                setVolume(initialVol, initialVol)
                prepare()
                start()
            }
        }.getOrNull()

        mediaPlayer = player

        if (player != null && gradualVolume) {
            volumeJob = serviceScope.launch {
                val steps = 20
                val totalMs = 6_000L
                for (step in 1..steps) {
                    delay(totalMs / steps)
                    val v = 0.15f + (0.85f * step / steps)
                    runCatching { player.setVolume(v, v) }
                }
            }
        }
    }

    private fun startVibration(isAlarm: Boolean) {
        val timings = if (isAlarm) {
            longArrayOf(0, 1400, 250, 1400, 250, 1400, 450)
        } else {
            longArrayOf(0, 800, 200, 800, 400)
        }
        val amplitudes = if (isAlarm) {
            intArrayOf(0, 255, 0, 255, 0, 255, 0)
        } else {
            intArrayOf(0, 255, 0, 255, 0)
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attrs)
        }
    }

    private fun stopPlaybackResources() {
        floatingBanner?.dismissImmediate()
        floatingBanner = null

        volumeJob?.cancel()
        volumeJob = null
        timeoutJob?.cancel()
        timeoutJob = null

        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        mediaPlayer = null

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { vibrator.cancel() }

        wakeLock?.let { wl ->
            runCatching { if (wl.isHeld) wl.release() }
        }
        wakeLock = null

        screenWakeLock?.let { swl ->
            runCatching { if (swl.isHeld) swl.release() }
        }
        screenWakeLock = null
    }

    private fun stopAlertAndSelf() {
        stopPlaybackResources()
        currentAlert = null
        _activeAlert.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class ResolvedAudio(
        val filePath: String?,
        val fallbackRawRes: Int,
        val displayLabel: String,
    )

    private fun resolveAudioSource(
        isAlarm: Boolean,
        itemPreset: String?,
        itemCustomPath: String?,
        itemCustomName: String?,
    ): ResolvedAudio {
        val prefs = SoundPreferences.get(this).settings.value
        val defaultRaw = if (isAlarm) prefs.alarmSound.rawResId else prefs.timerSound.rawResId

        // 1. Audio externo específico de esta alarma o temporizador
        if (!itemCustomPath.isNullOrBlank() && File(itemCustomPath).exists()) {
            return ResolvedAudio(
                filePath = itemCustomPath,
                fallbackRawRes = defaultRaw,
                displayLabel = itemCustomName?.ifBlank { null } ?: "Audio personalizado",
            )
        }

        // 2. Tono propio específico elegido en esta alarma o temporizador
        if (!itemPreset.isNullOrBlank()) {
            if (isAlarm) {
                AlarmSoundPreset.entries.firstOrNull { it.name == itemPreset }?.let { preset ->
                    return ResolvedAudio(null, preset.rawResId, preset.label)
                }
            } else {
                TimerSoundPreset.entries.firstOrNull { it.name == itemPreset }?.let { preset ->
                    return ResolvedAudio(null, preset.rawResId, preset.label)
                }
            }
        }

        // 3. Audio externo global configurado en Ajustes
        val globalCustomPath = if (isAlarm) prefs.customAlarmPath else prefs.customTimerPath
        val globalCustomName = if (isAlarm) prefs.customAlarmName else prefs.customTimerName
        if (!globalCustomPath.isNullOrBlank() && File(globalCustomPath).exists()) {
            return ResolvedAudio(
                filePath = globalCustomPath,
                fallbackRawRes = defaultRaw,
                displayLabel = globalCustomName?.ifBlank { null } ?: "Audio personalizado",
            )
        }

        // 4. Tono integrado configurado en Ajustes
        val label = if (isAlarm) prefs.alarmSound.label else prefs.timerSound.label
        val raw = if (isAlarm) prefs.alarmSound.rawResId else prefs.timerSound.rawResId
        return ResolvedAudio(null, raw, label)
    }

    companion object {
        const val ACTION_START = "com.reloj.app.alert.ACTION_START"
        const val ACTION_STOP = "com.reloj.app.alert.ACTION_STOP"
        const val ACTION_SNOOZE = "com.reloj.app.alert.ACTION_SNOOZE"

        private const val EXTRA_IS_ALARM = "extra_is_alarm"
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_SOUND_PRESET = "extra_sound_preset"
        private const val EXTRA_CUSTOM_PATH = "extra_custom_path"
        private const val EXTRA_CUSTOM_NAME = "extra_custom_name"

        private const val NOTIFICATION_ID = 7001

        private val _activeAlert = MutableStateFlow<ActiveAlertState?>(null)
        val activeAlert: StateFlow<ActiveAlertState?> = _activeAlert.asStateFlow()

        fun startAlert(
            context: Context,
            isAlarm: Boolean,
            itemId: Long,
            title: String,
            subtitle: String,
            soundPreset: String?,
            customPath: String?,
            customName: String?,
        ) {
            val intent = Intent(context, AlarmAlertService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IS_ALARM, isAlarm)
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_SOUND_PRESET, soundPreset)
                putExtra(EXTRA_CUSTOM_PATH, customPath)
                putExtra(EXTRA_CUSTOM_NAME, customName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopActive(context: Context) {
            val intent = Intent(context, AlarmAlertService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun stopIfMatching(context: Context, isAlarm: Boolean, itemId: Long) {
            val active = _activeAlert.value ?: return
            if (active.isAlarm == isAlarm && active.itemId == itemId) {
                stopActive(context)
            }
        }

        fun snoozeActive(context: Context) {
            val intent = Intent(context, AlarmAlertService::class.java).setAction(ACTION_SNOOZE)
            context.startService(intent)
        }
    }
}
