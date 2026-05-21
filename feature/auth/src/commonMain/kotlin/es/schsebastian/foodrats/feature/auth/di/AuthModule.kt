package es.schsebastian.foodrats.feature.auth.di

import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
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
import es.schsebastian.foodrats.feature.auth.data.firebase.FirestoreAccountWriter
import es.schsebastian.foodrats.feature.auth.data.repository.AuthSignOutPort
import es.schsebastian.foodrats.feature.auth.data.repository.FirebaseAuthRepository
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyAvatarUseCase
import es.schsebastian.foodrats.feature.auth.domain.usecase.profile.UpdateMyDisplayNameUseCase
import es.schsebastian.foodrats.feature.auth.presentation.profile.ProfileViewModel
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInViewModel
import es.schsebastian.foodrats.feature.auth.presentation.topbar.TopBarAvatarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthErrorMapper)
    single<AccountDocStore> { FirestoreAccountDocStore(firestore = get()) }
    single { FirebaseAuthDataSource(auth = get(), store = get(), clock = get<Clock>(), dispatchers = get()) }
    single<AuthRepository> { FirebaseAuthRepository(get(), get(), get(), get(), get()) }
    single<SessionProvider> { get<AuthRepository>() }
    single<SignOutPort> { AuthSignOutPort(get()) }
    single<AccountSnapshotSource> {
        FirebaseAccountSnapshotSource(
            firestore = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<AccountReadPort> { FirestoreAccountReadDataSource(source = get()) }
    singleOf(::AvatarStorageDataSource)
    single<AccountWritePort> {
        FirestoreAccountWriter(firestore = get(), avatarStorage = get(), dispatchers = get())
    }
    factoryOf(::UpdateMyDisplayNameUseCase)
    factoryOf(::UpdateMyAvatarUseCase)
    viewModelOf(::SignInViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::TopBarAvatarViewModel)
}
