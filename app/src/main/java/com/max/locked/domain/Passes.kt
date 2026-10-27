package com.max.locked.domain

import com.max.locked.data.Policy

/** How long a pass opens an app for. */
const val PASS_MILLIS = 5 * 60 * 1000L

/** Passes per lock. They come back when a new lock starts. */
const val PASSES_PER_LOCK = 3

/** A pass can be used for an app that's blocked right now, while any are left. */
fun canUsePass(policy: Policy, packageName: String, now: Long): Boolean =
    policy.isBlocked(packageName, now) && policy.passesLeft > 0
