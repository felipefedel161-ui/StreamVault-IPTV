package com.streamvault.app.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton

@Composable
fun EditProfileDialog(
    profile: UserProfile?,         // null = creating new
    canDelete: Boolean = true,
    onSave: (name: String, avatarIndex: Int, color: Long, pin: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var avatarIndex by remember(profile) { mutableStateOf(profile?.avatarIndex ?: 0) }
    var selectedColor by remember(profile) { mutableStateOf(profile?.color ?: PROFILE_COLORS[0]) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (profile == null) "Novo Perfil" else "Editar Perfil",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Avatar preview
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor).copy(alpha = 0.25f))
                        .border(2.dp, Color(selectedColor), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = PROFILE_AVATARS.getOrElse(avatarIndex) { "👤" },
                        fontSize = 32.sp
                    )
                }

                // Avatar picker
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PROFILE_AVATARS.indices.toList()) { idx ->
                        val isSelected = idx == avatarIndex
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(selectedColor).copy(alpha = 0.35f)
                                    else AppColors.Surface
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color(selectedColor) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { avatarIndex = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = PROFILE_AVATARS[idx], fontSize = 20.sp)
                        }
                    }
                }

                // Color picker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PROFILE_COLORS.forEach { colorLong ->
                        val c = Color(colorLong)
                        val isSelected = colorLong == selectedColor
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = colorLong }
                        )
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Nome do perfil", color = AppColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(selectedColor),
                        unfocusedBorderColor = AppColors.Outline,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        cursorColor = Color(selectedColor),
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface
                    )
                )

                // PIN section
                Text(
                    text = if (profile?.pinHash != null) "Novo PIN (deixe em branco para manter)"
                    else "PIN (opcional, 4 dígitos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("PIN", color = AppColors.TextSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedColor),
                            unfocusedBorderColor = AppColors.Outline,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = Color(selectedColor),
                            focusedContainerColor = AppColors.Surface,
                            unfocusedContainerColor = AppColors.Surface
                        )
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirmar", color = AppColors.TextSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedColor),
                            unfocusedBorderColor = AppColors.Outline,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = Color(selectedColor),
                            focusedContainerColor = AppColors.Surface,
                            unfocusedContainerColor = AppColors.Surface
                        )
                    )
                }

                pinError?.let {
                    Text(
                        text = it,
                        color = AppColors.Live,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (profile != null && canDelete && onDelete != null) {
                        if (showDeleteConfirm) {
                            TvButton(
                                onClick = { onDelete(); onDismiss() },
                                modifier = Modifier.weight(1f)
                            ) { Text("⚠ Confirmar exclusão", color = AppColors.Live) }
                        } else {
                            TvButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("🗑 Excluir", color = AppColors.TextSecondary) }
                        }
                    }

                    TvButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }

                    TvButton(
                        onClick = {
                            // Validate PIN
                            if (newPin.isNotEmpty()) {
                                if (newPin.length != 4) {
                                    pinError = "O PIN deve ter 4 dígitos"
                                    return@TvButton
                                }
                                if (newPin != confirmPin) {
                                    pinError = "Os PINs não coincidem"
                                    return@TvButton
                                }
                            }
                            pinError = null
                            val pinValue: String? = when {
                                newPin.isNotEmpty() -> newPin   // set new
                                profile?.pinHash != null -> null // keep (signal = null)
                                else -> ""                       // no PIN
                            }
                            onSave(name, avatarIndex, selectedColor, pinValue)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("✓ Salvar") }
                }
            }
        }
    }
}
