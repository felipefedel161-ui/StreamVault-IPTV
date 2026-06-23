package com.streamvault.app.ui.screens.activation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.activation.ActivationError
import com.streamvault.app.activation.ActivationResult
import com.streamvault.app.activation.InovaActivationManager
import com.streamvault.app.ui.components.shell.StatusPill
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.domain.model.ProviderEpgSyncMode
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ActivationState {
    object Checking : ActivationState()
    object NotActivated : ActivationState()
    data class Error(val message: String, val isExpired: Boolean = false, val isFingerprintMismatch: Boolean = false) : ActivationState()
    data class Activated(val m3uUrl: String, val expiresIn: Int, val expiracao: String) : ActivationState()
    object AddingProvider : ActivationState()
    object Done : ActivationState()
}

@HiltViewModel
class MacActivationViewModel @Inject constructor(
    private val inovaActivationManager: InovaActivationManager,
    private val validateAndAddProvider: ValidateAndAddProvider
) : androidx.lifecycle.ViewModel() {

    private val _state = MutableStateFlow<ActivationState>(ActivationState.Checking)
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _syncProgress = MutableStateFlow<String?>(null)
    val syncProgress: StateFlow<String?> = _syncProgress.asStateFlow()

    fun checkActivation() {
        val id = inovaActivationManager.getDeviceId()
        _deviceId.value = id
        _state.value = ActivationState.Checking

        viewModelScope.launch {
            when (val result = inovaActivationManager.checkActivation(id)) {
                is ActivationResult.Success -> {
                    _state.value = ActivationState.Activated(
                        m3uUrl = result.m3uUrl,
                        expiresIn = result.diasRestantes,
                        expiracao = result.expiracao
                    )
                }
                is ActivationResult.Error -> {
                    _state.value = when (result.error) {
                        ActivationError.NOT_FOUND -> ActivationState.NotActivated
                        ActivationError.EXPIRED -> ActivationState.Error(
                            "Assinatura expirada.\nContacte o administrador para renovar.",
                            isExpired = true
                        )
                        ActivationError.FINGERPRINT_MISMATCH -> ActivationState.Error(
                            "Licença vinculada a outro aparelho.\nContacte o administrador para liberar.",
                            isFingerprintMismatch = true
                        )
                        ActivationError.NO_M3U -> ActivationState.Error(
                            "Dispositivo ativo mas sem lista configurada.\nContacte o administrador."
                        )
                        ActivationError.NETWORK -> ActivationState.Error(
                            "Sem conexão com o servidor.\nVerifique sua internet e tente novamente."
                        )
                        ActivationError.GENERIC -> ActivationState.Error(
                            "Erro ao verificar ativação.\nTente novamente."
                        )
                    }
                }
            }
        }
    }

    fun addProviderFromM3u(m3uUrl: String, name: String = "INOVA PLAY") {
        _state.value = ActivationState.AddingProvider
        viewModelScope.launch {
            try {
                validateAndAddProvider.addM3u(
                    M3uProviderSetupCommand(
                        url = m3uUrl,
                        name = name,
                        httpUserAgent = "InovaPlayer/7.0",
                        httpHeaders = "",
                        epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
                        m3uVodClassificationEnabled = true,
                        existingProviderId = null
                    ),
                    onProgress = { msg -> _syncProgress.value = msg }
                )
            } catch (_: Exception) {}
            _state.value = ActivationState.Done
        }
    }
}

@Composable
fun MacActivationScreen(
    onActivated: () -> Unit,
    viewModel: MacActivationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkActivation() }

    LaunchedEffect(state) {
        if (state is ActivationState.Activated) {
            val s = state as ActivationState.Activated
            viewModel.addProviderFromM3u(s.m3uUrl)
        }
        if (state is ActivationState.Done) onActivated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppColors.Canvas, AppColors.HeroTop, AppColors.HeroBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedContent(targetState = state, label = "activation_state") { s ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (s) {
                            is ActivationState.Checking -> {
                                StatusPill(label = "INOVA PLAYER", containerColor = AppColors.BrandMuted)
                                CircularProgressIndicator(color = AppColors.Brand, modifier = Modifier.size(40.dp))
                                Text("Verificando ativação...", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
                                if (deviceId.isNotBlank()) Text("ID: $deviceId", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                            }
                            is ActivationState.NotActivated -> {
                                StatusPill(label = "NÃO ATIVADO", containerColor = AppColors.BrandMuted)
                                Text("Dispositivo não ativado", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
                                Text("Entre em contato com o administrador\ne informe seu ID:", style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                                DeviceIdBox(deviceId)
                                TvButton(onClick = { viewModel.checkActivation() }) { Text("↻ Tentar novamente") }
                            }
                            is ActivationState.Error -> {
                                StatusPill(label = if (s.isExpired) "EXPIRADO" else if (s.isFingerprintMismatch) "BLOQUEADO" else "ERRO", containerColor = AppColors.BrandMuted)
                                Text(s.message, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().background(AppColors.Brand.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(14.dp))
                                if (deviceId.isNotBlank()) { Text("ID do dispositivo:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary, textAlign = TextAlign.Center); DeviceIdBox(deviceId) }
                                if (!s.isFingerprintMismatch) TvButton(onClick = { viewModel.checkActivation() }) { Text("↻ Tentar novamente") }
                            }
                            is ActivationState.Activated, is ActivationState.AddingProvider -> {
                                StatusPill(label = "ATIVADO ✓", containerColor = AppColors.Brand)
                                CircularProgressIndicator(color = AppColors.Brand, modifier = Modifier.size(40.dp))
                                Text("Carregando sua lista...", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
                                if (s is ActivationState.Activated && s.expiresIn >= 0)
                                    Text("Expira em: ${s.expiracao} (${s.expiresIn} dias)", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                                syncProgress?.let {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), color = AppColors.Brand, trackColor = AppColors.BrandMuted)
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                                }
                            }
                            is ActivationState.Done -> CircularProgressIndicator(color = AppColors.Brand)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIdBox(deviceId: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Brand.copy(alpha = 0.12f))
    ) {
        Text(
            text = deviceId,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = AppColors.Brand,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
        )
    }
}
