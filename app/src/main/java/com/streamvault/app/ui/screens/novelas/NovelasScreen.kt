package com.streamvault.app.ui.screens.novelas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.streamvault.app.ui.components.SeriesCard
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.design.FocusSpec
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Series

private val AccentBlue = Color(0xFF3B82F6)

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
        subtitle = "${state.displayedSeries.size} títulos",
        navigationChrome = AppNavigationChrome.TopBar,
        showScreenHeader = false,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Novelas",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0xFF141820), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearch,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        "Buscar novela…",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(state.railItems, key = { it.key }) { item ->
                            val selected = item.key == state.selectedKey
                            TvClickableSurface(
                                onClick = { viewModel.selectRail(item.key) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (selected) AccentBlue else Color(0xFF141820),
                                    focusedContainerColor = if (selected) Color(0xFF60A5FA) else Color(0xFF1C2433),
                                    contentColor = Color.White,
                                    focusedContentColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                )
                            ) {
                                Text(
                                    text = "${item.label} · ${item.count}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${state.displayedSeries.size} títulos",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.displayedSeries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.searchQuery.isNotBlank()) {
                                    "Nenhuma novela para \"${state.searchQuery}\""
                                } else {
                                    "Nenhuma novela nesta categoria"
                                },
                                color = AppColors.TextSecondary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
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
