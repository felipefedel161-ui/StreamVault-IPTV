package com.streamvault.app.ui.screens.football

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.football.FootballFixture
import com.streamvault.app.football.FootballPrediction
import com.streamvault.app.football.FootballTeam
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.Channel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PitchGreenDeep = Color(0xFF071F18)
private val NeonLime = Color(0xFFB8FF3C)
private val LiveRed = Color(0xFFFF3B4E)
private val CardGlass = Color(0xFF0F1F1A)
private val Gold = Color(0xFFFFC857)

@Composable
fun FootballScreen(
    onNavigate: (String) -> Unit,
    onWatchChannel: (Channel) -> Unit,
    currentRoute: String,
    viewModel: FootballViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Arena",
        subtitle = "Centro de Futebol",
        showScreenHeader = false,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(PitchGreenDeep, Color(0xFF050A08), Color(0xFF0A0A0C))
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FootballHeader(
                    liveCount = state.fixtures.count { it.isLive },
                    onRefresh = viewModel::refresh
                )
                TabRow(
                    selected = state.tab,
                    onSelect = viewModel::selectTab
                )
                state.scheduleMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .background(NeonLime.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = msg, color = Color.White, fontSize = 13.sp)
            }
        }

                when {
                    state.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonLime)
                    }
                    state.error != null -> ErrorBlock(state.error!!, viewModel::refresh)
                    state.fixtures.isEmpty() -> EmptyBlock(state.tab)
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val live = state.fixtures.filter { it.isLive }
                        val rest = state.fixtures.filterNot { it.isLive }
                        if (live.isNotEmpty()) {
                            item { SectionTitle("AO VIVO", NeonLime) }
                            items(live, key = { it.id ?: it.hashCode() }) { fixture ->
                                MatchCard(
                                    fixture = fixture,
                                    channels = state.matchedChannels[fixture.id].orEmpty(),
                                    scheduled = fixture.id != null && state.scheduledFixtureIds.contains(fixture.id),
                                    onWatch = onWatchChannel,
                                    onSchedule = { f, ch -> viewModel.toggleSchedule(f, ch) },
                                    onPrediction = { id -> viewModel.loadPrediction(id) }
                                )
                            }
                        }
                        if (rest.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    if (state.tab == FootballTab.TODAY) "AGENDA DO DIA" else "OUTROS JOGOS",
                                    Color.White.copy(alpha = 0.7f)
                                )
                            }
                            items(rest, key = { it.id ?: it.hashCode() }) { fixture ->
                                MatchCard(
                                    fixture = fixture,
                                    channels = state.matchedChannels[fixture.id].orEmpty(),
                                    scheduled = fixture.id != null && state.scheduledFixtureIds.contains(fixture.id),
                                    onWatch = onWatchChannel,
                                    onSchedule = { f, ch -> viewModel.toggleSchedule(f, ch) },
                                    onPrediction = { id -> viewModel.loadPrediction(id) }
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }

            if (state.selectedFixtureId != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    PredictionSheet(
                        loading = state.predictionLoading,
                        prediction = state.prediction,
                        onClose = viewModel::clearPrediction
                    )
                }
            }
        }
    }
}

