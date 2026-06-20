package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Writes to the canonical `accounts/{uid}` Firestore doc and to the associated
 * `avatars/{uid}.jpg` Storage object. Lives in :core:domain so :feature:auth can
 * implement it without :feature:crew or other surfaces depending on auth.
 *
 * Reads of the canonical identity flow through [AccountReadPort].
 */
interface AccountWritePort {
    suspend fun updateDisplayName(
        accountId: AccountId,
        name: String,
    ): Result<Unit, AccountWriteError>

    /**
     * Persists the account's personal bio. [bio] is the validated [Bio] value (null = clear).
     * Validation (length cap) is enforced in the use-case layer via [Bio.of]; this method
     * expects a pre-validated value and only handles the persistence boundary.
     */
    suspend fun updateBio(
        accountId: AccountId,
        bio: Bio?,
    ): Result<Unit, AccountWriteError>

    /**
     * Uploads [bytes] to Storage at `avatars/{accountId}.jpg`, writes the resulting
     * download URL to `accounts/{accountId}.avatarUrl`, and returns the URL so the
     * caller can fan it out to denormalized member caches.
     */
    suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<String, AccountWriteError>
}

sealed interface AccountWriteError {
    sealed interface Validation : AccountWriteError {
        data object DisplayNameBlank : Validation
        data object DisplayNameTooLong : Validation
        data object BioTooLong : Validation
        data object EmptyBytes : Validation
    }

    sealed interface Backend : AccountWriteError {
        data object Unavailable : Backend
    }
}
