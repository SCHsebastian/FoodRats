package es.schsebastian.foodrats.core.data.di

import android.content.Context
import es.schsebastian.foodrats.core.data.connectivity.AndroidConnectivityMonitor
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import org.koin.dsl.module

/**
 * Android Koin module binding the app-wide [ConnectivityPort] to the
 * `ConnectivityManager`-backed [AndroidConnectivityMonitor]. Called from
 * `FoodRatsApplication` (which owns the [Context]), never in `coreDataModule` —
 * same per-platform rule as `analyticsAndroidModule`/`androidCrashModule`.
 *
 * The [Context] is passed in rather than resolved via `androidContext()` because
 * `:core:data` depends only on `koin-core` (not `koin-android`), matching how
 * `analyticsAndroidModule` takes its `Context`.
 */
fun connectivityAndroidModule(context: Context) = module {
    single<ConnectivityPort> { AndroidConnectivityMonitor(context) }
}
