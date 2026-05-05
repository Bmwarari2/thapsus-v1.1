package com.thapsus.cargo.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.thapsus.cargo.db.ThapsusDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(
        schema = ThapsusDatabase.Schema,
        name = "thapsus_cargo.db"
    )
}
