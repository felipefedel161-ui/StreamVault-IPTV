package com.streamvault.app.ui.screens.player

import com.streamvault.domain.model.Channel
import java.util.Locale

internal fun isAuthExpiryPlaybackError(message: String?): Boolean {
    val normalized = message.orEmpty().lowercase(Locale.ROOT)
    return "401" in normalized ||
        "403" in normalized ||
        "unauthorized" in normalized ||
        "forbidden" in normalized ||
        "authentication" in normalized ||
        "token" in normalized ||
        "expired" in normalized
}

internal fun resolveCatchUpFailureMessage(
    channel: Channel?,
    archiveRequested: Boolean,
    programHasArchive: Boolean
): String {
    if (!archiveRequested || channel == null) {
        return "A reprodução de catch-up precisa de um contexto de canal ao vivo válido."
    }
    return when {
        !channel.catchUpSupported && !programHasArchive ->
            "Este canal não anuncia suporte a arquivo no provedor atual."
        channel.streamId <= 0L && channel.catchUpSource.isNullOrBlank() ->
            "O provedor anuncia catch-up, mas não expôs metadados de replay para este canal."
        else ->
            "O replay está indisponível para o programa selecionado no momento."
    }
}

internal fun resolvePlaybackFormatLabel(
    currentResolvedPlaybackUrl: String,
    currentStreamUrl: String
): String {
    val url = currentResolvedPlaybackUrl.ifBlank { currentStreamUrl }.lowercase(Locale.ROOT)
    return when {
        url.contains("ext=m3u8") || url.endsWith(".m3u8") -> "HLS"
        url.contains("ext=ts") || url.endsWith(".ts") -> "TS"
        else -> "stream"
    }
}
