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

    @Query("SELECT * FROM mode WHERE id = :id")
    fun observe(id: Long): Flow<ModeEntity?>

    @Query("UPDATE mode SET blockNotifications = :on WHERE id = :id")
    suspend fun setBlockNotifications(id: Long, on: Boolean)

    @Query(
        "SELECT m.id, m.name, m.blockNotifications, COUNT(a.packageName) AS appCount " +
            "FROM mode m LEFT JOIN mode_app a ON a.modeId = m.id GROUP BY m.id ORDER BY m.id",
    )
    fun observeSummaries(): Flow<List<ModeSummary>>

    @Insert
    suspend fun insert(mode: ModeEntity): Long

    @Query("UPDATE mode SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM mode WHERE id = :id")
    suspend fun delete(id: Long)
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
interface PairedKeyDao {
    @Query("SELECT * FROM paired_key ORDER BY createdAt")
    fun observeAll(): Flow<List<PairedKeyEntity>>

    @Query("SELECT * FROM paired_key WHERE tagUid = :tagUid LIMIT 1")
    suspend fun findByUid(tagUid: String): PairedKeyEntity?

    @Upsert
    suspend fun upsert(key: PairedKeyEntity)

    @Query("DELETE FROM paired_key WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE paired_key SET modeId = :modeId WHERE id = :id")
    suspend fun setMode(id: Long, modeId: Long)

    @Query("UPDATE paired_key SET modeId = :to WHERE modeId = :from")
    suspend fun reassignMode(from: Long, to: Long)
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
