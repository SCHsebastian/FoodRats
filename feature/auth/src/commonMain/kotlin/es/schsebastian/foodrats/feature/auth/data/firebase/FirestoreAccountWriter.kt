package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.withContext

private const val MAX_DISPLAY_NAME = 40

/**
 * Default [AccountWritePort] implementation backed by Firestore + Firebase Storage.
 *
 * Validation lives here (length, blank, empty-bytes); backend failures are mapped to
 * [AccountWriteError.Backend.Unavailable]. Storage upload + Firestore write are
 * sequenced, not batched — Storage is a separate service from Firestore and cannot be
 * atomically committed with a doc update.
 */
class FirestoreAccountWriter(
    private val firestore: FirebaseFirestore,
    private val avatarStorage: AvatarStorageDataSource,
    private val dispatchers: DispatcherProvider,
) : AccountWritePort {

    override suspend fun updateDisplayName(
        accountId: AccountId,
        name: String,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(AccountWriteError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX_DISPLAY_NAME) return@withContext Result.failure(AccountWriteError.Validation.DisplayNameTooLong)
        runCatching {
            firestore.collection("accounts").document(accountId.value)
                .update("displayName" to trimmed)
            Result.success(Unit)
        }.getOrElse { Result.failure(AccountWriteError.Backend.Unavailable) }
    }

    override suspend fun updateBio(
        accountId: AccountId,
        bio: Bio?,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            firestore.collection("accounts").document(accountId.value)
                .update("bio" to bio?.value)
            Result.success(Unit)
        }.getOrElse { Result.failure(AccountWriteError.Backend.Unavailable) }
    }

    override suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<String, AccountWriteError> = withContext(dispatchers.io) {
        if (bytes.isEmpty()) return@withContext Result.failure(AccountWriteError.Validation.EmptyBytes)
        runCatching {
            // Upload returns the deterministic Storage PATH (not a URL); persist it as
            // `avatarPath`. The new avatar surfaces to the UI via AccountReadPort re-emission,
            // which resolves the path to a signed URL.
            val path = avatarStorage.upload(accountId, bytes)
            firestore.collection("accounts").document(accountId.value)
                .update("avatarPath" to path)
            Result.success(path)
        }.getOrElse { Result.failure(AccountWriteError.Backend.Unavailable) }
    }
}
