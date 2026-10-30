package com.max.locked.domain

import com.max.locked.data.Policy

/*
 * While locked, settings can only get stricter. These rules say which changes would loosen
 * what's locked right now. Adding apps, pairing pucks, adding schedules, turning notification
 * hiding on and cancelling a wait are always allowed, so they have no rule here.
 */

/** A lock or running schedule uses this mode right now. */
fun isModeInUse(policy: Policy, modeId: Long, now: Long): Boolean =
    policy.isActive(now) && modeId in policy.activeModeIds

/** Apps can't be taken out of a mode in use. */
fun canRemoveApp(policy: Policy, modeId: Long, now: Long): Boolean = !isModeInUse(policy, modeId, now)

/** Notification hiding can't be turned off for a mode in use. */
fun canStopHidingNotifications(policy: Policy, modeId: Long, now: Long): Boolean =
    !isModeInUse(policy, modeId, now)

/**
 * Modes can't be deleted while anything is locked: pucks on the deleted mode move to Default,
 * which could let them end a Default lock. Default itself is never deleted.
 */
fun canDeleteMode(policy: Policy, modeId: Long, now: Long): Boolean =
    modeId != DEFAULT_MODE_ID && !policy.isActive(now)

/** Pucks can't be removed or moved to another mode while locked: a moved puck could end the lock. */
fun canChangePucks(policy: Policy, now: Long): Boolean = !policy.isActive(now)

/** A schedule running right now can't be edited, switched off or deleted. Others can. */
fun canChangeSchedule(policy: Policy, scheduleId: Long, now: Long): Boolean =
    !(policy.isActive(now) && policy.schedules.any { it.scheduleId == scheduleId })
