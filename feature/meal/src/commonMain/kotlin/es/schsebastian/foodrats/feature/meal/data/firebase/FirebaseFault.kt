package es.schsebastian.foodrats.feature.meal.data.firebase

/**
 * Typed classification of a raw backend (Firebase) failure for the meal context.
 *
 * GitLive Firebase does not expose typed `FirebaseException` subtypes uniformly across
 * Android + iOS, so the *only* place a raw throwable is inspected by message-substring
 * is [toFirebaseFault] below. Everything downstream (`MealErrorMapper`, the repository
 * `when`s) maps **by fault type**, never by message — so a Firebase SDK message wording
 * change, a locale change, or the eventual Firebase→own-server swap touches exactly one
 * function instead of every mapper.
 *
 * Data-layer-private: this type never leaves `data/firebase/`; the domain stays
 * vendor-free.
 */
internal sealed interface FirebaseFault {
    /** PERMISSION_DENIED — a security rule rejected the operation. */
    data object PermissionDenied : FirebaseFault
    /** UNAUTHENTICATED — no/expired auth token. */
    data object Unauthenticated : FirebaseFault
    /** UNAVAILABLE / network unreachable / no route — transient connectivity. */
    data object Unavailable : FirebaseFault
    /** ALREADY_EXISTS — a uniqueness constraint was violated. */
    data object AlreadyExists : FirebaseFault
    /** NOT_FOUND — the target document does not exist. */
    data object NotFound : FirebaseFault
    /** Storage upload failed (Firebase Storage put/getDownloadUrl path). */
    data object StorageFailure : FirebaseFault
    /** Anything we cannot confidently bucket. */
    data class Unknown(val cause: Throwable) : FirebaseFault
}

/**
 * The single seam that turns a raw backend throwable into a typed [FirebaseFault].
 *
 * This is the *only* substring-matching site in the meal data layer. Order matters:
 * the more-specific codes (already-exists, unauthenticated) are checked before the
 * generic connectivity bucket so an `UNAVAILABLE`-shaped message can't shadow them.
 */
internal fun Throwable.toFirebaseFault(): FirebaseFault {
    val m = message.orEmpty().lowercase()
    return when {
        "already-exists" in m || "already_exists" in m -> FirebaseFault.AlreadyExists
        "unauthenticated" in m -> FirebaseFault.Unauthenticated
        "permission" in m || "permission_denied" in m || "permission-denied" in m ->
            FirebaseFault.PermissionDenied
        "storage" in m || "upload" in m -> FirebaseFault.StorageFailure
        "unavailable" in m || "unreachable" in m || "no route" in m || "network" in m ->
            FirebaseFault.Unavailable
        "not-found" in m || "not found" in m -> FirebaseFault.NotFound
        else -> FirebaseFault.Unknown(this)
    }
}
