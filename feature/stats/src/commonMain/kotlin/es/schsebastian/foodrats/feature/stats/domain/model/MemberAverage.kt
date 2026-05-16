package es.schsebastian.foodrats.feature.stats.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId

data class MemberAverage(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    val averageScore: Double,
    val postCount: Int,
)
