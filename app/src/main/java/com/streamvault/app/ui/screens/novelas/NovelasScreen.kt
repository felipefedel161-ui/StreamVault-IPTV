package com.streamvault.app.ui.screens.novelas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.streamvault.app.ui.components.shell.AppNavigationChrome
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
    var searchQuery by remember { mutableStateOf("") }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    val query = searchQuery.trim()
    val filteredSections = remember(state.sections, state.series, query) {
        if (query.isEmpty()) state.sections
        else {
            val q = query.lowercase()
            val hits = state.series.filter {
                it.name.contains(q, true) ||
                    (it.genre?.contains(q, true) == true) ||
                    (it.plot?.contains(q, true) == true)
            }
            if (hits.isEmpty()) emptyList()
            else listOf(NovelaSection("Resultados", hits))
        }
    }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Novelas",
        subtitle = "Por emissora · Globo · SBT · Record · Turcas",
        navigationChrome = AppNavigationChrome.TopBar
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
            expandedSection != null -> {
                val section = state.sections.firstOrNull { it.title == expandedSection }
                    ?: NovelaSection(expandedSection!!, state.series)
                SectionGrid(
                    title = section.title,
                    series = section.series,
                    onBack = { expandedSection = null },
                    onSeriesClick = onSeriesClick
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Search
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            placeholder = {
                                Text("Buscar novela…", color = AppColors.TextTertiary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                focusedBorderColor = AppColors.Brand,
                                unfocusedBorderColor = AppColors.Outline,
                                focusedContainerColor = AppColors.SurfaceElevated,
                                unfocusedContainerColor = AppColors.SurfaceElevated,
                                cursorColor = AppColors.Brand
                            )
                        )
                    }

                    val featured = state.featured
                    if (featured != null && query.isEmpty()) {
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

                    val sections = filteredSections.ifEmpty {
                        if (state.series.isNotEmpty() && query.isEmpty())
                            listOf(NovelaSection("Todas", state.series))
                        else emptyList()
                    }

                    if (sections.isEmpty() && query.isNotEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "Nenhuma novela encontrada para \"$query\"",
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }

                    sections.forEach { section ->
                        item(key = "sec-${section.title}") {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = section.title,
                                        color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    if (section.series.size > 4 && query.isEmpty()) {
                                        TvClickableSurface(
                                            onClick = { expandedSection = section.title },
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = AppColors.SurfaceElevated,
                                                focusedContainerColor = AppColors.SurfaceAccent
                                            )
                                        ) {
                                            Text(
                                                text = "Ver todas",
                                                color = AppColors.Brand,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(section.series.take(12), key = { it.id }) { s ->
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
private fun SectionGrid(
    title: String,
    series: List<Series>,
    onBack: () -> Unit,
    onSeriesClick: (Series) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TvClickableSurface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.SurfaceElevated,
                    focusedContainerColor = AppColors.SurfaceAccent
                )
            ) {
                Text(
                    text = "← Voltar",
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Text(
                text = "$title (${series.size})",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.width(80.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.id }) { s ->
                NovelaPosterCard(series = s, onClick = { onSeriesClick(s) })
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
