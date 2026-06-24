package com.streamvault.app.profiles

data class UserProfile(
    val id: String,
    val name: String,
    val avatarIndex: Int,   // index into PROFILE_AVATARS
    val pinHash: String?,   // null = no PIN
    val color: Long         // stored as Long to survive serialization
)

/** Built-in avatar set — emoji rendered inside themed cards. */
val PROFILE_AVATARS = listOf(
    "🎬", "🎮", "🎵", "📺",
    "🌟", "🦁", "🚀", "👑",
    "🎭", "🏆", "🦊", "🐉",
    "⚡", "🎯", "🌙", "🔥"
)

/** Accent colours for avatar cards. */
val PROFILE_COLORS = listOf(
    0xFF69A8FF, // Blue   (brand)
    0xFFFF6B6B, // Red
    0xFF4FD39A, // Green
    0xFFFFAB40, // Orange
    0xFFCE93D8, // Purple
    0xFF4DD0E1, // Cyan
    0xFFFFD54F, // Yellow
    0xFFFF80AB, // Pink
)
