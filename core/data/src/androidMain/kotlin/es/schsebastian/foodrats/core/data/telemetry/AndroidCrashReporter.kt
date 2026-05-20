package es.schsebastian.foodrats.core.data.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter

/**
 * [CrashReporter] backed by the native Firebase Crashlytics SDK.
 *
 * Crashlytics has no GitLive KMP binding, so this concrete implementation lives in
 * androidMain and is bound in Koin from the Android app bootstrap (see FoodRatsApplication).
 *
 * @param collectionEnabled when false, Crashlytics neither uploads automatic crash reports
 *   nor the non-fatals/logs recorded here. Wired to `!BuildConfig.DEBUG` so development
 *   crashes don't pollute production data.
 */
class AndroidCrashReporter(
    collectionEnabled: Boolean,
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : CrashReporter {

    init {
        crashlytics.setCrashlyticsCollectionEnabled(collectionEnabled)
    }

    override fun recordNonFatal(throwable: Throwable, tag: String?) {
        if (tag != null) crashlytics.setCustomKey(TAG_KEY, tag)
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    private companion object {
        const val TAG_KEY = "tag"
    }
}
