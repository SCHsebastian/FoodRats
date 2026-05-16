package es.schsebastian.foodrats.feature.auth.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId

data class Account(
    val id: AccountId,
    val handle: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
)
