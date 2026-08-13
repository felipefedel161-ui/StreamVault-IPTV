package com.streamvault.app.ui.screens.novelas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.streamvault.app.ui.components.SeriesCard
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.components.shell.VodClassicCategoryOption
import com.streamvault.app.ui.components.shell.VodClassicContentHeader
import com.streamvault.app.ui.components.shell.VodClassicSplitLayout
import com.streamvault.app.ui.design.AppColors
import com.streamvault.domain.model.Series

/**
 * Same shell as Series/Movies (VodClassic + SeriesCard + TopBar).
 * Search filters series titles (not only category labels).
 */
@Composable
fun NovelasScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: NovelasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selectedKey != "all" || state.searchQuery.isNotBlank()) {
        when {
            state.searchQuery.isNotBlank() -> viewModel.updateSearch("")
            state.selectedKey != "all" -> viewModel.selectRail("all")
        }
    }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Novelas",
        subtitle = null,
        navigationChrome = AppNavigationChrome.TopBar
    ) {
        when {
            state.loading && state.allSeries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Brand)
                }
            }
            state.error != null && state.allSeries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Erro", color = AppColors.TextSecondary)
                }
            }
            else -> {
                // Filter rail by search when query looks like category; always show all rail items,
                // content grid uses displayedSeries (title search).
                val categoryOptions = remember(state.railItems, state.selectedKey) {
                    state.railItems.map { item ->
                        VodClassicCategoryOption(
                            key = item.key,
                            label = item.label,
                            count = item.count,
                            isSelected = state.selectedKey == item.key,
                            onClick = { viewModel.selectRail(item.key) }
                        )
                    }
                }

                VodClassicSplitLayout(
                    railTitle = "Categorias",
                    railSearchValue = state.searchQuery,
                    onRailSearchValueChange = viewModel::updateSearch,
                    railSearchPlaceholder = "Buscar novela…",
                    categories = categoryOptions,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        val headerTitle = state.railItems
                            .firstOrNull { it.key == state.selectedKey }
                            ?.label
                            ?: "Novelas"

                        VodClassicContentHeader(
                            title = if (state.searchQuery.isBlank()) headerTitle else "Busca",
                            subtitle = when {
                                state.searchQuery.isNotBlank() ->
                                    "${state.displayedSeries.size} resultado(s) para \"${state.searchQuery}\""
                                else -> "${state.displayedSeries.size} títulos"
                            },
                            actions = emptyList(),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (state.loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.Brand)
                            }
                        } else if (state.displayedSeries.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (state.searchQuery.isNotBlank())
                                        "Nenhuma novela encontrada para \"${state.searchQuery}\""
                                    else "Nenhuma novela nesta categoria",
                                    color = AppColors.TextSecondary
                                )
                            }
                        } else {
                            val featured = state.featured
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (featured != null && state.searchQuery.isBlank() && state.selectedKey == "all") {
                                    item(
                                        key = "hero",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        BrowseHeroPanel(
                                            title = featured.name,
                                            subtitle = featured.plot?.takeIf { it.isNotBlank() }
                                                ?: featured.genre
                                                ?: "Novela em destaque",
                                            imageUrl = featured.backdropUrl?.takeIf { it.isNotBlank() }
                                                ?: featured.posterUrl,
                                            eyebrow = "NOVELAS",
                                            metadata = buildList {
                                                featured.genre?.takeIf { it.isNotBlank() }?.let {
                                                    add(it.split(",", "|").first().trim())
                                                }
                                                if (featured.rating > 0f) add("%.1f/10".format(featured.rating))
                                            },
                                            actionLabel = "Assistir",
                                            onClick = { onSeriesClick(featured) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        )
                                    }
                                }
                                items(state.displayedSeries, key = { it.id }) { s ->
                                    SeriesCard(
                                        series = s,
                                        onClick = { onSeriesClick(s) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
