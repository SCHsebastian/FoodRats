package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.Bio
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
        // Carries the avatar PATH at this layer; FirestoreAccountReadDataSource resolves it
        // to a signed URL before the Account reaches any consumer.
        avatarUrl = avatarPath,
        // Bio is CLAMPED to the cap rather than dropped: an overlong stored value (old data, or a
        // future tightened cap) is truncated for display instead of vanishing — dropping it would
        // also let a subsequent save send the now-null bio and erase it server-side.
        bio = bio?.let { raw -> Bio.of(raw.take(Bio.MAX_LENGTH)).getOrElse { null } },
        dataConsentVersion = dataConsentVersion,
        dataConsentGrantedAt = dataConsentGrantedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        // badgeId is null when the server hasn't assigned any yet (≤0 publishes).
        badgeId = badgeId,
    )
}

fun Account.toSession(): Session = Session(accountId = id, activeCrewId = null)
