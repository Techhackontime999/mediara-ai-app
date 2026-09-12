package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    fun create(
        sessionManager: SessionManager,
        moshi: Moshi,
        apiBaseUrl: String = BuildConfig.API_BASE_URL,
    ): MediaraApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = sessionManager.accessToken()
                val request = chain.request().newBuilder()
                    .apply {
                        if (token.isNotBlank()) header("Authorization", "Bearer $token")
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(sessionManager, moshi, apiBaseUrl))
            .build()

        return Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MediaraApiService::class.java)
    }
}

/**
 * Attempts to refresh the access token against /api/auth/refresh/ when a request
 * returns 401, then retries the original request with the new token.
 */
internal class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val moshi: Moshi,
    private val apiBaseUrl: String,
) : Authenticator {

    private val httpClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization").isNullOrBlank()) return null
        synchronized(this) {
            val refresh = sessionManager.refreshToken()
            if (refresh.isBlank()) return null

            return try {
                val serialized = moshi.adapter(RefreshRequestDto::class.java)
                    .toJson(RefreshRequestDto(refresh = refresh))
                    .toRequestBody("application/json".toMediaType())

                val refreshRequest = Request.Builder()
                    .url(apiBaseUrl.trimEnd('/') + "/api/auth/refresh/")
                    .post(serialized)
                    .build()

                httpClient.newCall(refreshRequest).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        sessionManager.clearSync()
                        return null
                    }
                    val body = resp.body?.string() ?: return null
                    val parsed = moshi.adapter(RefreshResponseDto::class.java).fromJson(body)
                    val newAccess = parsed?.access ?: return null

                    sessionManager.setAccessSync(newAccess)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccess")
                        .build()
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}