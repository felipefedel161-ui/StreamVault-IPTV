package com.streamvault.app.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfileSelectionUiState(
    val profiles: List<UserProfile> = emptyList(),
    val pendingPinProfileId: String? = null,   // show PIN prompt for this profile
    val pinError: Boolean = false,
    val editingProfile: UserProfile? = null,   // non-null → show edit dialog
    val isCreatingNew: Boolean = false
)

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    val repository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSelectionUiState())
    val uiState: StateFlow<ProfileSelectionUiState> = _uiState.asStateFlow()

    init {
        // Auto-create a default profile the very first time
        if (repository.hasNoProfiles()) {
            repository.createDefaultProfile()
        }
        viewModelScope.launch {
            repository.profiles.collectLatest { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
        }
    }

    /** User tapped a profile card. */
    fun onProfileSelected(profile: UserProfile, onActivated: (UserProfile) -> Unit) {
        if (profile.pinHash != null) {
            _uiState.value = _uiState.value.copy(
                pendingPinProfileId = profile.id,
                pinError = false
            )
        } else {
            activate(profile)
            onActivated(profile)
        }
    }

    /** User submitted PIN. */
    fun onPinSubmitted(pin: String, onSuccess: (UserProfile) -> Unit) {
        val id = _uiState.value.pendingPinProfileId ?: return
        val profile = _uiState.value.profiles.firstOrNull { it.id == id } ?: return
        if (repository.verifyPin(profile, pin)) {
            _uiState.value = _uiState.value.copy(pendingPinProfileId = null, pinError = false)
            activate(profile)
            onSuccess(profile)
        } else {
            _uiState.value = _uiState.value.copy(pinError = true)
        }
    }

    fun dismissPin() {
        _uiState.value = _uiState.value.copy(pendingPinProfileId = null, pinError = false)
    }

    /** Save / update a profile. */
    fun saveProfile(
        id: String?,
        name: String,
        avatarIndex: Int,
        color: Long,
        pin: String?          // null = keep existing; "" = remove PIN
    ) {
        val existing = _uiState.value.profiles.firstOrNull { it.id == id }
        val profile = UserProfile(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Usuário" },
            avatarIndex = avatarIndex,
            pinHash = when {
                pin == null -> existing?.pinHash         // keep
                pin.isBlank() -> null                    // remove
                else -> repository.hashPin(pin)          // set new
            },
            color = color
        )
        repository.saveProfile(profile)
        _uiState.value = _uiState.value.copy(editingProfile = null, isCreatingNew = false)
    }

    fun startEditProfile(profile: UserProfile) {
        _uiState.value = _uiState.value.copy(editingProfile = profile, isCreatingNew = false)
    }

    fun startCreateProfile() {
        _uiState.value = _uiState.value.copy(isCreatingNew = true, editingProfile = null)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingProfile = null, isCreatingNew = false)
    }

    fun deleteProfile(id: String) {
        repository.deleteProfile(id)
        _uiState.value = _uiState.value.copy(editingProfile = null)
    }

    private fun activate(profile: UserProfile) {
        repository.setActiveProfile(profile.id)
    }
}
