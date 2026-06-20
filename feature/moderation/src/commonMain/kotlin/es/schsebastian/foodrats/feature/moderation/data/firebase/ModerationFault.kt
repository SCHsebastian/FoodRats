package es.schsebastian.foodrats.feature.moderation.data.firebase

/**
 * Typed classification of a raw backend (Firebase) failure for the moderation context.
 *
 * GitLive Firebase does not expose typed `FirebaseException` subtypes uniformly across
 * Android + iOS, so the *only* place a raw throwable is inspected by message-substring
 * is [toModerationFault] below. The [BlockErrorMapper] / [ReportErrorMapper] then map
 * **by fault type**, never by message — so a Firebase SDK message wording change, a locale
 * change, or the eventual Firebase→own-server swap touches exactly one function instead of
 * the mappers.
 *
 * Data-layer-private: this type never leaves `data/firebase/`; the domain stays vendor-free.
 */
internal sealed interface ModerationFault {
    /** PERMISSION_DENIED — a security rule rejected the operation. */
    data object PermissionDenied : ModerationFault
    /** UNAVAILABLE / network unreachable — transient connectivity. */
    data object Network : ModerationFault
    /** ALREADY_EXISTS — the deterministic report doc already exists (idempotent create denied). */
    data object AlreadyExists : ModerationFault
    /** Anything we cannot confidently bucket. */
    data object Unavailable : ModerationFault
}

/**
 * The single seam that turns a raw backend throwable into a typed [ModerationFault].
 *
 * This is the *only* substring-matching site in the moderation data layer. Order preserves a
 * sensible precedence: a deterministic-id collision (already-exists) is checked first since it
 * is the meaningful "already reported" signal, then permission, then the generic connectivity
 * bucket, then the final catch-all.
 */
internal fun Throwable.toModerationFault(): ModerationFault {
    val m = message.orEmpty().lowercase()
    return when {
        "already exists" in m || "already_exists" in m -> ModerationFault.AlreadyExists
        "permission" in m || "permission_denied" in m -> ModerationFault.PermissionDenied
        "network" in m || "unavailable" in m -> ModerationFault.Network
        else -> ModerationFault.Unavailable
    }
}
