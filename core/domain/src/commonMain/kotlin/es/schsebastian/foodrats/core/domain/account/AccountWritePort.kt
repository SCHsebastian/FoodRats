package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Writes to the canonical `accounts/{uid}` Firestore doc and to the associated avatar Storage
 * object — content-versioned `avatars/{uid}/{token}.jpg`, persisted as a PATH (not a download URL)
 * to `accounts/{uid}.avatarPath` and resolved to a short-lived signed URL at read time. Lives in
 * :core:domain so :feature:auth can implement it without :feature:crew or other surfaces depending
 * on auth.
 *
 * Reads of the canonical identity flow through [AccountReadPort].
 *
 * Validation is the caller's job: [updateDisplayName] takes a pre-validated [DisplayName] and
 * [updateBio] a pre-validated [Bio] (see [DisplayName.of] / [Bio.of]). This port only owns the
 * persistence boundary and maps backend failures to the typed [AccountWriteError] tree.
 */
interface AccountWritePort {
    /** Persists the account's display name. [name] is pre-validated (trim/blank/length via [DisplayName.of]). */
    suspend fun updateDisplayName(
        accountId: AccountId,
        name: DisplayName,
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
     * Uploads [bytes] to the content-versioned Storage path `avatars/{accountId}/{token}.jpg` and
     * writes that PATH to `accounts/{accountId}.avatarPath`. The new avatar surfaces via
     * [AccountReadPort] re-emission (which resolves the path to a membership-checked signed URL),
     * so there is no URL to return. [bytes] is assumed non-empty (the use case guards it).
     */
    suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<Unit, AccountWriteError>

    /**
     * Clears `accounts/{accountId}.avatarPath` (sets it to null) FIRST — the user-visible source of
     * truth — then best-effort reclaims the avatar Storage object. The account doc re-emits via
     * [AccountReadPort], so the UI falls back to initials with no additional writes.
     *
     * No-ops silently if the user has no avatar; that case is not an error.
     */
    suspend fun removeAvatar(accountId: AccountId): Result<Unit, AccountWriteError>
}

/**
 * Backend failure surface for account writes. Mirrors the auth/crew error shape: connectivity vs.
 * server vs. permission vs. session-expired are distinct leaves so the UI can route a token-expired
 * write to re-auth, surface an offline notice, and so the offline outbox can decide retryable vs.
 * terminal. There is intentionally NO `Validation` group — validation lives in the use case / value
 * objects ([DisplayName.of] / [Bio.of]) before the port is ever called.
 */
sealed interface AccountWriteError {
    sealed interface Session : AccountWriteError {
        /** Auth token missing/expired — the write needs a fresh sign-in (route to re-auth). */
        data object Expired : Session
    }

    sealed interface Backend : AccountWriteError {
        /** Lost connectivity reaching Firestore/Storage — transient; retryable and queue-able. */
        data object Network : Backend
        /** Transient server unavailability — retryable. */
        data object Unavailable : Backend
        /** Security rules rejected the write (e.g. wrong doc shape) — terminal, not retryable. */
        data object PermissionDenied : Backend
        /** Unclassifiable failure — terminal; recorded as a non-fatal at the mapping seam. */
        data object Unknown : Backend
    }
}
