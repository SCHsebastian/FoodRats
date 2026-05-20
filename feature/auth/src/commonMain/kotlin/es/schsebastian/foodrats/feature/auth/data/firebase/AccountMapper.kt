package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.account.Account

fun AccountDto.toAccount(): Account? {
    val id = AccountId.of(id ?: return null).getOrElse { return null }
    return Account(
        id = id,
        handle = handle ?: "",
        displayName = displayName ?: "",
        email = email,
        avatarUrl = avatarUrl,
    )
}

fun Account.toSession(): Session = Session(accountId = id, activeCrewId = null)
