package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Placeholder [AccountDeletionPort]. Always returns
 * [AccountDeletionError.Backend.NotImplemented] so the UI surfaces a
 * "contact support" message until the Cloud Function cascade
 * (`onAccountPendingDeletion`) is rebuilt under `functions/`.
 *
 * The client-side gates (phrase confirmation, sign-out cleanup) are exercised
 * end-to-end despite the backend stub.
 */
class StubAccountDeletionPort : AccountDeletionPort {
    override suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError> =
        Result.failure(AccountDeletionError.Backend.NotImplemented)
}
