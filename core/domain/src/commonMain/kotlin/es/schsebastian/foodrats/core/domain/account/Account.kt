package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId

data class Account(
    val id: AccountId,
    val handle: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
)
