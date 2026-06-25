package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import kotlinx.coroutines.withContext

/**
 * Default [AccountWritePort] implementation backed by Firestore + Firebase Storage.
 *
 * Validation does NOT live here — the use cases pass pre-validated [DisplayName] / [Bio] values.
 * Backend failures are classified by [toAccountWriteFault] (the single message-inspection seam) and
 * mapped **by fault type** into the typed [AccountWriteError] tree, so permission-denied /
 * token-expired / offline are distinguishable (and an unmapped throwable is recorded as a
 * non-fatal). Storage upload + Firestore write are sequenced, not batched — Storage is a separate
 * service from Firestore and cannot be atomically committed with a doc update — so each write that
 * can orphan/dangle uses a compensating action.
 */
class FirestoreAccountWriter(
    private val firestore: FirebaseFirestore,
    private val avatarStorage: AvatarStorageDataSource,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : AccountWritePort {

    override suspend fun updateDisplayName(
        accountId: AccountId,
        name: DisplayName,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            firestore.collection("accounts").document(accountId.value)
                .update("displayName" to name.value)
            Result.success(Unit)
        }.getOrElse { Result.failure(it.toAccountWriteError()) }
    }

    override suspend fun updateBio(
        accountId: AccountId,
        bio: Bio?,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            firestore.collection("accounts").document(accountId.value)
                .update("bio" to bio?.value)
            Result.success(Unit)
        }.getOrElse { Result.failure(it.toAccountWriteError()) }
    }

    override suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            val doc = firestore.collection("accounts").document(accountId.value)
            // The avatar PATH is content-versioned (`avatars/{uid}/{token}.jpg`), so a NEW image
            // produces a NEW path string. Persisting it changes the `accounts/{uid}` DTO, which is
            // exactly what makes the change surface live: AccountReadPort re-emits and re-resolves
            // the path to a fresh signed URL (the old fixed path never changed → stale until restart).
            val previousPath = readAvatarPath(accountId)
            val path = avatarStorage.upload(accountId, bytes)
            // Storage + Firestore can't commit atomically. If the pointer write fails, the blob we
            // just uploaded would be orphaned — best-effort reclaim it before surfacing the error.
            try {
                doc.update("avatarPath" to path)
            } catch (t: Throwable) {
                runCatching { avatarStorage.delete(path) }
                throw t
            }
            // Best-effort: reclaim the previous version's object so old avatars don't accumulate.
            // Skipped when unchanged (same image) or absent. A failure here must not fail the update.
            if (previousPath != null && previousPath != path) {
                runCatching { avatarStorage.delete(previousPath) }
            }
            Result.success(Unit)
        }.getOrElse { Result.failure(it.toAccountWriteError()) }
    }

    override suspend fun removeAvatar(
        accountId: AccountId,
    ): Result<Unit, AccountWriteError> = withContext(dispatchers.io) {
        runCatching {
            // Clear the Firestore pointer FIRST (the user-visible source of truth) so a reader never
            // resolves a signed URL to a just-deleted blob (dangling pointer → broken image). Read
            // the current path before nulling it so we can reclaim the blob afterwards.
            val current = readAvatarPath(accountId)
            firestore.collection("accounts").document(accountId.value)
                .update("avatarPath" to null)
            // Best-effort blob reclaim: a failure here leaves an orphaned object (invisible cost,
            // swept by the account-deletion prefix delete), never a dangling pointer. Not-found is
            // already treated as success by AvatarStorageDataSource.delete.
            if (current != null) {
                runCatching { avatarStorage.delete(current) }
            }
            Result.success(Unit)
        }.getOrElse { Result.failure(it.toAccountWriteError()) }
    }

    /** Current `accounts/{uid}.avatarPath`, or null if the doc/field is absent. */
    private suspend fun readAvatarPath(accountId: AccountId): String? {
        val snap = firestore.collection("accounts").document(accountId.value).get()
        return if (snap.exists) snap.data<AccountDto>().avatarPath else null
    }

    /**
     * Classify a raw write throwable into the typed [AccountWriteError] tree via the single
     * [toAccountWriteFault] seam (mirrors `AuthErrorMapper`). Unmapped causes are recorded.
     */
    private fun Throwable.toAccountWriteError(): AccountWriteError = when (val fault = toAccountWriteFault()) {
        AccountWriteFault.PermissionDenied -> AccountWriteError.Backend.PermissionDenied
        AccountWriteFault.Unauthenticated -> AccountWriteError.Session.Expired
        AccountWriteFault.Network -> AccountWriteError.Backend.Network
        is AccountWriteFault.Unknown -> {
            crashReporter.recordNonFatal(fault.cause, "account-write-unmapped")
            AccountWriteError.Backend.Unknown
        }
    }
}
