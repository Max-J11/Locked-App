package com.max.locked.data

/**
 * The one immutable answer to "what should be blocked right now?".
 *
 * Built by [com.max.locked.domain.activePolicy] and exposed by [BrickRepository] as a StateFlow.
 * Enforcers only read it. [endsAt] and [cooldownEndsAt] are checked at read time, so a lock ends
 * on the clock, not on an alarm.
 */
data class Policy(
    val isLocked: Boolean,
    val modeId: Long?,
    val startedAt: Long?,
    val endsAt: Long?,
    val blockedPackages: Set<String>,
    /** Dismiss notifications from [blockedPackages] while active. */
    val blockNotifications: Boolean = false,
    /** "Unlock without puck" is counting down to this time. */
    val cooldownEndsAt: Long? = null,
    /** What started the lock, e.g. "puck:3" or "timer". */
    val source: String? = null,
) {
    fun isActive(now: Long): Boolean =
        isLocked &&
            (endsAt == null || now < endsAt) &&
            (cooldownEndsAt == null || now < cooldownEndsAt)

    fun isBlocked(packageName: String, now: Long): Boolean =
        isActive(now) && packageName in blockedPackages

    /** The next moment this policy changes on its own (a timer or cooldown ending), if any. */
    fun nextChangeAt(now: Long): Long? =
        listOfNotNull(endsAt, cooldownEndsAt).filter { it > now }.minOrNull()
            ?.takeIf { isActive(now) }

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
