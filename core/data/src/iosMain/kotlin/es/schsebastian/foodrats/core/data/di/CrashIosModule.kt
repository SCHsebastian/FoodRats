package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.data.telemetry.IosCrashReporter
import org.koin.dsl.module

/**
 * iOS-side Koin module that registers the Crashlytics-backed [CrashReporter]. The Swift caller
 * in ContentView.swift supplies the lambdas at app startup — see iosApp/CrashlyticsBridge.swift.
 */
fun crashIosModule(
    recordNonFatal: (domain: String, message: String) -> Unit,
    logMessage: (String) -> Unit,
) = module {
    single<CrashReporter> { IosCrashReporter(recordNonFatal, logMessage) }
}
