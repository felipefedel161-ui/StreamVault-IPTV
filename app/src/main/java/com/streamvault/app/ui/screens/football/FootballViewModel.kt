package com.streamvault.app.ui.screens.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Title keywords that identify football/sports programs in the EPG
private val FOOTBALL_KEYWORDS = listOf(
    "futebol", "football", "soccer", "copa", "liga", "campeonato", "champions",
    "premier", "bundesliga", "libertadores", "serie a", "série a", "laliga",
    "ligue 1", "brasileirao", "brasileiro", "mls", "eliminatórias", "eliminatorias",
    "mundial", "eurocopa", "euro", "conmebol", "uefa", "fifa", "jogo", "partida",
    "clássico", "classico", "derby", "match", "vs.", " x ", "ao vivo", "live"
)

private val SPORT_CATEGORIES = listOf(
    "esporte", "esportes", "sport", "sports", "football", "futebol", "soccer"
)

/** A channel paired with its current and next football program. */
data class FootballMatch(
    val channel: Channel,
    val currentProgram: Program?,
    val nextProgram: Program?,
    val isLive: Boolean,
    val progressFraction: Float  // 0..1 if live
)

data class FootballUiState(
    val liveMatches: List<FootballMatch> = emptyList(),
    val upcomingMatches: List<FootballMatch> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FootballViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootballUiState())
    val uiState: StateFlow<FootballUiState> = _uiState.asStateFlow()

    private val _tickMs = MutableStateFlow(System.currentTimeMillis())

    init {
        observeMatches()
        startProgressTick()
    }

    private fun startProgressTick() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                _tickMs.value = System.currentTimeMillis()
            }
        }
    }

    fun refresh() {
        _tickMs.value = System.currentTimeMillis()
    }

    private fun observeMatches() {
        viewModelScope.launch {
            providerRepository.getActiveProvider()
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .flatMapLatest { provider ->
                    val now = System.currentTimeMillis()
                    val windowEnd = now + 4 * 60 * 60 * 1000L // next 4 hours

                    channelRepository.getChannels(provider.id)
                        .map { channels ->
                            // Filter to sports-relevant channels first (by group/category name)
                            val sportChannels = channels.filter { ch ->
                                val group = ch.groupTitle?.lowercase() ?: ""
                                val cat = ch.categoryName?.lowercase() ?: ""
                                SPORT_CATEGORIES.any { it in group || it in cat } ||
                                    ch.currentProgram?.looksLikeFootball() == true ||
                                    ch.nextProgram?.looksLikeFootball() == true
                            }
                            Pair(provider, sportChannels)
                        }
                }
                .flatMapLatest { (provider, sportChannels) ->
                    val now = System.currentTimeMillis()
                    val windowEnd = now + 4 * 60 * 60 * 1000L
                    val epgIds = sportChannels.mapNotNull { it.epgChannelId }.distinct()

                    if (epgIds.isEmpty()) {
                        flow { emit(buildMatches(sportChannels, emptyMap(), emptyMap())) }
                    } else {
                        combine(
                            epgRepository.getNowPlayingForChannels(provider.id, epgIds),
                            epgRepository.getProgramsForChannels(
                                provider.id, epgIds, now, windowEnd
                            ),
                            _tickMs
                        ) { nowPlaying, upcoming, tick ->
                            buildMatches(sportChannels, nowPlaying, upcoming)
                        }
                    }
                }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { (live, upcoming) ->
                    _uiState.update {
                        it.copy(
                            liveMatches = live,
                            upcomingMatches = upcoming,
                            isLoading = false,
                            isEmpty = live.isEmpty() && upcoming.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun buildMatches(
        channels: List<Channel>,
        nowPlaying: Map<String, Program?>,
        upcoming: Map<String, List<Program>>
    ): Pair<List<FootballMatch>, List<FootballMatch>> {
        val now = System.currentTimeMillis()
        val liveList = mutableListOf<FootballMatch>()
        val upcomingList = mutableListOf<FootballMatch>()

        channels.forEach { ch ->
            val epgId = ch.epgChannelId ?: return@forEach
            val currentProg = nowPlaying[epgId] ?: ch.currentProgram
            val upcomingProgs = upcoming[epgId]?.filter { it.startTime > now } ?: emptyList()
            val nextProg = upcomingProgs.minByOrNull { it.startTime } ?: ch.nextProgram

            val currentIsFootball = currentProg?.looksLikeFootball() == true
            val nextIsFootball = nextProg?.looksLikeFootball() == true

            when {
                currentIsFootball -> {
                    val progress = currentProg!!.progressPercent(now)
                    liveList.add(
                        FootballMatch(
                            channel = ch,
                            currentProgram = currentProg,
                            nextProgram = nextProg?.takeIf { nextIsFootball },
                            isLive = true,
                            progressFraction = progress
                        )
                    )
                }
                nextIsFootball -> {
                    upcomingList.add(
                        FootballMatch(
                            channel = ch,
                            currentProgram = null,
                            nextProgram = nextProg,
                            isLive = false,
                            progressFraction = 0f
                        )
                    )
                }
            }
        }

        // Sort live by how far along they are (most progressed first feels most urgent)
        liveList.sortByDescending { it.progressFraction }
        // Sort upcoming by start time
        upcomingList.sortBy { it.nextProgram?.startTime ?: Long.MAX_VALUE }

        return Pair(liveList, upcomingList)
    }

    private fun Program.looksLikeFootball(): Boolean {
        val titleLower = title.lowercase()
        val descLower = description.lowercase()
        val genreLower = genre?.lowercase() ?: ""
        val catLower = category?.lowercase() ?: ""
        return FOOTBALL_KEYWORDS.any {
            it in titleLower || it in descLower || it in genreLower || it in catLower
        }
    }
}
