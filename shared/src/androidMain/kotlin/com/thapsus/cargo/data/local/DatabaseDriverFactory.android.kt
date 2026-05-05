package com.thapsus.cargo.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.thapsus.cargo.db.ThapsusDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(
        schema = ThapsusDatabase.Schema,
        context = context,
        name = "thapsus_cargo.db"
    )
}
