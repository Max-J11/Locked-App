package com.max.locked.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    @Test
    fun `parts of the day switch at 5 AM, noon and 5 PM`() {
        assertEquals(PartOfDay.EVENING, partOfDay(4))
        assertEquals(PartOfDay.MORNING, partOfDay(5))
        assertEquals(PartOfDay.MORNING, partOfDay(11))
        assertEquals(PartOfDay.AFTERNOON, partOfDay(12))
        assertEquals(PartOfDay.AFTERNOON, partOfDay(16))
        assertEquals(PartOfDay.EVENING, partOfDay(17))
        assertEquals(PartOfDay.EVENING, partOfDay(0))
    }

    @Test
    fun `tagline never repeats twice in a row and every option comes up`() {
        val random = Random(42)
        val seen = mutableSetOf<Int>()
        var previous = -1
        repeat(500) {
            val next = nextTagline(6, previous, random)
            assertTrue(next in 0 until 6)
            assertNotEquals(previous, next)
            seen += next
            previous = next
        }
        assertEquals((0 until 6).toSet(), seen)
    }

    @Test
    fun `tagline handles one option and an out-of-range previous`() {
        assertEquals(0, nextTagline(1, 0))
        assertTrue(nextTagline(3, 7) in 0 until 3)
    }
}
