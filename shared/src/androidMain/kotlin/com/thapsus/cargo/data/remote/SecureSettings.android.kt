package com.thapsus.cargo.data.remote

import android.content.Context
import android.content.SharedPreferences

// SharedPreferences-backed token store. v1 — fine for sc_token + supabase_token, mirrors
// the iOS NSUserDefaults choice. EncryptedSharedPreferences migration is a tracked follow-up
// alongside the iOS Keychain migration.
actual class SecureSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    actual fun clear() {
        prefs.edit()
            .remove(SecureKeys.SC_TOKEN)
            .remove(SecureKeys.SUPABASE_TOKEN)
            .remove(SecureKeys.SUPABASE_TOKEN_EXP)
            .remove(SecureKeys.USER_PROFILE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "thapsus_secure_settings"
    }
}
