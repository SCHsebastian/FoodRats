package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Cross-platform in-memory SQLDelight driver for tests, so DB behavior is exercised on BOTH the
 * Android host JVM (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`).
 * Each `actual` enables `PRAGMA foreign_keys = ON` so the `mealRating → meal` CASCADE delete fires.
 */
expect fun createInMemorySqlDriver(): SqlDriver
