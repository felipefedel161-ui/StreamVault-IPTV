package com.streamvault.app.activation

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InovaActivationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME     = "sv_activation_cache"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_M3U        = "m3u_url"
        private const val KEY_EXPIRACAO  = "expiracao"
        private const val KEY_DIAS       = "dias_restantes"
        private const val KEY_CACHED_AT  = "cached_at"
        private const val KEY_DEVICE_ID  = "device_id"

        // Render free tier pode demorar até 30s para acordar da hibernação
        const val DEFAULT_SERVER_URL = "https://vault-axvc.onrender.com"
        private const val CONNECT_TIMEOUT_MS = 35_000
        private const val READ_TIMEOUT_MS    = 35_000
        private const val MAX_RETRIES        = 2
        private const val RETRY_DELAY_MS     = 3_000L
        private const val USER_AGENT         = "StreamVaultApp/1.0"

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Device ID ────────────────────────────────────────────────────────────

    fun getDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = resolveDeviceId()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun resolveDeviceId(): String {
        // 1. MAC via WifiManager
        try {
            @Suppress("DEPRECATION")
            val wm  = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mac = wm?.connectionInfo?.macAddress
            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") return mac.uppercase()
        } catch (_: Exception) {}

        // 2. MAC via NetworkInterface
        try {
            val iface = java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull {
                    it.name.equals("wlan0", ignoreCase = true) ||
                    it.name.equals("eth0",  ignoreCase = true)
                }
            val bytes = iface?.hardwareAddress
            if (bytes != null && bytes.size == 6) {
                val mac = bytes.joinToString(":") { "%02X".format(it) }
                if (mac != "02:00:00:00:00:00") return mac
            }
        } catch (_: Exception) {}

        // 3. Android ID
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return (androidId?.takeIf { it.isNotBlank() }
            ?: Build.SERIAL?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            ?: "UNKNOWN").uppercase()
    }

    // ─── Server URL ───────────────────────────────────────────────────────────

    fun getServerUrl(): String =
        prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trimEnd('/')).apply()
    }

    // ─── Ativação principal ───────────────────────────────────────────────────

    suspend fun activate(): ActivationResult = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()

        // Verifica conectividade antes de tentar o servidor
        if (hasInternet()) {
            // Tenta com retry — Render free pode demorar para acordar
            var lastServerResult: ActivationResult? = null
            repeat(MAX_RETRIES) { attempt ->
                val result = runCatching { fetchFromServer(deviceId) }.getOrElse { e ->
                    ActivationResult.Error(ActivationError.NETWORK)
                }
                if (result is ActivationResult.Success) {
                    saveCache(result)
                    lastServerResult = result
                    return@repeat
                }
                // Só faz retry em erros de rede, não em erros de negócio
                if (result is ActivationResult.Error &&
                    result.error !in listOf(ActivationError.NOT_FOUND, ActivationError.EXPIRED,
                                             ActivationError.NO_M3U, ActivationError.GENERIC) &&
                    attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                } else {
                    lastServerResult = result
                    return@repeat
                }
            }

            val serverResult = lastServerResult
            if (serverResult is ActivationResult.Success) {
                return@withContext serverResult
            }

            // Erro de negócio (NOT_FOUND, EXPIRED, BLOQUEADO) → retorna o erro direto
            // sem cair no cache, para o usuário ver a mensagem correta
            if (serverResult is ActivationResult.Error &&
                serverResult.error in listOf(ActivationError.NOT_FOUND, ActivationError.EXPIRED,
                                              ActivationError.NO_M3U)) {
                return@withContext serverResult
            }
        }

        // Sem internet ou erro de rede → tenta cache
        return@withContext readFromCache()
    }

    suspend fun checkActivation(
        deviceId: String = getDeviceId(),
        fingerprint: String? = null
    ): ActivationResult = activate()

    // ─── HTTP ─────────────────────────────────────────────────────────────────

    private fun fetchFromServer(deviceId: String): ActivationResult {
        val url = "${getServerUrl()}/api/status/${deviceId.uppercase()}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout    = READ_TIMEOUT_MS
            setRequestProperty("Accept",     "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val code = conn.responseCode
        val body = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()

        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return ActivationResult.Error(ActivationError.GENERIC)

        return when (code) {
            200 -> {
                val m3u  = json.optString("m3u_url",        "").trim()
                val exp  = json.optString("expiracao",      "")
                val dias = json.optInt   ("dias_restantes", 30)
                when {
                    m3u.isBlank() -> ActivationResult.Error(ActivationError.NO_M3U)
                    exp.isBlank() -> ActivationResult.Error(ActivationError.GENERIC)
                    else -> ActivationResult.Success(
                        m3uUrl        = m3u,
                        expiracao     = exp,
                        diasRestantes = dias
                    )
                }
            }
            403 -> {
                val msg = json.optString("mensagem", "").lowercase()
                when {
                    msg.contains("expirad") -> ActivationResult.Error(ActivationError.EXPIRED)
                    msg.contains("bloqueado") -> ActivationResult.Error(ActivationError.GENERIC)
                    else -> ActivationResult.Error(ActivationError.GENERIC)
                }
            }
            404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
            else -> ActivationResult.Error(ActivationError.GENERIC)
        }
    }

    // ─── Internet check ───────────────────────────────────────────────────────

    private fun hasInternet(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps    = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }
    }

    // ─── Cache ────────────────────────────────────────────────────────────────

    private fun saveCache(result: ActivationResult.Success) {
        prefs.edit()
            .putString(KEY_M3U,       result.m3uUrl)
            .putString(KEY_EXPIRACAO, result.expiracao)
            .putInt   (KEY_DIAS,      result.diasRestantes)
            .putLong  (KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    fun writeCacheDirectly(m3uUrl: String, expiracao: String, diasRestantes: Int) {
        prefs.edit()
            .putString(KEY_M3U,       m3uUrl)
            .putString(KEY_EXPIRACAO, expiracao)
            .putInt   (KEY_DIAS,      diasRestantes)
            .putLong  (KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun readFromCache(): ActivationResult {
        val m3u = prefs.getString(KEY_M3U, null)
        val exp = prefs.getString(KEY_EXPIRACAO, null)

        if (m3u.isNullOrBlank() || exp.isNullOrBlank()) {
            return ActivationResult.Error(ActivationError.NOT_FOUND)
        }

        val dias = diasRestantes(exp)
        if (dias <= 0) {
            return ActivationResult.Error(ActivationError.EXPIRED)
        }

        return ActivationResult.Success(
            m3uUrl        = m3u,
            expiracao     = exp,
            diasRestantes = dias
        )
    }

    fun getCachedStatus(): ActivationResult = readFromCache()

    fun clearCache() {
        prefs.edit()
            .remove(KEY_M3U)
            .remove(KEY_EXPIRACAO)
            .remove(KEY_DIAS)
            .remove(KEY_CACHED_AT)
            .apply()
    }

    // ─── Utils ────────────────────────────────────────────────────────────────

    private fun diasRestantes(expiracao: String): Int {
        return try {
            val exp  = DATE_FORMAT.parse(expiracao) ?: return 0
            val hoje = DATE_FORMAT.parse(DATE_FORMAT.format(Date())) ?: return 0
            maxOf(0, ((exp.time - hoje.time) / 86_400_000).toInt())
        } catch (_: Exception) { 0 }
    }

    fun getDebugInfo(): Map<String, String> = mapOf(
        "Device ID"  to getDeviceId(),
        "Servidor"   to getServerUrl(),
        "M3U"        to (prefs.getString(KEY_M3U, "—") ?: "—"),
        "Expiração"  to (prefs.getString(KEY_EXPIRACAO, "—") ?: "—"),
        "Cache em"   to prefs.getLong(KEY_CACHED_AT, 0L).let {
            if (it == 0L) "—"
            else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
        }
    )
}
