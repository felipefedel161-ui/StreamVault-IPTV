package com.streamvault.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.streamvault.data.epg.EpgNameNormalizer
import com.streamvault.data.epg.EpgResolutionEngine
import com.streamvault.data.local.dao.EpgChannelDao
import com.streamvault.data.local.dao.EpgProgrammeDao
import com.streamvault.data.local.dao.EpgSourceDao
import com.streamvault.data.local.dao.ProviderEpgSourceDao
import com.streamvault.data.local.entity.EpgChannelEntity
import com.streamvault.data.local.entity.EpgProgrammeEntity
import com.streamvault.data.local.entity.EpgSourceEntity
import com.streamvault.data.local.entity.ProviderEpgSourceEntity
import com.streamvault.data.mapper.toDomain
import com.streamvault.data.parser.XmltvParser
import com.streamvault.data.util.UrlSecurityPolicy
import com.streamvault.domain.model.EpgResolutionSummary
import com.streamvault.domain.model.EpgSource
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.ProviderEpgSourceAssignment
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.EpgSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgSourceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epgSourceDao: EpgSourceDao,
    private val providerEpgSourceDao: ProviderEpgSourceDao,
    private val epgChannelDao: EpgChannelDao,
    private val epgProgrammeDao: EpgProgrammeDao,
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient,
    private val resolutionEngine: EpgResolutionEngine
) : EpgSourceRepository {

    companion object {
        private const val TAG = "EpgSourceRepo"
        private const val MAX_EPG_SIZE_BYTES = 200L * 1_048_576 // 200 MB
        private const val CHANNEL_BATCH_SIZE = 500
        private const val PROGRAMME_BATCH_SIZE = 500
    }

    private val sourceRefreshMutexes = ConcurrentHashMap<Long, Mutex>()

    // ── EPG Source CRUD ────────────────────────────────────────────

    override fun getAllSources(): Flow<List<EpgSource>> =
        epgSourceDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSourceById(id: Long): EpgSource? =
        epgSourceDao.getById(id)?.toDomain()

    override suspend fun addSource(name: String, url: String): Result<EpgSource> {
        val trimmedUrl = url.trim()
        val validationError = UrlSecurityPolicy.validateOptionalEpgUrl(trimmedUrl)
        if (validationError != null) return Result.error(validationError)
        if (trimmedUrl.isBlank()) return Result.error("URL cannot be empty")

        val existing = epgSourceDao.getByUrl(trimmedUrl)
        if (existing != null) return Result.error("A source with this URL already exists")

        val trimmedName = name.trim().takeIf { it.isNotEmpty() } ?: "EPG Source"
        val now = System.currentTimeMillis()
        val entity = EpgSourceEntity(
            name = trimmedName,
            url = trimmedUrl,
            createdAt = now,
            updatedAt = now
        )
        val id = epgSourceDao.insert(entity)
        return Result.success(entity.copy(id = id).toDomain())
    }

    override suspend fun updateSource(source: EpgSource): Result<Unit> {
        val trimmedUrl = source.url.trim()
        val validationError = UrlSecurityPolicy.validateOptionalEpgUrl(trimmedUrl)
        if (validationError != null) return Result.error(validationError)

        val existing = epgSourceDao.getById(source.id) ?: return Result.error("Source not found")
        epgSourceDao.update(
            existing.copy(
                name = source.name.trim().takeIf { it.isNotEmpty() } ?: existing.name,
                url = trimmedUrl,
                enabled = source.enabled,
                priority = source.priority,
                updatedAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun deleteSource(id: Long) {
        // Cascade: delete channels + programmes for this source, then the source itself
        epgProgrammeDao.deleteBySource(id)
        epgChannelDao.deleteBySource(id)
        epgSourceDao.delete(id)
    }

    override suspend fun setSourceEnabled(id: Long, enabled: Boolean) {
        epgSourceDao.setEnabled(id, enabled)
    }

    // ── Provider ↔ Source assignment ───────────────────────────────

    override fun getAssignmentsForProvider(providerId: Long): Flow<List<ProviderEpgSourceAssignment>> =
        providerEpgSourceDao.getForProvider(providerId)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun assignSourceToProvider(
        providerId: Long,
        epgSourceId: Long,
        priority: Int
    ): Result<Unit> {
        val source = epgSourceDao.getById(epgSourceId) ?: return Result.error("Source not found")
        providerEpgSourceDao.insert(
            ProviderEpgSourceEntity(
                providerId = providerId,
                epgSourceId = source.id,
                priority = priority
            )
        )
        return Result.success(Unit)
    }

    override suspend fun unassignSourceFromProvider(providerId: Long, epgSourceId: Long) {
        providerEpgSourceDao.delete(providerId, epgSourceId)
    }

    override suspend fun updateAssignmentPriority(providerId: Long, epgSourceId: Long, priority: Int) {
        val assignments = providerEpgSourceDao.getForProviderSync(providerId)
        val target = assignments.find { it.epgSourceId == epgSourceId } ?: return
        providerEpgSourceDao.update(target.copy(priority = priority))
    }

    // ── Refresh / Ingestion ────────────────────────────────────────

    override suspend fun refreshSource(sourceId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val mutex = sourceRefreshMutexes.computeIfAbsent(sourceId) { Mutex() }
        mutex.withLock {
            val source = epgSourceDao.getById(sourceId)
                ?: return@withLock Result.error("Source not found")

            val now = System.currentTimeMillis()
            try {
                epgSourceDao.updateRefreshStatus(sourceId, now, null)

                val rawInputStream: java.io.InputStream = if (source.url.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(source.url))
                        ?: run {
                            val err = "Cannot open local file"
                            epgSourceDao.updateRefreshStatus(sourceId, now, err)
                            return@withLock Result.error(err)
                        }
                } else {
                    val request = Request.Builder().url(source.url).build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val err = "HTTP ${response.code}"
                        response.close()
                        epgSourceDao.updateRefreshStatus(sourceId, now, err)
                        return@withLock Result.error("Failed to download EPG: $err")
                    }

                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    if (contentLength > MAX_EPG_SIZE_BYTES) {
                        response.close()
                        val err = "File too large (${contentLength / 1_048_576}MB)"
                        epgSourceDao.updateRefreshStatus(sourceId, now, err)
                        return@withLock Result.error(err)
                    }

                    response.body?.byteStream() ?: run {
                        epgSourceDao.updateRefreshStatus(sourceId, now, "Empty response")
                        return@withLock Result.error("Empty EPG response")
                    }
                }

                val channelBatch = ArrayList<EpgChannelEntity>(CHANNEL_BATCH_SIZE)
                val programmeBatch = ArrayList<EpgProgrammeEntity>(PROGRAMME_BATCH_SIZE)
                var channelCount = 0
                var programmeCount = 0

                // Delete old data before ingesting
                epgChannelDao.deleteBySource(sourceId)
                epgProgrammeDao.deleteBySource(sourceId)

                rawInputStream.use { raw ->
                    val limited = object : FilterInputStream(raw) {
                        private var bytesRead = 0L
                        override fun read(): Int {
                            if (bytesRead >= MAX_EPG_SIZE_BYTES) throw IOException("EPG response too large (>200 MB)")
                            return super.read().also { if (it >= 0) bytesRead++ }
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (bytesRead >= MAX_EPG_SIZE_BYTES) throw IOException("EPG response too large (>200 MB)")
                            return super.read(b, off, len).also { if (it > 0) bytesRead += it }
                        }
                    }
                    val decompressed = xmltvParser.maybeDecompressGzip(source.url, limited)

                    xmltvParser.parseStreamingWithChannels(
                        inputStream = decompressed,
                        onChannel = { xmltvChannel ->
                            channelBatch.add(
                                EpgChannelEntity(
                                    epgSourceId = sourceId,
                                    xmltvChannelId = xmltvChannel.id,
                                    displayName = xmltvChannel.displayName,
                                    normalizedName = EpgNameNormalizer.normalize(xmltvChannel.displayName),
                                    iconUrl = xmltvChannel.iconUrl
                                )
                            )
                            channelCount++
                            if (channelBatch.size >= CHANNEL_BATCH_SIZE) {
                                epgChannelDao.insertAll(channelBatch.toList())
                                channelBatch.clear()
                            }
                        },
                        onProgramme = { programme ->
                            programmeBatch.add(
                                EpgProgrammeEntity(
                                    epgSourceId = sourceId,
                                    xmltvChannelId = programme.channelId,
                                    startTime = programme.startTime,
                                    endTime = programme.endTime,
                                    title = programme.title,
                                    subtitle = programme.subtitle,
                                    description = programme.description,
                                    category = programme.category,
                                    lang = programme.lang,
                                    rating = programme.rating,
                                    imageUrl = programme.imageUrl,
                                    episodeInfo = programme.episodeInfo
                                )
                            )
                            programmeCount++
                            if (programmeBatch.size >= PROGRAMME_BATCH_SIZE) {
                                epgProgrammeDao.insertAll(programmeBatch.toList())
                                programmeBatch.clear()
                            }
                        }
                    )
                }

                // Flush remaining
                if (channelBatch.isNotEmpty()) {
                    epgChannelDao.insertAll(channelBatch.toList())
                }
                if (programmeBatch.isNotEmpty()) {
                    epgProgrammeDao.insertAll(programmeBatch.toList())
                }

                epgSourceDao.updateRefreshSuccess(sourceId, System.currentTimeMillis())
                Log.d(TAG, "Refreshed source $sourceId: $channelCount channels, $programmeCount programmes")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh source $sourceId", e)
                epgSourceDao.updateRefreshStatus(sourceId, now, e.message ?: "Unknown error")
                Result.error("Failed to refresh EPG source: ${e.message}", e)
            }
        }
    }

    override suspend fun refreshAllForProvider(providerId: Long): Result<Unit> {
        val assignments = providerEpgSourceDao.getEnabledForProviderSync(providerId)
        val errors = mutableListOf<String>()
        for (assignment in assignments) {
            val result = refreshSource(assignment.epgSourceId)
            if (result is Result.Error) {
                errors.add("Source ${assignment.epgSourceId}: ${result.message}")
            }
        }
        return if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.error("Some sources failed: ${errors.joinToString("; ")}")
        }
    }

    // ── Resolution ─────────────────────────────────────────────────

    override suspend fun resolveForProvider(providerId: Long): EpgResolutionSummary =
        resolutionEngine.resolveForProvider(providerId)

    override suspend fun getResolutionSummary(providerId: Long): EpgResolutionSummary =
        resolutionEngine.getResolutionSummary(providerId)

    // ── Resolved query ─────────────────────────────────────────────

    override suspend fun getResolvedProgramsForChannels(
        providerId: Long,
        channelIds: List<Long>,
        startTime: Long,
        endTime: Long
    ): Map<String, List<Program>> =
        resolutionEngine.getResolvedProgrammes(providerId, channelIds, startTime, endTime)
}
