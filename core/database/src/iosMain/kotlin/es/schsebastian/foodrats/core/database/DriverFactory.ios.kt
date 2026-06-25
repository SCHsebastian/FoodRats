package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(
            schema = FoodRatsDatabase.Schema,
            name = DATABASE_FILE_NAME,
            // SQLite opens with foreign-key enforcement OFF by default, so the mealRating → meal
            // ON DELETE CASCADE would NOT fire (offline-first P4-T1: the CachePruner relies on it).
            // Enable it on every connection via the SQLiter DatabaseConfiguration.
            onConfiguration = { config ->
                config.copy(
                    extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true),
                )
            },
        )
}
