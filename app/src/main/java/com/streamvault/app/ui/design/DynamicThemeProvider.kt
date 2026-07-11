package com.streamvault.app.ui.design

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides a dynamic accent color extracted from content artwork.
 * Falls back to AppColors.Brand when no artwork is available or the
 * extracted color has poor contrast against the dark canvas.
 */
val LocalDynamicAccent = compositionLocalOf { AppColors.Brand }

@Composable
fun DynamicThemeProvider(
    bitmap: Bitmap?,
    content: @Composable () -> Unit
) {
    var accentColor by remember { mutableStateOf(AppColors.Brand) }

    LaunchedEffect(bitmap) {
        if (bitmap == null) {
            accentColor = AppColors.Brand
            return@LaunchedEffect
        }
        accentColor = withContext(Dispatchers.Default) {
            extractAccent(bitmap) ?: AppColors.Brand
        }
    }

    CompositionLocalProvider(LocalDynamicAccent provides accentColor) {
        content()
    }
}

/**
 * Extracts a suitable accent color from [bitmap] using Palette API.
 * Prefers vibrant swatches; falls back through muted → dominant.
 * Returns null if no swatch meets the minimum contrast requirement.
 */
private fun extractAccent(bitmap: Bitmap): Color? {
    val palette = runCatching {
        Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()
    }.getOrNull() ?: return null

    val candidates = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.mutedSwatch,
        palette.lightMutedSwatch,
        palette.dominantSwatch
    )

    // Canvas luminance is ~0.01 (very dark). We need a color bright enough
    // to read on it — minimum contrast ratio of ~2.5:1.
    val canvasLuminance = Color(AppColors.Canvas.toArgb()).luminance()

    for (swatch in candidates) {
        val candidate = Color(swatch.rgb)
        val lum = candidate.luminance()
        // Contrast ratio = (lighter + 0.05) / (darker + 0.05)
        val contrast = (lum + 0.05f) / (canvasLuminance + 0.05f)
        if (contrast >= 2.5f) {
            // Slightly boost saturation/brightness for TV viewing distances
            return candidate.boostForTv()
        }
    }
    return null
}

/**
 * Nudges a color toward higher brightness while preserving hue, so it
 * reads clearly on a dark TV screen at living-room distances.
 */
private fun Color.boostForTv(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.85f).coerceIn(0f, 1f)   // slightly desaturate
    hsv[2] = (hsv[2] * 1.2f).coerceIn(0f, 1f)     // boost brightness
    return Color(android.graphics.Color.HSVToColor(hsv))
}
