package com.max.locked.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ModeEntity::class, ModeAppEntity::class, BrickStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LockedDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun modeAppDao(): ModeAppDao
    abstract fun brickStateDao(): BrickStateDao
}
