package es.schsebastian.foodrats.feature.auth.testdoubles

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FixedSessionProvider(private val session: Session?) : SessionProvider {
    override val current: StateFlow<Session?> = MutableStateFlow(session)

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        session?.let { Result.success(it) }
            ?: Result.failure(SessionError.NotSignedIn)
}
