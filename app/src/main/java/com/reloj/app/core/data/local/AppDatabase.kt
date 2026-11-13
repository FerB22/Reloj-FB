package com.reloj.app.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TimerEntity::class, AlarmEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timerDao(): TimerDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarms` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `soundPreset` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `customSoundPath` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `customSoundName` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `soundPreset` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `customSoundPath` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `customSoundName` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `repeatDaysMask` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reloj.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
