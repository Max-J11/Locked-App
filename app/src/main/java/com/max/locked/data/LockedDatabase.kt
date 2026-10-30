package com.max.locked.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ModeEntity::class,
        ModeAppEntity::class,
        BrickStateEntity::class,
        PairedKeyEntity::class,
        ScheduleEntity::class,
        ModeSiteEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds paired_key
        AutoMigration(from = 2, to = 3), // adds schedule
        AutoMigration(from = 3, to = 4), // adds brick_state.passSessionStart
        AutoMigration(from = 4, to = 5), // adds mode_site
    ],
)
abstract class LockedDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun modeAppDao(): ModeAppDao
    abstract fun brickStateDao(): BrickStateDao
    abstract fun pairedKeyDao(): PairedKeyDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun modeSiteDao(): ModeSiteDao
}
