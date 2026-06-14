package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Uploads the user's avatar bytes to Storage and writes the resulting object PATH to
 * `accounts/{uid}.avatarPath` (canonical). Returns the stored path on success.
 *
 * The new avatar surfaces in the UI via `AccountReadPort` re-emission, which resolves the
 * path to a membership-checked signed URL — there's no usable URL to hand back synchronously
 * (download-token URLs were removed in #15). Crew member lists resolve identity live, so this
 * single canonical write is enough — no denormalized cache to propagate.
 */
class UpdateMyAvatarUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(bytes: ByteArray): Result<String, ProfileError> {
        if (bytes.isEmpty()) return Result.failure(ProfileError.Validation.EmptyBytes)

        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        return when (val r = accountWrite.uploadAndSetAvatar(current.accountId, bytes)) {
            is Result.Ok -> Result.success(r.value)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
    }
}
