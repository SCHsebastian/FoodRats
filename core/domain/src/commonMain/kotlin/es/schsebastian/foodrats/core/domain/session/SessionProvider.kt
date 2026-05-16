package es.schsebastian.foodrats.core.domain.session

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

interface SessionProvider {
    val current: Flow<Session?>
    suspend fun requireCurrent(): Result<Session, SessionError>
}

enum class SessionError { NotSignedIn, TokenExpired, AccountDisabled, FirebaseUnavailable }
