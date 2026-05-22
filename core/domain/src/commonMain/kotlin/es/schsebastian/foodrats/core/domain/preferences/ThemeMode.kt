package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { Light, Dark, System }

interface ThemeModePort {
    val mode: Flow<ThemeMode>
    suspend fun set(mode: ThemeMode): Result<Unit, ThemePreferenceError>
}

sealed interface ThemePreferenceError {
    sealed interface Persist : ThemePreferenceError {
        data object Unavailable : Persist
    }
}
