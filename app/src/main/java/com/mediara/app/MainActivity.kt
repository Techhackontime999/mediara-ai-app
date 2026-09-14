package com.mediara.app

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mediara.app.data.local.AppDatabase
import com.mediara.app.data.preferences.UserPreferences
import com.mediara.app.data.remote.ApiClient
import com.mediara.app.data.remote.ServerConfigManager
import com.mediara.app.data.remote.SessionManager
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.navigation.AppNavigation
import com.mediara.app.ui.theme.MediaraTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var repository: MediationRepository
    private lateinit var preferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val serverConfig = ServerConfigManager(applicationContext)
        val sessionManager = SessionManager(applicationContext)
        val apiService = ApiClient.create(
            sessionManager = sessionManager,
            serverConfig = serverConfig,
            moshi = moshi
        )
        val database = AppDatabase.getDatabase(applicationContext)
        repository = MediationRepository(
            context = applicationContext,
            database = database,
            apiService = apiService,
            sessionManager = sessionManager
        )
        preferences = UserPreferences(applicationContext)
        applySavedLocale()

        setContent {
            val settings by preferences.settings.collectAsState()
            LaunchedEffect(settings.languageCode) {
                applyLocale(settings.languageCode)
            }
            MediaraTheme(settings = settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        repository = repository,
                        serverConfig = serverConfig,
                        preferences = preferences
                    )
                }
            }
        }
    }

    /** Applies the persisted language before the first composition renders. */
    private fun applySavedLocale() {
        val code = preferences.settings.value.languageCode
        applyLocale(code)
    }

    /** Mutates the activity's resources in place so stringResource reflects the choice. */
    private fun applyLocale(code: String) {
        if (code == "en") return
        runCatching {
            val config = Configuration(resources.configuration)
            val locale = Locale.forLanguageTag(code)
            Locale.setDefault(locale)
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }.onFailure { Log.w("Mediara", "Failed to apply locale $code", it) }
    }
}