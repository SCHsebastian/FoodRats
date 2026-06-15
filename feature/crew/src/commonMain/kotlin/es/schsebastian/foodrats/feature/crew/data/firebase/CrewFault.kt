package es.schsebastian.foodrats.feature.crew.data.firebase

/**
 * Typed classification of a raw backend (Firebase) failure for the crew context.
 *
 * GitLive Firebase does not expose typed `FirebaseException` subtypes uniformly across
 * Android + iOS, so the *only* place a raw throwable is inspected by message-substring
 * is [toCrewFault] below. [CrewErrorMapper] then maps **by fault type**, never by message
 * — so a Firebase SDK message wording change, a locale change, or the eventual
 * Firebase→own-server swap touches exactly one function instead of the mapper.
 *
 * Data-layer-private: this type never leaves `data/firebase/`; the domain stays
 * vendor-free.
 */
internal sealed interface CrewFault {
    /** PERMISSION_DENIED — a security rule rejected the operation. */
    data object PermissionDenied : CrewFault
    /** UNAVAILABLE / network unreachable — transient connectivity. */
    data object Network : CrewFault
    /** NOT_FOUND — the target crew document does not exist. */
    data object NotFound : CrewFault
    /** Anything we cannot confidently bucket. */
    data object Unavailable : CrewFault
}

/**
 * The single seam that turns a raw backend throwable into a typed [CrewFault].
 *
 * This is the *only* substring-matching site in the crew data layer. Order preserves the
 * original mapper's precedence: permission is checked before the generic connectivity
 * bucket, and not-found before the final catch-all.
 */
internal fun Throwable.toCrewFault(): CrewFault {
    val m = message.orEmpty().lowercase()
    return when {
        "permission" in m || "permission_denied" in m -> CrewFault.PermissionDenied
        "network" in m || "unavailable" in m -> CrewFault.Network
        "not found" in m || "no document" in m -> CrewFault.NotFound
        else -> CrewFault.Unavailable
    }
}
