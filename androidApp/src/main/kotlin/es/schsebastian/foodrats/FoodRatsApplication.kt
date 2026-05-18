package es.schsebastian.foodrats

import android.app.Application
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.auth.GoogleAuthClientAndroidFactory
import es.schsebastian.foodrats.core.data.datastore.installAndroidDataStoreContext
import es.schsebastian.foodrats.core.data.firebase.FirebaseInitializer
import es.schsebastian.foodrats.core.data.firebase.installAndroidFirebaseContext
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.feed.data.image.installFeedImageLoader
import es.schsebastian.foodrats.feature.notifications.di.notificationsAndroidModule
import es.schsebastian.foodrats.feature.notifications.platform.NotificationChannels
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class FoodRatsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installAndroidDataStoreContext(filesDir)
        installAndroidFirebaseContext(this)
        FirebaseInitializer.init()
        NotificationChannels.ensure(this)
        installFeedImageLoader()

        startKoin {
            androidLogger()
            androidContext(this@FoodRatsApplication)
            modules(
                appModules + listOf(
                    notificationsAndroidModule,
                    androidAuthModule(),
                ),
            )
        }
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
