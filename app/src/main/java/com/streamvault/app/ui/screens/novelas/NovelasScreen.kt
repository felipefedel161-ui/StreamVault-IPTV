package com.streamvault.app.ui.screens.novelas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Series

@Composable
fun NovelasScreen(
    onNavigate: (String) -> Unit,
    onSeriesClick: (Series) -> Unit,
    currentRoute: String,
    viewModel: NovelasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Novelas",
        subtitle = "Capítulos e dramas da sua lista",
        showScreenHeader = false,
        contentPadding = PaddingValues(0.dp)
    ) {
        when {
            state.loading && state.series.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Brand)
            }
            state.error != null && state.series.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error ?: "", color = AppColors.TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                TvButton(onClick = viewModel::refresh) { Text("Tentar de novo") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val featured = state.featured
                    if (featured != null) {
                        BrowseHeroPanel(
                            title = featured.name,
                            subtitle = featured.plot?.takeIf { it.isNotBlank() }
                                ?: featured.genre
                                ?: "Novela em destaque hoje",
                            imageUrl = featured.backdropUrl?.takeIf { it.isNotBlank() }
                                ?: featured.posterUrl,
                            eyebrow = "NOVELAS",
                            metadata = buildList {
                                featured.genre?.takeIf { it.isNotBlank() }?.let { g ->
                                    add(g.split(",", "|").first().trim())
                                }
                                if (featured.rating > 0f) add("%.1f/10".format(featured.rating))
                            },
                            actionLabel = "Assistir",
                            onClick = { onSeriesClick(featured) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text(
                                "Novelas",
                                style = MaterialTheme.typography.headlineSmall,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Nenhuma novela encontrada na lista. Categorias com “novela”, “soap” ou “telenovela” aparecem aqui.",
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (state.categories.isNotEmpty()) {
                    item {
                        Text(
                            text = "CATEGORIAS",
                            color = AppColors.BrandStrong,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                CategoryChip(
                                    label = "Todas",
                                    selected = state.selectedCategoryId == null,
                                    onClick = { viewModel.selectCategory(null) }
                                )
                            }
                            items(state.categories, key = { it.id }) { cat ->
                                CategoryChip(
                                    label = cat.name,
                                    selected = state.selectedCategoryId == cat.id,
                                    onClick = { viewModel.selectCategory(cat.id) }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (state.series.isEmpty()) "Sem títulos" else "${state.series.size} títulos",
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                items(state.series.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { series ->
                            NovelaCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Brand else AppColors.SurfaceElevated,
            focusedContainerColor = if (selected) AppColors.Brand else AppColors.SurfaceEmphasis
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.Black else AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun NovelaCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(
        onClick = onClick,
        modifier = modifier.height(220.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val art = series.posterUrl?.takeIf { it.isNotBlank() }
                    ?: series.backdropUrl
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = series.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            series.name.take(1),
                            color = AppColors.Brand,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = series.name,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            series.genre?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it.split(",", "|").first().trim(),
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    }
}
