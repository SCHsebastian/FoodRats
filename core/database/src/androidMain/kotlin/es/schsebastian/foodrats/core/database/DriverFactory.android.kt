package es.schsebastian.foodrats.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(FoodRatsDatabase.Schema, context, DATABASE_FILE_NAME)
}
