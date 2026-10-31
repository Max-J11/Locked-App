package com.max.locked.domain

import com.max.locked.data.Policy

/** How long a pass opens an app for. */
const val PASS_MILLIS = 5 * 60 * 1000L

/** Passes per lock until she picks her own number in Settings. They come back when a new lock starts. */
const val DEFAULT_PASSES_PER_LOCK = 3

/** The most passes per lock Settings offers. */
const val MAX_PASSES_PER_LOCK = 5

/** Passes per lock can be set to anything in range while unlocked, but only lowered while locked. */
fun canSetPassCap(policy: Policy, now: Long, current: Int, new: Int): Boolean =
    new in 0..MAX_PASSES_PER_LOCK && (new <= current || !policy.isActive(now))

/** A pass can be used for an app that's blocked right now, while any are left. */
fun canUsePass(policy: Policy, packageName: String, now: Long): Boolean =
    policy.isBlocked(packageName, now) && policy.passesLeft > 0

/** A pass for a website is stored in the pass slot as "site:<domain>". Package names never start with this. */
const val SITE_PASS_PREFIX = "site:"

fun sitePassTarget(site: String): String = SITE_PASS_PREFIX + site

/** The site a pass target is for, or null if it's an app's package. */
fun sitePassSite(target: String?): String? = target?.takeIf { it.startsWith(SITE_PASS_PREFIX) }?.removePrefix(SITE_PASS_PREFIX)

/** A pass can be used for a site that's blocked right now, while any are left. Shares the lock's passes with apps. */
fun canUseSitePass(policy: Policy, host: String, now: Long): Boolean =
    policy.isSiteBlocked(host, now) && policy.passesLeft > 0
