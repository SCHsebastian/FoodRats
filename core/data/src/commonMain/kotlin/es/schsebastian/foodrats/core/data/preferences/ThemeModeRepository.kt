package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.preferences.ThemePreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ThemeModeRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : ThemeModePort {

    override val mode: Flow<ThemeMode> = prefs.observe(Keys.ThemeMode).map { stored ->
        runCatching { stored?.let(ThemeMode::valueOf) }.getOrNull() ?: ThemeMode.System
    }

    override suspend fun set(mode: ThemeMode): Result<Unit, ThemePreferenceError> =
        withContext(dispatchers.io) {
            persistResult({ ThemePreferenceError.Persist.Unavailable }) {
                prefs.set(Keys.ThemeMode, mode.name)
            }
        }
}
