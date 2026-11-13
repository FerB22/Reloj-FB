package com.reloj.app.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val durationMillis: Long,
    val state: TimerState = TimerState.IDLE,
    /** Instante absoluto (epoch ms) en que termina; solo con estado RUNNING. */
    val endAtMillis: Long? = null,
    /** Tiempo restante congelado; solo con estado PAUSED. */
    val remainingMillis: Long? = null,
    /** Nombre del preset propio o null para usar el predeterminado global de Ajustes. */
    val soundPreset: String? = null,
    /** Ruta interna privada en `filesDir/custom_sounds/` cuando se importa un audio del teléfono. */
    val customSoundPath: String? = null,
    val customSoundName: String? = null,
)
