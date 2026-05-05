package com.thapsus.cargo.android

import android.app.Application
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.data.local.DatabaseDriverFactory
import com.thapsus.cargo.data.remote.SecureSettings

class ThapsusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.API_BASE_URL.isNotEmpty()) {
            "API_BASE_URL is unset. Add it to local.properties — see README. " +
                "Example: API_BASE_URL=https://your-app.up.railway.app"
        }
        ThapsusSdk.start(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            driverFactory = DatabaseDriverFactory(this),
            secureSettings = SecureSettings(this)
        )
    }
}
