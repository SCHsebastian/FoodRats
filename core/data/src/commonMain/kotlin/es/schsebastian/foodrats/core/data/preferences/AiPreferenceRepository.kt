package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AiPreferenceRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : AiPreferencePort {

    /** Absent key = enabled (opt-out model: user must actively disable AI). */
    override val enabled: Flow<Boolean> = prefs.observe(Keys.AiUsageEnabled).map { it ?: true }

    override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> =
        withContext(dispatchers.io) {
            runCatching { prefs.set(Keys.AiUsageEnabled, enabled) }
                .fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { Result.failure(AiPreferenceError.Persist.Unavailable) },
                )
        }
}
