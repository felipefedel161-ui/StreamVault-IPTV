package com.streamvault.app.activation

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InovaActivationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val SERVER_URL = "https://vault-axvc.onrender.com"
        private const val USER_AGENT = "StreamVaultApp/1.0"
        // Render free pode demorar ~30s ao acordar
        private const val TIMEOUT_SECONDS = 45L
    }

    private val activationClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
            .build()
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
            val url = "$SERVER_URL/api/status/${deviceId.uppercase()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            activationClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                parseResponse(response.code, json)
            }
        } catch (_: java.net.UnknownHostException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: java.net.SocketTimeoutException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: java.io.IOException) {
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (_: Exception) {
            ActivationResult.Error(ActivationError.NETWORK)
        }
    }

    suspend fun activate(): ActivationResult = checkActivation()

    private fun parseResponse(code: Int, json: JSONObject): ActivationResult {
        return when (code) {
            200 -> {
                val m3u = json.optString("m3u_url", "").trim()
                val exp = json.optString("expiracao", "")
                val dias = json.optInt("dias_restantes", -1)
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
