package com.max.locked.domain

import kotlin.random.Random

/**
 * Picks the phrase under the greeting from [count] options, never the same as [previous] twice in
 * a row. [previous] is -1 when nothing has been shown yet.
 */
fun nextTagline(count: Int, previous: Int, random: Random = Random): Int {
    if (count <= 1) return 0
    if (previous !in 0 until count) return random.nextInt(count)
    // Pick from the other count - 1 options, skipping over the previous one.
    val pick = random.nextInt(count - 1)
    return if (pick >= previous) pick + 1 else pick
}

enum class PartOfDay { MORNING, AFTERNOON, EVENING }

/** For the greeting on Home: morning 5 AM to noon, afternoon until 5 PM, evening otherwise. */
fun partOfDay(hour: Int): PartOfDay = when (hour) {
    in 5..11 -> PartOfDay.MORNING
    in 12..16 -> PartOfDay.AFTERNOON
    else -> PartOfDay.EVENING
}
