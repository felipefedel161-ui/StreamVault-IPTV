package com.streamvault.app.ui.screens.activation

import android.content.ClipData
import android.net.Uri
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
import com.streamvault.app.activation.ActivationManager
import com.streamvault.app.ui.components.shell.StatusPill
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.domain.model.ProviderEpgSyncMode
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val activationManager: ActivationManager,
    private val validateAndAddProvider: ValidateAndAddProvider,
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
) : androidx.lifecycle.ViewModel() {

    companion object {
        private const val CATALOG_FRESH_MS = 12L * 60 * 60 * 1000
    }

    private val _state = MutableStateFlow<ActivationState>(ActivationState.Checking)
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _syncProgress = MutableStateFlow<String?>(null)
    val syncProgress: StateFlow<String?> = _syncProgress.asStateFlow()

    fun checkActivation() {
        val id = activationManager.getDeviceId()
        _deviceId.value = id
        _state.value = ActivationState.Checking

        viewModelScope.launch {
            when (val result = activationManager.checkActivation(id)) {
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

    fun addProviderFromM3u(m3uUrl: String, name: String = "StreamVault") {
        _state.value = ActivationState.AddingProvider
        viewModelScope.launch {
            try {
                val url = m3uUrl.trim()
                val providers = runCatching { providerRepository.getProviders().first() }.getOrDefault(emptyList())
                val existing = findMatchingProvider(providers, url)

                if (existing != null) {
                    val count = runCatching {
                        channelRepository.getChannelCount(existing.id).first()
                    }.getOrDefault(0)

                    // PRIMARY RULE: if local catalog already has channels, enter immediately.
                    // Do NOT block on full re-sync — that was the "Updating existing provider" delay.
                    if (count > 0) {
                        val age = if (existing.lastSyncedAt > 0L) {
                            System.currentTimeMillis() - existing.lastSyncedAt
                        } else {
                            Long.MAX_VALUE
                        }
                        val stale = age >= CATALOG_FRESH_MS
                        _syncProgress.value = if (stale) {
                            "Lista em cache ($count) — atualização em segundo plano…"
                        } else {
                            "Lista em cache ($count canais) — entrando…"
                        }
                        _state.value = ActivationState.Done
                        return@launch
                    }

                    // No local channels yet — first real download for this provider
                    _syncProgress.value = "Baixando lista (primeira sincronização)…"
                    validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = url,
                            name = name.ifBlank { existing.name },
                            httpUserAgent = "StreamVault/1.0",
                            httpHeaders = "",
                            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
                            m3uVodClassificationEnabled = true,
                            existingProviderId = existing.id
                        ),
                        onProgress = { msg -> _syncProgress.value = msg }
                    )
                } else {
                    _syncProgress.value = "Baixando lista (primeira vez)…"
                    validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = url,
                            name = name,
                            httpUserAgent = "StreamVault/1.0",
                            httpHeaders = "",
                            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
                            m3uVodClassificationEnabled = true,
                            existingProviderId = null
                        ),
                        onProgress = { msg -> _syncProgress.value = msg }
                    )
                }
            } catch (_: Exception) {
                // If anything fails but we might already have local data, still proceed
            }
            _state.value = ActivationState.Done
        }
    }

    private fun findMatchingProvider(providers: List<com.streamvault.domain.model.Provider>, url: String): com.streamvault.domain.model.Provider? {
        if (providers.isEmpty()) return null
        // Exact / normalized URL
        providers.firstOrNull { urlsMatch(it.m3uUrl, url) || urlsMatch(it.serverUrl, url) }?.let { return it }
        // Xtream get.php?username=… → match by host + username
        val parsed = parseXtreamHint(url)
        if (parsed != null) {
            val (host, user) = parsed
            providers.firstOrNull { p ->
                val ph = hostOf(p.serverUrl.ifBlank { p.m3uUrl })
                val pu = p.username.trim()
                ph.isNotEmpty() && ph.equals(host, true) && pu.isNotEmpty() && pu.equals(user, true)
            }?.let { return it }
        }
        // Single active provider with any catalog — treat as the activation target
        if (providers.size == 1) return providers.first()
        providers.firstOrNull { it.isActive }?.let { return it }
        return null
    }

    private fun parseXtreamHint(url: String): Pair<String, String>? {
        return try {
            val u = Uri.parse(url)
            val user = u.getQueryParameter("username")?.trim().orEmpty()
            if (user.isEmpty()) return null
            val host = (u.host ?: "").lowercase()
            if (host.isEmpty()) return null
            host to user
        } catch (_: Exception) {
            null
        }
    }

    private fun hostOf(url: String): String {
        return try {
            Uri.parse(url).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun urlsMatch(a: String?, b: String?): Boolean {
        val x = (a ?: "").trim().trimEnd('/')
        val y = (b ?: "").trim().trimEnd('/')
        if (x.isEmpty() || y.isEmpty()) return false
        if (x.equals(y, ignoreCase = true)) return true
        // Compare without query string noise
        val xs = x.substringBefore('?')
        val ys = y.substringBefore('?')
        return xs.equals(ys, ignoreCase = true) && xs.length > 12
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
                                StatusPill(label = "StreamVaultER", containerColor = AppColors.BrandMuted)
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
                                Text(syncProgress ?: "Preparando sua lista...", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
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

private fun copyDeviceIdToClipboard(context: android.content.Context, deviceId: String) {
    if (deviceId.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
    Toast.makeText(context, "ID copiado", Toast.LENGTH_SHORT).show()
}

@Composable
private fun DeviceIdBox(deviceId: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
        TvButton(onClick = { copyDeviceIdToClipboard(context, deviceId) }) {
            Text("⧉ Copiar ID")
        }
    }
}
