package com.reloj.app.core.data.repository

import android.content.Context
import com.reloj.app.core.alarm.AlarmAlertService
import com.reloj.app.core.alarm.AlarmScheduler
import com.reloj.app.core.alarm.NotificationHelper
import com.reloj.app.core.data.local.AppDatabase
import com.reloj.app.core.data.local.CustomSoundManager
import com.reloj.app.core.data.local.TimerEntity
import com.reloj.app.core.data.local.TimerState
import kotlinx.coroutines.flow.Flow

/** Punto único de acceso a los temporizadores, compartido por la app y el widget. */
class TimerRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).timerDao()
    private val scheduler = AlarmScheduler(appContext)

    fun observeAll(): Flow<List<TimerEntity>> = dao.observeAll()

    suspend fun getAll(): List<TimerEntity> = dao.getAll()

    suspend fun getById(id: Long): TimerEntity? = dao.getById(id)

    suspend fun create(
        label: String,
        durationMillis: Long,
        soundPreset: String? = null,
        customSoundPath: String? = null,
        customSoundName: String? = null,
    ): Long =
        dao.insert(
            TimerEntity(
                label = label,
                durationMillis = durationMillis,
                soundPreset = soundPreset,
                customSoundPath = customSoundPath,
                customSoundName = customSoundName,
            ),
        )

    suspend fun update(
        id: Long,
        label: String,
        durationMillis: Long,
        soundPreset: String?,
        customSoundPath: String?,
        customSoundName: String?,
    ) {
        val current = dao.getById(id) ?: return
        val durationChanged = current.durationMillis != durationMillis
        val updated = if (durationChanged) {
            scheduler.cancel(id)
            current.copy(
                label = label,
                durationMillis = durationMillis,
                state = TimerState.IDLE,
                endAtMillis = null,
                remainingMillis = null,
                soundPreset = soundPreset,
                customSoundPath = customSoundPath,
                customSoundName = customSoundName,
            )
        } else {
            current.copy(
                label = label,
                soundPreset = soundPreset,
                customSoundPath = customSoundPath,
                customSoundName = customSoundName,
            )
        }
        dao.update(updated)
        CustomSoundManager.pruneUnusedSounds(appContext)
        syncNotifications()
    }

    suspend fun delete(timer: TimerEntity) {
        scheduler.cancel(timer.id)
        AlarmAlertService.stopIfMatching(appContext, isAlarm = false, itemId = timer.id)
        dao.delete(timer)
        CustomSoundManager.pruneUnusedSounds(appContext)
        syncNotifications()
    }

    suspend fun start(id: Long, now: Long = System.currentTimeMillis()) {
        val timer = dao.getById(id) ?: return
        val remaining = timer.remainingMillis ?: timer.durationMillis
        val endAt = now + remaining
        val updated = timer.copy(
            state = TimerState.RUNNING,
            endAtMillis = endAt,
            remainingMillis = null,
        )
        dao.update(updated)
        scheduler.schedule(updated, endAt)
        syncNotifications(now)
    }

    suspend fun pause(id: Long, now: Long = System.currentTimeMillis()) {
        val timer = dao.getById(id) ?: return
        val endAt = timer.endAtMillis ?: return
        scheduler.cancel(id)
        dao.update(
            timer.copy(
                state = TimerState.PAUSED,
                endAtMillis = null,
                remainingMillis = (endAt - now).coerceAtLeast(0),
            ),
        )
        syncNotifications(now)
    }

    suspend fun reset(id: Long) {
        val timer = dao.getById(id) ?: return
        scheduler.cancel(id)
        AlarmAlertService.stopIfMatching(appContext, isAlarm = false, itemId = id)
        dao.update(
            timer.copy(state = TimerState.IDLE, endAtMillis = null, remainingMillis = null),
        )
        syncNotifications()
    }

    suspend fun extendSnooze(id: Long, extraMillis: Long = 60_000L, now: Long = System.currentTimeMillis()) {
        val timer = dao.getById(id) ?: return
        val endAt = now + extraMillis
        val updated = timer.copy(
            state = TimerState.RUNNING,
            endAtMillis = endAt,
            remainingMillis = null,
        )
        dao.update(updated)
        scheduler.schedule(updated, endAt)
        syncNotifications(now)
    }

    suspend fun addMinute(id: Long, extraMillis: Long = 60_000L, now: Long = System.currentTimeMillis()) {
        val timer = dao.getById(id) ?: return
        when (timer.state) {
            TimerState.RUNNING -> {
                val baseEnd = maxOf(timer.endAtMillis ?: now, now)
                val newEnd = baseEnd + extraMillis
                val updated = timer.copy(endAtMillis = newEnd)
                dao.update(updated)
                scheduler.schedule(updated, newEnd)
                syncNotifications(now)
            }
            TimerState.PAUSED -> {
                val newRemaining = (timer.remainingMillis ?: timer.durationMillis) + extraMillis
                dao.update(timer.copy(remainingMillis = newRemaining))
                syncNotifications(now)
            }
            else -> Unit
        }
    }

    suspend fun markFinished(id: Long) {
        val timer = dao.getById(id) ?: return
        dao.update(
            timer.copy(state = TimerState.FINISHED, endAtMillis = null, remainingMillis = null),
        )
        syncNotifications()
    }

    /** Reprograma las alarmas tras reiniciar el teléfono o actualizar la app. */
    suspend fun rescheduleRunning(now: Long = System.currentTimeMillis()) {
        for (timer in dao.getAll()) {
            val endAt = timer.endAtMillis ?: continue
            if (timer.state != TimerState.RUNNING) continue
            if (endAt <= now) markFinished(timer.id) else scheduler.schedule(timer, endAt)
        }
        syncNotifications(now)
    }

    suspend fun syncNotifications(now: Long = System.currentTimeMillis()) {
        NotificationHelper.syncRunningTimerNotifications(appContext, dao.getAll(), now)
    }

    companion object {
        @Volatile private var instance: TimerRepository? = null

        fun get(context: Context): TimerRepository =
            instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
    }
}
