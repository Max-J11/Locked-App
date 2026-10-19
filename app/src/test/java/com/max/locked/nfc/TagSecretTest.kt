package com.max.locked.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSecretTest {

    @Test
    fun `generated secret is 22 url-safe characters`() {
        repeat(50) {
            val secret = TagSecret.generate()
            assertEquals(22, secret.length)
            assertTrue(secret.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }

    @Test
    fun `generated secrets differ`() {
        val secrets = List(100) { TagSecret.generate() }.toSet()
        assertEquals(100, secrets.size)
    }

    @Test
    fun `hash is stable 64-char lowercase hex`() {
        val h1 = TagSecret.hash("abc")
        assertEquals(h1, TagSecret.hash("abc"))
        assertEquals(64, h1.length)
        // Known SHA-256 of "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h1)
    }

    @Test
    fun `different secrets give different hashes`() {
        assertNotEquals(TagSecret.hash("a"), TagSecret.hash("b"))
    }

    @Test
    fun `hashesMatch compares exactly`() {
        val h = TagSecret.hash("secret")
        assertTrue(TagSecret.hashesMatch(h, TagSecret.hash("secret")))
        assertFalse(TagSecret.hashesMatch(h, TagSecret.hash("Secret")))
    }

    @Test
    fun `generated secret round-trips through KeyUri`() {
        val secret = TagSecret.generate()
        assertEquals(secret, KeyUri.parse(KeyUri.build(secret)))
    }
}
