package com.streamvault.app.ui.screens.activation

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.ui.components.shell.StatusPill
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.ValidateAndAddProviderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

// ─── URL do servidor ──────────────────────────────────────────────────────────
private const val SERVER_BASE_URL = "https://artsil121.eu.pythonanywhere.com"

// ─── State ────────────────────────────────────────────────────────────────────

sealed class ActivationState {
    object Checking : ActivationState()
    object NotActivated : ActivationState()
    data class Error(val message: String) : ActivationState()
    data class Activated(val m3uUrl: String, val expiresIn: Int) : ActivationState()
    object AddingProvider : ActivationState()
    object Done : ActivationState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MacActivationViewModel @Inject constructor(
    private val validateAndAddProvider: ValidateAndAddProvider
) : ViewModel() {

    private val _state = MutableStateFlow<ActivationState>(ActivationState.Checking)
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _syncProgress = MutableStateFlow<String?>(null)
    val syncProgress: StateFlow<String?> = _syncProgress.asStateFlow()

    fun checkActivation(androidId: String) {
        _deviceId.value = androidId
        _state.value = ActivationState.Checking

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deviceId = androidId.uppercase()
                val url = URL("$SERVER_BASE_URL/api/status/$deviceId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "InovaPlayer/7.0")

                val code = conn.responseCode
                val body = try {
                    conn.inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())

                when (code) {
                    200 -> {
                        val autorizado = json.optBoolean("autorizado", false)
                        if (autorizado) {
                            val m3uUrl = json.optString("m3u_url", "")
                            val diasRestantes = json.optInt("dias_restantes", 0)
                            if (m3uUrl.isBlank()) {
                                withContext(Dispatchers.Main) {
                                    _state.value = ActivationState.Error(
                                        "Lista não configurada para este dispositivo.\nContacte o administrador."
                                    )
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    _state.value = ActivationState.Activated(m3uUrl, diasRestantes)
                                }
                            }
                        } else {
                            val mensagem = json.optString("mensagem", "Dispositivo não ativado.")
                            withContext(Dispatchers.Main) {
                                _state.value = ActivationState.NotActivated
                            }
                        }
                    }
                    403 -> {
                        val msg = json.optString("mensagem", "")
                        val errorMsg = when {
                            msg.contains("expirada", ignoreCase = true) ->
                                "Assinatura expirada.\nContacte o administrador."
                            else -> "Acesso negado.\nContacte o administrador."
                        }
                        withContext(Dispatchers.Main) {
                            _state.value = ActivationState.Error(errorMsg)
                        }
                    }
                    404 -> {
                        withContext(Dispatchers.Main) {
                            _state.value = ActivationState.NotActivated
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            _state.value = ActivationState.Error(
                                "Erro no servidor (HTTP $code).\nTente novamente."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = ActivationState.Error(
                        "Sem conexão com o servidor.\nVerifique sua internet e tente novamente."
                    )
                }
            }
        }
    }

    fun addProviderFromM3u(m3uUrl: String, name: String = "INOVA PLAY") {
        _state.value = ActivationState.AddingProvider
        viewModelScope.launch {
            try {
                val result = validateAndAddProvider.addM3u(
                    M3uProviderSetupCommand(
                        url = m3uUrl,
                        name = name,
                        m3uVodClassificationEnabled = true
                    ),
                    onProgress = { msg -> _syncProgress.value = msg }
                )
                // Navigate regardless of sync result — the provider is saved.
                // If sync failed partially, the user can retry from Settings.
                _state.value = ActivationState.Done
            } catch (e: Exception) {
                // Even on unexpected error, navigate to app.
                // Provider may already be saved; user can retry sync.
                _state.value = ActivationState.Done
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MacActivationScreen(
    onActivated: () -> Unit,
    viewModel: MacActivationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    LaunchedEffect(Unit) {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN"
        viewModel.checkActivation(androidId)
    }

    LaunchedEffect(state) {
        if (state is ActivationState.Activated) {
            val s = state as ActivationState.Activated
            viewModel.addProviderFromM3u(s.m3uUrl)
        }
        if (state is ActivationState.Done) {
            onActivated()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.22f),
                        AppColors.HeroTop,
                        AppColors.HeroBottom
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.92f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (val s = state) {

                    is ActivationState.Checking -> {
                        StatusPill(label = "INOVA PLAYER", containerColor = AppColors.BrandMuted)
                        Spacer(Modifier.height(4.dp))
                        CircularProgressIndicator(color = AppColors.Brand)
                        Text(
                            text = "Verificando ativação...",
                            style = MaterialTheme.typography.titleLarge,
                            color = AppColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        if (deviceId.isNotBlank()) {
                            Text(
                                text = "ID: ${deviceId.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ActivationState.NotActivated -> {
                        StatusPill(label = "NÃO ATIVADO", containerColor = AppColors.BrandMuted)
                        Text(
                            text = "Dispositivo não ativado",
                            style = MaterialTheme.typography.titleLarge,
                            color = AppColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Entre em contato com o administrador\ne informe seu ID:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        DeviceIdBox(deviceId = deviceId)
                        TvButton(onClick = { viewModel.checkActivation(deviceId) }) {
                            Text("Tentar novamente")
                        }
                    }

                    is ActivationState.Error -> {
                        StatusPill(label = "ERRO", containerColor = AppColors.BrandMuted)
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (deviceId.isNotBlank()) {
                            Text(
                                text = "ID do dispositivo:",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            DeviceIdBox(deviceId = deviceId)
                        }
                        TvButton(onClick = { viewModel.checkActivation(deviceId) }) {
                            Text("Tentar novamente")
                        }
                    }

                    is ActivationState.Activated,
                    is ActivationState.AddingProvider -> {
                        StatusPill(label = "ATIVADO", containerColor = AppColors.Brand)
                        CircularProgressIndicator(color = AppColors.Brand)
                        Text(
                            text = "Carregando sua lista...",
                            style = MaterialTheme.typography.titleLarge,
                            color = AppColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        val progressMsg = syncProgress
                        if (progressMsg != null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = AppColors.Brand,
                                trackColor = AppColors.BrandMuted
                            )
                            Text(
                                text = progressMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ActivationState.Done -> {
                        CircularProgressIndicator(color = AppColors.Brand)
                    }
                }
            }
        }
    }
}

// ─── DeviceIdBox ──────────────────────────────────────────────────────────────

@Composable
private fun DeviceIdBox(deviceId: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Brand.copy(alpha = 0.12f))
    ) {
        Text(
            text = deviceId.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.Brand,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
        )
    }
}
