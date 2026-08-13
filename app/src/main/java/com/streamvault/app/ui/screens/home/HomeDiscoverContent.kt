package com.streamvault.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Provider

/**
 * Google TV–style discover home: hero + horizontal rows.
 * Used on Android TV in comfortable mode for a modern 10-foot first impression.
 */
@Composable
fun HomeDiscoverContent(
    uiState: HomeUiState,
    onChannelClick: (Channel, Category?, Provider?, Long?, Long?) -> Unit,
    resolveProvider: (Channel) -> Provider?,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val heroChannel = remember(uiState.recentChannels, uiState.filteredChannels) {
        uiState.recentChannels.firstOrNull()
            ?: uiState.filteredChannels.firstOrNull { it.currentProgram != null }
            ?: uiState.filteredChannels.firstOrNull()
    }
    val favorites = remember(uiState.filteredChannels, uiState.recentChannels) {
        (uiState.filteredChannels + uiState.recentChannels)
            .filter { it.isFavorite }
            .distinctBy { it.id }
            .take(24)
    }
    val recent = remember(uiState.recentChannels) {
        uiState.recentChannels.distinctBy { it.id }.take(24)
    }
    val liveNow = remember(uiState.filteredChannels) {
        uiState.filteredChannels
            .filter { it.currentProgram?.title?.isNotBlank() == true }
            .take(24)
    }
    val topCategories = remember(uiState.categories) {
        uiState.categories.take(8)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero
        item(key = "hero") {
            if (heroChannel != null) {
                val program = heroChannel.currentProgram
                BrowseHeroPanel(
                    title = heroChannel.name,
                    subtitle = program?.title?.takeIf { it.isNotBlank() }
                        ?: heroChannel.categoryName
                        ?: uiState.activeLiveSourceTitle.ifBlank { "Ao vivo agora" },
                    imageUrl = heroChannel.logoUrl,
                    eyebrow = "AO VIVO",
                    metadata = buildList {
                        if (heroChannel.number > 0) add("Ch ${heroChannel.number}")
                        program?.title?.takeIf { it.isNotBlank() }?.let { add(it) }
                    },
                    actionLabel = "Assistir",
                    onClick = {
                        onChannelClick(
                            heroChannel,
                            uiState.selectedCategory,
                            resolveProvider(heroChannel),
                            null,
                            null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "StreamVault",
                        style = MaterialTheme.typography.displaySmall,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adicione uma lista para começar a assistir",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "row-recent") {
                DiscoverRow(
                    title = "Continuar assistindo",
                    channels = recent,
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "row-fav") {
                DiscoverRow(
                    title = "Favoritos",
                    channels = favorites,
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        if (liveNow.isNotEmpty()) {
            item(key = "row-live") {
                DiscoverRow(
                    title = "No ar agora",
                    channels = liveNow,
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        // Category chips as quick jumps
        if (topCategories.isNotEmpty()) {
            item(key = "row-cats") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Categorias",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(topCategories, key = { it.id }) { cat ->
                            TvClickableSurface(
                                onClick = { onOpenCategory(cat) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = AppColors.SurfaceElevated,
                                    focusedContainerColor = AppColors.SurfaceAccent
                                )
                            ) {
                                Text(
                                    text = cat.name,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    color = AppColors.TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // More channels from current filter as a row
        if (uiState.filteredChannels.isNotEmpty()) {
            item(key = "row-all") {
                DiscoverRow(
                    title = uiState.selectedCategory?.name ?: "Todos os canais",
                    channels = uiState.filteredChannels.take(30),
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun DiscoverRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                DiscoverChannelCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceAccent
        ),
        modifier = Modifier.width(168.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = channel.name,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = channel.currentProgram?.title?.takeIf { it.isNotBlank() }
                    ?: channel.categoryName
                    ?: if (channel.number > 0) "Ch ${channel.number}" else "Canal",
                color = AppColors.TextSecondary,
                maxLines = 2,
                fontSize = 12.sp
            )
        }
    }
}
