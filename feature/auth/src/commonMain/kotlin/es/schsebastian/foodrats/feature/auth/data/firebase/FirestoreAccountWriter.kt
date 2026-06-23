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
            val doc = firestore.collection("accounts").document(accountId.value)
            // The avatar PATH is content-versioned (`avatars/{uid}/{token}.jpg`), so a NEW image
            // produces a NEW path string. Persisting it changes the `accounts/{uid}` DTO, which is
            // exactly what makes the change surface live: AccountReadPort re-emits and re-resolves
            // the path to a fresh signed URL (the old fixed path never changed → stale until restart).
            val previousPath = readAvatarPath(accountId)
            val path = avatarStorage.upload(accountId, bytes)
            doc.update("avatarPath" to path)
            // Best-effort: reclaim the previous version's object so old avatars don't accumulate.
            // Skipped when unchanged (same image) or absent. A failure here must not fail the update.
            if (previousPath != null && previousPath != path) {
                runCatching { avatarStorage.delete(previousPath) }
            }
            Result.success(path)
        }.getOrElse { Result.failure(AccountWriteError.Backend.Unavailable) }
    }

    override suspend fun removeAvatar(
        accountId: AccountId,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            // Delete the CURRENT object (path read from Firestore — it's now versioned, so we can't
            // derive it from the uid). Best-effort: not-found is treated as success.
            readAvatarPath(accountId)?.let { current ->
                runCatching { avatarStorage.delete(current) }
            }
            // Null out the Firestore path so AccountReadPort re-emits and the UI shows initials.
            firestore.collection("accounts").document(accountId.value)
                .update("avatarPath" to null)
            Result.success(Unit)
        }.getOrElse { Result.failure(AccountWriteError.Backend.Unavailable) }
    }

    /** Current `accounts/{uid}.avatarPath`, or null if the doc/field is absent. */
    private suspend fun readAvatarPath(accountId: AccountId): String? {
        val snap = firestore.collection("accounts").document(accountId.value).get()
        return if (snap.exists) snap.data<AccountDto>().avatarPath else null
    }
}
