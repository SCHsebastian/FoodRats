package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.Session
import kotlin.time.Instant

fun AccountDto.toAccount(): Account? {
    val id = AccountId.of(id ?: return null).getOrElse { return null }
    return Account(
        id = id,
        handle = handle ?: "",
        displayName = displayName ?: "",
        // Email no longer lives on the public account doc (PII; see AccountDto).
        // It is owned by Firebase Auth; surface it from there if ever displayed.
        email = null,
        avatarUrl = avatarUrl,
        dataConsentVersion = dataConsentVersion,
        dataConsentGrantedAt = dataConsentGrantedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    )
}

fun Account.toSession(): Session = Session(accountId = id, activeCrewId = null)
