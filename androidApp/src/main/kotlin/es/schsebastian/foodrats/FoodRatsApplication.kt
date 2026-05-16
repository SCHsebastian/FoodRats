package es.schsebastian.foodrats

import android.app.Application
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.core.data.datastore.installAndroidDataStoreContext
import es.schsebastian.foodrats.core.data.firebase.FirebaseInitializer
import es.schsebastian.foodrats.core.data.firebase.installAndroidFirebaseContext
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class FoodRatsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installAndroidDataStoreContext(filesDir)
        installAndroidFirebaseContext(this)
        FirebaseInitializer.init()

        val platformModule = module {
            single { GoogleAuthClient(contextProvider = { applicationContext }, serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID) }
        }

        startKoin {
            androidContext(this@FoodRatsApplication)
            modules(appModules + platformModule)
        }
    }
}
