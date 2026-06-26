package com.streamvault.app.ui.screens.player

import com.streamvault.player.PlayerError

internal fun classifyPlaybackError(error: PlayerError): PlayerRecoveryType = when (error) {
    is PlayerError.NetworkError -> {
        if (error.message.contains("timeout", ignoreCase = true)) {
            PlayerRecoveryType.BUFFER_TIMEOUT
        } else if (
            error.message.contains("HTTP 456", ignoreCase = true) ||
            error.message.contains("HTTP 509", ignoreCase = true) ||
            error.message.contains("access denied", ignoreCase = true) ||
            error.message.contains("temporary link", ignoreCase = true) ||
            error.message.contains("playback path", ignoreCase = true)
        ) {
            PlayerRecoveryType.SOURCE
        } else if (
            error.message.contains("401", ignoreCase = true) ||
            error.message.contains("403", ignoreCase = true) ||
            error.message.contains("unauthorized", ignoreCase = true) ||
            error.message.contains("forbidden", ignoreCase = true)
        ) {
            // 401/403 from the player engine (not from probe) = real auth issue
            PlayerRecoveryType.SOURCE
        } else {
            PlayerRecoveryType.NETWORK
        }
    }

    is PlayerError.SourceError -> PlayerRecoveryType.SOURCE
    is PlayerError.DecoderError -> PlayerRecoveryType.DECODER
    is PlayerError.DrmError -> PlayerRecoveryType.DRM
    is PlayerError.UnknownError -> {
        if (error.message.contains("timeout", ignoreCase = true)) {
            PlayerRecoveryType.BUFFER_TIMEOUT
        } else {
            PlayerRecoveryType.UNKNOWN
        }
    }
}

internal fun resolvePlaybackErrorMessage(error: PlayerError): String = when (classifyPlaybackError(error)) {
    PlayerRecoveryType.NETWORK ->
        "Este stream não está respondendo. Você pode tentar novamente ou tentar outra fonte."

    PlayerRecoveryType.SOURCE -> when {
        error.message.contains("HTTP 456", ignoreCase = true) ||
            error.message.contains("access denied", ignoreCase = true) ->
            "O provedor rejeitou a reprodução deste canal. " +
                "O MAC ou a assinatura pode não ter acesso a este stream."

        error.message.contains("HTTP 509", ignoreCase = true) ->
            "O provedor rejeitou a reprodução — provável limite de conexões simultâneas ou banda."

        error.message.contains("401", ignoreCase = true) ||
            error.message.contains("403", ignoreCase = true) ||
            error.message.contains("unauthorized", ignoreCase = true) ||
            error.message.contains("forbidden", ignoreCase = true) ->
            "O servidor rejeitou a conexão (autenticação). " +
                "Verifique se sua assinatura está ativa e tente novamente."

        error.message.contains("temporary link", ignoreCase = true) ->
            "O portal emitiu um link temporário inválido. Tentar novamente pode corrigir."

        error.message.contains("playback path", ignoreCase = true) ->
            "Este portal requer um caminho de reprodução diferente do padrão."

        else ->
            "Não foi possível iniciar este stream nas fontes disponíveis."
    }

    PlayerRecoveryType.DECODER ->
        "Este stream não pôde ser reproduzido no modo de decodificador atual."

    PlayerRecoveryType.DRM ->
        "A reprodução requer credenciais DRM válidas ou um nível de segurança de dispositivo compatível."

    PlayerRecoveryType.BUFFER_TIMEOUT ->
        "O buffering ficou parado por tempo excessivo neste stream."

    PlayerRecoveryType.CATCH_UP ->
        "A reprodução do replay está indisponível para o programa selecionado."

    PlayerRecoveryType.UNKNOWN ->
        error.message.ifBlank { "A reprodução falhou por uma razão desconhecida." }
}
