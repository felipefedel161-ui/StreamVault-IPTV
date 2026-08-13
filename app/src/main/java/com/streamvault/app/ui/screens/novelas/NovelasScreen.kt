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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Series

@Composable
fun NovelasScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: NovelasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Novelas",
        subtitle = "Por emissora · Globo · SBT · Record · Turcas"
    ) {
        when {
            state.loading && state.series.isEmpty() && state.sections.isEmpty() -> {
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val featured = state.featured
                    if (featured != null) {
                        item(key = "hero") {
                            BrowseHeroPanel(
                                title = featured.name,
                                subtitle = featured.plot?.takeIf { it.isNotBlank() }
                                    ?: featured.genre
                                    ?: "Novela em destaque",
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
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Sections by emissora — no category chips / lateral bar
                    val sections = state.sections.ifEmpty {
                        if (state.series.isNotEmpty()) listOf(NovelaSection("Todas", state.series))
                        else emptyList()
                    }
                    sections.forEach { section ->
                        item(key = "sec-${section.title}") {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    text = section.title,
                                    color = AppColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(section.series, key = { it.id }) { s ->
                                        NovelaPosterCard(series = s, onClick = { onSeriesClick(s) })
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
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
