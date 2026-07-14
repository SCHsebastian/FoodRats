package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationsPreferenceRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : NotificationsPreferencePort {

    override val enabled: Flow<Boolean> = prefs.observe(Keys.NotificationsAllowed).map { it ?: true }

    override val prompted: Flow<Boolean> = prefs.observe(Keys.NotificationsPermissionPrompted).map { it ?: false }

    override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> =
        withContext(dispatchers.io) {
            persistResult({ NotificationsPreferenceError.Persist.Unavailable }) {
                prefs.set(Keys.NotificationsAllowed, enabled)
            }
        }

    override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> =
        withContext(dispatchers.io) {
            persistResult({ NotificationsPreferenceError.Persist.Unavailable }) {
                prefs.set(Keys.NotificationsPermissionPrompted, true)
            }
        }
}
