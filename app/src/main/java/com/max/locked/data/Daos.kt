package com.max.locked.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ModeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(mode: ModeEntity)
}

@Dao
interface ModeAppDao {
    @Query("SELECT packageName FROM mode_app WHERE modeId = :modeId")
    fun observePackages(modeId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: ModeAppEntity)

    @Query("DELETE FROM mode_app WHERE modeId = :modeId AND packageName = :packageName")
    suspend fun delete(modeId: Long, packageName: String)
}

@Dao
interface BrickStateDao {
    @Query("SELECT * FROM brick_state WHERE id = ${BrickStateEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<BrickStateEntity?>

    @Query("SELECT * FROM brick_state WHERE id = ${BrickStateEntity.SINGLE_ROW_ID}")
    suspend fun get(): BrickStateEntity?

    @Upsert
    suspend fun upsert(state: BrickStateEntity)
}
