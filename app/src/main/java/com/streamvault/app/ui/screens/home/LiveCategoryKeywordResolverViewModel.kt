package com.streamvault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.ui.filter.matchesCategoryKeyword
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Resolve uma palavra-chave (ex: "infantil") para o categoryId real de
 * Live TV do provider ativo, comparando pelo nome da categoria com a mesma
 * lógica de sinônimos usada para filtrar séries (Novelas/Infantil/Animes).
 *
 * Usado pela aba "Infantil" da navegação, já que o conteúdo correspondente
 * na lista do provedor costuma ser canais de TV ao vivo (ex: "⛄ INFANTIS"),
 * não séries — diferente de Novelas/Animes, que costumam ser VOD.
 */
@HiltViewModel
class LiveCategoryKeywordResolverViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    sealed class ResolveState {
        data object Loading : ResolveState()
        data class Resolved(val categoryId: Long?) : ResolveState()
    }

    private val _state = MutableStateFlow<ResolveState>(ResolveState.Loading)
    val state: StateFlow<ResolveState> = _state.asStateFlow()

    fun resolve(keyword: String) {
        viewModelScope.launch {
            val provider = providerRepository.getActiveProvider().first()
            if (provider == null) {
                _state.value = ResolveState.Resolved(null)
                return@launch
            }
            val categories = channelRepository.getCategories(provider.id).first()
            val matched = categories.firstOrNull { matchesCategoryKeyword(it.name, keyword) }
            _state.value = ResolveState.Resolved(matched?.id)
        }
    }
}
