package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.connectivity.IosConnectivityMonitor
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import org.koin.dsl.module

/**
 * iOS Koin module binding the app-wide [ConnectivityPort] to the `NWPathMonitor`-backed
 * [IosConnectivityMonitor]. Registered in `MainViewController`, never in `coreDataModule`
 * — same per-platform rule as `locationIosModule` (the impl is a Native-only adapter).
 */
val connectivityIosModule = module {
    single<ConnectivityPort> { IosConnectivityMonitor() }
}
