package com.streamvault.app.profiles

import android.content.Context
import android.content.SharedPreferences
import com.streamvault.app.profiles.PROFILE_COLORS
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_profiles", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    init {
        _profiles.value = loadProfiles()
        _activeProfileId.value = prefs.getString(KEY_ACTIVE_PROFILE, null)
    }

    fun saveProfile(profile: UserProfile) {
        val current = _profiles.value.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        _profiles.value = current
        persistProfiles(current)
    }

    fun deleteProfile(id: String) {
        val updated = _profiles.value.filter { it.id != id }
        _profiles.value = updated
        persistProfiles(updated)
    }

    fun setActiveProfile(id: String) {
        _activeProfileId.value = id
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    fun clearActiveProfile() {
        _activeProfileId.value = null
        prefs.edit().remove(KEY_ACTIVE_PROFILE).apply()
    }

    fun verifyPin(profile: UserProfile, pin: String): Boolean {
        if (profile.pinHash == null) return true
        return hashPin(pin) == profile.pinHash
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Returns true if no profiles exist yet (first-time setup). */
    fun hasNoProfiles(): Boolean = _profiles.value.isEmpty()

    /** Creates a default "Principal" profile (no PIN). */
    fun createDefaultProfile(): UserProfile {
        val p = UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Principal",
            avatarIndex = 0,
            pinHash = null,
            color = PROFILE_COLORS[0]
        )
        saveProfile(p)
        return p
    }

    // ── persistence ────────────────────────────────────────────────────────────

    private fun loadProfiles(): List<UserProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toProfile() }
        } catch (_: Exception) { emptyList() }
    }

    private fun persistProfiles(list: List<UserProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun JSONObject.toProfile() = UserProfile(
        id = getString("id"),
        name = getString("name"),
        avatarIndex = optInt("avatarIndex", 0),
        pinHash = optString("pinHash").takeIf { it.isNotBlank() },
        color = optLong("color", PROFILE_COLORS[0])
    )

    private fun UserProfile.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("avatarIndex", avatarIndex)
        put("pinHash", pinHash ?: "")
        put("color", color)
    }

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    }
}
