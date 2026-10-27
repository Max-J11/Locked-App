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
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds paired_key
        AutoMigration(from = 2, to = 3), // adds schedule
    ],
)
abstract class LockedDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun modeAppDao(): ModeAppDao
    abstract fun brickStateDao(): BrickStateDao
    abstract fun pairedKeyDao(): PairedKeyDao
    abstract fun scheduleDao(): ScheduleDao
}
