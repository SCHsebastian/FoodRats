package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Curated accent-colour identities the user may apply to personalise the app palette.
 * [Ember] is the Iron & Ember brand default; the remaining variants are derived from the
 * same palette family so every option looks like it belongs.
 */
enum class AccentPalette {
    /** Brand default — deep-olive primary / ember-copper secondary. */
    Ember,
    /** Moss variant — medium-olive shifted slightly warmer. */
    Moss,
    /** Rust variant — terracotta drawn from the rust tertiary. */
    Rust,
    /** Steel variant — cool blue-grey for a more minimal feel. */
    Steel,
    /** Berry variant — muted plum for warmth without going off-brand. */
    Berry,
}

interface AccentPalettePort {
    /** Currently persisted accent. Emits [AccentPalette.Ember] when absent (default). */
    val palette: Flow<AccentPalette>
    suspend fun set(palette: AccentPalette): Result<Unit, AccentPaletteError>
}

sealed interface AccentPaletteError {
    sealed interface Persist : AccentPaletteError {
        data object Unavailable : Persist
    }
}
