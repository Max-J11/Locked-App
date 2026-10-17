package com.max.locked.data

/**
 * The one immutable answer to "what should be blocked right now?".
 *
 * Built by [com.max.locked.domain.activePolicy] and exposed by [BrickRepository] as a StateFlow.
 * Enforcers only read it. [endsAt] is checked at read time so a timed lock ends on the clock,
 * not on an alarm.
 */
data class Policy(
    val isLocked: Boolean,
    val modeId: Long?,
    val startedAt: Long?,
    val endsAt: Long?,
    val blockedPackages: Set<String>,
) {
    fun isActive(now: Long): Boolean = isLocked && (endsAt == null || now < endsAt)

    fun isBlocked(packageName: String, now: Long): Boolean =
        isActive(now) && packageName in blockedPackages

    companion object {
        val UNLOCKED = Policy(
            isLocked = false,
            modeId = null,
            startedAt = null,
            endsAt = null,
            blockedPackages = emptySet(),
        )
    }
}
