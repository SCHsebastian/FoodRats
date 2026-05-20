package es.schsebastian.foodrats.feature.auth.di

import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.feature.auth.data.firebase.AuthErrorMapper
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource
import es.schsebastian.foodrats.feature.auth.data.repository.AuthSignOutPort
import es.schsebastian.foodrats.feature.auth.data.repository.FirebaseAuthRepository
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthErrorMapper)
    singleOf(::FirebaseAuthDataSource)
    single<AuthRepository> { FirebaseAuthRepository(get(), get(), get(), get()) }
    single<SessionProvider> { get<AuthRepository>() }
    single<SignOutPort> { AuthSignOutPort(get()) }
    viewModelOf(::SignInViewModel)
}
