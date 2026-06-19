package es.schsebastian.foodrats.core.database.di

import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import org.koin.dsl.module

/**
 * Common Koin module: builds the [FoodRatsDatabase] over the platform-bound `SqlDriver`.
 *
 * The driver itself is bound per platform (the `SqlDriver` needs a `Context` on Android), so this
 * module depends on `single<SqlDriver>` from `databaseAndroidModule(context)` / `databaseIosModule`
 * — the same per-platform split as `connectivity{Android,Ios}Module`. Wired into `appModules` AFTER
 * `coreDataModule`.
 */
val databaseModule = module {
    single { FoodRatsDatabase(get()) }
}
