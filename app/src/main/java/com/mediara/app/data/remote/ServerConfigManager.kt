package com.mediara.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.mediara.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runtime API configuration (enterprise "environment switcher" pattern).
 *
 * The active backend base-URL is stored in Keystore-backed encrypted storage and
 * can be refreshed at runtime from an authenticated admin console — no rebuild
 * required. The value is read synchronously by the OkHttp host-selection
 * interceptor on every request (volatile) and exposed reactively to the UI
 * (StateFlow). Disk/keystore work is kept off the main thread.
 */
class ServerConfigManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serverUrl = MutableStateFlow(BuildConfig.API_BASE_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    @Volatile
    private var current: String = BuildConfig.API_BASE_URL

    private val prefs: SharedPreferences by lazy {
        SecurePrefs.forName(appContext, "mediara_server_config")
    }

    init {
        scope.launch {
            val stored = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
            if (stored != null) {
                val normalized = normalize(stored)
                current = normalized
                _serverUrl.value = normalized
            }
        }
    }

    /** Effective base URL used by the network layer right now. */
    fun currentBaseUrl(): String = current

    fun resetToDefault() {
        setServerUrl(BuildConfig.API_BASE_URL)
    }

    fun setServerUrl(url: String) {
        val normalized = normalize(url)
        current = normalized
        _serverUrl.value = normalized
        scope.launch {
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        }
    }

    private fun normalize(url: String): String =
        url.trim().trimEnd('/') + "/"

    private companion object {
        const val KEY_SERVER_URL = "server_url"
    }
}