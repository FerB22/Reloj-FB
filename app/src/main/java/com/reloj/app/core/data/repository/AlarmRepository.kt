package com.reloj.app.core.data.repository

import android.content.Context
import com.reloj.app.core.alarm.AlarmAlertService
import com.reloj.app.core.alarm.AlarmScheduler
import com.reloj.app.core.data.local.AlarmEntity
import com.reloj.app.core.data.local.AlarmRepeatUtils
import com.reloj.app.core.data.local.AppDatabase
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.feature.widget.ClockWidget
import kotlinx.coroutines.flow.Flow

class AlarmRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).alarmDao()
    private val scheduler = AlarmScheduler(appContext)

    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun getAll(): List<AlarmEntity> = dao.getAll()

    suspend fun getById(id: Long): AlarmEntity? = dao.getById(id)

    suspend fun create(
        hour: Int,
        minute: Int,
        label: String,
        repeatDaysMask: Int = 0,
        soundPreset: String? = null,
        customSoundPath: String? = null,
        customSoundName: String? = null,
    ): Long {
        val alarm = AlarmEntity(
            hour = hour,
            minute = minute,
            label = label,
            enabled = true,
            repeatDaysMask = repeatDaysMask,
            soundPreset = soundPreset,
            customSoundPath = customSoundPath,
            customSoundName = customSoundName,
        )
        val id = dao.insert(alarm)
        scheduler.scheduleClockAlarm(
            alarm.copy(id = id),
            AlarmRepeatUtils.nextTriggerMillis(hour, minute, repeatDaysMask),
        )
        ClockWidget.updateAll(appContext)
        return id
    }

    suspend fun update(
        id: Long,
        hour: Int,
        minute: Int,
        label: String,
        repeatDaysMask: Int,
        soundPreset: String?,
        customSoundPath: String?,
        customSoundName: String?,
    ) {
        val current = dao.getById(id) ?: return
        val updated = current.copy(
            hour = hour,
            minute = minute,
            label = label,
            repeatDaysMask = repeatDaysMask,
            soundPreset = soundPreset,
            customSoundPath = customSoundPath,
            customSoundName = customSoundName,
        )
        dao.update(updated)
        if (updated.enabled) {
            scheduler.scheduleClockAlarm(
                updated,
                AlarmRepeatUtils.nextTriggerMillis(hour, minute, repeatDaysMask),
            )
        }
        CustomSoundManager.pruneUnusedSounds(appContext)
        ClockWidget.updateAll(appContext)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = dao.getById(id) ?: return
        val updated = alarm.copy(enabled = enabled)
        dao.update(updated)
        if (enabled) {
            scheduler.scheduleClockAlarm(
                updated,
                AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
            )
        } else {
            scheduler.cancelClockAlarm(id)
            AlarmAlertService.stopIfMatching(appContext, isAlarm = true, itemId = id)
        }
        ClockWidget.updateAll(appContext)
    }

    /** Desactiva una alarma de una sola vez tras dispararse, sin detener la alerta recién iniciada. */
    suspend fun markTriggeredOneShot(id: Long) {
        val alarm = dao.getById(id) ?: return
        if (!alarm.enabled) return
        dao.update(alarm.copy(enabled = false))
        ClockWidget.updateAll(appContext)
    }

    suspend fun rescheduleSingle(id: Long) {
        val alarm = dao.getById(id) ?: return
        if (!alarm.enabled) return
        scheduler.scheduleClockAlarm(
            alarm,
            AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
        )
        ClockWidget.updateAll(appContext)
    }

    suspend fun delete(alarm: AlarmEntity) {
        scheduler.cancelClockAlarm(alarm.id)
        AlarmAlertService.stopIfMatching(appContext, isAlarm = true, itemId = alarm.id)
        dao.delete(alarm)
        CustomSoundManager.pruneUnusedSounds(appContext)
        ClockWidget.updateAll(appContext)
    }

    suspend fun rescheduleEnabled() {
        for (alarm in dao.getAll()) {
            if (alarm.enabled) {
                scheduler.scheduleClockAlarm(
                    alarm,
                    AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
                )
            }
        }
        ClockWidget.updateAll(appContext)
    }

    companion object {
        @Volatile private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
