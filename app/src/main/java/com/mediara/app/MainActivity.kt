package com.mediara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mediara.app.data.local.AppDatabase
import com.mediara.app.data.remote.ApiClient
import com.mediara.app.data.remote.SessionManager
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.navigation.AppNavigation
import com.mediara.app.ui.theme.MediaraTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MainActivity : ComponentActivity() {

    private lateinit var repository: MediationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val sessionManager = SessionManager(applicationContext)
        val apiService = ApiClient.create(sessionManager = sessionManager, moshi = moshi)
        val database = AppDatabase.getDatabase(applicationContext)
        repository = MediationRepository(
            context = applicationContext,
            database = database,
            apiService = apiService,
            sessionManager = sessionManager
        )

        setContent {
            MediaraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(repository = repository)
                }
            }
        }
    }
}