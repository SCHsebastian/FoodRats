package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.feature.crew.domain.error.CrewError

class CrewErrorMapper {
    /**
     * Map an arbitrary backend [Throwable] to a typed CrewError. We bucket by message-substring
     * because GitLive Firebase doesn't expose typed FirebaseException subtypes uniformly across
     * Android + iOS. Substrings come from real Firestore SDK error messages on each platform.
     */
    fun map(t: Throwable): CrewError {
        val msg = (t.message.orEmpty()).lowercase()
        return when {
            "permission" in msg || "permission_denied" in msg -> CrewError.Backend.PermissionDenied
            "network"    in msg || "unavailable"        in msg -> CrewError.Backend.Network
            "not found"  in msg || "no document"        in msg -> CrewError.Membership.NotFound
            else                                                 -> CrewError.Backend.Unavailable
        }
    }
}
