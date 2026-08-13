package com.streamvault.domain.model

/**
 * Detects and blocks adult / restricted catalog items.
 * Used by Kids profiles and parental control levels.
 */
object AdultContentPolicy {

    private val ADULT_KEYWORDS = listOf(
        "xxx", "x x x", "adult", "adulto", "adultos", "adulto ",
        "18+", "+18", "18 +", "21+", "+21", "nc-17", "nc17",
        "porn", "porno", "pornô", "porno ", "erotic", "erótico", "erotico",
        "sex ", "sexo", "sexy", "hot 18", "onlyfans", "playboy",
        "hustler", "penthouse", "brazzers", "redtube", "xvideos",
        "xnxx", "pornhub", "youporn", "cam4", "chaturbate",
        "nude", "nudes", "naked", "nuas", "nuas ", "strip",
        "lingerie show", "softcore", "hardcore", "fetish",
        "bdsm", "hentai", "ecchi 18", "yaoi 18", "yuri 18",
        "gay xxx", "lesbian xxx", "milf", "anal ", "oral ",
        "blowjob", "cumshot", "gangbang", "incest",
        "canal adulto", "canais adulto", "filmes adulto", "filme adulto",
        "séries adulto", "series adulto", "vod adulto", "adult vod",
        "adult movies", "adult movie", "adult series", "adult live",
        "pay-per-view adult", "night club", "sexy hot", "sexyhot",
        "multishow sexy", "playboy tv", "sextreme", "venus",
        "private tv", "brazzers tv", "reality kings",
        "adult swim 18" // keep generic adult markers; true Adult Swim is not blocked by flag alone
    )

    /** Category names that are almost always adult sections in IPTV lists. */
    private val ADULT_CATEGORY_KEYWORDS = listOf(
        "adult", "adulto", "adultos", "xxx", "18+", "+18", "porn", "erotic",
        "erótico", "sexy hot", "sexyhot", "playboy", "onlyfans", "hot 18"
    )

    fun textLooksAdult(vararg parts: String?): Boolean {
        val blob = parts.filterNotNull().joinToString(" ").lowercase()
        if (blob.isBlank()) return false
        return ADULT_KEYWORDS.any { blob.contains(it) }
    }

    fun categoryLooksAdult(name: String): Boolean {
        val n = name.lowercase()
        return ADULT_CATEGORY_KEYWORDS.any { n.contains(it) } || textLooksAdult(name)
    }

    fun isAdultMovie(movie: Movie): Boolean =
        movie.isAdult || textLooksAdult(movie.name, movie.genre, movie.plot, movie.categoryName)

    fun isAdultSeries(series: Series): Boolean =
        series.isAdult || textLooksAdult(series.name, series.genre, series.plot, series.categoryName)

    fun isAdultChannel(channel: Channel): Boolean =
        channel.isAdult || textLooksAdult(channel.name, channel.groupTitle)

    fun isAdultCategory(category: Category): Boolean =
        category.isAdult || categoryLooksAdult(category.name)

    /**
     * True when the active context must hide adult items.
     * @param isKidsProfile active profile is Kids
     * @param parentalLevel 0 = off, higher = stricter (matches existing app scale)
     */
    fun shouldBlockAdult(isKidsProfile: Boolean, parentalLevel: Int): Boolean =
        isKidsProfile || parentalLevel >= 2
}
