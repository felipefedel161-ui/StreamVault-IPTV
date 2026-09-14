package com.streamvault.app.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.ui.interaction.TvClickableSurface
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.rememberCrossfadeImageModel
import com.streamvault.app.ui.components.shell.BrowseHeroPanel
import com.streamvault.app.ui.design.AppColors
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Provider

/**
 * Home estilo streaming moderno: hero cinematográfico + fileiras horizontais.
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
        uiState.categories.take(10)
    }
    val top10 = remember(uiState.filteredChannels, favorites, recent) {
        (favorites + recent + uiState.filteredChannels)
            .distinctBy { it.id }
            .take(10)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "hero") {
            if (heroChannel != null) {
                val program = heroChannel.currentProgram
                BrowseHeroPanel(
                    title = heroChannel.name,
                    subtitle = program?.title?.takeIf { it.isNotBlank() }
                        ?: heroChannel.categoryName
                        ?: uiState.activeLiveSourceTitle.ifBlank { "Ao vivo agora" },
                    imageUrl = heroChannel.logoUrl,
                    eyebrow = "EM DESTAQUE · AO VIVO",
                    metadata = buildList {
                        if (heroChannel.number > 0) add("Ch ${heroChannel.number}")
                        heroChannel.categoryName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    },
                    actionLabel = "▶  Assistir",
                    onClick = {
                        onChannelClick(
                            heroChannel,
                            uiState.selectedCategory,
                            resolveProvider(heroChannel),
                            null,
                            null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                EmptyHomeBanner()
            }
        }

        if (top10.isNotEmpty()) {
            item(key = "row-top10") {
                TopTenRow(
                    title = "Top 10 no XtreamVault",
                    channels = top10,
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
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
                    title = "Meus favoritos",
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
                    showLiveBadge = true,
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        topCategories.forEach { category ->
            val channelsInCat = uiState.filteredChannels
                .filter { it.categoryName == category.name || it.categoryId == category.id }
                .take(20)
            if (channelsInCat.isNotEmpty()) {
                item(key = "row-cat-${category.id}") {
                    DiscoverRow(
                        title = category.name,
                        channels = channelsInCat,
                        onChannelClick = { ch ->
                            onChannelClick(ch, category, resolveProvider(ch), null, null)
                        },
                        onSeeAll = { onOpenCategory(category) }
                    )
                }
            }
        }

        if (uiState.filteredChannels.isNotEmpty() && topCategories.isEmpty()) {
            item(key = "row-all") {
                DiscoverRow(
                    title = uiState.selectedCategory?.name ?: "Todos os canais",
                    channels = uiState.filteredChannels.take(36),
                    onChannelClick = { ch ->
                        onChannelClick(ch, uiState.selectedCategory, resolveProvider(ch), null, null)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun EmptyHomeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(AppColors.Canvas, AppColors.SurfaceElevated, AppColors.HeroAccent)
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                text = "XtreamVault",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Adicione uma lista para começar a assistir",
                color = AppColors.TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun DiscoverRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    showLiveBadge: Boolean = false,
    onSeeAll: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (onSeeAll != null) {
                Text(
                    text = "Ver tudo ›",
                    color = AppColors.BrandStrong,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                DiscoverChannelCard(
                    channel = channel,
                    showLiveBadge = showLiveBadge,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun TopTenRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                TopTenCard(
                    rank = index + 1,
                    channel = channel,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun TopTenCard(
    rank: Int,
    channel: Channel,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = rank.toString(),
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 72.sp,
            modifier = Modifier.padding(end = 2.dp)
        )
        TvClickableSurface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppColors.SurfaceElevated,
                focusedContainerColor = AppColors.SurfaceAccent
            ),
            modifier = Modifier.width(120.dp)
        ) {
            Column {
                ChannelArt(
                    logoUrl = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                )
                Text(
                    text = channel.name,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscoverChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    showLiveBadge: Boolean = false
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceAccent
        ),
        modifier = Modifier.width(200.dp)
    ) {
        Column {
            Box {
                ChannelArt(
                    logoUrl = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                if (showLiveBadge || channel.currentProgram?.title?.isNotBlank() == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.Live)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AO VIVO",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = channel.name,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = channel.currentProgram?.title?.takeIf { it.isNotBlank() }
                        ?: channel.categoryName
                        ?: if (channel.number > 0) "Ch ${channel.number}" else "Canal",
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelArt(
    logoUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(AppColors.SurfaceEmphasis),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberCrossfadeImageModel(logoUrl),
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = name.take(2).uppercase(),
                color = AppColors.BrandStrong,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp
            )
        }
    }
}
