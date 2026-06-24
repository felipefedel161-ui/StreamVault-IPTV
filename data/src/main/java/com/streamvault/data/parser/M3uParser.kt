package com.streamvault.data.parser

import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.util.ChannelNormalizer
import com.streamvault.domain.util.StreamEntryUrlPolicy
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Robust M3U parser that handles real-world malformed playlists.
 * Parses line-by-line in a streaming fashion to handle large files.
 *
 * Supports:
 * - #EXTM3U header
 * - #EXTINF tags with attributes
 * - Group-title for categories
 * - TVG attributes (tvg-id, tvg-name, tvg-logo, tvg-chno)
 * - Tokenized / expiring URLs
 * - Broken / malformed entries (skipped gracefully)
 */
class M3uParser {

    data class M3uHeader(
        val tvgUrl: String? = null,
        val userAgent: String? = null
    )

    data class M3uEntry(
        val name: String,
        val groupTitle: String,
        val tvgId: String?,
        val tvgName: String?,
        val tvgLogo: String?,
        val tvgChno: Int?,
        val tvgLanguage: String?,
        val tvgCountry: String?,
        val catchUp: String?,
        val catchUpDays: Int?,
        val catchUpSource: String?,
        val timeshift: String?,
        val url: String,
        val userAgent: String? = null,
        val rating: String? = null,
        val year: String? = null,
        val genre: String? = null,
        val durationSeconds: Int? = null
    )

    data class ParseResult(
        val header: M3uHeader,
        val entries: List<M3uEntry>
    )

    fun parse(inputStream: InputStream): ParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val entries = mutableListOf<M3uEntry>()
        var header = M3uHeader()
        var pendingExtinf: ParsedExtinf? = null

