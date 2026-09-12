package com.example.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "mediara_session")

/**
 * Persists the JWT access/refresh tokens and the signed-in user via DataStore,
 * and exposes them to the UI plus a synchronous surface for OkHttp.
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

    init {
        scope.launch {
            val prefs = appContext.sessionDataStore.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()
            accessToken = prefs[ACCESS_KEY] ?: ""
            refreshToken = prefs[REFRESH_KEY] ?: ""
            _user.value = prefs[USER_KEY]?.let { json ->
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
        appContext.sessionDataStore.edit { prefs ->
            prefs[ACCESS_KEY] = access
            prefs[REFRESH_KEY] = refresh
            if (json != null) prefs[USER_KEY] = json else prefs.remove(USER_KEY)
        }
    }

    suspend fun setUser(user: User) {
        _user.value = user
        val json = runCatching { userAdapter.toJson(user) }.getOrNull()
        appContext.sessionDataStore.edit { prefs ->
            if (json != null) prefs[USER_KEY] = json
        }
    }

    suspend fun clear() {
        setSession(access = "", refresh = "", user = null)
    }

    /** Called from the OkHttp Authenticator (background thread), not suspendable. */
    fun setAccessSync(access: String) {
        accessToken = access
        scope.launch {
            appContext.sessionDataStore.edit { prefs -> prefs[ACCESS_KEY] = access }
        }
    }

    /** Called from the OkHttp Authenticator when the refresh token is rejected. */
    fun clearSync() {
        accessToken = ""
        refreshToken = ""
        _user.value = null
        _isLoggedIn.value = false
        scope.launch {
            appContext.sessionDataStore.edit { prefs ->
                prefs.remove(ACCESS_KEY)
                prefs.remove(REFRESH_KEY)
                prefs.remove(USER_KEY)
            }
        }
    }

    private companion object {
        val ACCESS_KEY = stringPreferencesKey("access_token")
        val REFRESH_KEY = stringPreferencesKey("refresh_token")
        val USER_KEY = stringPreferencesKey("user_json")
    }
}