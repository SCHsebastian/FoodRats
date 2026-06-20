package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toRemoveAvatarProfileError

/**
 * Removes the user's avatar from Storage and clears `accounts/{uid}.avatarPath`.
 * The account doc re-emits via [AccountReadPort], so the UI falls back to initials
 * automatically — no separate UI update needed on success.
 */
class RemoveMyAvatarUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(): Result<Unit, ProfileError> {
        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }
        return when (val r = accountWrite.removeAvatar(current.accountId)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toRemoveAvatarProfileError())
        }
    }
}
