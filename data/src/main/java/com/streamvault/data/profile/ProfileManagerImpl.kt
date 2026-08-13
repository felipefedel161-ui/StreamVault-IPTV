package com.streamvault.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamvault.domain.manager.ProfileManager
import com.streamvault.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "streamvault_profiles")

@Singleton
class ProfileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : ProfileManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store get() = context.profileDataStore

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    override val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    override val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val _sessionReady = MutableStateFlow(false)
    override val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    init {
        scope.launch {
            ensureDefaultProfile()
            val list = loadProfiles()
            _profiles.value = list
            // Do NOT auto-select: user must pick on ProfilePicker (Netflix behavior)
            _activeProfile.value = null
            _sessionReady.value = false
        }
    }

    override fun observeProfiles() = store.data.map { prefs ->
        decodeList(prefs[KEY_PROFILES])
    }

    override suspend fun ensureDefaultProfile() {
        val list = loadProfiles()
        if (list.isEmpty()) {
            val def = UserProfile(
                id = UUID.randomUUID().toString(),
                name = "Principal",
                avatarId = "fox",
                isKids = false
            )
            saveProfiles(listOf(def))
            _profiles.value = listOf(def)
        }
    }

    override suspend fun createProfile(name: String, avatarId: String, isKids: Boolean): UserProfile {
        val list = loadProfiles().toMutableList()
        require(list.size < MAX_PROFILES) { "Máximo de $MAX_PROFILES perfis" }
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Perfil" },
            avatarId = avatarId,
            isKids = isKids
        )
        list.add(profile)
        saveProfiles(list)
        _profiles.value = list
        return profile
    }

    override suspend fun updateProfile(profile: UserProfile) {
        val list = loadProfiles().map { if (it.id == profile.id) profile else it }
        saveProfiles(list)
        _profiles.value = list
        if (_activeProfile.value?.id == profile.id) {
            _activeProfile.value = profile
        }
    }

    override suspend fun deleteProfile(id: String): Boolean {
        val list = loadProfiles()
        if (list.size <= 1) return false
        val next = list.filterNot { it.id == id }
        saveProfiles(next)
        _profiles.value = next
        if (_activeProfile.value?.id == id) {
            _activeProfile.value = null
            _sessionReady.value = false
            store.edit { it.remove(KEY_ACTIVE) }
        }
        return true
    }

    override suspend fun selectProfile(id: String, pin: String?): Boolean {
        val profile = loadProfiles().firstOrNull { it.id == id } ?: return false
        if (profile.pinEnabled) {
            if (pin == null || !verifyPin(profile, pin)) return false
        }
        val updated = profile.copy(lastUsedAt = System.currentTimeMillis())
        val list = loadProfiles().map { if (it.id == id) updated else it }
        saveProfiles(list)
        store.edit { it[KEY_ACTIVE] = id }
        _profiles.value = list
        _activeProfile.value = updated
        _sessionReady.value = true
        return true
    }

    override suspend fun clearSession() {
        _activeProfile.value = null
        _sessionReady.value = false
    }

    override suspend fun setPin(profileId: String, pin: String?) {
        val list = loadProfiles().map { p ->
            if (p.id != profileId) p
            else if (pin.isNullOrBlank()) {
                p.copy(pinEnabled = false, pinHash = null, pinSalt = null)
            } else {
                val salt = UUID.randomUUID().toString()
                p.copy(pinEnabled = true, pinSalt = salt, pinHash = hashPin(pin, salt))
            }
        }
        saveProfiles(list)
        _profiles.value = list
        _activeProfile.value = list.firstOrNull { it.id == profileId } ?: _activeProfile.value
    }

    override fun verifyPin(profile: UserProfile, pin: String): Boolean {
        val salt = profile.pinSalt ?: return false
        val hash = profile.pinHash ?: return false
        return hashPin(pin, salt) == hash
    }

    override suspend fun maxProfiles(): Int = MAX_PROFILES

    private suspend fun loadProfiles(): List<UserProfile> {
        val prefs = store.data.first()
        return decodeList(prefs[KEY_PROFILES])
    }

    private suspend fun saveProfiles(list: List<UserProfile>) {
        store.edit { it[KEY_PROFILES] = gson.toJson(list) }
    }

    private fun decodeList(json: String?): List<UserProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<UserProfile>>() {}.type
            gson.fromJson<List<UserProfile>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val KEY_PROFILES = stringPreferencesKey("profiles_json")
        private val KEY_ACTIVE = stringPreferencesKey("active_profile_id")
        const val MAX_PROFILES = 5
    }
}
