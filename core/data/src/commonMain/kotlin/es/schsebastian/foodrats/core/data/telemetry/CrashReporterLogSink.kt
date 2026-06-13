package es.schsebastian.foodrats.core.data.telemetry

import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLogSink

/**
 * [FrLogSink] that forwards [FrLog][es.schsebastian.foodrats.core.domain.telemetry.FrLog]
 * warnings to the per-platform [CrashReporter] (Crashlytics on Android/iOS).
 *
 * Every warning is logged as a Crashlytics breadcrumb; warnings carrying a
 * throwable are additionally recorded as a non-fatal, tagged with the FrLog tag.
 * Reuses the existing CrashReporter seam — no new vendor dependency.
 */
class CrashReporterLogSink(
    private val crashReporter: CrashReporter,
) : FrLogSink {
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        crashReporter.log("FR/$tag $message")
        if (throwable != null) crashReporter.recordNonFatal(throwable, tag)
    }
}
