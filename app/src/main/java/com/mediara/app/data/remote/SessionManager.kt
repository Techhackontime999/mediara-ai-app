package com.mediara.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.mediara.app.data.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists the JWT access/refresh tokens and the signed-in user in
 * Keystore-backed encrypted storage, and exposes them to the UI plus a
 * synchronous surface for OkHttp. Disk/keystore work is kept off the main
 * thread; the in-memory mirrors are volatile for lock-free reads by interceptors.
 */
class SessionManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val userAdapter = moshi.adapter(User::class.java)

    @Volatile
    private var accessToken: String = ""
    @Volatile
    private var refreshToken: String = ""

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        SecurePrefs.forName(appContext, "mediara_session")
    }

    init {
        scope.launch {
            accessToken = prefs.getString(ACCESS_KEY, "") ?: ""
            refreshToken = prefs.getString(REFRESH_KEY, "") ?: ""
            _user.value = prefs.getString(USER_KEY, null)?.let { json ->
                runCatching { userAdapter.fromJson(json) }.getOrNull()
            }
            _isLoggedIn.value = refreshToken.isNotBlank()
        }
    }

    // --- Synchronous accessors for the OkHttp interceptor / authenticator ---

    fun accessToken(): String = accessToken

    fun refreshToken(): String = refreshToken

    fun isLoggedInNow(): Boolean = refreshToken.isNotBlank()

    // --- Suspend surface used by the repository ---

    suspend fun setSession(access: String, refresh: String, user: User?) {
        accessToken = access
        refreshToken = refresh
        _user.value = user
        _isLoggedIn.value = refresh.isNotBlank()
        val json = user?.let { runCatching { userAdapter.toJson(it) }.getOrNull() }
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(ACCESS_KEY, access)
                .putString(REFRESH_KEY, refresh)
                .apply()
            if (json != null) prefs.edit().putString(USER_KEY, json).apply()
            else prefs.edit().remove(USER_KEY).apply()
        }
    }

    suspend fun setUser(user: User) {
        _user.value = user
        val json = runCatching { userAdapter.toJson(user) }.getOrNull()
        if (json != null) {
            withContext(Dispatchers.IO) { prefs.edit().putString(USER_KEY, json).apply() }
        }
    }

    suspend fun clear() {
        setSession(access = "", refresh = "", user = null)
    }

    /** Called from the OkHttp Authenticator (background thread), not suspendable. */
    fun setAccessSync(access: String) {
        accessToken = access
        scope.launch { prefs.edit().putString(ACCESS_KEY, access).apply() }
    }

    /** Called from the OkHttp Authenticator when the refresh token is rejected. */
    fun clearSync() {
        accessToken = ""
        refreshToken = ""
        _user.value = null
        _isLoggedIn.value = false
        scope.launch {
            prefs.edit()
                .remove(ACCESS_KEY)
                .remove(REFRESH_KEY)
                .remove(USER_KEY)
                .apply()
        }
    }

    private companion object {
        const val ACCESS_KEY = "access_token"
        const val REFRESH_KEY = "refresh_token"
        const val USER_KEY = "user_json"
    }
}