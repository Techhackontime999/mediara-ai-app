package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.data.remote.SessionManager
import com.example.data.repository.MediationRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MediaraTheme
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