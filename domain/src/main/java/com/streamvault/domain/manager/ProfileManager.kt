package com.streamvault.domain.manager

import com.streamvault.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProfileManager {
    val profiles: StateFlow<List<UserProfile>>
    val activeProfile: StateFlow<UserProfile?>
    /** True after user picked a profile this process (or auto-selected single profile). */
    val sessionReady: StateFlow<Boolean>

    fun observeProfiles(): Flow<List<UserProfile>>

    suspend fun ensureDefaultProfile()
    suspend fun createProfile(name: String, avatarId: String, isKids: Boolean = false): UserProfile
    suspend fun updateProfile(profile: UserProfile)
    suspend fun deleteProfile(id: String): Boolean
    suspend fun selectProfile(id: String, pin: String? = null): Boolean
    suspend fun clearSession()
    suspend fun setPin(profileId: String, pin: String?)
    fun verifyPin(profile: UserProfile, pin: String): Boolean
    suspend fun maxProfiles(): Int
}
