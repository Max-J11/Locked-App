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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds paired_key
    ],
)
abstract class LockedDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun modeAppDao(): ModeAppDao
    abstract fun brickStateDao(): BrickStateDao
    abstract fun pairedKeyDao(): PairedKeyDao
}
