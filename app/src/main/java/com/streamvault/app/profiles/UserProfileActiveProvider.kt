package com.streamvault.app.profiles

import com.streamvault.domain.repository.ActiveProfileProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação de [ActiveProfileProvider] que delega ao [UserProfileRepository].
 *
 * Registrada via Hilt em [com.streamvault.app.di.ProfileModule] e injetada nos
 * repositórios de dados que precisam isolar conteúdo por perfil.
 */
@Singleton
class UserProfileActiveProvider @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) : ActiveProfileProvider {
    override fun activeProfileId(): String =
        userProfileRepository.activeProfileId.value ?: ""
}
