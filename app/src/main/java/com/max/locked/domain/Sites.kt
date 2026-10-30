package com.max.locked.domain

/**
 * Browsers whose address bar the blocker reads, and the view id of that bar. Matched against
 * Chrome and Brave on One UI 8.5. Both show the page's address without "https://", e.g.
 * "m.youtube.com/watch?v=...", and mark the bar as focused while it's being typed in.
 */
val BROWSER_URL_BARS: Map<String, String> = mapOf(
    "com.android.chrome" to "url_bar",
    "com.brave.browser" to "url_bar",
)

private val DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")

/**
 * The host in [text], lowercased and without "www.", or null if it isn't an address.
 * "https://www.YouTube.com/watch?v=1" and "m.youtube.com/watch" both work;
 * "Search Google or type URL" and "youtube" (no dot) don't.
 */
fun hostOf(text: String): String? {
    var s = text.trim().lowercase()
    if (s.isEmpty() || s.any { it.isWhitespace() }) return null
    s = s.substringAfter("://")
    s = s.substringBefore('/').substringBefore('?').substringBefore('#')
    s = s.substringAfterLast('@').substringBefore(':').trimEnd('.')
    s = s.removePrefix("www.")
    return s.takeIf { DOMAIN.matches(it) }
}

/** Cleans up a site typed in the mode editor into the domain to store, or null if it isn't one. */
fun normalizeSite(input: String): String? = hostOf(input)

/** True if [host] is one of [sites] or a subdomain of one: "youtube.com" covers "m.youtube.com". */
fun isSiteBlocked(host: String, sites: Set<String>): Boolean =
    sites.any { host == it || host.endsWith(".$it") }
