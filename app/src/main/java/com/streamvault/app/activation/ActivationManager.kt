package com.streamvault.app.activation

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val SERVER_URL = "https://vault-axvc.onrender.com"
        private const val USER_AGENT = "StreamVault/1.0"
        private const val TAG = "Activation"
        // Render free: cold start pode passar de 60s
        private const val TIMEOUT_SECONDS = 90L
        private const val MAX_ATTEMPTS = 3
    }

    private val activationClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS + 15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
        val id = deviceId.trim().uppercase()
        // Acorda o Render (free) antes da chamada real
        wakeServer()

        var lastError: ActivationResult = ActivationResult.Error(ActivationError.NETWORK)
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = attemptStatus(id)
            if (result !is ActivationResult.Error || result.error != ActivationError.NETWORK) {
                return@withContext result
            }
            lastError = result
            Log.w(TAG, "activation attempt ${attempt + 1}/$MAX_ATTEMPTS failed (NETWORK)")
            if (attempt < MAX_ATTEMPTS - 1) {
                // backoff: 2s, 5s
                delay(if (attempt == 0) 2_000L else 5_000L)
                wakeServer()
            }
        }
        lastError
    }

    suspend fun activate(): ActivationResult = checkActivation()

    private fun wakeServer() {
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/ping")
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            activationClient.newCall(request).execute().use { response ->
                Log.d(TAG, "wake ping HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake ping failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun attemptStatus(deviceId: String): ActivationResult {
        return try {
            val url = "$SERVER_URL/api/status/${deviceId}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            activationClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "status HTTP ${response.code} bodyLen=${body.length}")
                val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                parseResponse(response.code, json)
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "UnknownHost", e)
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IO ${e.javaClass.simpleName}: ${e.message}", e)
            ActivationResult.Error(ActivationError.NETWORK)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected ${e.javaClass.simpleName}: ${e.message}", e)
            ActivationResult.Error(ActivationError.NETWORK)
        }
    }

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
                    msg.contains("outro aparelho") || msg.contains("fingerprint") ||
                        msg.contains("bloqueado") ->
                        ActivationResult.Error(ActivationError.FINGERPRINT_MISMATCH)
                    else ->
                        ActivationResult.Error(ActivationError.NO_M3U)
                }
            }
            404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
            503, 502, 504 -> ActivationResult.Error(ActivationError.NETWORK)
            else -> ActivationResult.Error(ActivationError.GENERIC)
        }
    }
}
