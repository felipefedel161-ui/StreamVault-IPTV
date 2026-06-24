package com.streamvault.app.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.streamvault.app.ui.design.requestFocusSafely
import com.streamvault.app.ui.interaction.TvButton
import kotlinx.coroutines.delay

/**
 * What will happen to the profile's PIN when "Salvar" is pressed.
 *
 * Mirrors the contract expected by ProfileSelectionViewModel.saveProfile: null = keep
 * whatever currently exists, "" = remove, non-empty = set/replace.
 */
private sealed class PinAction {
    data object NoChange : PinAction()
    data object Remove : PinAction()
    data class SetNew(val pin: String) : PinAction()
}

private enum class PinEntryStage { ENTERING, CONFIRMING }

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
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // PIN keypad state — no hidden text fields, so a TV remote's D-pad can drive it
    // the same way PinDialog's unlock keypad works.
    var pinAction by remember(profile) { mutableStateOf<PinAction>(PinAction.NoChange) }
    var keypadOpen by remember(profile) { mutableStateOf(false) }
    var keypadStage by remember(profile) { mutableStateOf(PinEntryStage.ENTERING) }
    var keypadBuffer by remember(profile) { mutableStateOf("") }
    var tempNewPin by remember(profile) { mutableStateOf("") }
    var pinError by remember(profile) { mutableStateOf<String?>(null) }

    fun openKeypad() {
        keypadOpen = true
        keypadStage = PinEntryStage.ENTERING
        keypadBuffer = ""
        tempNewPin = ""
        pinError = null
    }

    fun closeKeypad() {
        keypadOpen = false
        keypadBuffer = ""
        tempNewPin = ""
    }

    fun onKeypadDigit(digit: String) {
        if (keypadBuffer.length >= 4) return
        keypadBuffer += digit
        if (keypadBuffer.length == 4) {
            when (keypadStage) {
                PinEntryStage.ENTERING -> {
                    tempNewPin = keypadBuffer
                    keypadBuffer = ""
                    keypadStage = PinEntryStage.CONFIRMING
                }
                PinEntryStage.CONFIRMING -> {
                    if (keypadBuffer == tempNewPin) {
                        pinAction = PinAction.SetNew(tempNewPin)
                        pinError = null
                        closeKeypad()
                    } else {
                        pinError = "Os PINs não coincidem. Tente de novo."
                        keypadBuffer = ""
                        tempNewPin = ""
                        keypadStage = PinEntryStage.ENTERING
                    }
                }
            }
        }
    }

    fun onKeypadBackspace() {
        if (keypadBuffer.isNotEmpty()) keypadBuffer = keypadBuffer.dropLast(1)
    }

    val accentColor = Color(selectedColor)
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the name field and raise the keyboard as soon as the dialog opens.
    // Material3 OutlinedTextField won't reliably pick up D-pad focus on its own on
    // Android TV, so we request it explicitly — same pattern used by
    // CreateGroupDialog elsewhere in the app.
    LaunchedEffect(profile) {
        delay(120)
        nameFocusRequester.requestFocusSafely(tag = "EditProfileDialog", target = "Nome do perfil")
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF0D1B2E))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
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

                // Name field — auto-focused & keyboard shown on open (see LaunchedEffect above)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Nome do perfil", color = AppColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
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

                // ─── PIN section ──────────────────────────────────────────────
                Text(
                    text = "🔐  PIN de acesso (opcional, 4 dígitos)",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                val hasExistingPin = profile?.pinHash != null

                if (!keypadOpen) {
                    // Status row + actions, depending on current PIN state.
                    when (val action = pinAction) {
                        is PinAction.NoChange -> {
                            if (hasExistingPin) {
                                Text(
                                    text = "Este perfil já tem PIN.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextTertiary.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TvButton(onClick = { openKeypad() }, modifier = Modifier.weight(1f)) {
                                        Text("Alterar PIN")
                                    }
                                    TvButton(
                                        onClick = { pinAction = PinAction.Remove },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Remover PIN", color = AppColors.Live) }
                                }
                            } else {
                                TvButton(onClick = { openKeypad() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Definir PIN")
                                }
                            }
                        }
                        is PinAction.Remove -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "O PIN será removido ao salvar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Live,
                                    modifier = Modifier.weight(1f)
                                )
                                TvButton(onClick = { pinAction = PinAction.NoChange }) {
                                    Text("Desfazer")
                                }
                            }
                        }
                        is PinAction.SetNew -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✓ Novo PIN definido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                TvButton(onClick = { openKeypad() }) {
                                    Text("Alterar")
                                }
                            }
                        }
                    }
                } else {
                    // ─── D-pad-navigable numeric keypad (no hidden text field) ───
                    Text(
                        text = if (keypadStage == PinEntryStage.ENTERING) "Digite o novo PIN" else "Confirme o novo PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            val isFilled = index < keypadBuffer.length
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isFilled) accentColor else AppColors.Outline.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }

                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Cancelar", "0", "⌫")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        keys.chunked(3).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { key ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        TvButton(
                                            onClick = {
                                                when (key) {
                                                    "⌫" -> onKeypadBackspace()
                                                    "Cancelar" -> closeKeypad()
                                                    else -> onKeypadDigit(key)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = key,
                                                style = if (key == "Cancelar") {
                                                    MaterialTheme.typography.labelSmall
                                                } else {
                                                    MaterialTheme.typography.titleMedium
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                            val pinValue: String? = when (val action = pinAction) {
                                is PinAction.NoChange -> null   // keep whatever exists (null for a new profile)
                                is PinAction.Remove -> ""       // remove
                                is PinAction.SetNew -> action.pin
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
