package com.streamvault.domain.usecase

import com.streamvault.domain.model.PlaybackHistory
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.util.shouldRethrowDomainFlowFailure
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Returns the most-watched VOD content in the last [windowDays] days, ordered by
 * total watch count descending. Duplicates within the same series are collapsed so
 * the shelf shows distinct titles rather than many episodes of the same show.
 *
 * Live channels are excluded — their watch count tends to be very high and would
 * dominate the shelf in a misleading way.
 */
class GetTrendingNow @Inject constructor(
    private val playbackHistoryRepository: PlaybackHistoryRepository
) {
    private val logger = Logger.getLogger("GetTrendingNow")

    operator fun invoke(
        providerIds: Set<Long>,
        limit: Int = 20,
        windowDays: Int = 7
    ): Flow<List<PlaybackHistory>> {
        val normalizedIds = providerIds.filterTo(linkedSetOf()) { it > 0L }
        if (normalizedIds.isEmpty()) return flowOf(emptyList())

        val cutoffMs = System.currentTimeMillis() - windowDays * 24 * 60 * 60 * 1000L
        // Fetch a generous slice — we'll filter and aggregate in-memory
        val historyFlow = if (normalizedIds.size == 1) {
            playbackHistoryRepository.getRecentlyWatchedByProvider(normalizedIds.first(), limit = 200)
        } else {
            playbackHistoryRepository.getRecentlyWatchedByProviders(normalizedIds, limit = 200)
        }

        return historyFlow
            .map { history ->
                history
                    // Only content watched within the rolling window
                    .filter { it.lastWatchedAt >= cutoffMs }
                    // Exclude live — not meaningful to rank by plays
                    .filter { it.contentType.name != "LIVE" }
                    // Collapse series episodes: use seriesId as the group key when available
                    .groupBy { entry ->
                        when {
                            entry.seriesId != null && entry.seriesId > 0L ->
                                "series:${entry.seriesId}"
                            else ->
                                "${entry.contentType.name}:${entry.contentId}"
                        }
                    }
                    // For each group, pick the representative entry (most recently watched)
                    // and accumulate the total watch count across all episodes
                    .map { (_, entries) ->
                        val representative = entries.maxBy { it.lastWatchedAt }
                        val totalWatches = entries.sumOf { it.watchCount }
                        representative.copy(watchCount = totalWatches)
                    }
                    // Sort by accumulated watch count, then recency as tiebreaker
                    .sortedWith(
                        compareByDescending<PlaybackHistory> { it.watchCount }
                            .thenByDescending { it.lastWatchedAt }
                    )
                    .take(limit)
            }
            .catch { error ->
                if (error.shouldRethrowDomainFlowFailure()) throw error
                logger.log(Level.WARNING, "Failed to build trending now list", error)
                emit(emptyList())
            }
    }
}
