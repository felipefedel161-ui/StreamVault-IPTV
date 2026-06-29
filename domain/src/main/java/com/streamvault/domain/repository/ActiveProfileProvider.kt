package com.streamvault.domain.repository

/**
 * Fornece o ID do perfil de usuário ativo para os repositórios de dados.
 *
 * Esta interface faz ponte entre o módulo `data` (que não pode depender de `app`)
 * e o [com.streamvault.app.profiles.UserProfileRepository] (que vive em `app`).
 *
 * String vazia indica "sem perfil ativo" — dados são tratados como legados/compartilhados.
 */
interface ActiveProfileProvider {
    /** Retorna o UUID do perfil ativo, ou string vazia se nenhum estiver selecionado. */
    fun activeProfileId(): String
}
