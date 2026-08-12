package com.streamvault.app.ui.util

import java.util.Calendar

/**
 * Picks a stable-but-rotating "hero of the day" from candidates.
 * [sectionSalt] must differ per tab (movies vs series vs home) so the same title
 * is not repeated across sections on the same day.
 * Prefers items that have artwork.
 */
fun <T> pickDailyHero(
    candidates: List<T>,
    sectionSalt: Int,
    idOf: (T) -> Long,
    hasImage: (T) -> Boolean
): T? {
    if (candidates.isEmpty()) return null
    val calendar = Calendar.getInstance()
    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    val seed = (year * 1000L + dayOfYear) * 31L + sectionSalt.toLong()

    val withImage = candidates.filter(hasImage)
    val pool = if (withImage.isNotEmpty()) withImage else candidates
    val ordered = pool.sortedBy { (idOf(it) xor seed) }
    val index = (((seed % ordered.size.toLong()) + ordered.size.toLong()) % ordered.size.toLong()).toInt()
    return ordered[index]
}

const val HERO_SALT_MOVIES = 11
const val HERO_SALT_SERIES = 29
const val HERO_SALT_HOME = 47
