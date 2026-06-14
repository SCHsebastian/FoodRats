package es.schsebastian.foodrats.app.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.providePreferencesDataStore
import es.schsebastian.foodrats.core.data.image.FirebaseImageUrlResolver
import es.schsebastian.foodrats.core.data.preferences.LocaleRepository
import es.schsebastian.foodrats.core.data.preferences.NotificationsPreferenceRepository
import es.schsebastian.foodrats.core.data.preferences.ThemeModeRepository
import es.schsebastian.foodrats.core.domain.coroutines.DefaultDispatcherProvider
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.domain.time.SystemClock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val coreDataModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<Clock> { SystemClock() }
    single<TimeZone> { TimeZone.currentSystemDefault() }
    // CrashReporter is bound per-platform (Crashlytics has no KMP binding):
    //   Android -> AndroidCrashReporter in FoodRatsApplication
    //   iOS     -> crashIosModule(...) in MainViewController
    single { providePreferencesDataStore() }
    single { AppPreferences(get()) }
    single<ThemeModePort> { ThemeModeRepository(prefs = get(), dispatchers = get()) }
    single<LocalePort> { LocaleRepository(prefs = get(), dispatchers = get()) }
    single<NotificationsPreferencePort> {
        NotificationsPreferenceRepository(prefs = get(), dispatchers = get())
    }
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }
    // Resolves Storage object paths → membership-checked V4 signed URLs via the
    // mintPlateUrls callable. Consumed by the meal-feed enrichment + AccountReadPort impl.
    single<ImageUrlPort> { FirebaseImageUrlResolver(dispatchers = get(), clock = get()) }
    // JSON serializer shared across features (MealDraftLocalStore + others).
    single { Json { ignoreUnknownKeys = true; isLenient = true } }
}
