package es.schsebastian.foodrats.core.data.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import es.schsebastian.foodrats.core.data.analytics.ConsentGatedAnalytics
import es.schsebastian.foodrats.core.data.analytics.FirebaseAnalyticsTracker
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import org.koin.dsl.module

/**
 * Android Koin module registering the consent-gated Firebase-Analytics-backed [AnalyticsPort]. Called
 * from `FoodRatsApplication` (which owns the `Context` + `BuildConfig.DEBUG`), never in
 * `coreDataModule` — same per-platform rule as `androidCrashModule`. Keeps the Firebase Analytics
 * dependency + the `internal FirebaseAnalyticsTracker` inside `:core:data`.
 *
 * @param debug when true, binds [NoopAnalyticsTracker] so dev traffic never reaches the prod GA4
 *   property (mirrors disabling Crashlytics collection in debug).
 */
fun analyticsAndroidModule(context: Context, debug: Boolean) = module {
    single<AnalyticsPort> {
        if (debug) {
            NoopAnalyticsTracker
        } else {
            ConsentGatedAnalytics(
                // Defer getInstance(context) to first use (post-consent), not startKoin.
                delegate = FirebaseAnalyticsTracker { FirebaseAnalytics.getInstance(context) },
                consent = get(),
            )
        }
    }
}
