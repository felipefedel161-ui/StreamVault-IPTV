package com.streamvault.app.ui.components.boot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.R
import kotlinx.coroutines.delay

/**
 * Branded cold-start overlay. Shows StreamVault mark, then fades out into the app.
 */
@Composable
fun BootSplashHost(
    content: @Composable () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(1f) }
    val logoScale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(900)
        alpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha.value)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF070B12),
                                Color(0xFF0D1B2A),
                                Color(0xFF061018)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_vault_art),
                        contentDescription = "StreamVault",
                        modifier = Modifier
                            .size(120.dp)
                            .alpha(logoScale.value)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "StreamVault",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Televisão",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        letterSpacing = 3.sp
                    )
                }
            }
        }
    }
}
