package com.streamvault.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.manager.ProfileManager
import com.streamvault.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles
    val activeProfile: StateFlow<UserProfile?> = profileManager.activeProfile
    val sessionReady: StateFlow<Boolean> = profileManager.sessionReady

    suspend fun select(id: String, pin: String?): Boolean =
        profileManager.selectProfile(id, pin)

    suspend fun create(name: String, avatarId: String, isKids: Boolean): UserProfile =
        profileManager.createProfile(name, avatarId, isKids)

    suspend fun delete(id: String): Boolean = profileManager.deleteProfile(id)

    suspend fun setPin(profileId: String, pin: String?) =
        profileManager.setPin(profileId, pin)

    suspend fun clearSession() = profileManager.clearSession()
}
