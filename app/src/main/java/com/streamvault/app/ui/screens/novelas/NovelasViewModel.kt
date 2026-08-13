package com.streamvault.app.ui.screens.novelas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NovelasUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val categorySearchQuery: String = "",
    val series: List<Series> = emptyList(),
    val featured: Series? = null,
    val allSeriesCache: List<Series> = emptyList()
)

private val NOVELA_KEYWORDS = listOf(
    "novela", "novelas", "soap", "telenovela", "telenovelas",
    "capítulo", "capitulo", "drama turc", "turca", "mexicana",
    "brasileira", "globo", "sbt", "record", "caracol", "televisa",
    "vale tudo", "travessia", "renascer"
)

fun isNovelaCategory(name: String): Boolean {
    val n = name.lowercase()
    return NOVELA_KEYWORDS.any { n.contains(it) }
}

fun isNovelaSeries(s: Series): Boolean {
    val blob = listOfNotNull(s.name, s.genre, s.plot).joinToString(" ").lowercase()
    return NOVELA_KEYWORDS.any { blob.contains(it) } ||
        (s.genre?.lowercase()?.contains("soap") == true)
}

@HiltViewModel
class NovelasViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NovelasUiState())
    val state: StateFlow<NovelasUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun updateCategorySearch(query: String) {
        _state.value = _state.value.copy(categorySearchQuery = query)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val provider = providerRepository.getActiveProvider().first()
                if (provider == null) {
                    _state.value = NovelasUiState(
                        loading = false,
                        error = "Nenhum provedor ativo."
                    )
                    return@launch
                }
                val allCategories = seriesRepository.getCategories(provider.id).first()
                val novelaCats = allCategories.filter { isNovelaCategory(it.name) }

                val collected = linkedMapOf<Long, Series>()
                if (novelaCats.isNotEmpty()) {
                    for (cat in novelaCats.take(50)) {
                        seriesRepository.getSeriesByCategoryPreview(provider.id, cat.id, 80).first()
                            .forEach { collected[it.id] = it }
                    }
                }
                if (collected.size < 12) {
                    seriesRepository.getSeries(provider.id).first()
                        .filter(::isNovelaSeries)
                        .forEach { collected[it.id] = it }
                }
                val list = collected.values.toList()
                _state.value = NovelasUiState(
                    loading = false,
                    categories = novelaCats,
                    series = list,
                    allSeriesCache = list,
                    featured = pickFeatured(list),
                    selectedCategoryId = null
                )
            } catch (e: Exception) {
                _state.value = NovelasUiState(
                    loading = false,
                    error = e.message ?: "Falha ao carregar novelas"
                )
            }
        }
    }

    fun selectCategory(categoryId: Long?) {
        viewModelScope.launch {
            if (categoryId == null) {
                _state.value = _state.value.copy(
                    selectedCategoryId = null,
                    series = _state.value.allSeriesCache,
                    featured = pickFeatured(_state.value.allSeriesCache),
                    loading = false
                )
                return@launch
            }
            val provider = providerRepository.getActiveProvider().first() ?: return@launch
            _state.value = _state.value.copy(loading = true, selectedCategoryId = categoryId)
            try {
                val items = seriesRepository.getSeriesByCategoryPreview(provider.id, categoryId, 120).first()
                _state.value = _state.value.copy(
                    loading = false,
                    series = items,
                    featured = pickFeatured(items),
                    selectedCategoryId = categoryId
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    private fun pickFeatured(list: List<Series>): Series? {
        if (list.isEmpty()) return null
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val seed = (year * 1000L + day) * 31L + 53L
        val withArt = list.filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
        val pool = if (withArt.isNotEmpty()) withArt else list
        val ordered = pool.sortedBy { it.id xor seed }
        val idx = (((seed % ordered.size.toLong()) + ordered.size) % ordered.size).toInt()
        return ordered[idx]
    }
}
