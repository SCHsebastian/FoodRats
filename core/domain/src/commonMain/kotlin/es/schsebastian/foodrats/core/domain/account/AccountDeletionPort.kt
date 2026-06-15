package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Initiates permanent, irreversible account deletion.
 *
 * [requestDeletion] invokes the server-side `deleteAccount` cascade **synchronously** and
 * returns [Result.success] only when the function reports the full cascade complete (the
 * `accounts/{uid}` doc, FCM tokens, avatar blob, every authored meal across every crew with
 * its plate/comments/ratings, this account's comments/ratings on other members' meals, crew
 * memberships — with automatic owner reassignment for multi-member owned crews — and the
 * Firebase Auth user record). There is no "pending deletion" marker: the call is the cascade.
 *
 * [confirmation] is the typed phrase, forwarded so the server re-validates it (defense in
 * depth — the client also gates it). The implementing adapter derives the caller's identity
 * server-side; [accountId] is the local session's id and a client may only delete itself.
 *
 * See `docs/specs/2026-06-14-account-deletion-design.md`. The adapter lives in the feature's
 * data layer; this domain port is vendor-free.
 */
interface AccountDeletionPort {
    suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError>
}

sealed interface AccountDeletionError {
    sealed interface Validation : AccountDeletionError {
        /** The typed confirmation phrase did not match (server `failed-precondition`). */
        data object PhraseMismatch : Validation
    }

    sealed interface Backend : AccountDeletionError {
        /**
         * Dead-but-kept one release: the stub-era "contact support" outcome. Removed in a
         * follow-up once no shipped build still points at the stub. See spec §10.
         */
        data object NotImplemented : Backend

        /** The cascade could not complete; the session is still valid, so the user can retry. */
        data object Unavailable : Backend
    }

    sealed interface Deletion : AccountDeletionError {
        /**
         * An owned crew with other members could not have its ownership reassigned (server
         * `aborted`). The account is NOT partially deleted — retryable. Replaces the obsolete
         * `Ownership.OwnerOfActiveCrew`, which automatic reassignment makes unnecessary.
         */
        data object OwnerReassignFailed : Deletion
    }
}
