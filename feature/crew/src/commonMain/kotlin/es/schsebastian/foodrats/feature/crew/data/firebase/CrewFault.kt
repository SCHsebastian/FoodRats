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
    /**
     * A transient backend failure that retrying CAN fix — Firestore transaction contention
     * (ABORTED / "too much contention"), DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, or a transient
     * INTERNAL. Distinct from [Network] only for clarity; both map to a retryable domain error.
     */
    data object Transient : CrewFault
    /**
     * A failure we cannot confidently classify as transient. Mapped to a TERMINAL domain error so
     * the outbox can't loop on it forever — the prior catch-all bucketed these into the retryable
     * `Unavailable`, which retried a genuinely-hopeless error indefinitely.
     */
    data object Unknown : CrewFault
}

/**
 * The single seam that turns a raw backend throwable into a typed [CrewFault].
 *
 * This is the *only* substring-matching site in the crew data layer. Order preserves the
 * original mapper's precedence: permission is checked before the generic connectivity bucket,
 * not-found before the transient bucket, and the transient codes before the final catch-all.
 */
internal fun Throwable.toCrewFault(): CrewFault {
    val m = message.orEmpty().lowercase()
    return when {
        "permission" in m -> CrewFault.PermissionDenied
        "network" in m || "unavailable" in m -> CrewFault.Network
        "not found" in m || "no document" in m -> CrewFault.NotFound
        "aborted" in m || "too much contention" in m || "deadline" in m ||
            "resource-exhausted" in m || "resource_exhausted" in m -> CrewFault.Transient
        else -> CrewFault.Unknown
    }
}
