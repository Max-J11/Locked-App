package com.max.locked.domain

/** How long "Unlock without puck" makes you wait. */
const val COOLDOWN_MILLIS = 15 * 60 * 1000L

const val SOURCE_TIMER = "timer"
private const val PUCK_PREFIX = "puck:"

/**
 * "Unlock without puck" is offered only while locked, not already counting down, and when waiting
 * would end the lock sooner than its own timer would.
 */
fun canStartCooldown(isActive: Boolean, endsAt: Long?, cooldownEndsAt: Long?, now: Long): Boolean =
    isActive && cooldownEndsAt == null && (endsAt == null || now + COOLDOWN_MILLIS < endsAt)

fun puckSource(keyId: Long): String = "$PUCK_PREFIX$keyId"

/** The puck that started a lock, or null if it wasn't started by a puck. */
fun lockingPuckId(source: String?): Long? =
    source?.takeIf { it.startsWith(PUCK_PREFIX) }?.removePrefix(PUCK_PREFIX)?.toLongOrNull()

/**
 * Which pucks can end a lock:
 * - A lock started by a puck: only that puck.
 * - Any other lock (a timer, for example): only pucks set to the mode in use. If no puck is set
 *   to that mode, no puck can end it; it ends on its own or with "Unlock without puck".
 */
fun canUnlockWithPuck(source: String?, keyId: Long, keyModeId: Long, activeModeId: Long?): Boolean {
    val lockingId = lockingPuckId(source)
    return if (lockingId != null) lockingId == keyId else keyModeId == activeModeId
}
