package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

data class Account(
    val id: AccountId,
    val handle: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    // Personal tagline / bio (≤ 100 chars). Null = no bio set.
    val bio: Bio? = null,
    // Reserved for the data-consent flow (spec §13). Not yet written by any path;
    // 0 / null means "no consent recorded". Defaults keep existing call sites intact.
    val dataConsentVersion: Int = 0,
    val dataConsentGrantedAt: Instant? = null,
)
