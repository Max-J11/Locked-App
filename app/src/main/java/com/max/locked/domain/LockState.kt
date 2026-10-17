package com.max.locked.domain

/** Plain copy of the stored lock row, so domain rules don't depend on Room. */
data class LockState(
    val isLocked: Boolean,
    val activeModeId: Long?,
    val startedAt: Long?,
    val endsAt: Long?,
)
