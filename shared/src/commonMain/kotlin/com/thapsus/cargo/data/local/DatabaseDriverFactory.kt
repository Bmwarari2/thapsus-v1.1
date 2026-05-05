package com.thapsus.cargo.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform glue: each Kotlin target supplies a SqlDriver bound to its native sqlite.
 * iOS uses NativeSqliteDriver (no constructor args); Android uses AndroidSqliteDriver
 * with a Context. The platform bootstrap instantiates the right `actual` and passes
 * it into [com.thapsus.cargo.ThapsusSdk.start].
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
