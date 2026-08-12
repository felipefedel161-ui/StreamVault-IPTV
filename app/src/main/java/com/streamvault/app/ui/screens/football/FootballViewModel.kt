package com.streamvault.app.ui.screens.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.football.FootballFixture
import com.streamvault.app.football.FootballPrediction
import com.streamvault.app.football.FootballRepository
import com.streamvault.domain.model.Channel
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FootballTab { LIVE, TODAY }

data class FootballUiState(
    val loading: Boolean = true,
    val tab: FootballTab = FootballTab.LIVE,
    val fixtures: List<FootballFixture> = emptyList(),
    val error: String? = null,
    val prediction: FootballPrediction? = null,
    val predictionLoading: Boolean = false,
    val selectedFixtureId: Int? = null,
    val matchedChannels: Map<Int, List<Channel>> = emptyMap()
)

@HiltViewModel
class FootballViewModel @Inject constructor(
    private val footballRepository: FootballRepository,
    private val channelRepository: ChannelRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FootballUiState())
    val state: StateFlow<FootballUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: FootballTab) {
        if (_state.value.tab == tab) return
        _state.value = _state.value.copy(tab = tab, loading = true, error = null)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = when (_state.value.tab) {
                FootballTab.LIVE -> footballRepository.loadLive()
                FootballTab.TODAY -> footballRepository.loadToday()
            }
            result.fold(
                onSuccess = { list ->
                    _state.value = _state.value.copy(loading = false, fixtures = list, error = null)
                    matchChannels(list)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Falha ao carregar jogos"
                    )
                }
            )
        }
    }

    fun loadPrediction(fixtureId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedFixtureId = fixtureId,
                predictionLoading = true,
                prediction = null
            )
            footballRepository.loadPrediction(fixtureId).fold(
                onSuccess = { pred ->
                    _state.value = _state.value.copy(prediction = pred, predictionLoading = false)
                },
                onFailure = {
                    _state.value = _state.value.copy(predictionLoading = false, prediction = null)
                }
            )
        }
    }

    fun clearPrediction() {
        _state.value = _state.value.copy(selectedFixtureId = null, prediction = null)
    }

    private suspend fun matchChannels(fixtures: List<FootballFixture>) {
        val provider = providerRepository.getActiveProvider().first() ?: return
        val matched = mutableMapOf<Int, List<Channel>>()
        val sportsHints = listOf(
            "sport", "espn", "premiere", "combate", "dazn", "fox sports",
            "band sports", "sportv", "tnt sports", "paramount", "disney"
        )
        for (f in fixtures) {
            val id = f.id ?: continue
            val teamTokens = (f.home.name + " " + f.away.name)
                .replace("-", " ")
                .split(" ")
                .map { it.trim() }
                .filter { it.length >= 4 }
                .distinct()
            val queries = buildList {
                add("${f.home.name} ${f.away.name}")
                add(f.home.name)
                add(f.away.name)
                addAll(teamTokens)
                add(f.league.name)
                addAll(f.matchKeywords)
                // Prefer sports-oriented channels when team names alone miss
                addAll(sportsHints)
            }.map { it.trim() }.filter { it.length >= 3 }.distinct()

            val found = linkedMapOf<Long, Channel>()
            for (q in queries.take(12)) {
                try {
                    val channels = channelRepository.searchChannels(provider.id, q).first()
                    channels.take(10).forEach { ch ->
                        val name = ch.name.lowercase()
                        val sportsLike = sportsHints.any { h -> name.contains(h) } ||
                            name.contains("futebol") || name.contains("football")
                        // Prefer sports channels; still keep team-name hits
                        if (sportsLike || q !in sportsHints) {
                            found[ch.id] = ch
                        }
                    }
                } catch (_: Exception) {
                }
                if (found.size >= 8) break
            }
            if (found.isNotEmpty()) {
                matched[id] = found.values.toList()
            }
        }
        _state.value = _state.value.copy(matchedChannels = matched)
    }
}
