package es.schsebastian.foodrats.feature.auth.data.firebase

/**
 * Typed classification of a raw Firestore/Storage write failure, mirroring [AuthFault].
 *
 * GitLive surfaces backend errors as platform exceptions whose message carries the code, so the
 * *only* place a raw throwable is inspected by message-substring is [toAccountWriteFault] below.
 * `FirestoreAccountWriter` then maps **by fault type**, never by message — so an SDK wording change
 * or the eventual Firebase→own-server swap touches exactly one function. Data-layer-private: never
 * leaves `data/firebase/`, keeping the domain vendor-free.
 */
internal sealed interface AccountWriteFault {
    /** Security rules rejected the write (`permission-denied`). Terminal — the doc shape is wrong. */
    data object PermissionDenied : AccountWriteFault
    /** Auth token missing/expired (`unauthenticated`). Route to re-auth. */
    data object Unauthenticated : AccountWriteFault
    /** Connectivity/transport failure reaching the backend (`unavailable` / network / deadline). */
    data object Network : AccountWriteFault
    /** Anything we cannot confidently bucket — recorded as a non-fatal by the mapper. */
    data class Unknown(val cause: Throwable) : AccountWriteFault
}

/**
 * The single seam that turns a raw Firestore/Storage throwable into a typed [AccountWriteFault].
 * Order matters: the specific codes are checked before the generic network bucket so a
 * `permission-denied`/`unauthenticated` doesn't fall through to a retryable classification.
 */
internal fun Throwable.toAccountWriteFault(): AccountWriteFault {
    val m = message.orEmpty().lowercase()
    return when {
        "permission-denied" in m || "permission_denied" in m || "permission denied" in m ->
            AccountWriteFault.PermissionDenied
        "unauthenticated" in m || "unauthorized" in m || "token" in m ->
            AccountWriteFault.Unauthenticated
        "unavailable" in m || "network" in m || "offline" in m || "deadline" in m ||
            "timeout" in m || "host" in m ->
            AccountWriteFault.Network
        else -> AccountWriteFault.Unknown(this)
    }
}
