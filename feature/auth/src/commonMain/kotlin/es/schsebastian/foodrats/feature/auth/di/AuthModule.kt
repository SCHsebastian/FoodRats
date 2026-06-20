package es.schsebastian.foodrats.feature.auth.di

import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.DataExportPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.auth.data.firebase.AccountDocStore
import es.schsebastian.foodrats.feature.auth.data.firebase.AccountSnapshotSource
import es.schsebastian.foodrats.feature.auth.data.firebase.AuthErrorMapper
import es.schsebastian.foodrats.feature.auth.data.firebase.AvatarStorageDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAccountSnapshotSource
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.FirestoreAccountDocStore
import es.schsebastian.foodrats.feature.auth.data.firebase.FirestoreAccountReadDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAccountDeletionPort
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseDataExportPort
import es.schsebastian.foodrats.feature.auth.data.firebase.FirestoreAccountWriter
import es.schsebastian.foodrats.feature.auth.data.repository.AuthSignOutPort
import es.schsebastian.foodrats.feature.auth.data.repository.FirebaseAuthRepository
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.DeleteMyAccountUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.EnableNotificationsUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.ExportMyDataUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetAiEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetLocaleUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetMealRemindersUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetNotificationsEnabledUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.SetThemeModeUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyBioUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyDisplayNameUseCase
import es.schsebastian.foodrats.feature.auth.presentation.profile.ProfileViewModel
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInViewModel
import es.schsebastian.foodrats.feature.auth.presentation.topbar.TopBarAvatarViewModel
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthErrorMapper)
    single<AccountDocStore> { FirestoreAccountDocStore(firestore = get()) }
    single { FirebaseAuthDataSource(auth = get(), store = get(), clock = get<Clock>(), dispatchers = get()) }
    // appleClient (2nd arg) is bound per-platform alongside GoogleAuthClient:
    // androidAuthModule() in FoodRatsApplication, authIosModule(...) on iOS.
    single<AuthRepository> { FirebaseAuthRepository(get(), get(), get(), get(), get(), get()) }
    single<SessionProvider> { get<AuthRepository>() }
    single<SignOutPort> { AuthSignOutPort(get(), get()) }
    single<AccountSnapshotSource> {
        FirebaseAccountSnapshotSource(
            firestore = get(),
            // Safety net mirroring FirebaseMealRepository.repoScope: even though the
            // snapshot flow now `.catch`es PERMISSION_DENIED-on-signout, a handler-less
            // scope means any other escaping exception would crash the process on
            // iOS/Native. The handler keeps the stateIn sharing coroutine non-fatal.
            scope = CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Default +
                    CoroutineExceptionHandler { _, t ->
                        FrLog.w("AccountSnapshot", t) { "scope uncaught: ${t.message}" }
                    },
            ),
        )
    }
    single<AccountReadPort> {
        FirestoreAccountReadDataSource(source = get(), imageUrls = get(), activeCrew = get())
    }
    singleOf(::AvatarStorageDataSource)
    single<AccountWritePort> {
        FirestoreAccountWriter(firestore = get(), avatarStorage = get(), dispatchers = get())
    }
    single<AccountDeletionPort> { FirebaseAccountDeletionPort(dispatchers = get()) }
    single<DataExportPort> { FirebaseDataExportPort(dispatchers = get()) }
    factoryOf(::UpdateMyDisplayNameUseCase)
    factoryOf(::UpdateMyBioUseCase)
    factoryOf(::UpdateMyAvatarUseCase)
    factoryOf(::SetThemeModeUseCase)
    factoryOf(::SetLocaleUseCase)
    factoryOf(::SetMealRemindersUseCase)
    factoryOf(::SetNotificationsEnabledUseCase)
    factoryOf(::EnableNotificationsUseCase)
    factoryOf(::SetAiEnabledUseCase)
    factoryOf(::DeleteMyAccountUseCase)
    factoryOf(::ExportMyDataUseCase)
    viewModel { SignInViewModel(auth = get(), tokenRegistration = get(), analytics = get(), eula = get()) }
    // Explicit (not viewModelOf): the `analytics` ctor param defaults to NoopAnalyticsTracker,
    // and viewModelOf would let that default short-circuit graph resolution instead of injecting
    // the real tracker. See the analytics-base convention in CLAUDE.md.
    viewModel {
        ProfileViewModel(
            accountRead = get(),
            session = get(),
            themePort = get(),
            localePort = get(),
            notificationsPort = get(),
            aiPreferencePort = get(),
            mealRemindersPort = get(),
            updateDisplayName = get(),
            updateBio = get(),
            updateAvatar = get(),
            signOut = get(),
            setThemeMode = get(),
            setLocale = get(),
            setMealReminders = get(),
            setNotificationsEnabled = get(),
            enableNotifications = get(),
            setAiEnabled = get(),
            notificationPermission = get(),
            deleteMyAccount = get(),
            exportMyData = get(),
            consent = get(),
            analytics = get(),
        )
    }
    viewModelOf(::TopBarAvatarViewModel)
}
