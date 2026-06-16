package es.schsebastian.foodrats.core.domain.image

/**
 * Why resolving storage paths to viewable URLs failed. Display is best-effort, so consumers
 * generally fall back to "no image" (initials / empty plate) on any of these rather than
 * surfacing an error — but the leaves are kept distinct for telemetry and future UX.
 */
sealed interface ImageUrlError {
    /** No live auth token — the signing callable requires a signed-in caller. */
    data object NotSignedIn : ImageUrlError

    /** The caller is not a member of the crew that owns the requested objects. */
    data object PermissionDenied : ImageUrlError

    /** Backend/network failure minting the URLs. */
    data object Unavailable : ImageUrlError
}
