package com.streamvault.app.profiles

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.ui.components.dialogs.PinDialog
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import kotlinx.coroutines.delay

/**
 * Netflix-style "Quem está assistindo?" profile selection screen.
 *
 * Features:
 * - Up to 4 user profiles with animated avatar cards
 * - Staggered entrance animation
 * - PIN dialog for locked profiles
 * - Long-press to edit profile
 * - "+ Adicionar perfil" when slots are available
 * - Each profile has isolated content (via activeProfileId in repository)
 */
@Composable
fun ProfileSelectionScreen(
    onProfileSelected: (UserProfile) -> Unit,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Animate in the whole screen
    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        screenVisible = true
    }

    // PIN entry for locked profiles
    val pendingId = uiState.pendingPinProfileId
    val pendingProfile = pendingId?.let { id -> uiState.profiles.firstOrNull { it.id == id } }
    if (pendingProfile != null) {
        PinDialog(
            onDismissRequest = { viewModel.dismissPin() },
            onPinEntered = { pin ->
                viewModel.onPinSubmitted(pin) { profile -> onProfileSelected(profile) }
            },
            title = "PIN de ${pendingProfile.name}",
            error = if (uiState.pinError) "PIN incorreto. Tente novamente." else null
        )
    }

    // Edit / Create dialog
    val editing = uiState.editingProfile
    val creating = uiState.isCreatingNew
    if (editing != null || creating) {
        EditProfileDialog(
            profile = editing,
            canDelete = uiState.profiles.size > 1,
            onSave = { name, avatarIndex, color, pin ->
                viewModel.saveProfile(editing?.id, name, avatarIndex, color, pin)
            },
            onDelete = editing?.let { { viewModel.deleteProfile(it.id) } },
            onDismiss = { viewModel.cancelEdit() }
        )
    }

    // Full-screen background with multi-layer depth effect
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF050D15),
                        0.4f to Color(0xFF07111B),
                        0.8f to Color(0xFF0A1523),
                        1.0f to Color(0xFF050D15)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow blobs for depth
        AmbientGlow()

        // Main content
        AnimatedVisibility(
            visible = screenVisible,
            enter = fadeIn(tween(600)) + slideInVertically(
                tween(600, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 12 }
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(52.dp),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // Header
                ScreenHeader()

                // Profile cards row with staggered entrance
                ProfileCardRow(
                    profiles = uiState.profiles,
                    onProfileClick = { viewModel.onProfileSelected(it) },
                    onProfileLongClick = { viewModel.startEditProfile(it) },
                    onAddProfile = { viewModel.startCreateProfile() },
                    screenVisible = screenVisible
                )

                // Footer hint
                AnimatedVisibility(
                    visible = screenVisible,
                    enter = fadeIn(tween(800, delayMillis = 500))
                ) {
                    Text(
                        text = "Toque e segure um perfil para editar  •  🔒 = PIN ativo",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─── Ambient glow background ─────────────────────────────────────────────────

@Composable
private fun AmbientGlow() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left purple blob
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset((-80).dp, (-60).dp)
                .blur(120.dp)
                .background(
                    Color(0xFF3D2080).copy(alpha = 0.18f),
                    CircleShape
                )
        )
        // Bottom-right brand blob
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomEnd)
                .offset(60.dp, 60.dp)
                .blur(100.dp)
                .background(
                    AppColors.Brand.copy(alpha = 0.10f),
                    CircleShape
                )
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ScreenHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Logo badge
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF5B6FFF).copy(alpha = 0.20f), AppColors.Brand.copy(alpha = 0.20f))
                    ),
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    AppColors.Brand.copy(alpha = 0.35f),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Text(
                text = "▶  STREAM VAULT",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppColors.Brand,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Quem está assistindo?",
            style = MaterialTheme.typography.displaySmall.copy(
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = "Selecione seu perfil para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary.copy(alpha = 0.75f)
        )
    }
}

// ─── Profile cards row ───────────────────────────────────────────────────────

@Composable
private fun ProfileCardRow(
    profiles: List<UserProfile>,
    onProfileClick: (UserProfile) -> Unit,
    onProfileLongClick: (UserProfile) -> Unit,
    onAddProfile: () -> Unit,
    screenVisible: Boolean
) {
    val displayProfiles = profiles.take(4)
    val canAdd = profiles.size < 4

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top
    ) {
        displayProfiles.forEachIndexed { index, profile ->
            // Staggered entrance delay per card
            var cardVisible by remember { mutableStateOf(false) }
            LaunchedEffect(screenVisible) {
                if (screenVisible) {
                    delay(index * 80L + 200L)
                    cardVisible = true
                }
            }
            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    tween(400, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 6 }
                )
            ) {
                ProfileCard(
                    profile = profile,
                    onClick = { onProfileClick(profile) },
                    onLongClick = { onProfileLongClick(profile) }
                )
            }
        }

        if (canAdd) {
            var addVisible by remember { mutableStateOf(false) }
            LaunchedEffect(screenVisible) {
                if (screenVisible) {
                    delay(displayProfiles.size * 80L + 280L)
                    addVisible = true
                }
            }
            AnimatedVisibility(
                visible = addVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    tween(400, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 6 }
                )
            ) {
                AddProfileCard(onClick = onAddProfile)
            }
        }
    }
}

// ─── Individual profile card ──────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    profile: UserProfile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.09f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_scale"
    )
    val accentColor = Color(profile.color)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .scale(scale)
            .pointerInput(profile.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        // Card face
        Box(
            modifier = Modifier
                .size(CARD_SIZE)
                .clip(RoundedCornerShape(CARD_RADIUS))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            accentColor.copy(alpha = 0.06f),
                            Color(0xFF0C1926)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.70f),
                            accentColor.copy(alpha = 0.20f)
                        )
                    ),
                    shape = RoundedCornerShape(CARD_RADIUS)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Avatar emoji
            Text(
                text = PROFILE_AVATARS.getOrElse(profile.avatarIndex) { "👤" },
                fontSize = 64.sp
            )

            // Inner glow overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.12f)
                            )
                        )
                    )
            )

            // PIN lock badge
            if (profile.pinHash != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C1926).copy(alpha = 0.92f))
                        .border(1.5.dp, accentColor.copy(alpha = 0.80f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔒", fontSize = 13.sp)
                }
            }
        }

        // Name label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (profile.pinHash != null) {
                Text(
                    text = "Protegido por PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Add profile card ────────────────────────────────────────────────────────

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "add_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(CARD_SIZE)
                .clip(RoundedCornerShape(CARD_RADIUS))
                .background(AppColors.Surface.copy(alpha = 0.6f))
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            AppColors.Outline.copy(alpha = 0.6f),
                            AppColors.Outline.copy(alpha = 0.2f)
                        )
                    ),
                    RoundedCornerShape(CARD_RADIUS)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "+",
                    fontSize = 44.sp,
                    color = AppColors.TextTertiary,
                    fontWeight = FontWeight.Thin,
                    lineHeight = 44.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Adicionar perfil",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Até 4 perfis",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Constants ────────────────────────────────────────────────────────────────

private val CARD_SIZE: Dp = 136.dp
private val CARD_RADIUS: Dp = 16.dp
