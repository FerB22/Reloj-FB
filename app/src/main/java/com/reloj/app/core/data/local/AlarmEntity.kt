package com.reloj.app.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    /** Máscara semanal de repetición (bits 0..6 = L..D; 0 = una sola vez). */
    val repeatDaysMask: Int = 0,
    /** Nombre del preset propio o null para usar el predeterminado global de Ajustes. */
    val soundPreset: String? = null,
    /** Ruta interna privada en `filesDir/custom_sounds/` cuando se importa un audio del teléfono. */
    val customSoundPath: String? = null,
    val customSoundName: String? = null,
)
