package com.streamvault.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Favorite
import com.streamvault.domain.model.PlaybackHistory
import com.streamvault.domain.model.PlaybackWatchedStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileLibraryStore: DataStore<Preferences> by preferencesDataStore(
    name = "streamvault_profile_library"
)

/**
 * Per-profile favorites + continue-watching (Netflix-style isolation).
 * Independent of Room so we avoid a risky schema migration mid-flight.
 */
@Singleton
class ProfileLibraryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val store get() = context.profileLibraryStore

    fun observeFavorites(profileId: String): Flow<List<Favorite>> =
        store.data.map { prefs ->
            val lib = decode(prefs[keyFor(profileId)])
            lib.favorites.map { it.toDomain() }
        }

    fun observeHistory(profileId: String, limit: Int = 100): Flow<List<PlaybackHistory>> =
        store.data.map { prefs ->
            val lib = decode(prefs[keyFor(profileId)])
            lib.history
                .map { it.toDomain() }
                .sortedByDescending { it.lastWatchedAt }
                .take(limit)
        }

    suspend fun getFavorites(profileId: String): List<Favorite> {
        val lib = load(profileId)
        return lib.favorites.map { it.toDomain() }
    }

    suspend fun getHistory(profileId: String, limit: Int = 100): List<PlaybackHistory> {
        val lib = load(profileId)
        return lib.history.map { it.toDomain() }
            .sortedByDescending { it.lastWatchedAt }
            .take(limit)
    }

    suspend fun addFavorite(profileId: String, favorite: Favorite) {
        update(profileId) { lib ->
            val filtered = lib.favorites.filterNot {
                it.providerId == favorite.providerId &&
                    it.contentId == favorite.contentId &&
                    it.contentType == favorite.contentType.name &&
                    it.groupId == favorite.groupId
            }
            lib.copy(favorites = filtered + FavoriteDto.from(favorite))
        }
    }

    suspend fun removeFavorite(
        profileId: String,
        providerId: Long,
        contentId: Long,
        contentType: ContentType,
        groupId: Long? = null
    ) {
        update(profileId) { lib ->
            lib.copy(
                favorites = lib.favorites.filterNot {
                    it.providerId == providerId &&
                        it.contentId == contentId &&
                        it.contentType == contentType.name &&
                        (groupId == null || it.groupId == groupId)
                }
            )
        }
    }

    suspend fun isFavorite(
        profileId: String,
        providerId: Long,
        contentId: Long,
        contentType: ContentType
    ): Boolean {
        return load(profileId).favorites.any {
            it.providerId == providerId &&
                it.contentId == contentId &&
                it.contentType == contentType.name
        }
    }

    suspend fun recordHistory(profileId: String, history: PlaybackHistory) {
        update(profileId) { lib ->
            val keyMatch = { h: HistoryDto ->
                h.contentId == history.contentId &&
                    h.contentType == history.contentType.name &&
                    h.providerId == history.providerId &&
                    h.seriesId == history.seriesId &&
                    h.seasonNumber == history.seasonNumber &&
                    h.episodeNumber == history.episodeNumber
            }
            val existing = lib.history.firstOrNull(keyMatch)
            val merged = HistoryDto.from(history).copy(
                id = existing?.id ?: history.id,
                watchCount = (existing?.watchCount ?: 0) + 1,
                lastWatchedAt = maxOf(history.lastWatchedAt, System.currentTimeMillis())
            )
            val rest = lib.history.filterNot(keyMatch)
            lib.copy(history = (listOf(merged) + rest).take(200))
        }
    }

    suspend fun updateResume(profileId: String, history: PlaybackHistory) {
        update(profileId) { lib ->
            val keyMatch = { h: HistoryDto ->
                h.contentId == history.contentId &&
                    h.contentType == history.contentType.name &&
                    h.providerId == history.providerId &&
                    h.seriesId == history.seriesId &&
                    h.seasonNumber == history.seasonNumber &&
                    h.episodeNumber == history.episodeNumber
            }
            val rest = lib.history.filterNot(keyMatch)
            lib.copy(history = (listOf(HistoryDto.from(history)) + rest).take(200))
        }
    }

    suspend fun getHistoryItem(
        profileId: String,
        contentId: Long,
        contentType: ContentType,
        providerId: Long,
        seriesId: Long? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): PlaybackHistory? {
        return load(profileId).history.firstOrNull {
            it.contentId == contentId &&
                it.contentType == contentType.name &&
                it.providerId == providerId &&
                (seriesId == null || it.seriesId == seriesId) &&
                (seasonNumber == null || it.seasonNumber == seasonNumber) &&
                (episodeNumber == null || it.episodeNumber == episodeNumber)
        }?.toDomain()
    }

    suspend fun removeHistory(
        profileId: String,
        contentId: Long,
        contentType: ContentType,
        providerId: Long
    ) {
        update(profileId) { lib ->
            lib.copy(
                history = lib.history.filterNot {
                    it.contentId == contentId &&
                        it.contentType == contentType.name &&
                        it.providerId == providerId
                }
            )
        }
    }

    suspend fun clearHistory(profileId: String) {
        update(profileId) { it.copy(history = emptyList()) }
    }

    private suspend fun load(profileId: String): ProfileLibrary {
        val prefs = store.data.first()
        return decode(prefs[keyFor(profileId)])
    }

    private suspend fun update(profileId: String, block: (ProfileLibrary) -> ProfileLibrary) {
        store.edit { prefs ->
            val current = decode(prefs[keyFor(profileId)])
            prefs[keyFor(profileId)] = gson.toJson(block(current))
        }
    }

    private fun decode(json: String?): ProfileLibrary {
        if (json.isNullOrBlank()) return ProfileLibrary()
        return try {
            gson.fromJson(json, ProfileLibrary::class.java) ?: ProfileLibrary()
        } catch (_: Exception) {
            ProfileLibrary()
        }
    }

    private fun keyFor(profileId: String) = stringPreferencesKey("lib_$profileId")

    data class ProfileLibrary(
        val favorites: List<FavoriteDto> = emptyList(),
        val history: List<HistoryDto> = emptyList()
    )

    data class FavoriteDto(
        val id: Long = 0,
        val providerId: Long,
        val contentId: Long,
        val contentType: String,
        val position: Int = 0,
        val groupId: Long? = null,
        val addedAt: Long = System.currentTimeMillis()
    ) {
        fun toDomain() = Favorite(
            id = id,
            providerId = providerId,
            contentId = contentId,
            contentType = runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.MOVIE),
            position = position,
            groupId = groupId,
            addedAt = addedAt
        )
        companion object {
            fun from(f: Favorite) = FavoriteDto(
                id = f.id,
                providerId = f.providerId,
                contentId = f.contentId,
                contentType = f.contentType.name,
                position = f.position,
                groupId = f.groupId,
                addedAt = f.addedAt
            )
        }
    }

    data class HistoryDto(
        val id: Long = 0,
        val contentId: Long,
        val contentType: String,
        val providerId: Long,
        val title: String = "",
        val posterUrl: String? = null,
        val streamUrl: String = "",
        val resumePositionMs: Long = 0,
        val totalDurationMs: Long = 0,
        val lastWatchedAt: Long = 0,
        val watchCount: Int = 1,
        val watchedStatus: String = "IN_PROGRESS",
        val seriesId: Long? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null
    ) {
        fun toDomain() = PlaybackHistory(
            id = id,
            contentId = contentId,
            contentType = runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.MOVIE),
            providerId = providerId,
            title = title,
            posterUrl = posterUrl,
            streamUrl = streamUrl,
            resumePositionMs = resumePositionMs,
            totalDurationMs = totalDurationMs,
            lastWatchedAt = lastWatchedAt,
            watchCount = watchCount,
            watchedStatus = runCatching { PlaybackWatchedStatus.valueOf(watchedStatus) }
                .getOrDefault(PlaybackWatchedStatus.IN_PROGRESS),
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
        companion object {
            fun from(h: PlaybackHistory) = HistoryDto(
                id = h.id,
                contentId = h.contentId,
                contentType = h.contentType.name,
                providerId = h.providerId,
                title = h.title,
                posterUrl = h.posterUrl,
                streamUrl = h.streamUrl,
                resumePositionMs = h.resumePositionMs,
                totalDurationMs = h.totalDurationMs,
                lastWatchedAt = h.lastWatchedAt,
                watchCount = h.watchCount,
                watchedStatus = h.watchedStatus.name,
                seriesId = h.seriesId,
                seasonNumber = h.seasonNumber,
                episodeNumber = h.episodeNumber
            )
        }
    }
}
