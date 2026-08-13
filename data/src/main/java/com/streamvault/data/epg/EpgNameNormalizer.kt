package com.streamvault.data.epg

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes IPTV channel display names for EPG matching.
 * Produces multiple candidate keys so "Globo HD [BR]", "GLOBO FHD", "tvglobo"
 * can all map to the same EPG channel.
 */
object EpgNameNormalizer {

    private val nonAlphanumericRegex = Regex("[^a-z0-9]")
    private val multiSpaceRegex = Regex("\\s+")

    /** Quality / codec / region noise common in IPTV lists. */
    private val NOISE_TOKENS = listOf(
        "uhd", "fhd", "hd", "sd", "4k", "8k", "2k", "2160p", "1080p", "720p", "480p",
        "h265", "hevc", "h264", "avc", "hdr", "hdr10", "dolby", "vision", "atmos",
        "fps", "60fps", "50fps", "30fps", "multi", "audio", "aac", "ac3",
        "br", "brazil", "brasil", "pt", "por", "us", "uk", "lat", "latino",
        "backup", "bkp", "alt", "option", "opt", "vip", "premium", "full",
        "raw", "down", "up", "server", "srv", "feed", "main", "secundario", "secundária"
    ).sortedByDescending { it.length }

    private val noiseTokenRegex = NOISE_TOKENS
        .map { Regex("""(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])""", RegexOption.IGNORE_CASE) }

    private val bracketNoiseRegex = Regex("""[\[\(\{].*?[\]\)\}]""")
    private val pipePrefixRegex = Regex("""^[^|]{0,12}\|""")
    private val leadingNumberRegex = Regex("""^\d{1,4}[\s.\-:]+""")
    private val trailingCopyRegex = Regex("""\s*(copy|copia|c[oó]pia)\s*\d*$""", RegexOption.IGNORE_CASE)

    /**
     * Brazilian / LATAM aliases → canonical key (already alphanumeric lowercase).
     * Keys and values must be normalize() output form.
     */
    private val ALIASES: Map<String, String> = mapOf(
        // Globo family
        "globo" to "globo",
        "tvglobo" to "globo",
        "redegglobo" to "globo",
        "globosp" to "globo",
        "globorj" to "globo",
        "globomg" to "globo",
        "globonews" to "globonews",
        "gnews" to "globonews",
        "gnt" to "gnt",
        "multishow" to "multishow",
        "sporTV" to "sportv",
        "sportv" to "sportv",
        "sportv2" to "sportv2",
        "sportv3" to "sportv3",
        "premiere" to "premiere",
        "premierefc" to "premiere",
        "premiere1" to "premiere1",
        "premiere2" to "premiere2",
        "premiere3" to "premiere3",
        "premiere4" to "premiere4",
        "premiere5" to "premiere5",
        "premiere6" to "premiere6",
        "premiere7" to "premiere7",
        "premiere8" to "premiere8",
        "combate" to "combate",
        "geglobo" to "sportv",
        // SBT / Record / Band
        "sbt" to "sbt",
        "tvsbt" to "sbt",
        "record" to "record",
        "recordtv" to "record",
        "recordnews" to "recordnews",
        "band" to "band",
        "bandtv" to "band",
        "bandnews" to "bandnews",
        "bandsports" to "bandsports",
        "band sports" to "bandsports",
        // Sports
        "espn" to "espn",
        "espn2" to "espn2",
        "espn3" to "espn3",
        "espn4" to "espn4",
        "espnbrasil" to "espn",
        "foxsports" to "foxsports",
        "foxsports2" to "foxsports2",
        "dazn" to "dazn",
        "tntsports" to "tntsports",
        "tntsports1" to "tntsports",
        "space" to "space",
        // News / kids / movies
        "cnnbrasil" to "cnnbrasil",
        "cnn" to "cnn",
        "discoverykids" to "discoverykids",
        "gloob" to "gloob",
        "gloobinho" to "gloobinho",
        "cartoon" to "cartoonnetwork",
        "cartoonnetwork" to "cartoonnetwork",
        "nick" to "nickelodeon",
        "nickelodeon" to "nickelodeon",
        "disney" to "disney",
        "disneyjr" to "disneyjr",
        "disneyjunior" to "disneyjr",
        "telecine" to "telecine",
        "telecinepipoca" to "telecinepipoca",
        "telecineaction" to "telecineaction",
        "hbo" to "hbo",
        "hbo2" to "hbo2",
        "max" to "max",
        "warner" to "warner",
        "universal" to "universal",
        "axn" to "axn",
        "sony" to "sony",
        "fx" to "fx",
        "star" to "star",
        "starchannel" to "starchannel",
        // International common
        "bbc" to "bbc",
        "bbcone" to "bbcone",
        "bbctwo" to "bbctwo",
        "cnninternational" to "cnn",
        "discovery" to "discovery",
        "history" to "history",
        "natgeo" to "nationalgeographic",
        "nationalgeographic" to "nationalgeographic"
    )

    /**
     * Full normalize: lowercase, strip accents, non-alphanumeric removed.
     * Example: "BBC One HD" → "bbconehd"
     */
    fun normalize(name: String): String = normalizeRaw(name)

    /**
     * Normalize after stripping quality/region noise.
     * Example: "Globo HD [BR]" → "globo"
     */
    fun normalizeCore(name: String): String {
        val cleaned = stripNoise(name)
        return normalizeRaw(cleaned)
    }

    /**
     * Canonical alias if known, else core normalize.
     */
    fun normalizeCanonical(name: String): String {
        val core = normalizeCore(name)
        if (core.isEmpty()) return ""
        return ALIASES[core] ?: core
    }

    /**
     * Ordered unique candidate keys for matching (strongest first).
     */
    fun candidates(name: String): List<String> {
        if (name.isBlank()) return emptyList()
        val keys = linkedSetOf<String>()
        normalize(name).takeIf { it.isNotEmpty() }?.let { keys.add(it) }
        normalizeCore(name).takeIf { it.isNotEmpty() }?.let { keys.add(it) }
        normalizeCanonical(name).takeIf { it.isNotEmpty() }?.let { keys.add(it) }
        // Also try without leading "tv"
        keys.filter { it.startsWith("tv") && it.length > 3 }
            .map { it.removePrefix("tv") }
            .forEach { keys.add(it) }
        return keys.toList()
    }

    /**
     * Token set for soft matching (words after noise strip).
     */
    fun tokens(name: String): Set<String> {
        val cleaned = stripNoise(name)
            .lowercase(Locale.ROOT)
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(nonAlphanumericRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
        if (cleaned.isEmpty()) return emptySet()
        return cleaned.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in NOISE_TOKENS }
            .toSet()
    }

    /**
     * Soft similarity: Jaccard on tokens. 0..1
     */
    fun tokenSimilarity(a: String, b: String): Float {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        if (union == 0) return 0f
        return inter.toFloat() / union.toFloat()
    }

    private fun stripNoise(name: String): String {
        var s = name.trim()
        s = bracketNoiseRegex.replace(s, " ")
        s = pipePrefixRegex.replace(s, " ")
        s = leadingNumberRegex.replace(s, " ")
        s = trailingCopyRegex.replace(s, " ")
        for (rx in noiseTokenRegex) {
            s = rx.replace(s, " ")
        }
        return s.replace(multiSpaceRegex, " ").trim()
    }

    private fun normalizeRaw(name: String): String {
        if (name.isBlank()) return ""
        val lower = name.lowercase(Locale.ROOT)
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return stripped.replace(nonAlphanumericRegex, "")
    }
}
