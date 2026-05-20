package es.schsebastian.foodrats.feature.auth.domain.repository

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

interface AuthRepository : SessionProvider {
    suspend fun signInWithGoogle(): Result<Session, AuthError>
    suspend fun signInWithEmail(email: String, password: String): Result<Session, AuthError>
    suspend fun signUpWithEmail(email: String, password: String): Result<Session, AuthError>
    suspend fun signOut(): Result<Unit, AuthError>
}
