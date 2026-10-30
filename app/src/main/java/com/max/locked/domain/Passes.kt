package com.max.locked.domain

import com.max.locked.data.Policy

/** How long a pass opens an app for. */
const val PASS_MILLIS = 5 * 60 * 1000L

/** Passes per lock. They come back when a new lock starts. */
const val PASSES_PER_LOCK = 3

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
