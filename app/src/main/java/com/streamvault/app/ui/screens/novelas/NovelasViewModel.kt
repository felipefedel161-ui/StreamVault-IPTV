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

data class NovelaSection(
    val title: String,
    val series: List<Series>
)

data class NovelasUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val series: List<Series> = emptyList(),
    val sections: List<NovelaSection> = emptyList(),
    val featured: Series? = null
)

private val NOVELA_KEYWORDS = listOf(
    "novela", "novelas", "soap", "telenovela", "telenovelas",
    "capítulo", "capitulo", "drama turc", "turca", "mexicana",
    "brasileira", "globo", "sbt", "record", "caracol", "televisa",
    "vale tudo", "travessia", "renascer", "familia é tudo", "família é tudo"
)

private val EMISSORA_ORDER = listOf(
    "Globo", "SBT", "Record", "Band", "Turcas", "Mexicanas", "Outras"
)

fun isNovelaCategory(name: String): Boolean {
    val n = name.lowercase()
    return NOVELA_KEYWORDS.any { n.contains(it) }
}

fun isNovelaSeries(s: Series): Boolean {
    val blob = listOfNotNull(s.name, s.genre, s.plot).joinToString(" ").lowercase()
    return NOVELA_KEYWORDS.any { blob.contains(it) } ||
        (s.genre?.lowercase()?.contains("soap") == true) ||
        (s.genre?.lowercase()?.contains("drama") == true && (
            s.name.contains("Capítulo", true) ||
                s.name.contains("Capitulo", true) ||
                Regex("""S\d+E\d+""", RegexOption.IGNORE_CASE).containsMatchIn(s.name)
            ))
}

fun classifyEmissora(s: Series, categoryName: String? = null): String {
    val blob = listOfNotNull(s.name, s.genre, s.plot, categoryName).joinToString(" ").lowercase()
    return when {
        listOf("globo", "globoplay", "vale tudo", "travessia", "renascer", "familia é tudo", "família é tudo")
            .any { blob.contains(it) } -> "Globo"
        listOf("sbt", "poliana", "chiquititas", "carrossel").any { blob.contains(it) } -> "SBT"
        listOf("record", "recordtv", "reis", "gênesis", "genesis").any { blob.contains(it) } -> "Record"
        listOf("band", "bandeirantes").any { blob.contains(it) } -> "Band"
        listOf("turc", "turca", "turkey", "dizis").any { blob.contains(it) } -> "Turcas"
        listOf("mexic", "televisa", "caracol", "telemundo").any { blob.contains(it) } -> "Mexicanas"
        else -> "Outras"
    }
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

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val provider = providerRepository.getActiveProvider().first()
                if (provider == null) {
                    _state.value = NovelasUiState(
                        loading = false,
                        error = "Nenhum provedor ativo. Ative o dispositivo primeiro."
                    )
                    return@launch
                }
                val allCategories = seriesRepository.getCategories(provider.id).first()
                val novelaCats = allCategories.filter { isNovelaCategory(it.name) }

                val collected = linkedMapOf<Long, Pair<Series, String?>>()
                if (novelaCats.isNotEmpty()) {
                    for (cat in novelaCats.take(40)) {
                        val items = seriesRepository.getSeriesByCategoryPreview(provider.id, cat.id, 60).first()
                        items.forEach { s -> collected[s.id] = s to cat.name }
                    }
                }
                if (collected.size < 12) {
                    val allSeries = seriesRepository.getSeries(provider.id).first()
                    allSeries.filter(::isNovelaSeries).forEach { s ->
                        collected.putIfAbsent(s.id, s to null)
                    }
                }

                val list = collected.values.map { it.first }.distinctBy { it.id }
                val withCat = collected.values.toList()
                val byEmissora = linkedMapOf<String, MutableList<Series>>()
                for ((s, catName) in withCat) {
                    val key = classifyEmissora(s, catName)
                    byEmissora.getOrPut(key) { mutableListOf() }.add(s)
                }
                // de-dupe within sections
                val sections = EMISSORA_ORDER.mapNotNull { title ->
                    val items = byEmissora[title]?.distinctBy { it.id }.orEmpty()
                    if (items.isEmpty()) null else NovelaSection(title, items.take(40))
                }

                val featured = pickFeatured(list)
                _state.value = NovelasUiState(
                    loading = false,
                    categories = novelaCats,
                    series = list,
                    sections = sections,
                    featured = featured,
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
        refresh()
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
