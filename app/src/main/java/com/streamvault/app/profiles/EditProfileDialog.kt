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
import androidx.compose.ui.graphics.Brush
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
    profile: UserProfile?,          // null = creating new
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

    val accentColor = Color(selectedColor)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF0D1B2E))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Title
                Text(
                    text = if (profile == null) "Novo Perfil" else "Editar Perfil",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Large avatar preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.30f),
                                    accentColor.copy(alpha = 0.06f),
                                    Color(0xFF0C1926)
                                )
                            )
                        )
                        .border(
                            2.dp,
                            accentColor.copy(alpha = 0.70f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = PROFILE_AVATARS.getOrElse(avatarIndex) { "👤" },
                        fontSize = 40.sp
                    )
                }

                // Avatar picker — 8 columns × 2 rows
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PROFILE_AVATARS.indices.toList()) { idx ->
                        val isSelected = idx == avatarIndex
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.30f)
                                    else AppColors.Surface.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
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
                    Text(
                        text = "Cor:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary,
                        modifier = Modifier.width(36.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PROFILE_COLORS.forEach { colorLong ->
                            val c = Color(colorLong)
                            val isSelected = colorLong == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 30.dp else 24.dp)
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
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Nome do perfil", color = AppColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = AppColors.Outline,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        cursorColor = accentColor,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface
                    )
                )

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.Divider)
                )

                // PIN section
                Text(
                    text = "🔐  PIN de acesso (opcional, 4 dígitos)",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (profile?.pinHash != null) {
                    Text(
                        text = "Este perfil já tem PIN. Preencha abaixo para alterar ou deixe em branco para manter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Novo PIN", color = AppColors.TextSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = AppColors.Outline,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = accentColor,
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
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = AppColors.Outline,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = accentColor,
                            focusedContainerColor = AppColors.Surface,
                            unfocusedContainerColor = AppColors.Surface
                        )
                    )
                }

                // Remove PIN option for existing locked profiles
                if (profile?.pinHash != null && newPin.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmPin = "REMOVE"; newPin = "REMOVE" }
                    ) {
                        // subtle remove PIN button handled via special sentinel
                    }
                }

                pinError?.let {
                    Text(
                        text = it,
                        color = AppColors.Live,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Live.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Delete
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
                                    pinError = "O PIN deve ter exatamente 4 dígitos"
                                    return@TvButton
                                }
                                if (newPin != confirmPin) {
                                    pinError = "Os PINs não coincidem"
                                    return@TvButton
                                }
                            }
                            pinError = null
                            val pinValue: String? = when {
                                newPin.isNotEmpty() -> newPin          // set new PIN
                                profile?.pinHash != null -> null       // keep existing (null = no change)
                                else -> ""                             // no PIN at all
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
