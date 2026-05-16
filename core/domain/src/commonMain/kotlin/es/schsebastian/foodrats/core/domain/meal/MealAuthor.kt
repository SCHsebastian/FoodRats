package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId

data class MealAuthor(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
)
