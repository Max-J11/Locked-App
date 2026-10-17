package com.max.locked.domain

import com.max.locked.data.Policy

/**
 * Works out what is blocked right now from stored state plus the clock.
 *
 * - Unlocked, or a timed lock whose end has passed: nothing is blocked.
 * - Locked: the mode's apps are blocked, minus anything on the never-block list.
 *
 * Schedules arrive in Milestone 6.
 */
fun activePolicy(
    now: Long,
    state: LockState?,
    modeApps: Set<String>,
    neverBlock: Set<String>,
): Policy {
    if (state == null || !state.isLocked) return Policy.UNLOCKED
    if (state.endsAt != null && now >= state.endsAt) return Policy.UNLOCKED

    return Policy(
        isLocked = true,
        modeId = state.activeModeId,
        startedAt = state.startedAt,
        endsAt = state.endsAt,
        blockedPackages = modeApps - neverBlock,
    )
}
