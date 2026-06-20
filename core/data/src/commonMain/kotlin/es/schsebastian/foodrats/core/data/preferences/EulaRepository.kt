package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.EulaError
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * [EulaPort] over DataStore (`AppPreferences`). Local-first so the login-screen acceptance gate works
 * before any network. Absence of a stored version = never accepted → the gate requires acceptance.
 *
 * Acceptance is intentionally NOT cleared on sign-out (a EULA is accepted by the human/device, not the
 * account), so this repository exposes no clear/reset.
 */
class EulaRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : EulaPort {

    override val acceptedVersion: Flow<Int?> = prefs.observe(Keys.EulaAcceptedVersion)

    override suspend fun accept(version: Int): Result<Unit, EulaError> = withContext(dispatchers.io) {
        try {
            prefs.set(Keys.EulaAcceptedVersion, version)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Result.failure(EulaError.PersistFailed)
        }
    }
}
