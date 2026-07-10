package com.streamvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.app.R
import com.streamvault.app.ui.components.shell.ContentMetadataStrip
import com.streamvault.app.ui.components.shell.StatusPill
import com.streamvault.app.ui.components.rememberCrossfadeImageModel
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series

/**
 * Lightweight detail sheet that slides up over the current screen when the user
 * clicks a card. Shows artwork, metadata, plot and quick-action buttons without
 * navigating away. A "Ver detalhes completos" button opens the full detail screen.
 */

sealed class DetailSheetContent {
    data class ForMovie(val movie: Movie) : DetailSheetContent()
    data class ForSeries(val series: Series) : DetailSheetContent()
}

@Composable
fun ContentDetailSheet(
    content: DetailSheetContent?,
    onDismiss: () -> Unit,
    onPlayMovie: (Movie) -> Unit = {},
    onPlaySeries: (Series) -> Unit = {},
    onOpenMovieDetail: (Movie) -> Unit = {},
    onOpenSeriesDetail: (Series) -> Unit = {}
) {
    AnimatedVisibility(
        visible = content != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
        )
    }

    AnimatedVisibility(
        visible = content != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            when (val c = content) {
                is DetailSheetContent.ForMovie -> MovieSheet(
                    movie = c.movie,
                    onDismiss = onDismiss,
                    onPlay = { onPlayMovie(c.movie) },
                    onOpenDetail = { onOpenMovieDetail(c.movie) }
                )
                is DetailSheetContent.ForSeries -> SeriesSheet(
                    series = c.series,
                    onDismiss = onDismiss,
                    onPlay = { onPlaySeries(c.series) },
                    onOpenDetail = { onOpenSeriesDetail(c.series) }
                )
                null -> {}
            }
        }
    }
}

@Composable
private fun MovieSheet(
    movie: Movie,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit
) {
    SheetScaffold(
        artworkUrl = movie.backdropUrl ?: movie.posterUrl,
        posterUrl = movie.posterUrl,
        title = movie.name,
        metadataValues = listOfNotNull(
            movie.year,
            movie.duration?.takeIf { it.isNotBlank() },
            movie.genre?.takeIf { it.isNotBlank() },
            movie.rating.takeIf { it > 0f }?.let { "★ ${"%.1f".format(it)}" }
        ),
        plot = movie.plot,
        extraInfo = listOfNotNull(
            movie.director?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.movie_detail_director) + ": $it" },
            movie.cast?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.movie_detail_cast) + ": $it" }
        ),
        onDismiss = onDismiss,
        onPlay = onPlay,
        onOpenDetail = onOpenDetail
    )
}

@Composable
private fun SeriesSheet(
    series: Series,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit
) {
    SheetScaffold(
        artworkUrl = series.backdropUrl ?: series.posterUrl,
        posterUrl = series.posterUrl,
        title = series.name,
        metadataValues = listOfNotNull(
            series.genre?.takeIf { it.isNotBlank() },
            series.rating.takeIf { it > 0f }?.let { "★ ${"%.1f".format(it)}" },
            series.seasons.size.takeIf { it > 0 }
                ?.let { "$it ${if (it == 1) "temporada" else "temporadas"}" }
        ),
        plot = series.plot,
        extraInfo = listOfNotNull(
            series.cast?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.movie_detail_cast) + ": $it" }
        ),
        onDismiss = onDismiss,
        onPlay = onPlay,
        onOpenDetail = onOpenDetail
    )
}

@Composable
private fun SheetScaffold(
    artworkUrl: String?,
    posterUrl: String?,
    title: String,
    metadataValues: List<String>,
    plot: String?,
    extraInfo: List<String>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AppColors.CanvasElevated)
            .clickable(enabled = false, onClick = {}) // prevent dismiss on sheet touch
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Backdrop hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberCrossfadeImageModel(artworkUrl),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        AppColors.CanvasElevated
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppColors.SurfaceEmphasis)
                    )
                }

                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            // Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 0.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Poster
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberCrossfadeImageModel(posterUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(90.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.SurfaceElevated)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (metadataValues.isNotEmpty()) {
                        ContentMetadataStrip(values = metadataValues)
                    }

                    if (!plot.isNullOrBlank()) {
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    extraInfo.forEach { info ->
                        Text(
                            text = info,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            onClick = onPlay,
                            modifier = Modifier.widthIn(min = 110.dp)
                        ) {
                            Text(stringResource(R.string.player_play))
                        }
                        TvClickableSurface(
                            onClick = onOpenDetail,
                            modifier = Modifier.widthIn(min = 110.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.content_detail_full),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
