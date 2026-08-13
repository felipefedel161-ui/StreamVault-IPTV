package com.streamvault.app.ui.screens.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.football.FootballFixture
import com.streamvault.app.football.FootballPrediction
import com.streamvault.app.football.FootballRepository
import com.streamvault.domain.manager.ProgramReminderManager
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
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
    val matchedChannels: Map<Int, List<Channel>> = emptyMap(),
    val scheduledFixtureIds: Set<Int> = emptySet(),
    val scheduleMessage: String? = null
)

@HiltViewModel
class FootballViewModel @Inject constructor(
    private val footballRepository: FootballRepository,
    private val channelRepository: ChannelRepository,
    private val providerRepository: ProviderRepository,
    private val programReminderManager: ProgramReminderManager
) : ViewModel() {

    private val _state = MutableStateFlow(FootballUiState())
    val state: StateFlow<FootballUiState> = _state.asStateFlow()

    init {
        load(FootballTab.LIVE)
        observeReminders()
    }

    private fun observeReminders() {
        viewModelScope.launch {
            programReminderManager.observeUpcomingReminders().collect { reminders ->
                val ids = reminders.mapNotNull { r ->
                    // encode fixture id in program title prefix when we schedule
                    r.programTitle.substringAfter("fixture:", "")
                        .substringBefore(" ")
                        .toIntOrNull()
                }.toSet()
                // Also match by title pattern "Team x Team"
                val byTitle = _state.value.fixtures.mapNotNull { f ->
                    val title = matchTitle(f)
                    val id = f.id ?: return@mapNotNull null
                    if (reminders.any { it.programTitle.contains(title) || it.programTitle.contains("fixture:$id") }) id else null
                }.toSet()
                _state.value = _state.value.copy(scheduledFixtureIds = ids + byTitle)
            }
        }
    }

    fun selectTab(tab: FootballTab) {
        if (_state.value.tab == tab && _state.value.fixtures.isNotEmpty()) return
        load(tab)
    }

    fun refresh() = load(_state.value.tab)

    private fun load(tab: FootballTab) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, tab = tab)
            val result = when (tab) {
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

    fun dismissScheduleMessage() {
        _state.value = _state.value.copy(scheduleMessage = null)
    }

    fun toggleSchedule(fixture: FootballFixture, channel: Channel?) {
        viewModelScope.launch {
            val id = fixture.id ?: run {
                _state.value = _state.value.copy(scheduleMessage = "Jogo sem identificador")
                return@launch
            }
            val startMs = (fixture.timestamp ?: 0L) * 1000L
            if (startMs <= 0L) {
                _state.value = _state.value.copy(scheduleMessage = "Horário do jogo indisponível")
                return@launch
            }
            val provider = providerRepository.getActiveProvider().first()
            val providerId = channel?.providerId ?: provider?.id ?: 0L
            val channelId = channel?.id?.toString() ?: "football-$id"
            val channelName = channel?.name ?: "Canal a definir"
            val title = "fixture:$id ${matchTitle(fixture)}"
            val program = Program(
                channelId = channelId,
                title = title,
                description = "${fixture.league.name} · ${matchTitle(fixture)}",
                startTime = startMs,
                endTime = startMs + 2 * 60 * 60 * 1000L,
                providerId = providerId
            )

            val already = programReminderManager.isReminderScheduled(
                providerId = providerId,
                channelId = channelId,
                programTitle = title,
                programStartTime = startMs
            )

            if (already) {
                programReminderManager.cancelReminder(providerId, channelId, title, startMs)
                _state.value = _state.value.copy(
                    scheduledFixtureIds = _state.value.scheduledFixtureIds - id,
                    scheduleMessage = "Agendamento removido"
                )
            } else {
                val result = programReminderManager.scheduleReminder(
                    providerId = providerId,
                    channelId = channelId,
                    channelName = channelName,
                    program = program,
                    leadTimeMinutes = 15
                )
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        scheduledFixtureIds = _state.value.scheduledFixtureIds + id,
                        scheduleMessage = "Lembrete 15 min antes do jogo"
                    )
                } else {
                    _state.value = _state.value.copy(
                        scheduleMessage = result.errorMessageOrNull() ?: "Falha ao agendar"
                    )
                }
            }
        }
    }

    private fun matchTitle(f: FootballFixture): String =
        "${f.home.name} x ${f.away.name}"

    private suspend fun matchChannels(fixtures: List<FootballFixture>) {
        val provider = providerRepository.getActiveProvider().first() ?: return
        val matched = mutableMapOf<Int, List<Channel>>()
        val sportsHints = listOf(
            "sport", "espn", "premiere", "combate", "dazn", "fox sports",
            "band sports", "sportv", "tnt sports", "paramount", "disney",
            "sport tv", "sportv2", "sportv3", "premiere fc"
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
                        if (sportsLike || q !in sportsHints) {
                            // Prefer channels that actually have a stream URL
                            if (ch.streamUrl.isNotBlank() || ch.id > 0) {
                                found[ch.id] = ch
                            }
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
