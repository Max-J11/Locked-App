package com.max.locked.data

import android.util.Log
import androidx.room.withTransaction
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.COOLDOWN_MILLIS
import com.max.locked.domain.LockState
import com.max.locked.domain.ModeRule
import com.max.locked.domain.SOURCE_TIMER
import com.max.locked.domain.ScheduleRule
import com.max.locked.domain.activePolicy
import com.max.locked.domain.PASSES_PER_LOCK
import com.max.locked.domain.PASS_MILLIS
import com.max.locked.domain.canChangePucks
import com.max.locked.domain.canChangeSchedule
import com.max.locked.domain.canDeleteMode
import com.max.locked.domain.canRemoveApp
import com.max.locked.domain.canRemoveSite
import com.max.locked.domain.normalizeSite
import com.max.locked.domain.canStartCooldown
import com.max.locked.domain.canStopHidingNotifications
import com.max.locked.domain.canUsePass
import com.max.locked.domain.canUnlockWithPuck
import com.max.locked.domain.lockingPuckId
import com.max.locked.domain.puckSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one source of truth. Combines the lock row, schedules and modes into [policy].
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
    private val scheduleDao = db.scheduleDao()
    private val modeSiteDao = db.modeSiteDao()

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

    // --- Policy ---------------------------------------------------------------------------

    /** Everything [activePolicy] needs, kept in memory so enforcers never query Room. */
    private data class PolicyInputs(
        val state: LockState?,
        val schedules: List<ScheduleRule>,
        val modes: Map<Long, ModeRule>,
        val neverBlock: Set<String>,
    )

    private val inputs: StateFlow<PolicyInputs?> =
        combine(
            stateDao.observe(),
            scheduleDao.observeAll(),
            modeDao.observeAll(),
            modeAppDao.observeAll(),
            modeSiteDao.observeAll(),
        ) { state, schedules, modes, apps, sites -> buildInputs(state, schedules, modes, apps, sites) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun buildInputs(
        state: BrickStateEntity?,
        schedules: List<ScheduleEntity>,
        modes: List<ModeEntity>,
        apps: List<ModeAppEntity>,
        sites: List<ModeSiteEntity>,
    ): PolicyInputs {
        val appsByMode = apps.groupBy({ it.modeId }, { it.packageName })
        val sitesByMode = sites.groupBy({ it.modeId }, { it.domain })
        return PolicyInputs(
            state = state?.toLockState(),
            schedules = schedules.map { it.toRule() },
            modes = modes.associate {
                it.id to ModeRule(
                    apps = appsByMode[it.id].orEmpty().toSet(),
                    blockNotifications = it.blockNotifications,
                    sites = sitesByMode[it.id].orEmpty().toSet(),
                )
            },
            neverBlock = neverBlockList.packages(),
        )
    }

    private fun compute(i: PolicyInputs, now: Long): Policy =
        activePolicy(now, ZoneId.systemDefault(), i.state, i.schedules, i.modes, i.neverBlock)

    /**
     * The policy right now, computed from in-memory inputs and the clock. Enforcers use this on
     * every check, so a schedule starting or a timer ending takes effect exactly on time.
     */
    fun currentPolicy(now: Long = System.currentTimeMillis()): Policy =
        inputs.value?.let { compute(it, now) } ?: Policy.UNLOCKED

    /** Reads stored state directly. For taps that may cold-start the process before [inputs] loads. */
    private suspend fun policyFromDb(now: Long): Policy =
        compute(
            buildInputs(stateDao.get(), scheduleDao.getAll(), modeDao.getAll(), modeAppDao.getAll(), modeSiteDao.getAll()),
            now,
        )

    /**
     * Recomputed whenever stored state changes, and again whenever a timer, wait or schedule
     * window starts or ends, so screens and the notification sweep see the change on time.
     */
    val policy: StateFlow<Policy> = inputs
        .filterNotNull()
        .transformLatest { i ->
            while (true) {
                val now = System.currentTimeMillis()
                val p = compute(i, now)
                emit(p)
                val next = p.nextChangeAt(now) ?: break
                delay(next - now + TICK_SLACK_MILLIS)
            }
        }
        .distinctUntilChanged()
        .onEach {
            Log.d(
                TAG,
                "policy: locked=${it.isLocked} manual=${it.manual?.source} schedules=${it.schedules.map { s -> s.scheduleId }} " +
                    "notifs=${it.notificationPackages.size} blocked=${it.blockedPackages}",
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, Policy.UNLOCKED)

    /**
     * The policy from stored state, for "only stricter while locked" checks. Reads Room rather than
     * [currentPolicy], which is empty for a moment after the process starts.
     */
    suspend fun storedPolicy(): Pair<Policy, Long> {
        val now = System.currentTimeMillis()
        return policyFromDb(now) to now
    }

    /** True if anything is locked right now (manual lock or schedule), from stored state. */
    suspend fun isLockedNow(): Boolean = storedPolicy().let { (p, now) -> p.isActive(now) }

    // --- Modes ----------------------------------------------------------------------------

    /** All modes with their app counts, oldest first (Default is always first). */
    val modes: Flow<List<ModeSummary>> = modeDao.observeSummaries()

    fun mode(id: Long): Flow<ModeEntity?> = modeDao.observe(id)

    fun selectedApps(modeId: Long): Flow<Set<String>> =
        modeAppDao.observePackages(modeId).map { it.toSet() }

    suspend fun createMode(name: String): Long {
        val id = modeDao.insert(ModeEntity(name = name.trim()))
        Log.d(TAG, "created mode $id")
        return id
    }

    suspend fun renameMode(id: Long, name: String) {
        modeDao.rename(id, name.trim())
    }

    /**
     * Deletes a mode. Refused for Default and while anything is locked ([canDeleteMode]). Pucks and
     * schedules set to the deleted mode switch to Default. Returns false if refused.
     */
    suspend fun deleteMode(id: Long): Boolean {
        val (p, now) = storedPolicy()
        if (!canDeleteMode(p, id, now)) {
            Log.d(TAG, "refused to delete mode $id")
            return false
        }
        db.withTransaction {
            pairedKeyDao.reassignMode(from = id, to = DEFAULT_MODE_ID)
            scheduleDao.reassignMode(from = id, to = DEFAULT_MODE_ID)
            modeDao.delete(id) // mode_app rows cascade
        }
        Log.d(TAG, "deleted mode $id")
        return true
    }

    /** Turning notification hiding on is always allowed; turning it off is refused while the mode is in use. */
    suspend fun setBlockNotifications(modeId: Long, on: Boolean): Boolean {
        val (p, now) = storedPolicy()
        if (!on && !canStopHidingNotifications(p, modeId, now)) {
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

    /** Ends the manual lock (debug unlock). Running schedules are left alone. */
    suspend fun unlock(source: String) = stateMutex.withLock {
        unlockLocked(source)
        clearCooldownIfNothingLocked(System.currentTimeMillis())
    }

    /** Timed lock from the app. Ends on its own at [endsAt]. Only allowed while nothing is locked. */
    suspend fun lockUntil(endsAt: Long, modeId: Long) = stateMutex.withLock {
        val now = System.currentTimeMillis()
        if (policyFromDb(now).isActive(now) || endsAt <= now) return@withLock
        lockLocked(SOURCE_TIMER, modeId, endsAt)
    }

    /** "Unlock without puck": everything locked now ends after [COOLDOWN_MILLIS]. */
    suspend fun startCooldown() = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val p = policyFromDb(now)
        if (!canStartCooldown(p.isActive(now), p.endsAt, p.cooldownEndsAt, now)) {
            Log.d(TAG, "cooldown not started (not locked, already running, or the lock ends sooner)")
            return@withLock
        }
        val current = stateDao.get() ?: BrickStateEntity()
        stateDao.upsert(current.copy(cooldownEndsAt = now + COOLDOWN_MILLIS))
        Log.d(TAG, "cooldown started")
    }

    /** Stay locked after all. Always allowed: it only makes things stricter. */
    suspend fun cancelCooldown() = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val current = stateDao.get() ?: return@withLock
        // Only a wait that's still counting down. A finished one has already ended its locks.
        if (current.cooldownEndsAt == null || current.cooldownEndsAt <= now) return@withLock
        stateDao.upsert(current.copy(cooldownEndsAt = null))
        Log.d(TAG, "cooldown cancelled")
    }

    /** A pending wait with nothing left to end would wrongly end a later lock, so drop it. */
    private suspend fun clearCooldownIfNothingLocked(now: Long) {
        val current = stateDao.get() ?: return
        val pending = current.cooldownEndsAt?.let { it > now } == true
        if (pending && !policyFromDb(now).isActive(now)) {
            stateDao.upsert(current.copy(cooldownEndsAt = null))
        }
    }

    sealed interface TapResult {
        data object Locked : TapResult
        data object Unlocked : TapResult
        /** This puck ended what it could, but another lock or schedule is still on. */
        data object StillLocked : TapResult
        /** Locked by a different puck, which is the only one that can unlock. */
        data class WrongPuck(val lockingPuckName: String?) : TapResult
        /** Timed locks and schedules: only pucks set to their mode can end them. */
        data class WrongMode(val modeNames: List<String>) : TapResult
    }

    /**
     * Puck tap. If nothing is locked, locks with the puck's mode. Otherwise ends everything this
     * puck is allowed to end: the manual lock if [canUnlockWithPuck] says so, and today's window
     * of any running schedule set to the puck's mode.
     * Reads stored state rather than [policy], because a tap can cold-start the process.
     */
    suspend fun toggleFromKey(key: PairedKeyEntity): TapResult = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val source = puckSource(key.id)
        val p = policyFromDb(now)
        if (!p.isActive(now)) {
            lockLocked(source, key.modeId, endsAt = null)
            return@withLock TapResult.Locked
        }

        val manual = p.manual
        val endManual = manual != null && canUnlockWithPuck(manual.source, key.id, key.modeId, manual.modeId)
        val endSchedules = p.schedules.filter { it.modeId == key.modeId }

        if (!endManual && endSchedules.isEmpty()) {
            Log.d(TAG, "puck ${key.id} can't end anything (manual=${manual?.source}, schedules=${p.schedules})")
            val lockingId = lockingPuckId(manual?.source)
            return@withLock if (lockingId != null) {
                TapResult.WrongPuck(pairedKeyDao.findById(lockingId)?.name)
            } else {
                TapResult.WrongMode(p.activeModeIds.mapNotNull { modeDao.get(it)?.name })
            }
        }

        if (endManual) unlockLocked(source)
        endSchedules.forEach {
            scheduleDao.setSkippedUntil(it.scheduleId, it.endsAt)
            Log.d(TAG, "ended schedule ${it.scheduleId} until ${it.endsAt} (puck ${key.id})")
        }
        clearCooldownIfNothingLocked(now)
        if (policyFromDb(now).isActive(now)) TapResult.StillLocked else TapResult.Unlocked
    }

    private suspend fun lockLocked(source: String, modeId: Long, endsAt: Long?) {
        val current = stateDao.get() ?: BrickStateEntity()
        // cooldownEndsAt is left as is: a finished wait only affects locks that were on before it
        // finished, and clearing it would bring back schedule windows it ended.
        stateDao.upsert(
            current.copy(
                isBricked = true,
                activeModeId = modeId,
                startedAt = System.currentTimeMillis(),
                endsAt = endsAt,
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
                source = source,
            ),
        )
        Log.d(TAG, "unlocked (source=$source)")
    }

    // --- Passes ---------------------------------------------------------------------------

    /**
     * Opens [packageName] for [PASS_MILLIS] without ending the lock. Uses one of the
     * [PASSES_PER_LOCK] passes for this locked stretch. Using a pass for another app replaces the
     * running one. Returns false if refused (not blocked, or none left).
     */
    suspend fun usePass(packageName: String): Boolean = stateMutex.withLock {
        val now = System.currentTimeMillis()
        val p = policyFromDb(now)
        if (!canUsePass(p, packageName, now)) {
            Log.d(TAG, "pass refused for $packageName (left=${p.passesLeft})")
            return@withLock false
        }
        val current = stateDao.get() ?: BrickStateEntity()
        val usedBefore = PASSES_PER_LOCK - p.passesLeft
        stateDao.upsert(
            current.copy(
                passPackage = packageName,
                passEndsAt = now + PASS_MILLIS,
                passesUsed = usedBefore + 1,
                passSessionStart = p.lockedSince,
            ),
        )
        Log.d(TAG, "pass used for $packageName (${p.passesLeft - 1} left)")
        true
    }

    // --- Schedules ------------------------------------------------------------------------

    val schedules: Flow<List<ScheduleEntity>> = scheduleDao.observeAll()

    suspend fun getSchedule(id: Long): ScheduleEntity? = scheduleDao.get(id)

    /** A schedule whose window is running now (and wasn't ended early) can't be changed. */
    private suspend fun isScheduleRunning(id: Long): Boolean {
        val (p, now) = storedPolicy()
        return !canChangeSchedule(p, id, now)
    }

    /** Adds or updates a schedule. Refused for a schedule that's running. Returns false if refused. */
    suspend fun saveSchedule(schedule: ScheduleEntity): Boolean {
        if (schedule.id != 0L && isScheduleRunning(schedule.id)) {
            Log.d(TAG, "refused to edit running schedule ${schedule.id}")
            return false
        }
        if (schedule.id == 0L) {
            val id = scheduleDao.insert(schedule)
            Log.d(TAG, "added schedule $id")
        } else {
            val existing = scheduleDao.get(schedule.id)
            scheduleDao.update(schedule.copy(skippedUntil = existing?.skippedUntil))
            Log.d(TAG, "updated schedule ${schedule.id}")
        }
        return true
    }

    /** Switching on is always allowed; switching off is refused while it's running. */
    suspend fun setScheduleEnabled(id: Long, on: Boolean): Boolean {
        if (!on && isScheduleRunning(id)) {
            Log.d(TAG, "refused to switch off running schedule $id")
            return false
        }
        scheduleDao.setEnabled(id, on)
        return true
    }

    suspend fun deleteSchedule(id: Long): Boolean {
        if (isScheduleRunning(id)) {
            Log.d(TAG, "refused to delete running schedule $id")
            return false
        }
        scheduleDao.delete(id)
        Log.d(TAG, "deleted schedule $id")
        return true
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
        val (p, now) = storedPolicy()
        if (!canChangePucks(p, now)) {
            Log.d(TAG, "refused to delete puck $id while locked")
            return false
        }
        pairedKeyDao.delete(id)
        return true
    }

    /** A puck's name is only a label, so it can be changed even while locked. */
    suspend fun renamePuck(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        pairedKeyDao.rename(id, trimmed)
    }

    /** Which mode a puck locks. Frozen while locked: moving a puck onto a running mode would let it unlock. */
    suspend fun setPuckMode(keyId: Long, modeId: Long): Boolean {
        val (p, now) = storedPolicy()
        if (!canChangePucks(p, now)) {
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

    /** Only stricter while locked: apps can't be removed from a mode in use. Returns false if refused. */
    suspend fun removeApp(modeId: Long, packageName: String): Boolean {
        val (p, now) = storedPolicy()
        if (!canRemoveApp(p, modeId, now)) {
            Log.d(TAG, "refused to remove $packageName from active mode $modeId")
            return false
        }
        modeAppDao.delete(modeId, packageName)
        return true
    }

    // --- Websites -------------------------------------------------------------------------

    fun sites(modeId: Long): Flow<List<String>> = modeSiteDao.observeDomains(modeId)

    /**
     * Adds a website to a mode, cleaned up with [normalizeSite] ("https://www.YouTube.com/x"
     * becomes "youtube.com"). Always allowed. Returns the stored domain, or null if [input]
     * isn't a website address.
     */
    suspend fun addSite(modeId: Long, input: String): String? {
        val domain = normalizeSite(input) ?: return null
        modeSiteDao.insert(ModeSiteEntity(modeId, domain))
        Log.d(TAG, "added site $domain to mode $modeId")
        return domain
    }

    /** Only stricter while locked: sites can't be removed from a mode in use. Returns false if refused. */
    suspend fun removeSite(modeId: Long, domain: String): Boolean {
        val (p, now) = storedPolicy()
        if (!canRemoveSite(p, modeId, now)) {
            Log.d(TAG, "refused to remove $domain from active mode $modeId")
            return false
        }
        modeSiteDao.delete(modeId, domain)
        return true
    }

    private fun BrickStateEntity.toLockState() = LockState(
        isLocked = isBricked,
        activeModeId = activeModeId,
        startedAt = startedAt,
        endsAt = endsAt,
        cooldownEndsAt = cooldownEndsAt,
        source = source,
        passPackage = passPackage,
        passEndsAt = passEndsAt,
        passesUsed = passesUsed,
        passSessionStart = passSessionStart,
    )

    companion object {
        const val DEFAULT_MODE_ID = com.max.locked.domain.DEFAULT_MODE_ID
        private const val TICK_SLACK_MILLIS = 50L
        private const val TAG = "Locked"
    }
}