        // lineSequence() streams the InputStream one line at a time — only a single
        // String is allocated per iteration; prior lines are immediately eligible for GC.
        // This keeps memory flat regardless of playlist size (e.g. 500 MB feeds).
        reader.use {
            reader.lineSequence()
                .map { sanitizeLine(it) }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    when {
                        line.startsWith("#EXTM3U", ignoreCase = true) -> {
                            header = parseHeader(line)
                        }
                        line.startsWith("#EXTINF", ignoreCase = true) -> {
                            pendingExtinf = parseExtinf(line)
                        }
                        line.startsWith("#") -> {
                            pendingExtinf = pendingExtinf?.let { applyPendingDirective(it, line) }
                        }
                        pendingExtinf != null -> {
                            parseEntry(pendingExtinf!!, line, header.userAgent)?.let(entries::add)
                            pendingExtinf = null
                        }
                        else -> {
                            // Non-comment, non-URL line with no pending EXTINF — skip
                        }
                    }
                }
        }

        return ParseResult(header, entries)
    }

    suspend fun parseStreaming(
        inputStream: InputStream,
        onHeader: suspend (M3uHeader) -> Unit = {},
        onEntry: suspend (M3uEntry) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var header = M3uHeader()
        var pendingExtinf: ParsedExtinf? = null

        // Use a for-loop over lineSequence() rather than .forEach{} because onHeader and
        // onEntry are suspend lambdas. Sequence.forEach is not an inline function, so the
        // compiler cannot allow coroutine suspension inside it. A for-loop over the same
        // sequence is fully coroutine-compatible and preserves the one-line-at-a-time
        // memory profile.
        reader.use {
            for (rawLine in reader.lineSequence()) {
                val line = sanitizeLine(rawLine)
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        header = parseHeader(line)
                        onHeader(header)
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingExtinf = parseExtinf(line)
                    }
                    line.startsWith("#") -> {
                        pendingExtinf = pendingExtinf?.let { applyPendingDirective(it, line) }
                    }
                    pendingExtinf != null -> {
                        parseEntry(pendingExtinf!!, line, header.userAgent)?.let { onEntry(it) }
                        pendingExtinf = null
                    }
                    else -> {
                        // Non-comment, non-URL line with no pending EXTINF — skip
                    }
                }
            }
        }
    }

    fun parseToChannels(inputStream: InputStream, providerId: Long): List<Channel> {
        return parse(inputStream).entries.mapIndexed { index, entry ->
            Channel(
                id = index.toLong() + 1, // Will be replaced by stableId in SyncManager
                name = entry.name,
                logoUrl = entry.tvgLogo,
                groupTitle = entry.groupTitle,
                epgChannelId = entry.tvgId ?: entry.tvgName,
                number = entry.tvgChno ?: (index + 1),
                streamUrl = entry.url,
                catchUpSupported = entry.supportsCatchUp(),
                catchUpDays = entry.catchUpDays ?: 0,
                catchUpSource = entry.catchUpSource,
                providerId = providerId,
                logicalGroupId = ChannelNormalizer.getLogicalGroupId(entry.name, providerId)
            )
        }
    }

    private fun parseEntry(extinf: ParsedExtinf, url: String, globalUserAgent: String?): M3uEntry? {
        if (url.isBlank() || extinf.name.isBlank()) return null
        if (!isAllowedStreamUrl(url)) return null
        return M3uEntry(
            name = extinf.name.take(500),
            groupTitle = (extinf.groupTitle ?: "Uncategorized").take(200),
            tvgId = extinf.tvgId,
            tvgName = extinf.tvgName,
            tvgLogo = extinf.tvgLogo,
            tvgChno = extinf.tvgChno?.toIntOrNull()?.takeIf { it in 1..99999 },
            tvgLanguage = extinf.tvgLanguage,
            tvgCountry = extinf.tvgCountry,
            catchUp = extinf.catchUp,
            catchUpDays = extinf.catchUpDays?.toIntOrNull()?.takeIf { it in 0..365 },
            catchUpSource = extinf.catchUpSource,
            timeshift = extinf.timeshift,
            url = url,
            userAgent = extinf.userAgent ?: globalUserAgent,
            rating = extinf.rating,
            year = extinf.year,
            genre = extinf.genre,
            durationSeconds = extinf.durationSeconds
        )
    }

    private fun M3uEntry.supportsCatchUp(): Boolean {
        return !catchUp.isNullOrBlank() || !catchUpSource.isNullOrBlank() || !timeshift.isNullOrBlank()
    }

    private fun applyPendingDirective(extinf: ParsedExtinf, line: String): ParsedExtinf {
        val extGrpTitle = parseStandaloneGroupTitle(line) ?: return extinf
        return extinf.copy(groupTitle = extGrpTitle)
    }

    private fun sanitizeLine(rawLine: String): String =
        rawLine.removePrefix("\uFEFF").trim()

    private data class ParsedExtinf(
        val name: String,
        val durationSeconds: Int?,
        val tvgId: String?,
        val tvgName: String?,
        val tvgLogo: String?,
        val groupTitle: String?,
        val tvgChno: String?,
        val tvgLanguage: String?,
        val tvgCountry: String?,
        val catchUp: String?,
        val catchUpDays: String?,
        val catchUpSource: String?,
        val timeshift: String?,
        val userAgent: String?,
        val rating: String?,
        val year: String?,
        val genre: String?
    )

    private fun parseHeader(line: String): M3uHeader {
        val attributes = parseAttributes(line, line.indexOf(' '))
        return M3uHeader(
            tvgUrl = extractHeaderEpgUrl(attributes),
            userAgent = attributes["user-agent"]
        )
    }

    private fun extractHeaderEpgUrl(attributes: Map<String, String>): String? {
        val rawValue = headerEpgAttributes
            .firstNotNullOfOrNull { key -> attributes[key]?.takeIf { it.isNotBlank() } }
            ?: return null

        return rawValue
            .split(',')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun parseStandaloneGroupTitle(line: String): String? {
        if (!line.startsWith(EXT_GRP_TAG, ignoreCase = true)) return null
        val suffix = line.substring(EXT_GRP_TAG.length).trimStart()
        val rawValue = when {
            suffix.startsWith(":") || suffix.startsWith("=") -> suffix.substring(1)
            else -> suffix
        }
        return rawValue.trim().trim('"').takeIf { it.isNotEmpty() }
    }

    private fun parseExtinf(line: String): ParsedExtinf? {
        val colonIndex = line.indexOf(':')
        if (colonIndex < 0 || colonIndex == line.lastIndex) return null
        val payload = line.substring(colonIndex + 1).trim()
        if (payload.isEmpty()) return null

        val commaIndex = findFirstUnquotedComma(payload)
        if (commaIndex < 0) return null

        val metadata = payload.substring(0, commaIndex).trim()
        val name = payload.substring(commaIndex + 1).trim()
        if (name.isEmpty()) return null

        val attributes = parseAttributes(metadata, findAttributeStart(metadata))
        return ParsedExtinf(
            name = name,
            durationSeconds = extractDuration(metadata),
            tvgId = attributes["tvg-id"],
            tvgName = attributes["tvg-name"],
            tvgLogo = attributes["tvg-logo"],
            groupTitle = attributes["group-title"],
            tvgChno = attributes["tvg-chno"],
            tvgLanguage = attributes["tvg-language"],
            tvgCountry = attributes["tvg-country"],
            catchUp = attributes["catchup"],
            catchUpDays = attributes["catchup-days"],
            catchUpSource = attributes["catchup-source"],
            timeshift = attributes["timeshift"],
            userAgent = attributes["user-agent"],
            rating = attributes["rating"],
            year = attributes["year"],
            genre = attributes["genre"]
        )
    }

    private fun extractDuration(metadata: String): Int? {
        val attributeStart = findAttributeStart(metadata)
        val durationEnd = if (attributeStart >= 0) attributeStart else metadata.length
        return metadata.substring(0, durationEnd).trim().toIntOrNull()
    }

    private fun findAttributeStart(metadata: String): Int {
        val length = metadata.length
        var index = 0
        while (index < length && !metadata[index].isWhitespace()) {
            index++
        }
        while (index < length && metadata[index].isWhitespace()) {
            index++
        }
        return if (index < length) index else -1
    }

    private fun findFirstUnquotedComma(value: String): Int {
        var inQuotes = false
        var i = 0
        while (i < value.length) {
            when {
                // Backslash escape inside a quoted string: skip the next character entirely
                value[i] == '\\' && inQuotes && i + 1 < value.length -> i++
                value[i] == '"' -> inQuotes = !inQuotes
                value[i] == ',' && !inQuotes -> return i
            }
            i++
        }
        return -1
    }

    private fun String.indexOfFirst(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (i in startIndex until length) {
            if (predicate(this[i])) return i
        }
        return -1
    }

    private fun parseAttributes(content: String, startIndex: Int): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        if (startIndex < 0 || startIndex >= content.length) {
            return attributes
        }

        val length = content.length
        var index = startIndex
        while (index < length) {
            while (index < length && content[index].isWhitespace()) {
                index++
            }
            if (index >= length) {
                break
            }

            val keyStart = index
            while (index < length && !content[index].isWhitespace() && content[index] != '=') {
                index++
            }
            if (index <= keyStart) {
                break
            }
            val key = content.substring(keyStart, index).lowercase()

            while (index < length && content[index].isWhitespace()) {
                index++
            }
            if (index >= length || content[index] != '=') {
                while (index < length && !content[index].isWhitespace()) {
                    index++
                }
                continue
            }
            index++

            while (index < length && content[index].isWhitespace()) {
                index++
            }
            if (index >= length) {
                break
            }

            val value = if (content[index] == '"') {
                index++
                val sb = StringBuilder()
                while (index < length && content[index] != '"') {
                    if (content[index] == '\\' && index + 1 < length) {
                        // Consume the backslash and include the next character literally
                        index++
                        sb.append(content[index])
                    } else {
                        sb.append(content[index])
                    }
                    index++
                }
                if (index < length && content[index] == '"') {
                    index++
                }
                sb.toString()
            } else {
                // Unquoted value — consume until the next known attribute key or end
                val valueStart = index
                var end = index
                while (end < length) {
                    if (content[end].isWhitespace()) {
                        val nextTokenStart = content.indexOfFirst(end) { !it.isWhitespace() }
                        if (nextTokenStart < 0) break
                        val eqPos = content.indexOf('=', nextTokenStart)
                        if (eqPos > nextTokenStart) {
                            val candidateKey = content.substring(nextTokenStart, eqPos).trim().lowercase()
                            if (candidateKey.isNotEmpty() && !candidateKey.any { it.isWhitespace() } && candidateKey in knownAttributes) {
                                break
                            }
                        }
                        end++
                    } else {
                        end++
                    }
                }
                index = end
                content.substring(valueStart, end).trim()
            }

            if (key.isNotBlank()) {
                attributes[key] = value
            }
        }

        return attributes
    }

    /**
     * Returns true if [url] is an acceptable stream entry URL.
     *
     * Delegates to the shared [StreamEntryUrlPolicy] (which detects control-separator
     * injection up to two encoding layers and enforces the allowed-scheme set) and
     * additionally rejects URLs that exceed the maximum safe length.
     */
    private fun isAllowedStreamUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.length > 8192) return false
        return StreamEntryUrlPolicy.isAllowed(trimmed)
    }

    companion object {
        private const val EXT_GRP_TAG = "#EXTGRP"

        private val headerEpgAttributes = listOf(
            "x-tvg-url",
            "url-tvg",
            "tvg-url",
            "url-xml"
        )

        /** Exposed for callers outside M3uParser (e.g. SyncManager) to avoid duplicate logic. */
        fun isVodEntry(entry: M3uEntry): Boolean {
            val url = entry.url.lowercase()
            val group = entry.groupTitle.lowercase()

            // URL-based detection (most reliable)
            if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") ||
                url.contains("/movie/") || url.contains("/series/")) return true

            // Group-based detection: normalize to strip emojis and diacritics for matching
            val normalizedGroup = normalizeGroupTitle(group)

            // VOD group keywords (movie + series)
            return normalizedGroup.contains("movie") ||
                    normalizedGroup.contains("vod") ||
                    normalizedGroup.contains("film") ||
                    normalizedGroup.contains("serie") ||
                    normalizedGroup.contains("novela") ||
                    normalizedGroup.contains("dorama") ||
                    normalizedGroup.contains("anime") ||
                    normalizedGroup.contains("animacao") ||
                    normalizedGroup.contains("infantil") ||
                    normalizedGroup.contains("infantis") ||
                    normalizedGroup.contains("netflix") ||
                    normalizedGroup.contains("prime video") ||
                    normalizedGroup.contains("disney") ||
                    normalizedGroup.contains("hbo max") ||
                    normalizedGroup.contains("max") ||
                    normalizedGroup.contains("globoplay") ||
                    normalizedGroup.contains("paramount") ||
                    normalizedGroup.contains("star+") ||
                    normalizedGroup.contains("apple tv") ||
                    normalizedGroup.contains("discovery+") ||
                    normalizedGroup.contains("crunchyroll") ||
                    normalizedGroup.contains("funimation") ||
                    normalizedGroup.contains("pluto") ||
                    normalizedGroup.contains("lionsgate") ||
                    normalizedGroup.contains("amc plus") ||
                    normalizedGroup.contains("diretv") ||
                    normalizedGroup.contains("directv") ||
                    normalizedGroup.contains("claro video") ||
                    normalizedGroup.contains("sbt+") ||
                    normalizedGroup.contains("brasil paralelo") ||
                    normalizedGroup.contains("lancamento") ||
                    normalizedGroup.contains("lançamento") ||
                    normalizedGroup.contains("legenda") ||
                    normalizedGroup.contains("nacional") ||
                    normalizedGroup.contains("drama") ||
                    normalizedGroup.contains("comedia") ||
                    normalizedGroup.contains("acao") ||
                    normalizedGroup.contains("ação") ||
                    normalizedGroup.contains("terror") ||
                    normalizedGroup.contains("suspense") ||
                    normalizedGroup.contains("romance") ||
                    normalizedGroup.contains("aventura") ||
                    normalizedGroup.contains("fantasia") ||
                    normalizedGroup.contains("faroeste") ||
                    normalizedGroup.contains("ficcao") ||
                    normalizedGroup.contains("guerra") ||
                    normalizedGroup.contains("crime") ||
                    normalizedGroup.contains("familia") ||
                    normalizedGroup.contains("documentario") ||
                    normalizedGroup.contains("religioso") ||
                    normalizedGroup.contains("top 10") ||
                    normalizedGroup.contains("cinema") ||
                    normalizedGroup.contains("programas de tv") ||
                    normalizedGroup.contains("outras produtoras") ||
                    normalizedGroup.contains("univer")
        }

        /**
         * Returns true when the entry belongs to a series/episode group (has episodes S01E01 etc.),
         * as opposed to a standalone movie. Used to route entries into the Series catalog
         * instead of the Movie catalog during M3U import.
         *
         * Detection priority:
         * 1. URL path: /series/ → always series
         * 2. URL path: /movie/ → always movie (not series)
         * 3. Name pattern: contains "S01", "E01", "S01E01" style → series
         * 4. Group title keywords: streaming platforms and novela/dorama/anime groups → series
         */
        fun isSeriesEntry(entry: M3uEntry): Boolean {
            val url = entry.url.lowercase()
            val name = entry.name.lowercase()
            val group = entry.groupTitle.lowercase()
            val normalizedGroup = normalizeGroupTitle(group)

            // URL path is definitive
            if (url.contains("/series/")) return true
            if (url.contains("/movie/")) return false

            // Episode name pattern: "S01 E01", "S01E01", "S1E1", "S01 E001" etc.
            val episodePattern = Regex("""s\d{1,2}\s*e\d{1,3}""")
            if (episodePattern.containsMatchIn(name)) return true

            // Group-based: streaming platforms that carry series in this list
            return normalizedGroup.contains("novela") ||
                    normalizedGroup.contains("dorama") ||
                    normalizedGroup.contains("anime") ||
                    normalizedGroup.contains("animacao") ||
                    normalizedGroup.contains("crunchyroll") ||
                    normalizedGroup.contains("funimation") ||
                    normalizedGroup.contains("netflix") ||
                    normalizedGroup.contains("prime video") ||
                    normalizedGroup.contains("globoplay") ||
                    normalizedGroup.contains("max") ||
                    normalizedGroup.contains("disney+") ||
                    normalizedGroup.contains("star+") ||
                    normalizedGroup.contains("apple tv") ||
                    normalizedGroup.contains("paramount+") ||
                    normalizedGroup.contains("discovery+") ||
                    normalizedGroup.contains("pluto") ||
                    normalizedGroup.contains("lionsgate") ||
                    normalizedGroup.contains("amc plus") ||
                    normalizedGroup.contains("diretv") ||
                    normalizedGroup.contains("directv") ||
                    normalizedGroup.contains("claro video") ||
                    normalizedGroup.contains("sbt+") ||
                    normalizedGroup.contains("brasil paralelo") ||
                    normalizedGroup.contains("outras produtoras") ||
                    normalizedGroup.contains("univer") ||
                    normalizedGroup.contains("programas de tv") ||
                    // ── ABA INFANTIL ─────────────────────────────────────────────────────────
                    // Garante que grupos como "⛄ INFANTIS", "INFANTIL", "KIDS", etc. sejam
                    // roteados para o catálogo de Séries (que alimenta a aba Infantil),
                    // em vez de cair como Filmes avulsos.
                    normalizedGroup.contains("infantil") ||
                    normalizedGroup.contains("infantis") ||
                    normalizedGroup.contains("kids") ||
                    normalizedGroup.contains("cartoon") ||
                    normalizedGroup.contains("desenho")
        }

        /**
         * Strip emojis, leading/trailing symbols and diacritics from a group title
         * to make keyword matching reliable regardless of decoration.
         */
        private fun normalizeGroupTitle(raw: String): String =
            raw.replace(Regex("[^\\p{L}\\p{N}\\s+]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase()

        val knownAttributes = setOf(
            "tvg-id",
            "tvg-name",
            "tvg-logo",
            "group-title",
            "tvg-chno",
            "tvg-language",
            "tvg-country",
            "catchup",
            "catchup-days",
            "catchup-source",
            "timeshift",
            "user-agent",
            "rating",
            "year",
            "genre"
        )
    }
}
