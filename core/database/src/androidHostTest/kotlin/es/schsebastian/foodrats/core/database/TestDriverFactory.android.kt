package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun createInMemorySqlDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        execute(null, "PRAGMA foreign_keys = ON", 0)
        FoodRatsDatabase.Schema.create(this)
    }
