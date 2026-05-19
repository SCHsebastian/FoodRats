package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

data class MealRating(
    val raterId: AccountId,
    val raterDisplayName: String,
    val raterAvatarUrl: String?,
    val score: Score,
    val ratedAt: Instant,
)
