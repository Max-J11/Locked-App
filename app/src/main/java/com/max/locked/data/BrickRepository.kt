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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val policy: StateFlow<Policy> = stateFlow
        .flatMapLatest { state ->
            val modeId = state?.activeModeId ?: DEFAULT_MODE_ID
            modeAppDao.observePackages(modeId).map { apps ->
                activePolicy(
                    now = System.currentTimeMillis(),
                    state = state?.toLockState(),
                    modeApps = apps.toSet(),
                    neverBlock = neverBlockList.packages(),
                )
            }
        }
        .onEach { Log.d(TAG, "policy: locked=${it.isLocked} blocked=${it.blockedPackages}") }
        .stateIn(scope, SharingStarted.Eagerly, Policy.UNLOCKED)

    fun neverBlockPackages(): Set<String> = neverBlockList.packages()

    suspend fun lock(source: String) {
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(
            current.copy(
                isBricked = true,
                activeModeId = DEFAULT_MODE_ID,
                startedAt = System.currentTimeMillis(),
                endsAt = null,
                source = source,
            ),
        )
        Log.d(TAG, "locked (source=$source)")
    }

    suspend fun unlock(source: String) {
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
