package com.streamvault.app.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton

/**
 * Tela de ativação por MAC — sem campo de PIN.
 * O dispositivo é identificado automaticamente e verificado no servidor.
 */
@Composable
fun ActivationScreen(
    onActivationSuccess: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var providerName by remember { mutableStateOf("") }

    LaunchedEffect(uiState.activationSuccess) {
        if (uiState.activationSuccess) onActivationSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E30).copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cabeçalho
                Text(
                    text = "▶ INOVA PLAYER",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color(0xFFB47AFF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )

                Text(
                    text = "Ativação do Dispositivo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ID do dispositivo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F1A), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ID do Dispositivo",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.deviceId.ifBlank { "Obtendo ID..." },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFB47AFF),
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Informe este ID ao seu revendedor para ativar",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // Campo nome da lista (opcional)
                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("Nome da lista (opcional)", color = AppColors.TextSecondary) },
                    placeholder = { Text("Ex: Minha Lista", color = AppColors.TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!uiState.isLoading) viewModel.activate(providerName)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color(0xFF7C4DFF55),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C4DFF),
                        focusedContainerColor = Color(0xFF0F0F1A),
                        unfocusedContainerColor = Color(0xFF0F0F1A)
                    )
                )

                // Progresso
                if (uiState.syncProgress != null) {
                    Text(
                        text = uiState.syncProgress!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF7C4DFF),
                        textAlign = TextAlign.Center
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF7C4DFF),
                        trackColor = Color(0xFF7C4DFF33)
                    )
                }

                // Erro
                uiState.error?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF5350),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4D1E1E), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }

                // Botão ativar
                TvButton(
                    onClick = { if (!uiState.isLoading) viewModel.activate(providerName) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ativando...")
                    } else {
                        Text("✓ Ativar Agora")
                    }
                }

                Text(
                    text = "A ativação é feita automaticamente pelo ID do dispositivo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
