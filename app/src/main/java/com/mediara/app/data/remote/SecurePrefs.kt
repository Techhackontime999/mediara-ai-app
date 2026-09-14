package com.mediara.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed encrypted storage for sensitive values (auth tokens, server
 * configuration). Keys are AES256-SIV encrypted, values AES256-GCM encrypted,
 * with the master key held in the Android Keystore — never in app source.
 */
object SecurePrefs {

    private const val MASTER_KEY_ALIAS = "mediara_master_key"

    @Volatile
    private var masterKey: MasterKey? = null

    private fun masterKey(context: Context): MasterKey {
        masterKey?.let { return it }
        return synchronized(this) {
            masterKey ?: MasterKey.Builder(context.applicationContext, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { masterKey = it }
        }
    }

    fun forName(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext
        val key = masterKey(appContext)
        return EncryptedSharedPreferences.create(
            appContext,
            name,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}