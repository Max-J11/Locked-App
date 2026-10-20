package com.max.locked.data

import android.util.Log
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.LockState
import com.max.locked.domain.activePolicy
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
    db: LockedDatabase,
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

    /** Apps chosen for the mode the picker edits. One mode until Milestone 6. */
    val selectedApps: Flow<Set<String>> =
        modeAppDao.observePackages(DEFAULT_MODE_ID).map { it.toSet() }

    /** "Also hide notifications" for the mode the picker edits. */
    val blockNotificationsSetting: Flow<Boolean> =
        modeDao.observe(DEFAULT_MODE_ID).map { it?.blockNotifications == true }

    val policy: StateFlow<Policy> = stateFlow
        .flatMapLatest { state ->
            val modeId = state?.activeModeId ?: DEFAULT_MODE_ID
            combine(modeDao.observe(modeId), modeAppDao.observePackages(modeId)) { mode, apps ->
                activePolicy(
                    now = System.currentTimeMillis(),
                    state = state?.toLockState(),
                    modeApps = apps.toSet(),
                    neverBlock = neverBlockList.packages(),
                    modeBlocksNotifications = mode?.blockNotifications == true,
                )
            }
        }
        .onEach {
            Log.d(TAG, "policy: locked=${it.isLocked} notifs=${it.blockNotifications} blocked=${it.blockedPackages}")
        }
        .stateIn(scope, SharingStarted.Eagerly, Policy.UNLOCKED)

    /** Turning notification hiding on is always allowed; turning it off is refused while locked. */
    suspend fun setBlockNotifications(on: Boolean): Boolean {
        if (!on && policy.value.isActive(System.currentTimeMillis())) {
            Log.d(TAG, "refused to turn off notification hiding while locked")
            return false
        }
        modeDao.setBlockNotifications(DEFAULT_MODE_ID, on)
        return true
    }

    fun neverBlockPackages(): Set<String> = neverBlockList.packages()

    // --- Lock state -----------------------------------------------------------------------

    suspend fun lock(source: String, modeId: Long = DEFAULT_MODE_ID) = stateMutex.withLock {
        lockLocked(source, modeId)
    }

    suspend fun unlock(source: String) = stateMutex.withLock {
        unlockLocked(source)
    }

    /**
     * Puck tap: lock with the key's mode if unlocked, otherwise unlock.
     * Reads stored state rather than [policy], because a tap can cold-start the process before
     * the StateFlow has its first value. Returns true if now locked.
     */
    suspend fun toggleFromKey(key: PairedKeyEntity): Boolean = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val lockedNow = activePolicy(now, stateDao.get()?.toLockState(), emptySet(), emptySet())
            .isActive(now)
        val source = "puck:${key.id}"
        if (lockedNow) unlockLocked(source) else lockLocked(source, key.modeId)
        !lockedNow
    }

    private suspend fun lockLocked(source: String, modeId: Long) {
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(
            current.copy(
                isBricked = true,
                activeModeId = modeId,
                startedAt = System.currentTimeMillis(),
                endsAt = null,
                source = source,
            ),
        )
        Log.d(TAG, "locked (source=$source, mode=$modeId)")
    }

    private suspend fun unlockLocked(source: String) {
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(
            current.copy(
                isBricked = false,
                activeModeId = null,
                startedAt = null,
                endsAt = null,
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

    // --- Apps -----------------------------------------------------------------------------

    suspend fun addApp(packageName: String) {
        modeAppDao.insert(ModeAppEntity(DEFAULT_MODE_ID, packageName))
    }

    suspend fun removeApp(packageName: String) {
        // "Only stricter while locked" is enforced in Milestone 7.
        modeAppDao.delete(DEFAULT_MODE_ID, packageName)
    }

    private fun BrickStateEntity.toLockState() = LockState(
        isLocked = isBricked,
        activeModeId = activeModeId,
        startedAt = startedAt,
        endsAt = endsAt,
    )

    companion object {
        const val DEFAULT_MODE_ID = 1L
        private const val TAG = "Locked"
    }
}
