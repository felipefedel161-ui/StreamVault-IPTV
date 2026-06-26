package com.streamvault.app.ui.screens.player

import com.streamvault.domain.model.ProviderType
import java.net.URI
import java.util.Locale

internal data class PlaybackProbeFailure(
    val message: String,
    val recoveryType: PlayerRecoveryType,
    /**
     * When true the probe returned a hard failure (404, 456, 5xx) that
     * almost certainly means the stream won't play — block playback.
     *
     * When false the probe returned an ambiguous rejection (401/403) that
     * many IPTV providers issue on plain HTTP requests even though the
     * actual media player succeeds. We still surface the failure object so
     * the caller can decide, but [shouldBlockPlayback] = false lets it
     * fall through and attempt playback anyway.
     */
    val shouldBlockPlayback: Boolean = true
)

/**
 * Maps an HTTP probe response code to a [PlaybackProbeFailure], or null
 * if the response code is acceptable (2xx / 3xx).
 *
 * 401 / 403 are deliberately treated as **non-blocking** because many
 * IPTV providers serve those codes on a simple GET/HEAD probe but accept
 * the stream when opened by an actual media player (different User-Agent,
 * byte-range behaviour, or cookie handling). Blocking on 403 produces a
 * false-positive "rejected" error that prevents streams from ever playing.
 *
 * The caller should:
 *  - If [PlaybackProbeFailure.shouldBlockPlayback] == true  → show error, abort.
 *  - If [PlaybackProbeFailure.shouldBlockPlayback] == false → attempt playback;
 *    let the player engine surface the real error if it actually fails.
 */
internal fun resolvePlaybackProbeFailure(responseCode: Int): PlaybackProbeFailure? = when (responseCode) {
    // ── Soft rejections — probe may be unreliable, try playing anyway ──────
    401, 403 -> PlaybackProbeFailure(
        message = "O provedor retornou $responseCode ao verificar o stream. " +
            "Tentando reproduzir diretamente — se falhar, tente outra fonte ou verifique sua assinatura.",
        recoveryType = PlayerRecoveryType.SOURCE,
        shouldBlockPlayback = false   // ← KEY CHANGE: do not block playback
    )

    // ── Empty temporary link — Stalker/MAG portal behaviour ────────────────
    204 -> PlaybackProbeFailure(
        message = "O portal emitiu um link temporário vazio para este stream (HTTP 204). " +
            "Tentar novamente pode atualizar a sessão do portal.",
        recoveryType = PlayerRecoveryType.SOURCE,
        shouldBlockPlayback = true
    )

    // ── Provider-level access denial ────────────────────────────────────────
    456 -> PlaybackProbeFailure(
        message = "O provedor rejeitou a reprodução deste canal (HTTP 456). " +
            "O MAC ou a assinatura pode não ter acesso a este stream.",
        recoveryType = PlayerRecoveryType.SOURCE,
        shouldBlockPlayback = true
    )

    // ── Stream not found ────────────────────────────────────────────────────
    404 -> PlaybackProbeFailure(
        message = "Este stream do provedor está indisponível no momento (404).",
        recoveryType = PlayerRecoveryType.SOURCE,
        shouldBlockPlayback = true
    )

    // ── Server-side errors — usually transient, allow retry ─────────────────
    in 500..599 -> PlaybackProbeFailure(
        message = "O provedor retornou um erro de servidor para este stream ($responseCode).",
        recoveryType = PlayerRecoveryType.NETWORK,
        shouldBlockPlayback = true
    )

    // ── Anything else (2xx, 3xx, etc.) is fine ──────────────────────────────
    else -> null
}

internal fun shouldSkipPlaybackProbe(
    providerType: ProviderType,
    url: String
): Boolean {
    val normalizedPath = runCatching {
        URI(url).path?.lowercase(Locale.ROOT).orEmpty()
    }.getOrDefault("")
    val normalizedQuery = runCatching {
        URI(url).query?.lowercase(Locale.ROOT).orEmpty()
    }.getOrDefault("")
    return when (providerType) {
        ProviderType.STALKER_PORTAL -> normalizedPath.endsWith("/play/live.php") ||
            normalizedPath.endsWith("/play/movie.php") ||
            "play_token=" in normalizedQuery

        ProviderType.XTREAM_CODES -> normalizedPath.contains("/live/")

        else -> false
    }
}
