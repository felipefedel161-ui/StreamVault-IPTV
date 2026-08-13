package com.streamvault.domain.model

/**
 * Kids-safe catalog filter (provider catalog only — no external scrapers).
 * Always excludes adult content; requires positive kids signals for inclusion.
 */
object KidsContentPolicy {
    private val KIDS_POSITIVE = listOf(
        "kids", "kid ", "infantil", "infantis", "criança", "criancas", "crianças",
        "desenho", "desenhos", "cartoon", "anima", "animation", "family", "família",
        "familia", "baby", "bebê", "bebes", "preschool", "discovery kids",
        "nick jr", "nickjr", "gloob", "gloobinho", "disney jr", "disney junior",
        "pbs kids", "peppa", "galinha pintadinha", "mundo bita", "patati",
        "super why", "paw patrol", "patrulha canina", "mickey", "frozen",
        "moana", "encanto", "toy story", "minions", "shrek", "madagascar",
        "cartoon network", "boomerang", "discovery family", "nat geo kids",
        "sesame", "cocomelon", "bluey", "dora ", "bubble guppies"
    )

    fun isKidsSafeText(vararg parts: String?): Boolean {
        val blob = parts.filterNotNull().joinToString(" ").lowercase()
        if (blob.isBlank()) return false
        if (AdultContentPolicy.textLooksAdult(blob)) return false
        return KIDS_POSITIVE.any { blob.contains(it) }
    }

    fun isKidsSafeCategory(name: String): Boolean {
        if (AdultContentPolicy.categoryLooksAdult(name)) return false
        return isKidsSafeText(name)
    }

    fun isKidsSafeSeries(series: Series): Boolean {
        if (AdultContentPolicy.isAdultSeries(series)) return false
        return isKidsSafeText(series.name, series.genre, series.plot, series.categoryName)
    }

    fun isKidsSafeMovie(movie: Movie): Boolean {
        if (AdultContentPolicy.isAdultMovie(movie)) return false
        return isKidsSafeText(movie.name, movie.genre, movie.plot, movie.categoryName)
    }

    fun isKidsSafeChannel(channel: Channel): Boolean {
        if (AdultContentPolicy.isAdultChannel(channel)) return false
        return isKidsSafeText(channel.name, channel.groupTitle)
    }
}
