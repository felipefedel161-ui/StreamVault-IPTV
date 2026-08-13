package com.streamvault.domain.model

/**
 * Kids-safe catalog filter (no external scrapers — uses the provider catalog only).
 */
object KidsContentPolicy {
    private val KIDS_POSITIVE = listOf(
        "kids", "kid ", "infantil", "infantis", "criança", "criancas", "crianças",
        "desenho", "desenhos", "cartoon", "anima", "animation", "family", "família",
        "familia", "baby", "bebê", "bebes", "preschool", "discovery kids",
        "nick jr", "nickjr", "gloob", "gloobinho", "disney jr", "disney junior",
        "pbs kids", "peppa", "galinha pintadinha", "mundo bita", "patati",
        "super why", "paw patrol", "patrulha canina", "mickey", "frozen",
        "moana", "encanto", "toy story", "minions", "shrek", "madagascar"
    )

    private val KIDS_NEGATIVE = listOf(
        "terror", "horror", "18+", "+18", "adulto", "adult ", "erotic", "xxx",
        "violence", "violência", "violencia", "gore", "slasher", "thriller adulto"
    )

    fun isKidsSafeText(vararg parts: String?): Boolean {
        val blob = parts.filterNotNull().joinToString(" ").lowercase()
        if (blob.isBlank()) return false
        if (KIDS_NEGATIVE.any { blob.contains(it) }) return false
        return KIDS_POSITIVE.any { blob.contains(it) }
    }

    fun isKidsSafeCategory(name: String): Boolean = isKidsSafeText(name)

    fun isKidsSafeSeries(series: Series): Boolean =
        isKidsSafeText(series.name, series.genre, series.plot)

    fun isKidsSafeMovie(movie: Movie): Boolean =
        isKidsSafeText(movie.name, movie.genre, movie.plot)

    fun isKidsSafeChannel(channel: Channel): Boolean =
        isKidsSafeText(channel.name, channel.groupTitle)
}
