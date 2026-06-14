package es.schsebastian.foodrats

import android.app.Application
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.auth.GoogleAuthClientAndroidFactory
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.clearLegacyDevCrewIfPresent
import es.schsebastian.foodrats.core.data.datastore.installAndroidDataStoreContext
import es.schsebastian.foodrats.core.data.firebase.FirebaseInitializer
import es.schsebastian.foodrats.core.data.firebase.installAndroidFirebaseContext
import es.schsebastian.foodrats.core.data.location.AndroidLocationProvider
import es.schsebastian.foodrats.core.data.location.LocationPermissionLauncherHolder
import es.schsebastian.foodrats.core.data.share.ShareControllerAndroid
import es.schsebastian.foodrats.core.domain.share.ShareController
import es.schsebastian.foodrats.core.data.config.RemoteConfigFeatureFlags
import es.schsebastian.foodrats.core.data.di.analyticsAndroidModule
import es.schsebastian.foodrats.core.data.telemetry.AndroidCrashReporter
import es.schsebastian.foodrats.core.data.telemetry.CrashReporterLogSink
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.feed.presentation.components.MapsApiKey
import es.schsebastian.foodrats.core.data.image.installImageLoader
import es.schsebastian.foodrats.feature.meal.di.mealAndroidModule
import es.schsebastian.foodrats.feature.mealai.di.mealAiAndroidModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsAndroidModule
import es.schsebastian.foodrats.feature.notifications.platform.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

class FoodRatsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installAndroidDataStoreContext(filesDir)
        installAndroidFirebaseContext(this)
        FirebaseInitializer.init()
        NotificationChannels.ensure(this)
        installImageLoader()

        startKoin {
            androidLogger()
            androidContext(this@FoodRatsApplication)
            modules(
                appModules + listOf(
                    notificationsAndroidModule,
                    mealAndroidModule,
                    mealAiAndroidModule,
                    androidShareModule(),
                    androidAuthModule(),
                    androidCrashModule(),
                    analyticsAndroidModule(context = this@FoodRatsApplication, debug = BuildConfig.DEBUG),
                    androidFeatureFlagsModule(),
                    androidLocationModule(),
                    androidMapsModule(),
                ),
            )
        }

        // Release-only: route FrLog warnings/errors to Crashlytics breadcrumbs + non-fatals
        // via the bound CrashReporter. Debug keeps the println-only path (sink stays null).
        if (!BuildConfig.DEBUG) {
            FrLog.installSink(CrashReporterLogSink(KoinPlatform.getKoin().get<CrashReporter>()))
        }

        // One-shot self-healing migration: legacy "test-crew-1" pref from the removed
        // dev-crew hardcode gets wiped so signed-in upgraders are re-routed to CrewPicker
        // instead of pinning to a crew they're not a member of.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            KoinPlatform.getKoin().get<AppPreferences>().clearLegacyDevCrewIfPresent()
        }
    }

    private fun androidShareModule() = module {
        single<ShareController> { ShareControllerAndroid(androidContext()) }
    }

    private fun androidCrashModule() = module {
        // Disable Crashlytics collection in debug builds so dev crashes don't pollute prod data.
        single<CrashReporter> { AndroidCrashReporter(collectionEnabled = !BuildConfig.DEBUG) }
    }

    private fun androidFeatureFlagsModule() = module {
        // Remote Config-backed kill-switches (meal-AI classifier, …). Defaults on, so behavior
        // is unchanged until the remote flag is flipped.
        single<FeatureFlagPort> { RemoteConfigFeatureFlags() }
    }

    private fun androidLocationModule() = module {
        single { LocationPermissionLauncherHolder() }
        single<LocationProvider> { AndroidLocationProvider(androidContext(), get()) }
    }

    private fun androidMapsModule() = module {
        // Google Static Maps key for the feed/detail location preview (Android only;
        // iOS uses MapKit). Sourced from BuildConfig ← `googleMapsApiKey` Gradle property.
        single { MapsApiKey(BuildConfig.MAPS_API_KEY) }
    }

    private fun androidAuthModule() = module {
        // GoogleAuthClient needs a Context + serverClientId. Resolved at construction time.
        single<GoogleAuthClient> {
            GoogleAuthClientAndroidFactory.create(
                applicationContext = androidContext(),
                serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
            )
        }
        // MainActivity binds `() -> Activity?` (see MainActivity.kt) via Koin.declare so the
        // notification gateway can read shouldShowRequestPermissionRationale.
    }
}
