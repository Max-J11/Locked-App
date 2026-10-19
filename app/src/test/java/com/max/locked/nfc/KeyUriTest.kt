package com.max.locked.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyUriTest {

    private val secret = "AbCdEfGhIjKlMnOpQr_-12"

    @Test
    fun `valid uri returns the secret`() {
        assertEquals(secret, KeyUri.parse("brickdiy://key/$secret"))
    }

    @Test
    fun `scheme and host are case-insensitive`() {
        assertEquals(secret, KeyUri.parse("BrickDIY://KEY/$secret"))
    }

    @Test
    fun `secret is case-sensitive and kept as-is`() {
        assertEquals(secret.lowercase(), KeyUri.parse("brickdiy://key/${secret.lowercase()}"))
    }

    @Test
    fun `wrong scheme or host is rejected`() {
        assertNull(KeyUri.parse("https://key/$secret"))
        assertNull(KeyUri.parse("brickdiy://door/$secret"))
    }

    @Test
    fun `missing, empty or malformed secret is rejected`() {
        assertNull(KeyUri.parse(null))
        assertNull(KeyUri.parse("brickdiy://key/"))
        assertNull(KeyUri.parse("brickdiy://key"))
        assertNull(KeyUri.parse("brickdiy://key/short"))
        assertNull(KeyUri.parse("brickdiy://key/${secret}x"))
        assertNull(KeyUri.parse("brickdiy://key/AbCdEfGhIjKlMnOpQr+/12"))
    }

    @Test
    fun `extra path segments are rejected`() {
        assertNull(KeyUri.parse("brickdiy://key/$secret/extra"))
        assertNull(KeyUri.parse("brickdiy://key/extra/$secret"))
    }
}
