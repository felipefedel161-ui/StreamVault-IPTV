package com.streamvault.app.activation

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InovaActivationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // ⚠️ Altere para o URL do seu servidor PythonAnywhere
        private const val SERVER_URL = "https://artsil121.eu.pythonanywhere.com"
        private const val TIMEOUT_MS = 15_000
    }

    /**
     * Obtém o identificador único do dispositivo:
     * Tenta primeiro o MAC Wi-Fi; se indisponível (Android 10+), usa o Android ID.
     * O ID é sempre normalizado em MAIÚSCULAS para corresponder ao banco do servidor.
     */
    fun getDeviceId(): String {
        // Tentativa 1: MAC Wi-Fi (disponível em Android < 10 e alguns TVs)
        try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mac = wifiManager?.connectionInfo?.macAddress
            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") {
                return mac.uppercase()
            }
        } catch (_: Exception) {}

        // Tentativa 2: endereço MAC da interface de rede via /sys/class/net
        try {
            val netInterface = java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull { it.name.equals("wlan0", ignoreCase = true) || it.name.equals("eth0", ignoreCase = true) }
            val macBytes = netInterface?.hardwareAddress
            if (macBytes != null && macBytes.size == 6) {
                val mac = macBytes.joinToString(":") { "%02X".format(it) }
                if (mac != "02:00:00:00:00:00") return mac
            }
        } catch (_: Exception) {}

        // Fallback: Android ID (único por dispositivo + aplicativo + usuário)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return (androidId ?: Build.SERIAL ?: "UNKNOWN").uppercase()
    }

    /**
     * Verifica o status de ativação do dispositivo junto ao servidor.
     * Sem PIN — apenas identifica pelo MAC/Android ID.
     */
    suspend fun activate(): ActivationResult = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()
        try {
            val url = URL("$SERVER_URL/api/status/$deviceId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "InovaPlayer/7.0")
            }

            val responseCode = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()

            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext ActivationResult.Error(ActivationError.GENERIC)

            when (responseCode) {
                200 -> {
                    val m3u = json.optString("m3u_url", "")
                    val exp = json.optString("expiracao", "")
                    val dias = json.optInt("dias_restantes", -1)
                    if (m3u.isBlank()) {
                        ActivationResult.Error(ActivationError.NO_M3U)
                    } else {
                        ActivationResult.Success(m3uUrl = m3u, expiracao = exp, diasRestantes = dias)
                    }
                }
                403 -> {
                    val msg = json.optString("mensagem", "")
                    if (msg.contains("expirada", ignoreCase = true)) {
                        ActivationResult.Error(ActivationError.EXPIRED)
                    } else {
                        ActivationResult.Error(ActivationError.NO_M3U)
                    }
                }
                404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
                else -> ActivationResult.Error(ActivationError.GENERIC)
            }
        } catch (_: java.net.UnknownHostException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: java.net.SocketTimeoutException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: java.io.IOException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: Exception) {
            ActivationResult.Error(ActivationError.GENERIC)
        }
    }
}
