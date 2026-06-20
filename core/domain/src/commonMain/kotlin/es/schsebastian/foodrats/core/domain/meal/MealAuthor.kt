package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.model.AccountId

data class MealAuthor(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    // Added in U5b — additive, defaulted at the END so all positional fixtures still compile.
    val bio: Bio? = null,
    val badgeId: String? = null,
)
