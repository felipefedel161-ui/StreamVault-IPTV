package com.streamvault.app.ui.screens.novelas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.components.shell.VodClassicCategoryOption
import com.streamvault.app.ui.components.shell.VodClassicContentHeader
import com.streamvault.app.ui.components.shell.VodClassicSplitLayout
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Series

/**
 * Novelas uses the same VodClassic layout as Series/Movies (original app pattern).
 */
@Composable
fun NovelasScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: NovelasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selectedCategoryId != null) {
        viewModel.selectCategory(null)
    }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Novelas",
        subtitle = null,
        navigationChrome = AppNavigationChrome.TopBar
    ) {
        when {
            state.loading && state.categories.isEmpty() && state.series.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Brand)
                }
            }
            state.error != null && state.series.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Erro", color = AppColors.TextSecondary)
                }
            }
            else -> {
                val search = state.categorySearchQuery.trim()
                val visibleCategories = remember(state.categories, search) {
                    if (search.isEmpty()) state.categories
                    else state.categories.filter { it.name.contains(search, ignoreCase = true) }
                }

                val categoryOptions = buildList {
                    add(
                        VodClassicCategoryOption(
                            key = "all",
                            label = "Todas",
                            count = state.allSeriesCache.size,
                            isSelected = state.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) }
                        )
                    )
                    visibleCategories.forEach { cat ->
                        add(
                            VodClassicCategoryOption(
                                key = cat.id.toString(),
                                label = cat.name,
                                count = 0,
                                isSelected = state.selectedCategoryId == cat.id,
                                onClick = { viewModel.selectCategory(cat.id) }
                            )
                        )
                    }
                }

                VodClassicSplitLayout(
                    railTitle = "Categorias",
                    railSearchValue = state.categorySearchQuery,
                    onRailSearchValueChange = viewModel::updateCategorySearch,
                    railSearchPlaceholder = "Buscar categoria…",
                    categories = categoryOptions,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        val headerTitle = state.categories
                            .firstOrNull { it.id == state.selectedCategoryId }
                            ?.name
                            ?: "Todas as novelas"

                        VodClassicContentHeader(
                            title = headerTitle,
                            subtitle = "${state.series.size} títulos",
                            actions = emptyList(),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (state.loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.Brand)
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
                                if (featured != null && state.selectedCategoryId == null) {
                                    item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
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
                                items(state.series, key = { it.id }) { s ->
                                    NovelaPosterCard(series = s, onClick = { onSeriesClick(s) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelaPosterCard(series: Series, onClick: () -> Unit) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceAccent
        ),
        modifier = Modifier.width(140.dp)
    ) {
        Column {
            AsyncImage(
                model = series.posterUrl ?: series.backdropUrl,
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Text(
                text = series.name,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
