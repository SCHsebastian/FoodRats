package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(FoodRatsDatabase.Schema, DATABASE_FILE_NAME)
}
