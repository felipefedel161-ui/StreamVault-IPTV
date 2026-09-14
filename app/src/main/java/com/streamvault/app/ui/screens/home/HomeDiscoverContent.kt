package com.streamvault.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import coil3.compose.AsyncImage
import com.streamvault.app.ui.components.rememberCrossfadeImageModel
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.design.FocusSpec
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Provider
import com.streamvault.app.navigation.Routes

private val HomeBg = Color(0xFF0A0C12)
private val AccentBlue = Color(0xFF3B82F6)
private val TextMuted = Color(0xFFA1A1AA)

@Composable
fun HomeDiscoverContent(
    uiState: HomeUiState,
    onChannelClick: (Channel, Category?, Provider?, Long?, Long?) -> Unit,
    resolveProvider: (Channel) -> Provider?,
    onOpenCategory: (Category) -> Unit,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val recentIds = remember(uiState.recentChannels) {
        uiState.recentChannels.map { it.id }.toSet()
    }
    val recentMovies = remember(uiState.filteredChannels, uiState.recentChannels) {
        (uiState.filteredChannels + uiState.recentChannels)
            .distinctBy { it.id }
            .filter { ch ->
                val n = ch.name.lowercase()
                !n.contains("futebol") && !n.contains("ao vivo") && !n.contains("news")
            }
            .take(18)
    }
    val recommended = remember(uiState.filteredChannels, uiState.recentChannels) {
        val fav = (uiState.filteredChannels + uiState.recentChannels).filter { it.isFavorite }
        (fav + uiState.filteredChannels).distinctBy { it.id }.take(12)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBg),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // ===== Filmes adicionados recentemente =====
        if (recentMovies.isNotEmpty()) {
            item(key = "recent-movies") {
                SectionTitle("Filmes adicionados recentemente")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(recentMovies, key = { _, c -> "rm-${c.id}" }) { index, ch ->
                        val rating = 6.5f + (index % 25) / 10f
                        val progress = if (ch.id in recentIds) {
                            ((index * 17 + 28) % 70 + 15) / 100f
                        } else null
                        MoviePosterCard(
                            channel = ch,
                            rating = rating,
                            progress = progress,
                            onClick = {
                                onChannelClick(ch, null, resolveProvider(ch), null, null)
                            }
                        )
                    }
                }
            }
        }

        // ===== Recomendados para você =====
        if (recommended.isNotEmpty()) {
            item(key = "recommended") {
                SectionTitle("Recomendados para você")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(recommended, key = { _, c -> "rec-${c.id}" }) { index, ch ->
                        RecommendedCard(
                            channel = ch,
                            rating = 7.0f + (index % 20) / 10f,
                            reviewsLabel = listOf("4.5M", "1.2M", "890K", "2.1M")[index % 4],
                            onClick = {
                                onChannelClick(ch, null, resolveProvider(ch), null, null)
                            }
                        )
                    }
                }
            }
        }

        // ===== Atalhos ao vivo =====
        item(key = "shortcuts") {
            SectionTitle("Atalhos ao vivo")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiveShortcutCard(
                    icon = Icons.Default.Tv,
                    title = "TV ao vivo",
                    subtitle = "Canais ao vivo",
                    onClick = { onNavigate(Routes.LIVE_TV) },
                    modifier = Modifier.weight(1f)
                )
                LiveShortcutCard(
                    icon = Icons.Default.Star,
                    title = "Esportes ao vivo",
                    subtitle = "Arena · AO VIVO",
                    onClick = { onNavigate(Routes.FOOTBALL) },
                    modifier = Modifier.weight(1f)
                )
                LiveShortcutCard(
                    icon = Icons.Default.MusicNote,
                    title = "Rádio online",
                    subtitle = "Música e mais",
                    onClick = { onNavigate(Routes.RADIO) },
                    modifier = Modifier.weight(1f)
                )
                LiveShortcutCard(
                    icon = Icons.Default.PlayArrow,
                    title = "Novelas",
                    subtitle = "Capítulos de hoje",
                    onClick = { onNavigate(Routes.NOVELAS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun CinematicHero(
    channel: Channel,
    onPlay: () -> Unit,
    onAddList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val program = channel.currentProgram
    val title = program?.title?.takeIf { it.isNotBlank() } ?: channel.name
    val description = program?.description?.takeIf { it.isNotBlank() }
        ?: "Assista agora em alta qualidade no StreamVault."
    val imageUrl = channel.logoUrl
    val model = rememberCrossfadeImageModel(imageUrl)

    TvClickableSurface(
        onClick = onPlay,
        modifier = modifier.height(320.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF12151C),
            focusedContainerColor = Color(0xFF161A24)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                shape = RoundedCornerShape(20.dp)
            )
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f)
                        .align(Alignment.CenterEnd)
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A2030), Color(0xFF0E121A))
                            )
                        )
                )
            }

            // Gradients for readability
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to HomeBg,
                            0.42f to HomeBg.copy(alpha = 0.92f),
                            0.72f to HomeBg.copy(alpha = 0.35f),
                            1f to Color.Transparent
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to HomeBg.copy(alpha = 0.5f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.48f)
                    .padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "EXCLUSIVO  ·  DESTAQUE  ·  2025",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 40.sp
                )
                val meta = buildList {
                    channel.groupTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                    channel.categoryName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add("HD")
                    program?.let {
                        val mins = ((it.endTime - it.startTime) / 60_000L).coerceAtLeast(0)
                        if (mins > 0) add("${mins}min")
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = onPlay,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AccentBlue,
                            focusedContainerColor = Color(0xFF60A5FA),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Text("Assistir agora", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                    Surface(
                        onClick = onAddList,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(999.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(1.5.dp, AccentBlue),
                                shape = RoundedCornerShape(999.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Minha lista", color = Color.White)
                        }
                    }
                }
            }

            // Carousel dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(if (i == 0) 8.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) AccentBlue else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF12151C)),
        contentAlignment = Alignment.Center
    ) {
        Text("Carregando destaques…", color = TextMuted)
    }
}

@Composable
private fun ContinueWatchingCard(
    channel: Channel,
    progress: Float,
    onClick: () -> Unit
) {
    val model = rememberCrossfadeImageModel(channel.logoUrl)
    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(220.dp).height(124.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141820),
            focusedContainerColor = Color(0xFF1A2030)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                            .background(AccentBlue)
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}% assistido",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MoviePosterCard(
    channel: Channel,
    rating: Float,
    progress: Float? = null,
    onClick: () -> Unit
) {
    val model = rememberCrossfadeImageModel(channel.logoUrl)
    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141820),
            focusedContainerColor = Color(0xFF1A2030)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1C2230)))
                }
                // Rating badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                    Text(
                        text = String.format("%.1f", rating),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = channel.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            if (progress != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                            .background(AccentBlue)
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun RecommendedCard(
    channel: Channel,
    rating: Float,
    reviewsLabel: String,
    onClick: () -> Unit
) {
    val model = rememberCrossfadeImageModel(channel.logoUrl)
    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(280.dp).height(110.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141820),
            focusedContainerColor = Color(0xFF1A2030)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(88.dp)
                    .fillMaxHeight()
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1C2230)))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.groupTitle?.takeIf { it.isNotBlank() } ?: "Série · Drama",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", rating),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  ·  $reviewsLabel avaliações",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveShortcutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141820),
            focusedContainerColor = Color(0xFF1C2433)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AccentBlue),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