@Composable
private fun FootballHeader(liveCount: Int, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ARENA",
                style = MaterialTheme.typography.labelMedium,
                color = NeonLime,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                text = "Centro de Futebol",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (liveCount > 0) {
                    "$liveCount jogo(s) ao vivo agora"
                } else {
                    "Grandes competições do mundo"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
        if (liveCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiveRed.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LiveRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("LIVE", color = LiveRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        TvButton(onClick = onRefresh) {
            Text("Atualizar")
        }
    }
}

@Composable
private fun TabRow(selected: FootballTab, onSelect: (FootballTab) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabChip("Ao vivo", selected == FootballTab.LIVE) { onSelect(FootballTab.LIVE) }
        TabChip("Hoje", selected == FootballTab.TODAY) { onSelect(FootballTab.TODAY) }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NeonLime else Color.Transparent,
            focusedContainerColor = if (selected) NeonLime else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            color = if (selected) PitchGreenDeep else Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SectionTitle(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun MatchCard(
    fixture: FootballFixture,
    channels: List<Channel>,
    scheduled: Boolean,
    onWatch: (Channel) -> Unit,
    onSchedule: (FootballFixture, Channel?) -> Unit,
    onPrediction: (Int) -> Unit
) {
    // Non-clickable container so channel chips receive TV focus/clicks
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGlass, RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        if (fixture.isLive) LiveRed.copy(alpha = 0.5f) else NeonLime.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fixture.league.logo.isNotBlank()) {
                AsyncImage(
                    model = fixture.league.logo,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = fixture.league.name.ifBlank { "Competição" },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(fixture)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamBlock(fixture.home, Alignment.Start, Modifier.weight(1f))
            ScoreBlock(fixture)
            TeamBlock(fixture.away, Alignment.End, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary actions row — TV-friendly discrete buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (channels.isNotEmpty()) {
                val primary = channels.first()
                TvClickableSurface(
                    onClick = { onWatch(primary) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = NeonLime.copy(alpha = 0.22f),
                        focusedContainerColor = NeonLime.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = if (fixture.isLive) "▶ ASSISTIR" else "▶ ABRIR CANAL",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (!fixture.isLive && !fixture.isFinished) {
                TvClickableSurface(
                    onClick = { onSchedule(fixture, channels.firstOrNull()) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (scheduled) NeonLime.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.22f)
                    )
                ) {
                    Text(
                        text = if (scheduled) "✓ AGENDADO" else "🔔 AGENDAR",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            fixture.id?.let { fid ->
                TvClickableSurface(
                    onClick = { onPrediction(fid) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.18f)
                    )
                ) {
                    Text(
                        text = "PALPITE",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (channels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "CANAIS NA SUA LISTA",
                color = NeonLime.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels.take(8), key = { it.id }) { ch ->
                    TvClickableSurface(
                        onClick = { onWatch(ch) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = NeonLime.copy(alpha = 0.12f),
                            focusedContainerColor = NeonLime.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = ch.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nenhum canal correspondente na M3U — use Palpite ou busque no ao vivo",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TeamBlock(team: FootballTeam, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = align) {
        if (team.logo.isNotBlank()) {
            AsyncImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = team.name.ifBlank { "—" },
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (align == Alignment.Start) TextAlign.Start else TextAlign.End
        )
    }
}

@Composable
private fun ScoreBlock(fixture: FootballFixture) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        if (fixture.isLive || fixture.isFinished || fixture.goals.home != null) {
            Text(
                text = "${fixture.goals.home ?: 0}  –  ${fixture.goals.away ?: 0}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
        } else {
            Text(
                text = formatKickoff(fixture.timestamp),
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Text(
            text = if (fixture.isLive && fixture.elapsed != null) {
                "${fixture.elapsed}'"
            } else {
                fixture.statusLong.ifBlank { fixture.status }
            },
            color = if (fixture.isLive) LiveRed else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBadge(fixture: FootballFixture) {
    val (label, color) = when {
        fixture.isLive -> "AO VIVO" to LiveRed
        fixture.isFinished -> "ENCERRADO" to Color.White.copy(alpha = 0.4f)
        else -> "AGENDADO" to Gold
    }
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun PredictionSheet(
    loading: Boolean,
    prediction: FootballPrediction?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF101A16))
            .padding(24.dp)
    ) {
        Text(
            "PALPITE PRÉ-JOGO",
            color = NeonLime,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator(color = NeonLime, modifier = Modifier.size(28.dp))
            prediction == null -> Text(
                "Sem predição disponível para este jogo.",
                color = Color.White.copy(alpha = 0.6f)
            )
            else -> {
                prediction.advice?.let {
                    Text(it, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                prediction.winner?.let {
                    Text("Favorito: $it", color = Gold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                PercentBar("Casa", prediction.percentHome)
                PercentBar("Empate", prediction.percentDraw)
                PercentBar("Fora", prediction.percentAway)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TvButton(onClick = onClose) { Text("Fechar") }
    }
}

@Composable
private fun PercentBar(label: String, percent: String?) {
    val value = percent?.replace("%", "")?.toFloatOrNull()?.div(100f) ?: 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(64.dp),
            fontSize = 13.sp
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = NeonLime,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            percent ?: "—",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color.White.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(12.dp))
        TvButton(onClick = onRetry) { Text("Tentar de novo") }
    }
}

@Composable
private fun EmptyBlock(tab: FootballTab) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (tab == FootballTab.LIVE) {
                "Nenhum jogo ao vivo no momento"
            } else {
                "Sem jogos das grandes ligas hoje"
            },
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

private fun formatKickoff(ts: Long?): String {
    if (ts == null || ts <= 0L) return "--:--"
    return try {
        val dt = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("HH:mm").format(dt)
    } catch (_: Exception) {
        "--:--"
    }
}
