package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.feature.auth.data.firebase.AuthErrorMapper
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.toAccount
import es.schsebastian.foodrats.feature.auth.data.firebase.toSession
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

internal class FirebaseAuthRepository(
    private val googleClient: GoogleAuthClient,
    private val firebase: FirebaseAuthDataSource,
    private val errorMapper: AuthErrorMapper,
    private val prefs: AppPreferences,
) : AuthRepository {

    // The base session from Firebase Auth carries the account id; the active crew
    // id lives in DataStore (set during sign-in via the dev-crew hack, and later
    // by the Crew picker). Combine both so consumers see a complete Session and
    // CaptureMealViewModel doesn't error with CrewNotFound for signed-in users.
    override val current: Flow<Session?> =
        combine(firebase.sessions(), prefs.observe(Keys.ActiveCrewId)) { session, crewIdValue ->
            if (session == null) null
            else {
                val crewId = crewIdValue?.let { value ->
                    (CrewId.of(value) as? Result.Ok)?.value
                }
                session.copy(activeCrewId = crewId)
            }
        }

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        current.first()?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        val token = when (val r = googleClient.signIn()) {
            is Result.Ok  -> r.value
            is Result.Err -> return Result.failure(r.error)
        }
        return try {
            val uid = firebase.signInWithGoogle(token)
            val account = firebase.ensureAccountDoc(uid).toAccount()
                ?: return Result.failure(AuthError.Firebase.Unavailable)
            // TODO(scope = "feature:crew"): remove this dev-crew hardcode once the
            // Crew feature lets users create/pick a crew. Until then, smoke-testing
            // meal publishing needs a non-null Session.activeCrewId AND the crew
            // document to actually exist (Firestore rules require auth.uid in
            // crews/{crewId}.memberIds for meal writes). Without ensureDevCrewMembership,
            // meal publish writes are silently rejected by Firestore rules: they queue
            // locally (publish reports success) but never land on the server.
            runCatching {
                firebase.ensureDevCrewMembership(
                    crewId = DEV_CREW_ID,
                    uid = uid,
                    displayName = account.displayName,
                    nowEpochMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
            }
            // If runCatching failed, the crew doc may already exist for another user
            // and rules forbid self-add. We continue — only writes for an unauthorised
            // user will fail later.
            val devCrewId = (CrewId.of(DEV_CREW_ID) as Result.Ok).value
            prefs.set(Keys.ActiveCrewId, DEV_CREW_ID)
            Result.success(account.toSession().copy(activeCrewId = devCrewId))
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    private companion object {
        const val DEV_CREW_ID = "test-crew-1"
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        return try {
            firebase.signOut()
            googleClient.signOut()
            prefs.clear(Keys.SessionToken)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }
}
