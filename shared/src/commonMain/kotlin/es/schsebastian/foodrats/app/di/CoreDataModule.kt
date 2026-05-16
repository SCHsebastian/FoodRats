package es.schsebastian.foodrats.app.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.providePreferencesDataStore
import es.schsebastian.foodrats.core.domain.coroutines.DefaultDispatcherProvider
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.domain.time.SystemClock
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

val coreDataModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<Clock> { SystemClock() }
    single<TimeZone> { TimeZone.currentSystemDefault() }
    single<CrashReporter> { NoopCrashReporter }
    single { providePreferencesDataStore() }
    single { AppPreferences(get()) }
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }
}
