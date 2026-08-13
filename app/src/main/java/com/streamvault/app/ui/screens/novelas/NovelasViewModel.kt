package com.streamvault.app.ui.screens.novelas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.manager.ProfileManager
import com.streamvault.domain.model.KidsContentPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Rail entry: real category id or synthetic emissora key (negative / string). */
data class NovelaRailItem(
    val key: String,
    val label: String,
    val categoryId: Long? = null,
    val emissora: String? = null,
    val count: Int = 0
)

data class NovelasUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val railItems: List<NovelaRailItem> = emptyList(),
    val selectedKey: String = "all",
    val searchQuery: String = "",
    val series: List<Series> = emptyList(),
    val displayedSeries: List<Series> = emptyList(),
    val featured: Series? = null,
    val allSeries: List<Series> = emptyList(),
    val seriesByEmissora: Map<String, List<Series>> = emptyMap(),
    val seriesByCategoryId: Map<Long, List<Series>> = emptyMap()
)

private val NOVELA_CAT_KEYWORDS = listOf(
    "novela", "novelas", "soap", "telenovela", "capítulo", "capitulo",
    "turca", "mexicana", "brasileira", "globo", "sbt", "record"
)

private val EMISSORA_ORDER = listOf("Globo", "SBT", "Record", "Band", "Turcas", "Mexicanas", "Outras")

fun isNovelaCategory(name: String): Boolean {
    val n = name.lowercase()
    return NOVELA_CAT_KEYWORDS.any { n.contains(it) }
}

fun classifyEmissora(s: Series, categoryName: String? = null): String {
    val blob = listOfNotNull(s.name, s.genre, s.plot, categoryName).joinToString(" ").lowercase()
    return when {
        listOf("globo", "globoplay", "vale tudo", "travessia", "renascer", "dona de mim", "mania de você")
            .any { blob.contains(it) } -> "Globo"
        listOf("sbt", "poliana", "chiquititas", "carrossel").any { blob.contains(it) } -> "SBT"
        listOf("record", "recordtv", "reis", "gênesis", "genesis").any { blob.contains(it) } -> "Record"
        listOf("band", "bandeirantes").any { blob.contains(it) } -> "Band"
        listOf("turc", "turca", "dizi").any { blob.contains(it) } -> "Turcas"
        listOf("mexic", "televisa", "caracol", "telemundo").any { blob.contains(it) } -> "Mexicanas"
        else -> "Outras"
    }
}

@HiltViewModel
class NovelasViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val providerRepository: ProviderRepository,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _state = MutableStateFlow(NovelasUiState())
    val state: StateFlow<NovelasUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun updateSearch(query: String) {
        val q = query
        val base = seriesForSelection(_state.value)
        val displayed = filterBySearch(base, q)
        _state.value = _state.value.copy(
            searchQuery = q,
            displayedSeries = displayed,
            featured = if (q.isBlank()) pickFeatured(base) else displayed.firstOrNull()
        )
    }

    fun selectRail(key: String) {
        val next = _state.value.copy(selectedKey = key, searchQuery = "")
        val base = seriesForSelection(next)
        _state.value = next.copy(
            series = base,
            displayedSeries = base,
            featured = pickFeatured(base)
        )
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val provider = providerRepository.getActiveProvider().first()
                if (provider == null) {
                    _state.value = NovelasUiState(loading = false, error = "Nenhum provedor ativo.")
                    return@launch
                }

                val allCategories = seriesRepository.getCategories(provider.id).first()
                val novelaCats = allCategories.filter { isNovelaCategory(it.name) }

                val byCategory = mutableMapOf<Long, MutableList<Series>>()
                val all = linkedMapOf<Long, Series>()
                val catNameBySeries = mutableMapOf<Long, String>()

                if (novelaCats.isNotEmpty()) {
                    for (cat in novelaCats.take(60)) {
                        val items = seriesRepository.getSeriesByCategoryPreview(provider.id, cat.id, 100).first()
                        byCategory[cat.id] = items.toMutableList()
                        items.forEach {
                            all[it.id] = it
                            catNameBySeries[it.id] = cat.name
                        }
                    }
                }

                // Fallback: pull series list and keep only names that look like novelas / soap
                if (all.size < 20) {
                    seriesRepository.getSeries(provider.id).first().forEach { s ->
                        val blob = listOfNotNull(s.name, s.genre).joinToString(" ").lowercase()
                        val hit = NOVELA_CAT_KEYWORDS.any { blob.contains(it) } ||
                            blob.contains("soap") ||
                            Regex("""S\d+E\d+""", RegexOption.IGNORE_CASE).containsMatchIn(s.name)
                        if (hit) all.putIfAbsent(s.id, s)
                    }
                }

                val rawList = all.values.toList()
                val kids = profileManager.activeProfile.value?.isKids == true
                val allList = if (kids) rawList.filter { KidsContentPolicy.isKidsSafeSeries(it) } else rawList
                val byEmissora = linkedMapOf<String, MutableList<Series>>()
                for (s in allList) {
                    val e = classifyEmissora(s, catNameBySeries[s.id])
                    byEmissora.getOrPut(e) { mutableListOf() }.add(s)
                }

                val rail = buildList {
                    add(NovelaRailItem("all", "Todas", count = allList.size))
                    // Prefer real categories when available
                    if (novelaCats.isNotEmpty()) {
                        novelaCats.forEach { cat ->
                            val c = byCategory[cat.id]?.size ?: 0
                            add(
                                NovelaRailItem(
                                    key = "cat-${cat.id}",
                                    label = cat.name,
                                    categoryId = cat.id,
                                    count = c
                                )
                            )
                        }
                    } else {
                        EMISSORA_ORDER.forEach { name ->
                            val list = byEmissora[name].orEmpty()
                            if (list.isNotEmpty()) {
                                add(
                                    NovelaRailItem(
                                        key = "em-$name",
                                        label = name,
                                        emissora = name,
                                        count = list.size
                                    )
                                )
                            }
                        }
                    }
                }

                _state.value = NovelasUiState(
                    loading = false,
                    railItems = rail,
                    selectedKey = "all",
                    allSeries = allList,
                    series = allList,
                    displayedSeries = allList,
                    seriesByEmissora = byEmissora.mapValues { it.value.distinctBy { s -> s.id } },
                    seriesByCategoryId = byCategory.mapValues { it.value.distinctBy { s -> s.id } },
                    featured = pickFeatured(allList)
                )
            } catch (e: Exception) {
                _state.value = NovelasUiState(loading = false, error = e.message ?: "Falha ao carregar")
            }
        }
    }

    private fun seriesForSelection(state: NovelasUiState): List<Series> {
        val key = state.selectedKey
        return when {
            key == "all" -> state.allSeries
            key.startsWith("cat-") -> {
                val id = key.removePrefix("cat-").toLongOrNull()
                if (id != null) state.seriesByCategoryId[id].orEmpty() else state.allSeries
            }
            key.startsWith("em-") -> {
                val em = key.removePrefix("em-")
                state.seriesByEmissora[em].orEmpty()
            }
            else -> state.allSeries
        }
    }

    private fun filterBySearch(list: List<Series>, query: String): List<Series> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter {
            it.name.contains(q, ignoreCase = true) ||
                (it.genre?.contains(q, true) == true) ||
                (it.plot?.contains(q, true) == true)
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
