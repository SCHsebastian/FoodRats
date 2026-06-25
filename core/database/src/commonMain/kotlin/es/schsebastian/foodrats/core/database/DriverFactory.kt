package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_FILE_NAME = "foodrats.db"

/** Per-platform SQLDelight driver factory (Android needs a Context; iOS is parameterless). */
expect class DriverFactory {
    fun create(): SqlDriver
}
