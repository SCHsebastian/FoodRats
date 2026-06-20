package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Updates the user's personal bio. Validates the raw string via [Bio.of] (trims, cap 100 chars,
 * blank = clear). A blank value is treated as "remove bio" — persists `null` to Firestore.
 *
 * Single I/O boundary is in [AccountWritePort.updateBio].
 */
class UpdateMyBioUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(raw: String): Result<Unit, ProfileError> {
        val bio = when (val v = Bio.of(raw)) {
            is Result.Ok -> v.value   // Bio? — null means "clear"
            is Result.Err -> return Result.failure(ProfileError.Validation.BioTooLong)
        }

        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        return when (val r = accountWrite.updateBio(current.accountId, bio)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
    }
}
