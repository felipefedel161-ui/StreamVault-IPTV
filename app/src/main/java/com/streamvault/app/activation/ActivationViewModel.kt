package com.streamvault.app.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.ProviderEpgSyncMode
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.ValidateAndAddProviderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val inovaActivationManager: InovaActivationManager,
    private val validateAndAddProvider: ValidateAndAddProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(deviceId = inovaActivationManager.getDeviceId()) }
    }

    /**
     * Inicia a ativação apenas pelo MAC/Android ID — sem PIN.
     * [name] é o nome amigável que o usuário dá à lista (opcional).
     */
    fun activate(name: String = "") {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    syncProgress = "Verificando ativação..."
                )
            }

            when (val result = inovaActivationManager.activate()) {
                is ActivationResult.Success -> {
                    val providerName = name.ifBlank { "Minha Lista" }
                    _uiState.update { it.copy(syncProgress = "Carregando canais...") }

                    when (val addResult = validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = result.m3uUrl,
                            name = providerName,
                            httpUserAgent = "InovaPlayer/7.0",
                            httpHeaders = "",
                            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
                            m3uVodClassificationEnabled = false,
                            existingProviderId = null
                        ),
                        onProgress = { msg ->
                            _uiState.update { it.copy(syncProgress = msg) }
                        }
                    )) {
                        is ValidateAndAddProviderResult.Success,
                        is ValidateAndAddProviderResult.SavedWithWarning -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    activationSuccess = true,
                                    syncProgress = null,
                                    diasRestantes = result.diasRestantes,
                                    expiracao = result.expiracao
                                )
                            }
                        }
                        is ValidateAndAddProviderResult.ValidationError -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    syncProgress = null,
                                    error = addResult.message
                                )
                            }
                        }
                        is ValidateAndAddProviderResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    syncProgress = null,
                                    error = "Ativação válida, mas erro ao carregar lista: ${addResult.message}"
                                )
                            }
                        }
                    }
                }

                is ActivationResult.Error -> {
                    val errorMsg = when (result.error) {
                        ActivationError.EXPIRED ->
                            "Assinatura expirada. Contacte o administrador."
                        ActivationError.NOT_FOUND ->
                            "Dispositivo não cadastrado. Informe o ID ao seu revendedor:\n${_uiState.value.deviceId}"
                        ActivationError.NO_M3U ->
                            "Lista não configurada para este dispositivo. Contacte o administrador."
                        ActivationError.NETWORK ->
                            "Sem conexão com o servidor. Verifique sua internet."
                        ActivationError.GENERIC ->
                            "Erro ao verificar ativação. Tente novamente."
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            syncProgress = null,
                            error = errorMsg
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ActivationUiState(
    val isLoading: Boolean = false,
    val activationSuccess: Boolean = false,
    val deviceId: String = "",
    val error: String? = null,
    val syncProgress: String? = null,
    val diasRestantes: Int = -1,
    val expiracao: String = ""
)
