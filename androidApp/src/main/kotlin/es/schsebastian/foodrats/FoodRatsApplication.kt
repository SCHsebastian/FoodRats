package es.schsebastian.foodrats

import android.app.Application
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.auth.GoogleAuthClientAndroidFactory
import es.schsebastian.foodrats.core.data.datastore.installAndroidDataStoreContext
import es.schsebastian.foodrats.core.data.firebase.FirebaseInitializer
import es.schsebastian.foodrats.core.data.firebase.installAndroidFirebaseContext
import es.schsebastian.foodrats.core.data.location.AndroidLocationProvider
import es.schsebastian.foodrats.core.data.location.LocationPermissionLauncherHolder
import es.schsebastian.foodrats.core.data.share.ForegroundActivityHolder
import es.schsebastian.foodrats.core.data.share.PlateImageDecoder
import es.schsebastian.foodrats.core.data.share.ShareControllerAndroid
import es.schsebastian.foodrats.core.data.share.StoryCardRenderer
import es.schsebastian.foodrats.core.data.share.StoryCardRendererAndroid
import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareControllerImpl
import es.schsebastian.foodrats.core.data.share.StoryShareLauncher
import es.schsebastian.foodrats.core.data.share.StoryShareLauncherAndroid
import es.schsebastian.foodrats.core.domain.share.ShareController
import es.schsebastian.foodrats.core.data.config.RemoteConfigFeatureFlags
import es.schsebastian.foodrats.core.data.di.analyticsAndroidModule
import es.schsebastian.foodrats.core.data.di.connectivityAndroidModule
import es.schsebastian.foodrats.core.data.telemetry.AndroidCrashReporter
import es.schsebastian.foodrats.core.data.telemetry.CrashReporterLogSink
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.auth.data.apple.AppleAuthClient
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.feed.presentation.components.MapsApiKey
import es.schsebastian.foodrats.core.data.image.installImageLoader
import es.schsebastian.foodrats.feature.meal.di.mealAndroidModule
import es.schsebastian.foodrats.feature.mealai.di.mealAiAndroidModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsAndroidModule
import es.schsebastian.foodrats.feature.notifications.platform.NotificationChannels
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
        // Track the foreground Activity so the off-screen share-card renderer has a window to host
        // its capture in. Must register before MainActivity resumes (see ForegroundActivityHolder).
        ForegroundActivityHolder.install(this)

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
                    connectivityAndroidModule(context = this@FoodRatsApplication),
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

    }

    private fun androidShareModule() = module {
        single<ShareController> { ShareControllerAndroid(androidContext()) }
        // Shareable story cards (spec 2026-06-14): off-screen card → PNG, then to IG Stories / sheet.
        single<StoryCardRenderer> { StoryCardRendererAndroid(androidContext(), dispatchers = get()) }
        single<StoryShareLauncher> { StoryShareLauncherAndroid(androidContext()) }
        single { PlateImageDecoder(platformContext = androidContext()) }
        // The single testable seam feed/stats ViewModels inject (decode → render → launch).
        single<StoryShareController> { StoryShareControllerImpl(decoder = get(), renderer = get(), launcher = get()) }
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
        // Apple sign-in is wired but "being built" — parameterless stub for now (Apple on Android
        // is a future web-OAuth flow). FirebaseAuthRepository consumes it as its 2nd ctor arg.
        single<AppleAuthClient> { AppleAuthClient() }
        // MainActivity binds `() -> Activity?` (see MainActivity.kt) via Koin.declare so the
        // notification gateway can read shouldShowRequestPermissionRationale.
    }
}
