package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Initiates permanent deletion of the current account. The caller is responsible
 * for the UI phrase-confirmation gate; this use case re-validates the phrase as
 * defense in depth and forwards to the [AccountDeletionPort].
 */
class DeleteMyAccountUseCase(
    private val session: SessionProvider,
    private val deletion: AccountDeletionPort,
) {
    suspend operator fun invoke(
        confirmation: String,
        expected: String,
    ): Result<Unit, ProfileError> {
        if (confirmation != expected) {
            return Result.failure(ProfileError.Delete.PhraseMismatch)
        }
        val current = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }
        return when (val r = deletion.requestDeletion(current.accountId, confirmation)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
    }
}
