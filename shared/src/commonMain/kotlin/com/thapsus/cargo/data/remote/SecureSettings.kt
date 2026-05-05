package com.thapsus.cargo.data.remote

// Tiny key-value store for auth tokens. iOS is Keychain-backed
// (kSecAttrAccessibleWhenUnlockedThisDeviceOnly, items excluded from iCloud
// backups). Android is still on plain SharedPreferences pending a parallel
// EncryptedSharedPreferences migration. Constructor signature is platform-
// specific (iOS no-arg, Android takes a Context), so the platform bootstrap
// instantiates and hands the instance into [com.thapsus.cargo.ThapsusSdk.start].

expect class SecureSettings {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun clear()
}

object SecureKeys {
    const val SC_TOKEN = "sc_token"
    const val SUPABASE_TOKEN = "supabase_token"
    const val SUPABASE_TOKEN_EXP = "supabase_token_expires_at"
    const val USER_PROFILE = "sc_user"
}
