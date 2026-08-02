package com.streamvault.app.ui.screens.football

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.R
import com.streamvault.app.ui.components.ChannelLogoBadge
import com.streamvault.app.ui.components.rememberCrossfadeImageModel
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FootballScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    viewModel: FootballViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var detailMatch by remember { mutableStateOf<FootballMatch?>(null) }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = stringResource(R.string.nav_football),
        subtitle = if (!uiState.isLoading && !uiState.isEmpty) {
            "${uiState.liveMatches.size} ao vivo · ${uiState.upcomingMatches.size} em breve"
        } else null,
        topBarActions = {
            TvButton(
                onClick = viewModel::refresh,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_refresh),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> FootballLoadingState()
                uiState.isEmpty -> FootballEmptyState()
                else -> FootballFeed(
                    uiState = uiState,
                    onMatchClick = { match ->
                        detailMatch = match
                    },
                    onWatchClick = { match ->
                        onChannelClick(match.channel)
                    }
                )
            }

            // Match detail bottom sheet
            AnimatedVisibility(
                visible = detailMatch != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                // Dim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { detailMatch = null }
                )
            }

            AnimatedVisibility(
                visible = detailMatch != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                detailMatch?.let { match ->
                    MatchDetailSheet(
                        match = match,
                        onDismiss = { detailMatch = null },
                        onWatch = {
                            detailMatch = null
                            onChannelClick(match.channel)
                        }
                    )
                }
            }
        }
    }
}

// ─── Feed ────────────────────────────────────────────────────────────────────

@Composable
private fun FootballFeed(
    uiState: FootballUiState,
    onMatchClick: (FootballMatch) -> Unit,
    onWatchClick: (FootballMatch) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── Live now ──────────────────────────────────────────────────────
        if (uiState.liveMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Ao Vivo Agora",
                    count = uiState.liveMatches.size,
                    isLive = true
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.liveMatches, key = { it.channel.id }) { match ->
                        LiveMatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onWatch = { onWatchClick(match) }
                        )
                    }
                }
            }
        }

        // ── Upcoming ──────────────────────────────────────────────────────
        if (uiState.upcomingMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Em Breve",
                    count = uiState.upcomingMatches.size,
                    isLive = false
                )
            }
            items(uiState.upcomingMatches, key = { "upcoming_${it.channel.id}" }) { match ->
                UpcomingMatchRow(
                    match = match,
                    onClick = { onMatchClick(match) }
                )
            }
        }
    }
}

// ─── Live Match Card (horizontal scroll, big) ────────────────────────────────

