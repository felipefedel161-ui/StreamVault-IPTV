package com.streamvault.app.ui.screens.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Channel

@Composable
fun RadioScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onPlayStation: (Channel) -> Unit,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        navigationChrome = AppNavigationChrome.TopBar,
        title = "Rádio",
        subtitle = "Estações ao vivo",
        showScreenHeader = true,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = {},
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = AppColors.Surface),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextPrimary),
                            cursorBrush = SolidColor(AppColors.Brand),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (state.query.isEmpty()) {
                                        Text("Buscar estação…", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextTertiary)
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                TvClickableSurface(
                    onClick = { viewModel.load() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.SurfaceAccent,
                        focusedContainerColor = AppColors.Brand,
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = AppColors.TextPrimary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.isLoading -> "Carregando estações…"
                    else -> "${state.filtered.size} estação(ões)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Brand)
                    }
                }
                state.filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = AppColors.TextTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.error ?: "Nenhuma rádio encontrada",
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Categorias ou canais com “rádio”, “FM”, “AM” na lista",
                                color = AppColors.TextTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.filtered, key = { it.id }) { station ->
                            RadioStationCard(station = station, onClick = { onPlayStation(station) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioStationCard(station: Channel, onClick: () -> Unit) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.Surface,
            focusedContainerColor = AppColors.SurfaceAccent,
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SurfaceAccent),
                contentAlignment = Alignment.Center
            ) {
                val logo = station.logoUrl
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AppColors.Brand,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
