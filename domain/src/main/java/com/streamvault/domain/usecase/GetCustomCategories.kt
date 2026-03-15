package com.streamvault.domain.usecase

import com.streamvault.domain.model.Category
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.VirtualCategoryIds
import com.streamvault.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class GetCustomCategories @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) {
    private val logger = Logger.getLogger("GetCustomCategories")
    operator fun invoke(contentType: ContentType = ContentType.LIVE): Flow<List<Category>> {
        return kotlinx.coroutines.flow.combine(
            favoriteRepository.getGroups(contentType),
            favoriteRepository.getGlobalFavoriteCount(contentType),
            favoriteRepository.getGroupFavoriteCounts(contentType)
        ) { groups, globalCount, groupCounts ->
            val categories = groups.map { group ->
                Category(
                    id = -group.id, // Negative IDs reserve virtual groups.
                    name = group.name,
                    type = contentType,
                    isVirtual = true,
                    count = groupCounts.getOrDefault(group.id, 0)
                )
            }.toMutableList()

            // Prepend global "Favorites" virtual category.
            categories.add(
                index = 0,
                element = Category(
                    id = VirtualCategoryIds.FAVORITES,
                    name = "Favorites",
                    type = contentType,
                    isVirtual = true,
                    count = globalCount
                )
            )

            categories.toList()
        }.catch { e ->
            logger.log(Level.WARNING, "Failed to load custom categories", e)
            emit(emptyList())
        }
    }
}
