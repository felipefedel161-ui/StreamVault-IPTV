package com.streamvault.app.ui.screens.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.streamvault.player.PlaybackState

private const val TAG = "PlayerNetReconnect"

/**
 * Monitora a conectividade de rede durante a reprodução e aciona uma nova tentativa
 * automaticamente quando a conexão é restaurada após uma queda.
 *
 * Ciclo de vida:
 *  - [register] ao abrir o player (ou quando o stream começa a tocar)
 *  - [unregister] em [PlayerViewModel.onCleared]
 *
 * Só reconecta se:
 *  1. O player estava em estado de erro ou buffering prolongado (provável queda de rede)
 *  2. Existe um stream ativo (currentStreamUrl não vazio)
 *  3. A rede restaurada tem capacidade de internet validada
 */
internal fun PlayerViewModel.registerNetworkReconnectCallback(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

    val callback = object : ConnectivityManager.NetworkCallback() {
        private var wasLost = false

        override fun onLost(network: Network) {
            wasLost = true
            Log.d(TAG, "Rede perdida durante reprodução — aguardando restauração")
        }

        override fun onAvailable(network: Network) {
            if (!wasLost) return          // primeira conexão — não é reconexão
            wasLost = false

            val url = currentStreamUrl
            if (url.isBlank()) {
                Log.d(TAG, "Rede restaurada mas nenhum stream ativo — ignorando")
                return
            }

            val state = playerEngine.playbackState.value
            val shouldRetry = state == PlaybackState.ERROR ||
                state == PlaybackState.BUFFERING ||
                state == PlaybackState.IDLE

            if (!shouldRetry) {
                Log.d(TAG, "Rede restaurada mas player ok ($state) — ignorando")
                return
            }

            Log.i(TAG, "Rede restaurada — reconectando stream automaticamente (estado=$state)")
            appendRecoveryAction("Reconexão automática de rede")
            retryStream(url, currentChannelFlow.value?.epgChannelId)
        }
    }

    try {
        cm.registerNetworkCallback(request, callback)
        _networkCallback = callback
        _connectivityManager = cm
        Log.d(TAG, "NetworkCallback registrado")
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao registrar NetworkCallback: ${e.message}")
    }
}

internal fun PlayerViewModel.unregisterNetworkReconnectCallback() {
    val cb = _networkCallback ?: return
    try {
        _connectivityManager?.unregisterNetworkCallback(cb)
        Log.d(TAG, "NetworkCallback removido")
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao remover NetworkCallback: ${e.message}")
    } finally {
        _networkCallback = null
        _connectivityManager = null
    }
}
