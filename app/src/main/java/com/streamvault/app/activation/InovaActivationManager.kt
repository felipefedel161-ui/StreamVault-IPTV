package com.streamvault.app.activation

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerencia a ativação do StreamVault.
 *
 * Fluxo:
 *  1. Tenta contatar o servidor do painel (Pydroid 3 na rede local).
 *     URL configurada pelo usuário em PREFS_SERVER_URL.
 *  2. Se o servidor responder com sucesso → salva o resultado em cache
 *     criptografado (SharedPreferences) e retorna Success.
 *  3. Se offline ou servidor não encontrado → lê o cache local.
 *     - Cache válido (não expirado) → retorna Success sem internet.
 *     - Cache expirado → retorna Error(EXPIRED).
 *     - Cache ausente → retorna Error(NOT_FOUND).
 *
 * O painel Pydroid grava os dados no cache via /api/cache quando
 * o usuário ativa o dispositivo pelo painel.
 */
@Singleton
class InovaActivationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME       = "sv_activation_cache"
        private const val KEY_SERVER_URL   = "server_url"
        private const val KEY_M3U          = "m3u_url"
        private const val KEY_EXPIRACAO    = "expiracao"
        private const val KEY_DIAS         = "dias_restantes"
        private const val KEY_CACHED_AT    = "cached_at"
        private const val KEY_DEVICE_ID    = "device_id"

        /** URL padrão do painel Pydroid — usuário pode trocar nas configurações */
        const val DEFAULT_SERVER_URL = "https://streamvault-server.onrender.com"

        private const val TIMEOUT_MS = 8_000
        private const val USER_AGENT = "StreamVaultApp/1.0"

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Device ID ────────────────────────────────────────────────────────────

    fun getDeviceId(): String {
        // Retorna o ID já fixado (garante que não muda entre sessões)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        val id = resolveDeviceId()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun resolveDeviceId(): String {
        // 1. MAC via WifiManager
        try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mac = wm?.connectionInfo?.macAddress
            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") return mac.uppercase()
        } catch (_: Exception) {}

        // 2. MAC via NetworkInterface (mais confiável em Android 6+)
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

        // 3. Android ID (fallback permanente)
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
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

        // 1. Tenta o servidor do painel
        val serverResult = runCatching { fetchFromPanel(deviceId) }.getOrNull()
        if (serverResult is ActivationResult.Success) {
            saveCache(serverResult)
            return@withContext serverResult
        }

        // 2. Servidor offline ou falhou → usa cache local
        return@withContext readFromCache()
    }

    suspend fun checkActivation(
        deviceId: String = getDeviceId(),
        fingerprint: String? = null
    ): ActivationResult = activate()

    // ─── Comunicação com o painel Pydroid ─────────────────────────────────────

    private fun fetchFromPanel(deviceId: String): ActivationResult {
        val serverUrl = getServerUrl()
        val url = "$serverUrl/api/status/${deviceId.uppercase()}"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
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
                val dias = json.optInt   ("dias_restantes", -1)
                if (m3u.isBlank()) ActivationResult.Error(ActivationError.NO_M3U)
                else ActivationResult.Success(m3uUrl = m3u, expiracao = exp, diasRestantes = dias)
            }
            403 -> {
                val msg = json.optString("mensagem", "").lowercase()
                when {
                    msg.contains("expirad") || msg.contains("expired") ->
                        ActivationResult.Error(ActivationError.EXPIRED)
                    else ->
                        ActivationResult.Error(ActivationError.NO_M3U)
                }
            }
            404 -> ActivationResult.Error(ActivationError.NOT_FOUND)
            else -> ActivationResult.Error(ActivationError.GENERIC)
        }
    }

    // ─── Cache local ──────────────────────────────────────────────────────────

    private fun saveCache(result: ActivationResult.Success) {
        prefs.edit()
            .putString(KEY_M3U,        result.m3uUrl)
            .putString(KEY_EXPIRACAO,  result.expiracao)
            .putInt   (KEY_DIAS,       result.diasRestantes)
            .putLong  (KEY_CACHED_AT,  System.currentTimeMillis())
            .apply()
    }

    /**
     * Grava cache diretamente — chamado quando o painel Pydroid
     * faz push da ativação via /api/cache (sem precisar do app fazer o fetch).
     */
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

        // Sem cache → dispositivo nunca foi ativado
        if (m3u.isNullOrBlank() || exp.isNullOrBlank()) {
            return ActivationResult.Error(ActivationError.NOT_FOUND)
        }

        // Verifica expiração
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
            val exp   = DATE_FORMAT.parse(expiracao) ?: return 0
            val hoje  = DATE_FORMAT.parse(DATE_FORMAT.format(Date())) ?: return 0
            val diff  = exp.time - hoje.time
            maxOf(0, (diff / 86_400_000).toInt())
        } catch (_: Exception) { 0 }
    }

    /** Retorna info de debug para exibir nas configurações */
    fun getDebugInfo(): Map<String, String> = mapOf(
        "Device ID"   to getDeviceId(),
        "Servidor"    to getServerUrl(),
        "M3U"         to (prefs.getString(KEY_M3U, "—") ?: "—"),
        "Expiração"   to (prefs.getString(KEY_EXPIRACAO, "—") ?: "—"),
        "Cache em"    to prefs.getLong(KEY_CACHED_AT, 0L).let {
            if (it == 0L) "—" else java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm", Locale.getDefault()
            ).format(Date(it))
        }
    )
}
