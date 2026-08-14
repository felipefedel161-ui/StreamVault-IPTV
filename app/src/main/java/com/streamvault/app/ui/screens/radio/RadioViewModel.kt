package com.streamvault.app.ui.screens.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Channel
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioUiState(
    val isLoading: Boolean = true,
    val stations: List<Channel> = emptyList(),
    val filtered: List<Channel> = emptyList(),
    val query: String = "",
    val error: String? = null,
)

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val provider = providerRepository.getActiveProvider().first()
                    ?: providerRepository.getProviders().first().firstOrNull()
                if (provider == null) {
                    _state.update {
                        it.copy(isLoading = false, stations = emptyList(), filtered = emptyList(), error = "Nenhum provedor ativo")
                    }
                    return@launch
                }

                val categories = channelRepository.getCategories(provider.id).first()
                val radioCategoryIds = categories
                    .filter { isRadioText(it.name) }
                    .map { it.id }
                    .toSet()

                // Prefer channels from radio categories; fall back to name scan on all live channels
                val allChannels = channelRepository.getChannels(provider.id).first()
                val stations = allChannels
                    .filter { ch ->
                        (ch.categoryId != null && ch.categoryId in radioCategoryIds) ||
                            isRadioText(ch.name) ||
                            isRadioText(ch.groupTitle) ||
                            isRadioText(ch.categoryName) ||
                            isRadioText(categories.firstOrNull { it.id == ch.categoryId }?.name.orEmpty())
                    }
                    .distinctBy { it.id }
                    .sortedBy { it.name.lowercase() }

                _state.update {
                    it.copy(
                        isLoading = false,
                        stations = stations,
                        filtered = applyQuery(stations, it.query),
                        error = if (stations.isEmpty()) "Nenhuma estação de rádio encontrada na lista" else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Erro ao carregar rádios")
                }
            }
        }
    }

    fun setQuery(q: String) {
        _state.update {
            it.copy(query = q, filtered = applyQuery(it.stations, q))
        }
    }

    private fun applyQuery(list: List<Channel>, q: String): List<Channel> {
        val needle = q.trim()
        if (needle.isEmpty()) return list
        return list.filter { it.name.contains(needle, ignoreCase = true) }
    }

    companion object {
        private val RADIO_HINTS = listOf(
            "radio", "rádio", "radios", "rádios", "webradio", "web radio",
            "fm ", " fm", "am ", " am", "estação", "estacao"
        )

        fun isRadioText(raw: String?): Boolean {
            val t = (raw ?: "").lowercase().trim()
            if (t.isEmpty()) return false
            return RADIO_HINTS.any { hint -> t.contains(hint) }
        }
    }
}
