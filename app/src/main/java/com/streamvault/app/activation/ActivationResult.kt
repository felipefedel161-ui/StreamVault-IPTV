package com.streamvault.app.activation

sealed class ActivationResult {
    data class Success(
        val m3uUrl: String,
        val expiracao: String,
        val diasRestantes: Int
    ) : ActivationResult()

    data class Error(val error: ActivationError) : ActivationResult()
}

enum class ActivationError {
    EXPIRED,
    NOT_FOUND,
    NO_M3U,
    NETWORK,
    GENERIC
}
