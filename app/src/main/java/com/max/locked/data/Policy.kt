package com.max.locked.data

/** The manual lock (puck tap or "Lock for...") while it's in effect. */
data class ManualLock(
    val modeId: Long,
    val startedAt: Long?,
    /** Null for an open-ended lock. */
    val endsAt: Long?,
    /** What started it, e.g. "puck:3" or "timer". */
    val source: String?,
)

/** A schedule window running right now. */
data class ActiveSchedule(
    val scheduleId: Long,
    val modeId: Long,
    val startedAt: Long,
    val endsAt: Long,
)

/**
 * The one immutable answer to "what should be blocked right now?".
 *
 * Built by [com.max.locked.domain.activePolicy] and exposed by [BrickRepository]. Enforcers only
 * read it. End times are also checked at read time, so a lock ends on the clock, not on an alarm.
 */
data class Policy(
    val manual: ManualLock? = null,
    val schedules: List<ActiveSchedule> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    /** Blocked apps whose notifications are dismissed. */
    val notificationPackages: Set<String> = emptySet(),
    /** Domains blocked in browsers; each also covers its subdomains. */
    val blockedSites: Set<String> = emptySet(),
    /** "Unlock without puck" is counting down to this time. */
    val cooldownEndsAt: Long? = null,
    /** The next moment the policy changes on its own: a timer, wait, pass or schedule window starting or ending. */
    val nextChange: Long? = null,
    /** An app opened with a pass, until [passEndsAt]. */
    val passPackage: String? = null,
    val passEndsAt: Long? = null,
    val passesLeft: Int = 0,
    /** When the current locked stretch began. Passes are counted per stretch. */
    val lockedSince: Long? = null,
) {
    val isLocked: Boolean get() = manual != null || schedules.isNotEmpty()

    /** When everything currently locking has ended, or null if something is open-ended. */
    val endsAt: Long?
        get() = when {
            !isLocked -> null
            manual != null && manual.endsAt == null -> null
            else -> (listOfNotNull(manual?.endsAt) + schedules.map { it.endsAt }).max()
        }

    /** The manual lock's mode, or the first running schedule's. */
    val modeId: Long? get() = manual?.modeId ?: schedules.firstOrNull()?.modeId

    val activeModeIds: Set<Long> get() = setOfNotNull(manual?.modeId) + schedules.map { it.modeId }

    val source: String? get() = manual?.source

    val blockNotifications: Boolean get() = notificationPackages.isNotEmpty()

    fun isActive(now: Long): Boolean {
        val end = endsAt
        return isLocked &&
            (end == null || now < end) &&
            (cooldownEndsAt == null || now < cooldownEndsAt)
    }

    fun isBlocked(packageName: String, now: Long): Boolean =
        isActive(now) && packageName in blockedPackages && !hasPass(packageName, now)

    /** This app is open on a pass right now. */
    fun hasPass(packageName: String, now: Long): Boolean =
        packageName == passPackage && passEndsAt != null && now < passEndsAt

    /** [host] is a blocked site right now. Sites have no passes. */
    fun isSiteBlocked(host: String, now: Long): Boolean =
        isActive(now) && com.max.locked.domain.isSiteBlocked(host, blockedSites)

    fun nextChangeAt(now: Long): Long? = nextChange?.takeIf { it > now }

    companion object {
        val UNLOCKED = Policy()
    }
}