@Composable
private fun LiveMatchCard(
    match: FootballMatch,
    onClick: () -> Unit,
    onWatch: () -> Unit
) {
    val prog = match.currentProgram
    val progressAnim by animateFloatAsState(
        targetValue = match.progressFraction,
        animationSpec = tween(600),
        label = "progress"
    )

    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.SurfaceElevated)
        ) {
            // Background channel logo (very dim, decorative)
            if (!match.channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = rememberCrossfadeImageModel(match.channel.logoUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.07f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gradient from bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to AppColors.SurfaceElevated.copy(alpha = 0.6f),
                            1f to AppColors.SurfaceElevated
                        )
                    )
            )

            Column(modifier = Modifier.padding(16.dp)) {

                // LIVE badge + channel name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiveBadge()
                    Text(
                        text = match.channel.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Teams — using channel logo as team badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamBadge(
                        name = prog?.title?.extractHomeTeam() ?: match.channel.name,
                        logoUrl = match.channel.logoUrl,
                        modifier = Modifier.weight(1f)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = AppColors.TextTertiary,
                            fontSize = 18.sp
                        )
                    }
                    TeamBadge(
                        name = prog?.title?.extractAwayTeam() ?: "",
                        logoUrl = null,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Program title
                if (prog != null) {
                    Text(
                        text = prog.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Progress bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppColors.SurfaceAccent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressAnim)
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AppColors.Live, AppColors.Live.copy(alpha = 0.6f))
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        prog?.let {
                            Text(
                                text = formatTime(it.startTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${it.durationMinutes}min",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Watch button
                TvButton(
                    onClick = onWatch,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text(
                        text = "▶ Assistir",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Upcoming Row ────────────────────────────────────────────────────────────

@Composable
private fun UpcomingMatchRow(
    match: FootballMatch,
    onClick: () -> Unit
) {
    val prog = match.nextProgram ?: return
    val minutesUntil = ((prog.startTime - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)

    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel logo
            ChannelLogoBadge(
                channelName = match.channel.name,
                logoUrl = match.channel.logoUrl,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prog.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${match.channel.name} · ${prog.genre?.ifBlank { null } ?: "Futebol"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTime(prog.startTime),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.Brand,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "em ${minutesUntil}min",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ─── Match Detail Sheet ───────────────────────────────────────────────────────

@Composable
private fun MatchDetailSheet(
    match: FootballMatch,
    onDismiss: () -> Unit,
    onWatch: () -> Unit
) {
    val prog = match.currentProgram ?: match.nextProgram

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AppColors.CanvasElevated)
            .clickable(enabled = false, onClick = {})
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {

            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppColors.SurfaceAccent)
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (match.isLive) LiveBadge()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = match.channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✕",
                        color = AppColors.TextTertiary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Teams
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamBadge(
                    name = prog?.title?.extractHomeTeam() ?: match.channel.name,
                    logoUrl = match.channel.logoUrl,
                    modifier = Modifier.weight(1f),
                    large = true
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (match.isLive) {
                        Text(
                            text = "AO VIVO",
                            color = AppColors.Live,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = if (match.isLive) prog?.let { formatTime(it.startTime) } ?: "LIVE"
                        else prog?.let { formatTime(it.startTime) } ?: "--:--",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = AppColors.TextPrimary
                    )
                }
                TeamBadge(
                    name = prog?.title?.extractAwayTeam() ?: "",
                    logoUrl = null,
                    modifier = Modifier.weight(1f),
                    large = true
                )
            }

            // Progress (live only)
            if (match.isLive && match.currentProgram != null) {
                val prog2 = match.currentProgram
                val elapsed = System.currentTimeMillis() - prog2.startTime
                val elapsedMin = (elapsed / 60_000).toInt().coerceAtLeast(0)
                val totalMin = prog2.durationMinutes

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${elapsedMin}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Live
                        )
                        Text(
                            text = "${totalMin}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextTertiary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppColors.SurfaceEmphasis)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(match.progressFraction)
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AppColors.Live, Color(0xFFFF9A3E))
                                    )
                                )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Program description
            if (!prog?.description.isNullOrBlank()) {
                Text(
                    text = prog!!.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            // Metadata chips
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                prog?.genre?.takeIf { it.isNotBlank() }?.let {
                    MetaChip(it)
                }
                prog?.rating?.takeIf { it.isNotBlank() }?.let {
                    MetaChip("⭐ $it")
                }
                if (match.isLive) MetaChip("🔴 Ao vivo")
                else prog?.let { MetaChip(formatTime(it.startTime)) }
            }

            Spacer(Modifier.height(20.dp))

            // Watch button
            TvButton(
                onClick = onWatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = if (match.isLive) "▶ Assistir ao Vivo" else "▶ Abrir Canal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, isLive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AppColors.Live)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isLive) AppColors.Live.copy(alpha = 0.18f) else AppColors.BrandMuted)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive) AppColors.Live else AppColors.Brand,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Live.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(AppColors.Live)
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Live,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun TeamBadge(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val logoSize = if (large) 56.dp else 40.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChannelLogoBadge(
            channelName = name,
            logoUrl = logoUrl,
            modifier = Modifier
                .size(logoSize)
                .clip(CircleShape),
            shape = CircleShape,
            backgroundColor = AppColors.SurfaceEmphasis
        )
        if (name.isNotBlank()) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = if (large) 12.sp else 10.sp,
                fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceEmphasis)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun FootballLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⚽", fontSize = 48.sp)
            Text(
                text = "Buscando jogos no guia...",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun FootballEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🏟️", fontSize = 56.sp)
            Text(
                text = "Nenhum jogo encontrado",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Certifique-se de que o guia de programação (EPG) está configurado e que há canais de esportes disponíveis.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String =
    if (millis > 0) timeFormat.format(Date(millis)) else "--:--"

/**
 * Naive heuristic: many EPG titles use patterns like "Team A x Team B",
 * "Team A vs Team B", "Team A - Team B".
 */
private fun String.extractHomeTeam(): String =
    split(Regex(" [xX×vV][sS]? | [-–] ", setOf(RegexOption.IGNORE_CASE)))
        .firstOrNull()?.trim() ?: this

private fun String.extractAwayTeam(): String =
    split(Regex(" [xX×vV][sS]? | [-–] ", setOf(RegexOption.IGNORE_CASE)))
        .getOrNull(1)?.trim() ?: ""
