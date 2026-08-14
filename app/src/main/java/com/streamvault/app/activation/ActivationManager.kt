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
        const val SERVER_URL = "https://vault-1-c68s.onrender.com"
        /** Must match ACTIVATION_API_KEY on the panel when configured. */
        const val ACTIVATION_API_KEY = ""
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

    /**
     * Stable device identifier for license binding.
     * Priority (survives app reinstall on the same hardware):
     *  1. Real hardware MAC (wlan0 / eth0) — common on Android TV boxes
     *  2. ANDROID_ID — persists across reinstall until factory reset
     *  3. Build serial / hardware fingerprint fallback
     *
     * Result is always uppercase and never blank.
     */

    /**
     * Secondary hardware binding (survives MAC spoof attempts better when combined with device id).
     * Bound server-side on first successful activation.
     */
    fun buildFingerprint(): String {
        val parts = listOfNotNull(
            Build.BOARD,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.HARDWARE,
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ).joinToString("|")
        // stable short token
        return parts.uppercase().replace(" ", "").take(120).ifBlank { "UNKNOWN" }
    }

    fun getDeviceId(): String {
        // 1. Hardware MAC (best for TV boxes / stick)
        readHardwareMac()?.let { return it }

        // 2. ANDROID_ID — survives uninstall/reinstall on the same device
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("9774d56d682e549c", ignoreCase = true)
        }
        if (!androidId.isNullOrBlank()) {
            return androidId.uppercase()
        }

        // 3. Build serial (when available)
        val serial = runCatching {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }.getOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true)
        }
        if (!serial.isNullOrBlank()) {
            return serial.uppercase()
        }

        // 4. Composite fingerprint (last resort — still stable for same build/device)
        val fingerprint = listOfNotNull(
            Build.BOARD, Build.BRAND, Build.DEVICE, Build.HARDWARE,
            Build.MANUFACTURER, Build.MODEL, Build.PRODUCT
        ).joinToString("-")
            .replace(Regex("[^A-Za-z0-9\\-]"), "")
            .take(64)
        return if (fingerprint.isNotBlank()) fingerprint.uppercase() else "UNKNOWN"
    }

    /** Prefer real NIC MAC; ignore randomized / zeroed addresses. */
    private fun readHardwareMac(): String? {
        return try {
            val preferred = listOf("eth0", "wlan0", "eth1", "wlan1")
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val ordered = preferred.mapNotNull { name ->
                ifaces.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } + ifaces.filter { iface ->
                !iface.isLoopback && preferred.none { it.equals(iface.name, ignoreCase = true) }
            }
            for (iface in ordered) {
                val bytes = iface.hardwareAddress ?: continue
                if (bytes.size != 6) continue
                val mac = bytes.joinToString(":") { b -> "%02X".format(b) }
                if (mac == "00:00:00:00:00:00" || mac == "02:00:00:00:00:00") continue
                if (mac.startsWith("02:00:00")) continue
                return mac
            }
            null
        } catch (_: Exception) {
            null
        }
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
            val fp = buildFingerprint()
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .header("X-Device-Fingerprint", fp)
            if (ACTIVATION_API_KEY.isNotBlank()) {
                reqBuilder.header("X-Vault-Key", ACTIVATION_API_KEY)
            }
            reqBuilder.header("X-Device-Model", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80))
            reqBuilder.header("X-Android-Version", Build.VERSION.RELEASE ?: "")
            reqBuilder.header("X-App-Version", "StreamVault")
            val request = reqBuilder.build()

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
            202 -> ActivationResult.Error(ActivationError.PENDING)
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
