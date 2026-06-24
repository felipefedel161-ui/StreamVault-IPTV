package com.streamvault.app.ui.filter

import java.text.Normalizer

/**
 * Termos base usados pelas abas de categoria (Novelas/Infantil/Animes) e os
 * sinônimos/variações comuns usados por provedores de IPTV em português.
 * A busca é feita por substring (contains), então cada termo já cobre
 * variações como plural automaticamente (ex: "novela" casa com "novelas").
 *
 * Refinado a partir de uma lista M3U real de referência:
 * - novela: ♦️ Novelas, ♦️ Novelas Turcas, ♦️ Doramas, ♦️ Legendadas
 * - infantil: ⛄ INFANTIS, CANAIS 24H (parcial), ♦️ PlutoTV (parcial)
 * - anime: ♦️ Crunchyroll, ♦️ Funimation Now, ♦️ Animacao (parcial)
 */
val CATEGORY_KEYWORD_SYNONYMS: Map<String, List<String>> = mapOf(
    "novela" to listOf(
        "novela", "novelas", "telenovela", "telenovelas", "novelinha",
        "novela turca", "novelas turcas", "turca", "turcas",
        "dorama", "doramas", "kdrama", "k-drama", "k drama",
        "legendada", "legendadas", "legendados", "legendado"
    ),
    "infantil" to listOf(
        "infantil", "infantis", "kids", "kid",
        "crianca", "criancas",
        "desenho", "desenhos", "cartoon", "cartoons",
        "junior", "baby",
        "nickelodeon", "gloob", "gloobinho",
        "cartoon network", "tooncast",
        "ratimbum", "ra tim bum",
        "canais 24h", "canal 24h"
    ),
    "anime" to listOf(
        "anime", "animes", "animee", "manga",
        "animacao", "animacoes",
        "crunchyroll", "funimation", "funimation now"
    )
)

/** Remove acentos para comparações tolerantes a variações de escrita. */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        .lowercase()

/**
 * Verifica se o nome de uma categoria corresponde a uma palavra-chave base
 * (ex: "infantil"), considerando sinônimos comuns e ignorando acentos.
 */
fun matchesCategoryKeyword(categoryName: String, keyword: String): Boolean {
    val normalizedCategory = categoryName.normalizeForMatch()
    val baseKeyword = keyword.normalizeForMatch()
    val candidates = CATEGORY_KEYWORD_SYNONYMS[baseKeyword]
        ?.map { it.normalizeForMatch() }
        ?: listOf(baseKeyword)
    return candidates.any { normalizedCategory.contains(it) }
}
