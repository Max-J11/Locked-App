package com.max.locked.domain

import com.max.locked.data.ActiveSchedule
import com.max.locked.data.ManualLock
import com.max.locked.data.Policy
import java.time.ZoneId
import java.time.ZoneOffset

/** Mode id for the built-in Default mode. */
const val DEFAULT_MODE_ID = 1L

/** What a mode blocks. */
data class ModeRule(
    val apps: Set<String>,
    val blockNotifications: Boolean = false,
)

/**
 * Works out what is blocked right now from stored state, schedules and the clock.
 *
 * Two things can lock:
 * - The manual lock (a puck tap or "Lock for..."). It ends when unlocked, when its timer passes,
 *   or when an "Unlock without puck" wait finishes.
 * - Schedule windows that contain [now], unless a puck ended that window early or an
 *   "Unlock without puck" wait finished while it was running.
 *
 * Everything active is combined: the apps of every active mode are blocked (minus the never-block
 * list), and notifications are hidden for apps from active modes that have that switched on.
 */
fun activePolicy(
    now: Long,
    zone: ZoneId,
    state: LockState?,
    schedules: List<ScheduleRule>,
    modes: Map<Long, ModeRule>,
    neverBlock: Set<String>,
): Policy {
    val cooldown = state?.cooldownEndsAt
    val cooldownDone = cooldown != null && now >= cooldown

    // A finished wait only ends a manual lock that was already on when it finished.
    val manualEndedByCooldown = cooldownDone && (state?.startedAt == null || state.startedAt <= cooldown!!)
    val manual = state
        ?.takeIf { it.isLocked && (it.endsAt == null || now < it.endsAt) && !manualEndedByCooldown }
        ?.let { ManualLock(it.activeModeId ?: DEFAULT_MODE_ID, it.startedAt, it.endsAt, it.source) }

    val enabled = schedules.filter { it.enabled && it.days.isNotEmpty() }
    val activeSchedules = enabled.mapNotNull { s ->
        val occ = s.occurrenceAt(now, zone) ?: return@mapNotNull null
        if (s.isSkipped(occ) || endedByCooldown(occ, cooldown, now)) null
        else ActiveSchedule(s.id, s.modeId, occ.end)
    }

    val nextChange = (
        listOfNotNull(manual?.endsAt, cooldown) + enabled.flatMap { it.boundariesAfter(now, zone) }
        ).filter { it > now }.minOrNull()

    if (manual == null && activeSchedules.isEmpty()) {
        return Policy.UNLOCKED.copy(nextChange = nextChange)
    }

    val activeModes = (listOfNotNull(manual?.modeId) + activeSchedules.map { it.modeId })
        .distinct()
        .mapNotNull { modes[it] }

    return Policy(
        manual = manual,
        schedules = activeSchedules,
        blockedPackages = activeModes.flatMap { it.apps }.toSet() - neverBlock,
        notificationPackages = activeModes.filter { it.blockNotifications }.flatMap { it.apps }.toSet() - neverBlock,
        cooldownEndsAt = cooldown?.takeIf { it > now },
        nextChange = nextChange,
    )
}

/** Manual lock only, one mode. Kept for simple tests and callers without schedules. */
fun activePolicy(
    now: Long,
    state: LockState?,
    modeApps: Set<String>,
    neverBlock: Set<String>,
    modeBlocksNotifications: Boolean = false,
): Policy = activePolicy(
    now = now,
    zone = ZoneOffset.UTC,
    state = state,
    schedules = emptyList(),
    modes = mapOf((state?.activeModeId ?: DEFAULT_MODE_ID) to ModeRule(modeApps, modeBlocksNotifications)),
    neverBlock = neverBlock,
)

/**
 * Should a notification from [packageName] be dismissed right now?
 * Ongoing notifications (music, navigation, foreground services) are left alone; Android won't let
 * a listener clear them anyway.
 */
fun shouldDismissNotification(
    policy: Policy,
    packageName: String,
    isOngoing: Boolean,
    now: Long,
): Boolean =
    !isOngoing &&
        policy.isActive(now) &&
        packageName in policy.notificationPackages
