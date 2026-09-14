package com.streamvault.domain.model

enum class AppHomeDashboardShelf(
    val storageValue: String,
    val defaultEnabled: Boolean
) {
    RECENT_MOVIES("recent_movies", defaultEnabled = true),
    RECENT_SERIES("recent_series", defaultEnabled = true),
    RECENT_CHANNELS("recent_channels", defaultEnabled = true),
    FAVORITE_CHANNELS("favorite_channels", defaultEnabled = true),
    FAVORITE_MOVIES("favorite_movies", defaultEnabled = true),
    FAVORITE_SERIES("favorite_series", defaultEnabled = true),
    TOP_RATED_MOVIES("top_rated_movies", defaultEnabled = true),
    RECOMMENDED_MOVIES("recommended_movies", defaultEnabled = true),
    // clutter — off by default
    LIVE_SHORTCUTS("live_shortcuts", defaultEnabled = false),
    CONTINUE_WATCHING("continue_watching", defaultEnabled = false),
    CONTINUE_WATCHING_MOVIES("continue_watching_movies", defaultEnabled = false),
    CONTINUE_WATCHING_SERIES("continue_watching_series", defaultEnabled = false);

    companion object {
        val catalogOrder: List<AppHomeDashboardShelf> = listOf(
            RECENT_MOVIES,
            RECENT_SERIES,
            RECENT_CHANNELS,
            FAVORITE_CHANNELS,
            FAVORITE_MOVIES,
            FAVORITE_SERIES,
            TOP_RATED_MOVIES,
            RECOMMENDED_MOVIES,
            CONTINUE_WATCHING,
            CONTINUE_WATCHING_MOVIES,
            CONTINUE_WATCHING_SERIES,
            LIVE_SHORTCUTS
        )

        val defaultOrder: List<AppHomeDashboardShelf> = catalogOrder.filter { it.defaultEnabled }

        fun fromStorage(value: String?): AppHomeDashboardShelf? =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) }

        fun normalizeForStorage(shelves: List<AppHomeDashboardShelf>): List<AppHomeDashboardShelf> {
            val unique = linkedSetOf<AppHomeDashboardShelf>()
            shelves.forEach(unique::add)
            return unique.toList()
        }
    }
}
