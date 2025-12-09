package com.coulterpeterson.floatnative.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "floatnative_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_AUTH_COOKIE = "sails.sid"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_COMPANION_API_KEY = "companion_api_key"
    }

    var accessToken: String?
        get() = retrieve(KEY_ACCESS_TOKEN)
        set(value) = save(KEY_ACCESS_TOKEN, value)

    var refreshToken: String?
        get() = retrieve(KEY_REFRESH_TOKEN)
        set(value) = save(KEY_REFRESH_TOKEN, value)

    var authCookie: String?
        get() = retrieve(KEY_AUTH_COOKIE)
        set(value) = save(KEY_AUTH_COOKIE, value)
        
    var tokenExpiry: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRY, value).apply()
        
    var userId: String?
        get() = retrieve(KEY_USER_ID)
        set(value) = save(KEY_USER_ID, value)

    var companionApiKey: String?
        get() = retrieve(KEY_COMPANION_API_KEY)
        set(value) = save(KEY_COMPANION_API_KEY, value)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun save(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    private fun retrieve(key: String): String? {
        return prefs.getString(key, null)
    }
}
