package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Updates the user's canonical display name. Trim + length validation (max 40 chars) live
 * here. The crew members list resolves identity live via `AccountReadPort`, so a single
 * write to `accounts/{uid}` is enough — no denormalized cache to propagate.
 */
class UpdateMyDisplayNameUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(name: String): Result<Unit, ProfileError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(ProfileError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX) return Result.failure(ProfileError.Validation.DisplayNameTooLong)

        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        return when (val r = accountWrite.updateDisplayName(current.accountId, trimmed)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
    }

    companion object {
        const val MAX = 40
    }
}
