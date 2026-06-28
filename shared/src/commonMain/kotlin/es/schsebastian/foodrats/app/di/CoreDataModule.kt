package es.schsebastian.foodrats.app.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.providePreferencesDataStore
import es.schsebastian.foodrats.core.data.session.LocalAccountDataEraser
import es.schsebastian.foodrats.core.domain.session.LocalDataEraser
import es.schsebastian.foodrats.core.data.analytics.AnalyticsIdentityBinder
import es.schsebastian.foodrats.core.data.image.FirebaseImageUrlResolver
import es.schsebastian.foodrats.core.data.preferences.AccentPaletteRepository
import es.schsebastian.foodrats.core.data.preferences.ConsentRepository
import es.schsebastian.foodrats.core.data.preferences.EulaRepository
import es.schsebastian.foodrats.core.data.preferences.LocaleRepository
import es.schsebastian.foodrats.core.data.preferences.MealReminderScheduleRepository
import es.schsebastian.foodrats.core.data.preferences.AiPreferenceRepository
import es.schsebastian.foodrats.core.data.preferences.DefaultAudienceRepository
import es.schsebastian.foodrats.core.data.preferences.NotificationsPreferenceRepository
import es.schsebastian.foodrats.core.data.preferences.WelcomeDismissalRepository
import es.schsebastian.foodrats.core.data.preferences.ThemeModeRepository
import es.schsebastian.foodrats.core.domain.coroutines.DefaultDispatcherProvider
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.preferences.AccentPalettePort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
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
    // AnalyticsPort is likewise bound per-platform (analyticsAndroidModule / analyticsIosModule);
    // these two common bindings are platform-agnostic: the consent store + the identity binder.
    single<ConsentPort> { ConsentRepository(prefs = get(), clock = get(), dispatchers = get()) }
    // EULA / Community-Guidelines acceptance (UGC compliance §6). Local-first over DataStore; the
    // login-screen acceptance gate reads `acceptedVersion`. NOT cleared on sign-out.
    single<EulaPort> { EulaRepository(prefs = get(), dispatchers = get()) }
    // Eager: starts observing the session at boot so the account UID tracks sign-in/out for the whole
    // app lifetime. The actual setUserId stays consent-gated by ConsentGatedAnalytics.
    single(createdAtStart = true) { AnalyticsIdentityBinder(session = get(), analytics = get()) }
    single { providePreferencesDataStore() }
    single { AppPreferences(get()) }
    // Sign-out local-cache wipe (security #3): erases the SQLDelight feed/crew/outbox cache + the
    // account-scoped DataStore keys so the next account on this device can't read the previous
    // user's data and their queued writes don't replay. Consumed by AuthSignOutPort.
    single<LocalDataEraser> {
        LocalAccountDataEraser(database = get(), prefs = get(), dispatchers = get())
    }
    single<ThemeModePort> { ThemeModeRepository(prefs = get(), dispatchers = get()) }
    single<LocalePort> { LocaleRepository(prefs = get(), dispatchers = get()) }
    single<NotificationsPreferencePort> {
        NotificationsPreferenceRepository(prefs = get(), dispatchers = get())
    }
    single<AiPreferencePort> {
        AiPreferenceRepository(prefs = get(), dispatchers = get())
    }
    single<AccentPalettePort> {
        AccentPaletteRepository(prefs = get(), dispatchers = get())
    }
    single<DefaultAudiencePort> {
        DefaultAudienceRepository(prefs = get(), dispatchers = get())
    }
    single<MealReminderSchedulePort> {
        MealReminderScheduleRepository(prefs = get(), dispatchers = get())
    }
    // Per-crew welcome-banner dismissal store (C6). Not interface-bound here — CrewWelcomePort
    // (in crewModule) combines this with the crew repo for the full port implementation.
    single { WelcomeDismissalRepository(prefs = get(), dispatchers = get()) }
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }
    // Resolves Storage object paths → membership-checked V4 signed URLs via the
    // mintPlateUrls callable. Consumed by the meal-feed enrichment + AccountReadPort impl.
    // Persists immutable-path URLs (prefs) so cold starts reuse them instead of re-minting.
    single<ImageUrlPort> {
        FirebaseImageUrlResolver(dispatchers = get(), clock = get(), prefs = get(), json = get())
    }
    // JSON serializer shared across features (MealDraftLocalStore + others).
    single { Json { ignoreUnknownKeys = true; isLenient = true } }
}
