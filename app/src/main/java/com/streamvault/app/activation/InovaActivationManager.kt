package com.streamvault.app.activation

import android.content.Context
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
        const val SERVER_URL    = "https://vault-axvc.onrender.com"
        private const val USER_AGENT = "StreamVaultApp/1.0"
        private const val TIMEOUT_MS = 50_000 // Render free pode demorar ~30-45s ao acordar
    }

    fun getDeviceId(): String {
        // 1. MAC via NetworkInterface
        try {
            val iface = java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull {
                    it.name.equals("wlan0", ignoreCase = true) ||
                    it.name.equals("eth0", ignoreCase = true)
                }
            val bytes = iface?.hardwareAddress
            if (bytes != null && bytes.size == 6) {
                val mac = bytes.joinToString(":") { "%02X".format(it) }
                if (mac != "02:00:00:00:00:00") return mac
            }
        } catch (_: Exception) {}

        // 2. Android ID
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
            val urlStr = "$SERVER_URL/api/status/${deviceId.uppercase()}"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: "{}"
            }
            conn.disconnect()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            parseResponse(code, json)
        } catch (e: Exception) {
            ActivationResult.Error(ActivationError.NETWORK)
        }
    }

    suspend fun activate(): ActivationResult = checkActivation()

    private fun parseResponse(code: Int, json: JSONObject): ActivationResult {
        return when (code) {
            200 -> {
                val m3u  = json.optString("m3u_url",        "").trim()
                val exp  = json.optString("expiracao",       "")
                val dias = json.optInt   ("dias_restantes", -1)
                if (m3u.isBlank()) ActivationResult.Error(ActivationError.NO_M3U)
                else ActivationResult.Success(m3uUrl = m3u, expiracao = exp, diasRestantes = dias)
            }
            403 -> {
                val msg = json.optString("mensagem", "").lowercase()
                when {
                    msg.contains("expirad") || msg.contains("expired") ->
                        ActivationResult.Error(ActivationError.EXPIRED)
                    msg.contains("outro aparelho") || msg.contains("fingerprint") ->
                        ActivationResult.Error(ActivationError.FINGERPRINT_MISMATCH)
                    else ->
                        ActivationResult.Error(ActivationError.NO_M3U)
                }
            }
            404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
            else -> ActivationResult.Error(ActivationError.GENERIC)
        }
    }
}
