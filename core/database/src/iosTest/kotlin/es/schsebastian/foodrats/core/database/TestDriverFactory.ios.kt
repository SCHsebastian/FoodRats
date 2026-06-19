package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

/**
 * In-memory NativeSqliteDriver (SQLDelight's own helper applies the schema). Foreign keys are then
 * turned on so the `mealRating → meal` CASCADE delete fires, matching the host JVM test.
 */
actual fun createInMemorySqlDriver(): SqlDriver =
    inMemoryDriver(FoodRatsDatabase.Schema).apply {
        execute(null, "PRAGMA foreign_keys = ON", 0)
    }
