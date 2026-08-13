package com.streamvault.domain.model

/**
 * Local viewer profile (Netflix-style). Scoped to this device installation.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarId: String,
    val isKids: Boolean = false,
    val pinEnabled: Boolean = false,
    /** SHA-256 hex of pin+salt; null if no pin */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

data class ProfileAvatar(
    val id: String,
    val emoji: String,
    /** ARGB color for background circle */
    val colorArgb: Long,
    val label: String
)

object ProfileAvatars {
    val all: List<ProfileAvatar> = listOf(
        ProfileAvatar("fox", "🦊", 0xFFE67E22, "Raposa"),
        ProfileAvatar("panda", "🐼", 0xFF2C3E50, "Panda"),
        ProfileAvatar("owl", "🦉", 0xFF8E44AD, "Coruja"),
        ProfileAvatar("cat", "🐱", 0xFFE74C3C, "Gato"),
        ProfileAvatar("dog", "🐶", 0xFF3498DB, "Cão"),
        ProfileAvatar("lion", "🦁", 0xFFF39C12, "Leão"),
        ProfileAvatar("frog", "🐸", 0xFF27AE60, "Sapo"),
        ProfileAvatar("unicorn", "🦄", 0xFF9B59B6, "Unicórnio"),
        ProfileAvatar("robot", "🤖", 0xFF1ABC9C, "Robô"),
        ProfileAvatar("alien", "👽", 0xFF16A085, "Alien"),
        ProfileAvatar("ninja", "🥷", 0xFF34495E, "Ninja"),
        ProfileAvatar("star", "🌟", 0xFFF1C40F, "Estrela"),
        ProfileAvatar("rocket", "🚀", 0xFF2980B9, "Foguete"),
        ProfileAvatar("dragon", "🐲", 0xFFC0392B, "Dragão"),
        ProfileAvatar("penguin", "🐧", 0xFF5D6D7E, "Pinguim"),
        ProfileAvatar("kids_bear", "🧸", 0xFFFF6B9D, "Ursinho"),
        ProfileAvatar("kids_rainbow", "🌈", 0xFF00CEC9, "Arco-íris"),
        ProfileAvatar("kids_dino", "🦕", 0xFF55EFC4, "Dino")
    )

    fun byId(id: String): ProfileAvatar =
        all.firstOrNull { it.id == id } ?: all.first()
}
