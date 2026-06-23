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
        const val SERVER_URL = "https://artsil121.eu.pythonanywhere.com"
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "InovaPlayer/7.0"
    }

    fun getDeviceId(): String {
        try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mac = wifiManager?.connectionInfo?.macAddress
            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") {
                return mac.uppercase()
            }
        } catch (_: Exception) {}

        try {
            val netInterface = java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull {
                    it.name.equals("wlan0", ignoreCase = true) ||
                    it.name.equals("eth0", ignoreCase = true)
                }
            val macBytes = netInterface?.hardwareAddress
            if (macBytes != null && macBytes.size == 6) {
                val mac = macBytes.joinToString(":") { "%02X".format(it) }
                if (mac != "02:00:00:00:00:00") return mac
            }
        } catch (_: Exception) {}

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return (androidId?.takeIf { it.isNotBlank() }
            ?: Build.SERIAL?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            ?: "UNKNOWN").uppercase()
    }

    suspend fun checkActivation(
        deviceId: String = getDeviceId(),
        fingerprint: String? = null
    ): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = buildString {
                append("$SERVER_URL/api/status/${deviceId.uppercase()}")
                if (!fingerprint.isNullOrBlank()) append("?fp=$fingerprint")
            }
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
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

            parseResponse(responseCode, json)

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

    suspend fun activate(): ActivationResult = checkActivation()

    private fun parseResponse(code: Int, json: JSONObject): ActivationResult {
        return when (code) {
            200 -> {
                val m3u = json.optString("m3u_url", "").trim()
                val exp = json.optString("expiracao", "")
                val dias = json.optInt("dias_restantes", -1)
                if (m3u.isBlank()) {
                    ActivationResult.Error(ActivationError.NO_M3U)
                } else {
                    ActivationResult.Success(m3uUrl = m3u, expiracao = exp, diasRestantes = dias)
                }
            }
            403 -> {
                val msg = json.optString("mensagem", "").lowercase()
                if (msg.contains("expirad") || msg.contains("expired")) {
                    ActivationResult.Error(ActivationError.EXPIRED)
                } else if (msg.contains("outro aparelho") || msg.contains("clonagem") || msg.contains("fingerprint")) {
                    ActivationResult.Error(ActivationError.FINGERPRINT_MISMATCH)
                } else {
                    ActivationResult.Error(ActivationError.NO_M3U)
                }
            }
            404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
            else -> ActivationResult.Error(ActivationError.GENERIC)
        }
    }
}
