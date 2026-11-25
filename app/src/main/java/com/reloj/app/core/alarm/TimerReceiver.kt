package com.reloj.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.reloj.app.R
import com.reloj.app.core.data.local.AppLocaleManager
import com.reloj.app.core.data.repository.TimerRepository
import com.reloj.app.feature.widget.TimerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_FINISHED) return
        val id = intent.getLongExtra(EXTRA_TIMER_ID, -1)
        if (id < 0) return
        val hasPayload = intent.getBooleanExtra(EXTRA_HAS_PAYLOAD, false)

        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reloj:TimerReceiverWakeLock")
                ?.apply {
                    setReferenceCounted(false)
                    acquire(15_000L)
                }
        }.getOrNull()

        var startedSynchronously = false
        val localized = AppLocaleManager.localizedContext(context)
        if (hasPayload) {
            val title = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null }
                ?: localized.getString(R.string.timer_finished)
            AlarmAlertService.startAlert(
                context = context,
                isAlarm = false,
                itemId = id,
                title = title,
                subtitle = localized.getString(R.string.timer_finished),
                soundPreset = intent.getStringExtra(EXTRA_SOUND_PRESET),
                customPath = intent.getStringExtra(EXTRA_CUSTOM_PATH),
                customName = intent.getStringExtra(EXTRA_CUSTOM_NAME),
            )
            startedSynchronously = true
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TimerRepository.get(context)
                val timer = repo.getById(id)

                if (!startedSynchronously) {
                    val title = timer?.label?.ifBlank { null } ?: localized.getString(R.string.timer_finished)
                    AlarmAlertService.startAlert(
                        context = context,
                        isAlarm = false,
                        itemId = id,
                        title = title,
                        subtitle = localized.getString(R.string.timer_finished),
                        soundPreset = timer?.soundPreset,
                        customPath = timer?.customSoundPath,
                        customName = timer?.customSoundName,
                    )
                }

                repo.markFinished(id)
                TimerWidget.updateAll(context)
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.reloj.app.action.TIMER_FINISHED"
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_HAS_PAYLOAD = "has_payload"
        const val EXTRA_LABEL = "timer_label"
        const val EXTRA_SOUND_PRESET = "timer_sound_preset"
        const val EXTRA_CUSTOM_PATH = "timer_custom_path"
        const val EXTRA_CUSTOM_NAME = "timer_custom_name"
    }
}
