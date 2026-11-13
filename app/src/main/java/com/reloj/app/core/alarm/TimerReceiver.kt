package com.reloj.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reloj.app.R
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

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TimerRepository.get(context)
                val timer = repo.getById(id)
                repo.markFinished(id)
                TimerWidget.updateAll(context)

                val title = timer?.label?.ifBlank { null } ?: context.getString(R.string.timer_finished)
                AlarmAlertService.startAlert(
                    context = context,
                    isAlarm = false,
                    itemId = id,
                    title = title,
                    subtitle = context.getString(R.string.timer_finished),
                    soundPreset = timer?.soundPreset,
                    customPath = timer?.customSoundPath,
                    customName = timer?.customSoundName,
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.reloj.app.action.TIMER_FINISHED"
        const val EXTRA_TIMER_ID = "timer_id"
    }
}
