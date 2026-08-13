package com.streamvault.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.domain.model.ProfileAvatars
import com.streamvault.domain.model.UserProfile
import kotlinx.coroutines.launch

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Stay on this screen until the user explicitly picks a profile
    LaunchedEffect(Unit) {
        viewModel.clearSession()
    }
    var mode by remember { mutableStateOf(PickerMode.Select) }
    var pinTarget by remember { mutableStateOf<UserProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0E14), Color(0xFF121A24), Color(0xFF0A0E14))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            PickerMode.Select -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Quem está assistindo?",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(36.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileAvatarTile(
                                profile = profile,
                                onClick = {
                                    if (profile.pinEnabled) {
                                        pinTarget = profile
                                        pinInput = ""
                                        pinError = null
                                        mode = PickerMode.Pin
                                    } else {
                                        scope.launch {
                                            if (viewModel.select(profile.id, null)) onProfileSelected()
                                        }
                                    }
                                }
                            )
                        }
                        if (profiles.size < 5) {
                            item {
                                AddProfileTile(onClick = { mode = PickerMode.Create })
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Use o controle para escolher um perfil",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp
                    )
                }
            }
            PickerMode.Create -> {
                CreateProfilePanel(
                    onCancel = { mode = PickerMode.Select },
                    onCreate = { name, avatar, kids ->
                        scope.launch {
                            viewModel.create(name, avatar, kids)
                            mode = PickerMode.Select
                        }
                    }
                )
            }
            PickerMode.Pin -> {
                val target = pinTarget
                if (target != null) {
                    PinPanel(
                        profileName = target.name,
                        pin = pinInput,
                        error = pinError,
                        onPinChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        onConfirm = {
                            scope.launch {
                                val ok = viewModel.select(target.id, pinInput)
                                if (ok) onProfileSelected()
                                else pinError = "PIN incorreto"
                            }
                        },
                        onCancel = {
                            mode = PickerMode.Select
                            pinTarget = null
                        }
                    )
                }
            }
        }
    }
}

private enum class PickerMode { Select, Create, Pin }

@Composable
private fun ProfileAvatarTile(profile: UserProfile, onClick: () -> Unit) {
    val avatar = ProfileAvatars.byId(profile.avatarId)
    val bg = Color(avatar.colorArgb)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvClickableSurface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.08f)
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(120.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .border(3.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = avatar.emoji, fontSize = 52.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (profile.isKids) {
            Text(text = "Kids", color = Color(0xFFFF6B9D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvClickableSurface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.08f)
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(120.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(text = "Adicionar", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
    }
}

@Composable
private fun CreateProfilePanel(
    onCancel: () -> Unit,
    onCreate: (name: String, avatarId: String, isKids: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf(ProfileAvatars.all.first().id) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("Criar perfil", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            placeholder = { Text("Nome", color = Color.White.copy(alpha = 0.4f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppColors.Brand,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = AppColors.Brand
            ),
            modifier = Modifier.width(320.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Avatar", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ProfileAvatars.all, key = { it.id }) { av ->
                val selected = av.id == avatarId
                TvClickableSurface(
                    onClick = { avatarId = av.id },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(av.colorArgb),
                        focusedContainerColor = Color(av.colorArgb)
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                            else Modifier
                        )
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(av.emoji, fontSize = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TvClickableSurface(
            onClick = { onCreate(name.ifBlank { "Perfil" }, avatarId, false) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppColors.Brand,
                focusedContainerColor = AppColors.Brand.copy(alpha = 0.85f)
            )
        ) {
            Text(
                text = "Salvar",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        TvClickableSurface(
            onClick = onCancel,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f)
            )
        ) {
            Text(
                text = "Cancelar",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun PinPanel(
    profileName: String,
    pin: String,
    error: String?,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PIN de $profileName", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            placeholder = { Text("4 dígitos", color = Color.White.copy(alpha = 0.4f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppColors.Brand,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.width(200.dp)
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        TvClickableSurface(
            onClick = onConfirm,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppColors.Brand,
                focusedContainerColor = AppColors.Brand.copy(alpha = 0.85f)
            )
        ) {
            Text("Entrar", color = Color.Black, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        TvClickableSurface(
            onClick = onCancel,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f)
            )
        ) {
            Text("Voltar", color = Color.White, modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}
