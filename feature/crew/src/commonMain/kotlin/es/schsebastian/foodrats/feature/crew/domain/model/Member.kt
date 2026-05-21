package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

/**
 * A Crew-scoped projection of an Account. Identity (display name + avatar) is resolved live
 * via `AccountReadPort` at the presentation layer — the Member model itself only carries the
 * crew-membership facts (who and when they joined).
 */
data class Member(
    val accountId: AccountId,
    val joinedAt: Instant,
)
