package com.max.locked.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ModeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(mode: ModeEntity)

    @Query("SELECT * FROM mode WHERE id = :id")
    fun observe(id: Long): Flow<ModeEntity?>

    @Query("SELECT * FROM mode WHERE id = :id")
    suspend fun get(id: Long): ModeEntity?

    @Query("SELECT * FROM mode")
    fun observeAll(): Flow<List<ModeEntity>>

    @Query("SELECT * FROM mode")
    suspend fun getAll(): List<ModeEntity>

    @Query("UPDATE mode SET blockNotifications = :on WHERE id = :id")
    suspend fun setBlockNotifications(id: Long, on: Boolean)

    @Query(
        "SELECT m.id, m.name, m.blockNotifications, " +
            "(SELECT COUNT(*) FROM mode_app a WHERE a.modeId = m.id) AS appCount, " +
            "(SELECT COUNT(*) FROM mode_site s WHERE s.modeId = m.id) AS siteCount " +
            "FROM mode m ORDER BY m.id",
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

    @Query("SELECT * FROM mode_app")
    fun observeAll(): Flow<List<ModeAppEntity>>

    @Query("SELECT * FROM mode_app")
    suspend fun getAll(): List<ModeAppEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: ModeAppEntity)

    @Query("DELETE FROM mode_app WHERE modeId = :modeId AND packageName = :packageName")
    suspend fun delete(modeId: Long, packageName: String)
}

@Dao
interface ModeSiteDao {
    @Query("SELECT domain FROM mode_site WHERE modeId = :modeId ORDER BY domain")
    fun observeDomains(modeId: Long): Flow<List<String>>

    @Query("SELECT * FROM mode_site")
    fun observeAll(): Flow<List<ModeSiteEntity>>

    @Query("SELECT * FROM mode_site")
    suspend fun getAll(): List<ModeSiteEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: ModeSiteEntity)

    @Query("DELETE FROM mode_site WHERE modeId = :modeId AND domain = :domain")
    suspend fun delete(modeId: Long, domain: String)
}

@Dao
interface PairedKeyDao {
    @Query("SELECT * FROM paired_key ORDER BY createdAt")
    fun observeAll(): Flow<List<PairedKeyEntity>>

    @Query("SELECT * FROM paired_key WHERE id = :id")
    suspend fun findById(id: Long): PairedKeyEntity?

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

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY startMinute, id")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun get(id: Long): ScheduleEntity?

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("UPDATE schedule SET enabled = :on WHERE id = :id")
    suspend fun setEnabled(id: Long, on: Boolean)

    @Query("UPDATE schedule SET skippedUntil = :until WHERE id = :id")
    suspend fun setSkippedUntil(id: Long, until: Long?)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE schedule SET modeId = :to WHERE modeId = :from")
    suspend fun reassignMode(from: Long, to: Long)
}
