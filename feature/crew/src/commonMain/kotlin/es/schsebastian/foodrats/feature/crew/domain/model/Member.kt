package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

/** A Crew-scoped projection of an Account. Distinct from `Account` in :core:domain. */
data class Member(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    val joinedAt: Instant,
)
