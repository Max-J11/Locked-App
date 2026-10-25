package com.max.locked.data

import android.util.Log
import androidx.room.withTransaction
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.COOLDOWN_MILLIS
import com.max.locked.domain.LockState
import com.max.locked.domain.SOURCE_TIMER
import com.max.locked.domain.activePolicy
import com.max.locked.domain.canStartCooldown
import com.max.locked.domain.canUnlockWithPuck
import com.max.locked.domain.lockingPuckId
import com.max.locked.domain.puckSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one source of truth. Combines the lock row and the active mode's apps into [policy].
 * All state changes go through here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BrickRepository @Inject constructor(
    private val db: LockedDatabase,
    private val neverBlockList: NeverBlockList,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val modeDao = db.modeDao()
    private val modeAppDao = db.modeAppDao()
    private val stateDao = db.brickStateDao()
    private val pairedKeyDao = db.pairedKeyDao()

    /** Serializes lock/unlock so two quick taps can't race. */
    private val stateMutex = Mutex()

    init {
        // Make sure the default mode and the single state row exist (first launch, or after a
        // destructive migration during development).
        scope.launch {
            modeDao.insertIfMissing(ModeEntity(id = DEFAULT_MODE_ID, name = "Default"))
            if (stateDao.get() == null) stateDao.upsert(BrickStateEntity())
        }
    }

    private val stateFlow = stateDao.observe()

    // --- Modes ----------------------------------------------------------------------------

    /** All modes with their app counts, oldest first (Default is always first). */
    val modes: Flow<List<ModeSummary>> = modeDao.observeSummaries()

    fun mode(id: Long): Flow<ModeEntity?> = modeDao.observe(id)

    fun selectedApps(modeId: Long): Flow<Set<String>> =
        modeAppDao.observePackages(modeId).map { it.toSet() }

    /** True while a lock is in effect using this mode. Such a mode can only get stricter. */
    fun isModeActive(modeId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val p = policy.value
        return p.isActive(now) && p.modeId == modeId
    }

    suspend fun createMode(name: String): Long {
        val id = modeDao.insert(ModeEntity(name = name.trim()))
        Log.d(TAG, "created mode $id")
        return id
    }

    suspend fun renameMode(id: Long, name: String) {
        modeDao.rename(id, name.trim())
    }

    /**
     * Deletes a mode. The Default mode and the mode currently in use can't be deleted.
     * Pucks set to the deleted mode switch to Default. Returns false if refused.
     */
    suspend fun deleteMode(id: Long): Boolean {
        if (id == DEFAULT_MODE_ID || isModeActive(id)) {
            Log.d(TAG, "refused to delete mode $id")
            return false
        }
        db.withTransaction {
            pairedKeyDao.reassignMode(from = id, to = DEFAULT_MODE_ID)
            modeDao.delete(id) // mode_app rows cascade
        }
        Log.d(TAG, "deleted mode $id")
        return true
    }

    /**
     * Recomputed whenever stored state changes, and again at the moment a timer or cooldown ends,
     * so screens and the notification sweep see the change on time. Enforcers also check the
     * clock on every read, so a late tick (deep sleep) never leaves anything blocked too long.
     */
    val policy: StateFlow<Policy> = stateFlow
        .flatMapLatest { state ->
            val modeId = state?.activeModeId ?: DEFAULT_MODE_ID
            combine(modeDao.observe(modeId), modeAppDao.observePackages(modeId)) { mode, apps ->
                { now: Long ->
                    activePolicy(
                        now = now,
                        state = state?.toLockState(),
                        modeApps = apps.toSet(),
                        neverBlock = neverBlockList.packages(),
                        modeBlocksNotifications = mode?.blockNotifications == true,
                    )
                }
            }
        }
        .transformLatest { compute ->
            while (true) {
                val now = System.currentTimeMillis()
                val p = compute(now)
                emit(p)
                val next = p.nextChangeAt(now) ?: break
                delay(next - now + TICK_SLACK_MILLIS)
            }
        }
        .distinctUntilChanged()
        .onEach {
            Log.d(TAG, "policy: locked=${it.isLocked} notifs=${it.blockNotifications} blocked=${it.blockedPackages}")
        }
        .stateIn(scope, SharingStarted.Eagerly, Policy.UNLOCKED)

    /** Turning notification hiding on is always allowed; turning it off is refused while the mode is in use. */
    suspend fun setBlockNotifications(modeId: Long, on: Boolean): Boolean {
        if (!on && isModeActive(modeId)) {
            Log.d(TAG, "refused to turn off notification hiding for active mode $modeId")
            return false
        }
        modeDao.setBlockNotifications(modeId, on)
        return true
    }

    fun neverBlockPackages(): Set<String> = neverBlockList.packages()

    // --- Lock state -----------------------------------------------------------------------

    suspend fun lock(source: String, modeId: Long = DEFAULT_MODE_ID) = stateMutex.withLock {
        lockLocked(source, modeId, endsAt = null)
    }

    suspend fun unlock(source: String) = stateMutex.withLock {
        unlockLocked(source)
    }

    /** Timed lock from the app. Ends on its own at [endsAt]; any puck can end it early. */
    suspend fun lockUntil(endsAt: Long, modeId: Long) = stateMutex.withLock {
        val now = System.currentTimeMillis()
        if (isLockedNow(now) || endsAt <= now) return@withLock
        lockLocked(SOURCE_TIMER, modeId, endsAt)
    }

    /** "Unlock without puck": the lock ends after [COOLDOWN_MILLIS]. Does nothing if not locked. */
    suspend fun startCooldown() = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val current = stateDao.get() ?: return@withLock
        if (!canStartCooldown(isLockedNow(now), current.endsAt, current.cooldownEndsAt, now)) {
            Log.d(TAG, "cooldown not started (not locked, already running, or the lock ends sooner)")
            return@withLock
        }
        stateDao.upsert(current.copy(cooldownEndsAt = now + COOLDOWN_MILLIS))
        Log.d(TAG, "cooldown started")
    }

    /** Stay locked after all. Always allowed: it only makes things stricter. */
    suspend fun cancelCooldown() = stateMutex.withLock {
        val current = stateDao.get() ?: return@withLock
        if (!isLockedNow(System.currentTimeMillis())) return@withLock
        stateDao.upsert(current.copy(cooldownEndsAt = null))
        Log.d(TAG, "cooldown cancelled")
    }

    sealed interface TapResult {
        data object Locked : TapResult
        data object Unlocked : TapResult
        /** Locked by a different puck, which is the only one that can unlock. */
        data class WrongPuck(val lockingPuckName: String?) : TapResult
        /** A timed lock: only pucks set to its mode can end it early. */
        data class WrongMode(val modeName: String?) : TapResult
    }

    /**
     * Puck tap: lock with the key's mode if unlocked, otherwise unlock (if this puck is allowed).
     * Reads stored state rather than [policy], because a tap can cold-start the process before
     * the StateFlow has its first value.
     */
    suspend fun toggleFromKey(key: PairedKeyEntity): TapResult = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val source = puckSource(key.id)
        if (!isLockedNow(now)) {
            lockLocked(source, key.modeId, endsAt = null)
            return@withLock TapResult.Locked
        }
        val current = stateDao.get()
        val lockSource = current?.source
        val activeModeId = current?.activeModeId
        if (!canUnlockWithPuck(lockSource, key.id, key.modeId, activeModeId)) {
            Log.d(TAG, "puck ${key.id} can't unlock a lock started by $lockSource (mode $activeModeId)")
            val lockingId = lockingPuckId(lockSource)
            return@withLock if (lockingId != null) {
                TapResult.WrongPuck(pairedKeyDao.findById(lockingId)?.name)
            } else {
                TapResult.WrongMode(activeModeId?.let { modeDao.get(it)?.name })
            }
        }
        unlockLocked(source)
        TapResult.Unlocked
    }

    private suspend fun isLockedNow(now: Long): Boolean =
        activePolicy(now, stateDao.get()?.toLockState(), emptySet(), emptySet()).isActive(now)

    private suspend fun lockLocked(source: String, modeId: Long, endsAt: Long?) {
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(
            current.copy(
                isBricked = true,
                activeModeId = modeId,
                startedAt = System.currentTimeMillis(),
                endsAt = endsAt,
                cooldownEndsAt = null,
                source = source,
            ),
        )
        Log.d(TAG, "locked (source=$source, mode=$modeId, endsAt=$endsAt)")
    }

    private suspend fun unlockLocked(source: String) {
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(
            current.copy(
                isBricked = false,
                activeModeId = null,
                startedAt = null,
                endsAt = null,
                cooldownEndsAt = null,
                source = source,
            ),
        )
        Log.d(TAG, "unlocked (source=$source)")
    }

    // --- Pucks ----------------------------------------------------------------------------

    val pairedKeys: Flow<List<PairedKeyEntity>> = pairedKeyDao.observeAll()

    suspend fun findKeyByUid(tagUid: String): PairedKeyEntity? = pairedKeyDao.findByUid(tagUid)

    /** Saves a newly paired puck. Re-pairing a known tag replaces its secret and name. */
    suspend fun savePairedKey(name: String, tagUid: String, secretHash: String) {
        val existing = pairedKeyDao.findByUid(tagUid)
        val key = existing?.copy(name = name, secretHash = secretHash)
            ?: PairedKeyEntity(
                name = name,
                tagUid = tagUid,
                secretHash = secretHash,
                modeId = DEFAULT_MODE_ID,
                createdAt = System.currentTimeMillis(),
            )
        pairedKeyDao.upsert(key)
        Log.d(TAG, if (existing != null) "re-paired puck ${existing.id}" else "paired new puck")
    }

    /** Pucks can't be deleted while locked (settings only get stricter). Returns false if refused. */
    suspend fun deletePairedKey(id: Long): Boolean {
        if (policy.value.isActive(System.currentTimeMillis())) {
            Log.d(TAG, "refused to delete puck $id while locked")
            return false
        }
        pairedKeyDao.delete(id)
        return true
    }

    /** Which mode a puck locks. Takes effect from its next tap, so it's allowed while locked. */
    suspend fun setPuckMode(keyId: Long, modeId: Long): Boolean {
        // Frozen while locked: moving a puck onto the active mode would let it unlock a timed lock.
        if (policy.value.isActive(System.currentTimeMillis())) {
            Log.d(TAG, "refused to change puck $keyId's mode while locked")
            return false
        }
        pairedKeyDao.setMode(keyId, modeId)
        return true
    }

    // --- Apps -----------------------------------------------------------------------------

    suspend fun addApp(modeId: Long, packageName: String) {
        modeAppDao.insert(ModeAppEntity(modeId, packageName))
    }

    /** Only stricter while locked: apps can't be removed from the mode in use. Returns false if refused. */
    suspend fun removeApp(modeId: Long, packageName: String): Boolean {
        if (isModeActive(modeId)) {
            Log.d(TAG, "refused to remove $packageName from active mode $modeId")
            return false
        }
        modeAppDao.delete(modeId, packageName)
        return true
    }

    private fun BrickStateEntity.toLockState() = LockState(
        isLocked = isBricked,
        activeModeId = activeModeId,
        startedAt = startedAt,
        endsAt = endsAt,
        cooldownEndsAt = cooldownEndsAt,
        source = source,
    )

    companion object {
        const val DEFAULT_MODE_ID = 1L
        private const val TICK_SLACK_MILLIS = 50L
        private const val TAG = "Locked"
    }
}
