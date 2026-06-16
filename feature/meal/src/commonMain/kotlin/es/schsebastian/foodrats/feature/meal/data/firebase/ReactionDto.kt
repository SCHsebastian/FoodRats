package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore document for one member's reaction on a meal, stored at
 * `crews/{crewId}/meals/{mealId}/reactions/{uid}` — the doc ID is the reactor's uid, which
 * authoritatively enforces the one-reaction-per-member invariant (see security rules + the
 * `reactions` subcollection rule in `firestore.rules`).
 *
 * Only the *fact* of the reaction is persisted: who ([reactorId]), which kind ([kind] — a stable
 * snake_case discriminator from [es.schsebastian.foodrats.core.domain.meal.ReactionKind.key],
 * never the rendered glyph), and when ([reactedAtEpochMs]). The displayed glyph for the parked
 * `DailyGlyph` kind is DERIVED at render time from the meal's day (`DailyEmote.forDay(meal.day)`),
 * not stored here.
 *
 * Pre-launch: no migration. All fields are nullable defaults so any future field addition or an
 * older/partial doc deserializes (kotlinx-serialization `ignoreUnknownKeys` tolerates extras).
 */
@Serializable
data class ReactionDto(
    /** The reactor's uid; mirrors the doc ID. Set from the doc ID on read. */
    val reactorId: String? = null,
    /** Persisted kind discriminator, e.g. `"daily_glyph"`. Unknown values map to null kind on read. */
    val kind: String? = null,
    val reactedAtEpochMs: Long? = null,
)
