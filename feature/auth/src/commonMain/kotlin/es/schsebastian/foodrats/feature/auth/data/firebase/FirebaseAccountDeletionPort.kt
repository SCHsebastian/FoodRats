package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import es.schsebastian.foodrats.core.domain.account.AccountDeletionError
import es.schsebastian.foodrats.core.domain.account.AccountDeletionPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * [AccountDeletionPort] over the `deleteAccount` callable Cloud Function (region `europe-west3`).
 *
 * Replaces `StubAccountDeletionPort`. Invokes the server cascade **synchronously** and returns
 * [Result.success] only when the function reports completion — there is no "pending deletion"
 * marker. The Admin-SDK cascade deletes every trace of the caller (authored meals + plates +
 * comments/ratings across crews, this account's votes/comments on others' meals, crew memberships
 * with automatic owner reassignment, the `accounts/{uid}` doc + FCM tokens, the avatar blob, and
 * the Firebase Auth user record). See `docs/specs/2026-06-14-account-deletion-design.md` §4.2/§5.
 *
 * The request carries **no** [accountId] — the function derives the caller's uid from
 * `request.auth.uid`; a client may only delete itself. [confirmation] is forwarded so the server
 * re-validates the phrase (defense in depth — the client also gates it).
 *
 * Mirrors `core/data/.../image/FirebaseImageUrlResolver.kt`: same `Firebase.functions(region)
 * .httpsCallable(NAME).invoke(req).data<Resp>()` call, same single [DispatcherProvider.io]
 * boundary, same `runCatching { … }.fold(…)` error mapping by inspecting the [Throwable.message]
 * for the `HttpsError` code.
 */
class FirebaseAccountDeletionPort(
    private val dispatchers: DispatcherProvider,
    private val region: String = "europe-west3",
) : AccountDeletionPort {

    private val functions by lazy { Firebase.functions(region) }

    override suspend fun requestDeletion(
        @Suppress("UNUSED_PARAMETER") // required by AccountDeletionPort; server derives uid from request.auth.uid (see KDoc)
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError> = withContext(dispatchers.io) {
        runCatching {
            functions.httpsCallable(CALLABLE)
                .invoke(DeleteAccountRequest(confirmation = confirmation))
                .data<DeleteAccountResponse>()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t ->
                FrLog.w("AccountDeletion", t) { "deleteAccount failed: ${t.message}" }
                Result.failure(t.toAccountDeletionError())
            },
        )
    }

    private companion object {
        const val CALLABLE = "deleteAccount"
    }
}

/**
 * Maps the `HttpsError` code (string-matched on [Throwable.message], like
 * `FirebaseImageUrlResolver.toImageUrlError()`) to the typed error tree:
 * `failed-precondition → Validation.PhraseMismatch`, `aborted → Deletion.OwnerReassignFailed`,
 * `unauthenticated`/`internal`/anything else → `Backend.Unavailable` (retryable; session still valid).
 *
 * `internal` (not `private`) so `FirebaseAccountDeletionPortTest` can exercise the mapping
 * directly, mirroring `AuthFault.toAuthFault()` / `AuthFaultTest` — `requestDeletion` itself
 * constructs a live `Firebase.functions(region)` and is not unit-testable.
 */
internal fun Throwable.toAccountDeletionError(): AccountDeletionError {
    val msg = message?.lowercase() ?: ""
    return when {
        "failed-precondition" in msg || "failed_precondition" in msg ->
            AccountDeletionError.Validation.PhraseMismatch
        "aborted" in msg -> AccountDeletionError.Deletion.OwnerReassignFailed
        else -> AccountDeletionError.Backend.Unavailable
    }
}

@Serializable
private data class DeleteAccountRequest(val confirmation: String)

@Serializable
private data class DeleteAccountResponse(val deleted: Boolean = true)
