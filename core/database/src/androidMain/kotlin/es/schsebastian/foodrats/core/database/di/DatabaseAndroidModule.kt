package es.schsebastian.foodrats.core.database.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import es.schsebastian.foodrats.core.database.DriverFactory
import org.koin.dsl.module

/**
 * Android Koin module binding the SQLDelight `SqlDriver` (an `AndroidSqliteDriver` needs a
 * [Context]). Called from `FoodRatsApplication`, never in `databaseModule` — same per-platform rule
 * as `connectivityAndroidModule`. The [Context] is passed in rather than resolved via
 * `androidContext()` because `:core:database` depends only on `koin-core` (not `koin-android`).
 */
fun databaseAndroidModule(context: Context) = module {
    single<SqlDriver> { DriverFactory(context).create() }
}
