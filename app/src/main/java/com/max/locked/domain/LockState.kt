package com.max.locked.domain

/** Plain copy of the stored lock row, so domain rules don't depend on Room. */
data class LockState(
    val isLocked: Boolean,
    val activeModeId: Long?,
    val startedAt: Long?,
    val endsAt: Long?,
    /** "Unlock without puck" was started; the lock ends at this time. */
    val cooldownEndsAt: Long? = null,
    /** What started the lock, e.g. "puck:3" or "timer". */
    val source: String? = null,
    /** The app (package) or site ("site:<domain>") a pass is open for, until [passEndsAt]. */
    val passPackage: String? = null,
    val passEndsAt: Long? = null,
    /** Passes used in the locked stretch that began at [passSessionStart]. */
    val passesUsed: Int = 0,
    val passSessionStart: Long? = null,
)
