package com.streamvault.app.activation

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class InovaActivationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SERVER_URL     = "https://vault-axvc.onrender.com"
        private const val TIMEOUT_MS = 50_000
    }

    fun getDeviceId(): String {
        // 1. MAC via NetworkInterface
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (iface in interfaces) {
                if (!iface.name.equals("wlan0", ignoreCase = true) &&
                    !iface.name.equals("eth0",  ignoreCase = true)) continue
                val bytes = iface.hardwareAddress ?: continue
                if (bytes.size != 6) continue
                val mac = bytes.joinToString(":") { "%02X".format(it) }
                if (mac != "02:00:00:00:00:00") return mac
            }
        } catch (_: Exception) {}

        // 2. Android ID fallback
        return (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN").uppercase()
    }

    suspend fun checkActivation(
        deviceId: String = getDeviceId(),
        fingerprint: String? = null
    ): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = "$SERVER_URL/api/status/${deviceId.uppercase()}"
            val url    = URL(urlStr)

            // Usa HttpsURLConnection explicitamente para controle total
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
                // Força HTTP/1.1 — evita problemas com HTTP/2 em alguns Android
                setRequestProperty("Connection",    "close")
                setRequestProperty("User-Agent",    "StreamVaultApp/1.0")
                setRequestProperty("Accept",        "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                instanceFollowRedirects = true
                doInput  = true
                doOutput = false
                useCaches = false
            }

            conn.connect()
            val code = conn.responseCode

            val body = try {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } catch (_: Exception) {
                try {
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                } catch (_: Exception) { "{}" }
            }

            conn.disconnect()

            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }

            when (code) {
                200 -> {
                    val m3u  = json.optString("m3u_url",       "").trim()
                    val exp  = json.optString("expiracao",      "")
                    val dias = json.optInt   ("dias_restantes", 30)
                    if (m3u.isBlank())
                        ActivationResult.Error(ActivationError.NO_M3U)
                    else
                        ActivationResult.Success(m3uUrl = m3u, expiracao = exp, diasRestantes = dias)
                }
                403 -> {
                    val msg = json.optString("mensagem", "").lowercase()
                    when {
                        msg.contains("expirad") ->
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

        } catch (e: Exception) {
            ActivationResult.Error(ActivationError.NETWORK)
        }
    }

    suspend fun activate(): ActivationResult = checkActivation()
}
