package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

data class MealRating(
    val raterId: AccountId,
    val raterDisplayName: String,
    val raterAvatarUrl: String?,
    val score: Score,
    val ratedAt: Instant,
    /**
     * `true` once the rater has used their single allowed vote change. A rater may overwrite their
     * score exactly once; after that the entry is `edited = true` and further changes are rejected
     * (authoritatively by the Firestore rules + rate transaction). Drives the "change my vote"
     * affordance + its one-time-only confirmation in the meal detail UI.
     */
    val edited: Boolean = false,
)
