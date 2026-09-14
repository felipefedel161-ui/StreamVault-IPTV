package com.streamvault.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppHomeDashboardShelfTest {

    @Test
    fun `defaultOrder prioritizes content shelves without clutter`() {
        assertThat(AppHomeDashboardShelf.defaultOrder).doesNotContain(AppHomeDashboardShelf.LIVE_SHORTCUTS)
        assertThat(AppHomeDashboardShelf.defaultOrder).doesNotContain(AppHomeDashboardShelf.CONTINUE_WATCHING)
        assertThat(AppHomeDashboardShelf.defaultOrder.first()).isEqualTo(AppHomeDashboardShelf.RECENT_MOVIES)
    }

    @Test
    fun `normalizeForStorage keeps unique shelves`() {
        val ordered = AppHomeDashboardShelf.normalizeForStorage(
            listOf(
                AppHomeDashboardShelf.RECENT_MOVIES,
                AppHomeDashboardShelf.RECENT_MOVIES,
                AppHomeDashboardShelf.RECENT_SERIES
            )
        )
        assertThat(ordered).containsExactly(
            AppHomeDashboardShelf.RECENT_MOVIES,
            AppHomeDashboardShelf.RECENT_SERIES
        ).inOrder()
    }
}
