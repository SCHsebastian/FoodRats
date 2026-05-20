package es.schsebastian.foodrats.core.data.telemetry

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter

/**
 * [CrashReporter] for iOS. The native FirebaseCrashlytics SDK is resolved via SPM inside Xcode
 * and is therefore invisible to Gradle/Kotlin — so, like GoogleSignIn, we bridge through Swift
 * lambdas supplied at app startup (see iosApp/CrashlyticsBridge.swift + MainViewController).
 *
 * @param recordNonFatalBridge called with `(domain, message)`; the Swift side builds an NSError
 *   and forwards it to `Crashlytics.crashlytics().record(error:)`.
 * @param logBridge forwarded to `Crashlytics.crashlytics().log(_:)`.
 */
class IosCrashReporter(
    private val recordNonFatalBridge: (domain: String, message: String) -> Unit,
    private val logBridge: (String) -> Unit,
) : CrashReporter {

    override fun recordNonFatal(throwable: Throwable, tag: String?) {
        // simpleName (not qualifiedName) — the latter is not reliably available on Kotlin/Native.
        val domain = tag ?: throwable::class.simpleName ?: "NonFatal"
        val message = throwable.message ?: throwable.toString()
        recordNonFatalBridge(domain, message)
    }

    override fun log(message: String) {
        logBridge(message)
    }
}
