package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AccentPaletteError
import es.schsebastian.foodrats.core.domain.preferences.AccentPalettePort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccentPaletteRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : AccentPalettePort {

    /** Absent or unrecognised key → [AccentPalette.Ember] (brand default). */
    override val palette: Flow<AccentPalette> = prefs.observe(Keys.AccentPalette).map { stored ->
        runCatching { stored?.let(AccentPalette::valueOf) }.getOrNull() ?: AccentPalette.Ember
    }

    override suspend fun set(palette: AccentPalette): Result<Unit, AccentPaletteError> =
        withContext(dispatchers.io) {
            persistResult({ AccentPaletteError.Persist.Unavailable }) {
                prefs.set(Keys.AccentPalette, palette.name)
            }
        }
}
