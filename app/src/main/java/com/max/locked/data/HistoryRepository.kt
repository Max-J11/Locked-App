package com.max.locked.data

import android.os.SystemClock
import android.util.Log
import com.max.locked.di.ApplicationScope
import com.max.locked.domain.PASSES_PER_LOCK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stats history: locked stretches and the times Locked stepped in. Only ever written and read on
 * this phone. Recording follows [BrickRepository.policy]; it never decides whether anything is
 * locked.
 */
@Singleton
class HistoryRepository @Inject constructor(
    db: LockedDatabase,
    private val brickRepository: BrickRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val sessionDao = db.sessionLogDao()
    private val blockEventDao = db.blockEventDao()
    private val mutex = Mutex()

    val sessions: Flow<List<SessionLogEntity>> = sessionDao.observeAll()

    fun blockCountsSince(from: Long): Flow<List<TargetCount>> = blockEventDao.observeCountsSince(from)

    /** Called once from [com.max.locked.LockedApp]. */
    fun start() {
        scope.launch {
            brickRepository.awaitLoaded()
            // Each emission means something changed; read the policy fresh for "now".
            brickRepository.policy.collect { sync() }
        }
    }

    /** Opens, updates or closes the session row so it matches the lock right now. */
    private suspend fun sync() = mutex.withLock {
        val now = System.currentTimeMillis()
        val p = brickRepository.currentPolicy(now)
        val open = sessionDao.getOpen()

        if (!p.isActive(now)) {
            if (open != null) close(open, now)
            return@withLock
        }

        val since = p.lockedSince ?: now
        val expectedEnd = listOfNotNull(p.endsAt, p.cooldownEndsAt).minOrNull()
        val passesUsed = PASSES_PER_LOCK - p.passesLeft
        val current = if (open != null && open.expectedEnd != null && open.expectedEnd <= since) {
            // The app wasn't running when that one ended, and another lock has started since.
            close(open, now)
            null
        } else {
            open
        }

        if (current == null) {
            // Never start before the last one ended, so no time is counted twice.
            val start = maxOf(since, sessionDao.latestEnd() ?: 0L)
            sessionDao.insert(
                SessionLogEntity(
                    startedAt = start,
                    expectedEnd = expectedEnd,
                    modeId = p.modeId ?: BrickRepository.DEFAULT_MODE_ID,
                    passesUsed = passesUsed,
                ),
            )
            Log.d(TAG, "history: session started at $start")
        } else if (current.expectedEnd != expectedEnd || passesUsed > current.passesUsed) {
            sessionDao.update(
                current.copy(expectedEnd = expectedEnd, passesUsed = maxOf(current.passesUsed, passesUsed)),
            )
        }
    }

    /** Ends [session]: now, or when it was due to end if the app wasn't running then. */
    private suspend fun close(session: SessionLogEntity, now: Long) {
        val end = minOf(now, session.expectedEnd ?: now).coerceAtLeast(session.startedAt)
        sessionDao.update(session.copy(endedAt = end))
        Log.d(TAG, "history: session ${session.id} ended at $end")
    }

    private var lastTarget: String? = null
    private var lastTargetAtMs = 0L

    /**
     * Notes that Locked stepped in on an app or site. Called from the accessibility service: this
     * never reads Room, and the write happens in the background. Repeats within [REPEAT_MS] count once.
     */
    fun recordBlock(target: String, isSite: Boolean) {
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (target == lastTarget && nowMs - lastTargetAtMs < REPEAT_MS) return
            lastTarget = target
            lastTargetAtMs = nowMs
        }
        val at = System.currentTimeMillis()
        scope.launch { blockEventDao.insert(BlockEventEntity(at = at, target = target, isSite = isSite)) }
    }

    /** Deletes stats history. Modes, pucks, schedules and the lock itself are untouched. */
    suspend fun clear() = mutex.withLock {
        sessionDao.deleteFinished()
        blockEventDao.deleteAll()
        Log.d(TAG, "history cleared")
    }

    private companion object {
        const val TAG = "Locked"
        const val REPEAT_MS = 5_000L
    }
}
