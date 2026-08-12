package com.streamvault.app.football

data class FootballTeam(
    val id: Int? = null,
    val name: String = "",
    val logo: String = ""
)

data class FootballLeague(
    val id: Int? = null,
    val name: String = "",
    val country: String = "",
    val logo: String = "",
    val round: String = ""
)

data class FootballGoals(
    val home: Int? = null,
    val away: Int? = null
)

data class FootballFixture(
    val id: Int? = null,
    val date: String? = null,
    val timestamp: Long? = null,
    val status: String = "",
    val statusLong: String = "",
    val elapsed: Int? = null,
    val isLive: Boolean = false,
    val isFinished: Boolean = false,
    val isMajor: Boolean = true,
    val league: FootballLeague = FootballLeague(),
    val home: FootballTeam = FootballTeam(),
    val away: FootballTeam = FootballTeam(),
    val goals: FootballGoals = FootballGoals(),
    val matchKeywords: List<String> = emptyList()
)

data class FootballPrediction(
    val winner: String? = null,
    val winnerComment: String? = null,
    val advice: String? = null,
    val percentHome: String? = null,
    val percentDraw: String? = null,
    val percentAway: String? = null
)
