package com.max.locked.domain

import com.max.locked.data.Policy

/**
 * Works out what is blocked right now from stored state plus the clock.
 *
 * - Unlocked, or a timed lock whose end has passed: nothing is blocked.
 * - Locked: the mode's apps are blocked, minus anything on the never-block list, and their
 *   notifications are dismissed if the mode says so.
 *
 * Schedules arrive in Milestone 6.
 */
fun activePolicy(
    now: Long,
    state: LockState?,
    modeApps: Set<String>,
    neverBlock: Set<String>,
    modeBlocksNotifications: Boolean = false,
): Policy {
    if (state == null || !state.isLocked) return Policy.UNLOCKED
    if (state.endsAt != null && now >= state.endsAt) return Policy.UNLOCKED

    return Policy(
        isLocked = true,
        modeId = state.activeModeId,
        startedAt = state.startedAt,
        endsAt = state.endsAt,
        blockedPackages = modeApps - neverBlock,
        blockNotifications = modeBlocksNotifications,
    )
}

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
    policy.blockNotifications &&
        !isOngoing &&
        policy.isBlocked(packageName, now)
