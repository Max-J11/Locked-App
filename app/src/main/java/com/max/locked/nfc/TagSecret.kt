package com.max.locked.nfc

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The secret written to a puck. Only its SHA-256 is stored on the phone.
 * Pure JVM (no Android imports) so it can be unit-tested.
 */
object TagSecret {

    private const val SECRET_BYTES = 16
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 16 random bytes as base64url without padding (22 characters). */
    fun generate(): String {
        val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
        return encoder.encodeToString(bytes)
    }

    /** Lowercase hex SHA-256 of the secret's UTF-8 bytes. */
    fun hash(secret: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(Charsets.UTF_8))
            .toHex()

    /** Constant-time comparison of two hex hashes. */
    fun hashesMatch(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
