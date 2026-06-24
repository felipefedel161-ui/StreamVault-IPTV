package com.streamvault.app.profiles

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.ui.components.dialogs.PinDialog
import com.streamvault.app.ui.design.AppColors

/**
 * Netflix-style "Quem está assistindo?" profile selection screen.
 *
 * - Up to 4 user profiles shown as cards
 * - Tap → enter profile (shows PIN dialog if locked)
 * - Long-press → edit profile
 * - "+ Adicionar perfil" card when slots remain
 */
@Composable
fun ProfileSelectionScreen(
    onProfileSelected: (UserProfile) -> Unit,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    // Full-screen gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1B2E), AppColors.Canvas),
                    radius = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(44.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "▶ STREAM VAULT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = AppColors.Brand,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Quem está assistindo?",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Light
                    )
                )
            }

            // Profile row
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.profiles.take(4).forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { viewModel.onProfileSelected(profile) },
                        onLongClick = { viewModel.startEditProfile(profile) }
                    )
                }
                if (uiState.profiles.size < 4) {
                    AddProfileCard(onClick = { viewModel.startCreateProfile() })
                }
            }

            // Bottom hint
            Text(
                text = "Toque e segure um perfil para editar",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    profile: UserProfile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "scale"
    )

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
                .size(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(profile.color).copy(alpha = 0.15f))
                .border(
                    width = 2.dp,
                    color = Color(profile.color).copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = PROFILE_AVATARS.getOrElse(profile.avatarIndex) { "👤" },
                fontSize = 60.sp
            )
            // PIN lock badge
            if (profile.pinHash != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AppColors.Canvas.copy(alpha = 0.9f))
                        .border(1.dp, Color(profile.color).copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔒", fontSize = 13.sp)
                }
            }
        }

        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
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
                .size(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.Surface)
                .border(2.dp, AppColors.Outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 52.sp,
                color = AppColors.TextTertiary,
                fontWeight = FontWeight.Thin
            )
        }

        Text(
            text = "Adicionar perfil",
            style = MaterialTheme.typography.bodyLarge.copy(color = AppColors.TextSecondary),
            textAlign = TextAlign.Center
        )
    }
}
