package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Initiates permanent account deletion. The client writes a "pending deletion"
 * marker; a Cloud Function performs the actual cascade (meals, comments, crew
 * memberships, avatar, FCM tokens, Firebase Auth user) and reports completion.
 *
 * The current implementation is a stub — see the data layer for details.
 */
interface AccountDeletionPort {
    suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError>
}

sealed interface AccountDeletionError {
    sealed interface Validation : AccountDeletionError {
        data object PhraseMismatch : Validation
    }

    sealed interface Backend : AccountDeletionError {
        data object NotImplemented : Backend
        data object Unavailable : Backend
    }

    sealed interface Ownership : AccountDeletionError {
        data object OwnerOfActiveCrew : Ownership
    }
}
