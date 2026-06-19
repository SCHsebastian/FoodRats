package es.schsebastian.foodrats.core.database.di

import app.cash.sqldelight.db.SqlDriver
import es.schsebastian.foodrats.core.database.DriverFactory
import org.koin.dsl.module

/**
 * iOS Koin module binding the SQLDelight `SqlDriver` (a `NativeSqliteDriver`). Registered in
 * `MainViewController`, never in `databaseModule` — same per-platform rule as `connectivityIosModule`.
 */
val databaseIosModule = module {
    single<SqlDriver> { DriverFactory().create() }
}
