package com.max.locked.nfc

/**
 * The URI written to a puck: `brickdiy://key/<secret>`.
 * Pure Kotlin so parsing can be unit-tested without a device.
 */
object KeyUri {
    const val SCHEME = "brickdiy"
    const val HOST = "key"
    private const val PREFIX = "$SCHEME://$HOST/"

    // 16 bytes in base64url without padding is exactly 22 characters.
    private val SECRET_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")

    fun build(secret: String): String = PREFIX + secret

    /** Returns the secret, or null if this isn't one of our key URIs. */
    fun parse(uri: String?): String? {
        if (uri == null) return null
        // Android lowercases the scheme in URI records; match scheme and host case-insensitively.
        if (!uri.startsWith(PREFIX, ignoreCase = true)) return null
        val secret = uri.substring(PREFIX.length)
        return secret.takeIf { SECRET_PATTERN.matches(it) }
    }
}
