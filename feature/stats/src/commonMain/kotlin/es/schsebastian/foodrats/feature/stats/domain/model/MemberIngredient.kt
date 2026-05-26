package es.schsebastian.foodrats.feature.stats.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId

/** A crew member's most-used ingredient in a window. Mirrors [MemberCount] / [MemberAverage]. */
data class MemberIngredient(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    val ingredientName: String,
    val mealCount: Int,
)
