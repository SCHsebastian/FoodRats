package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.feature.crew.domain.error.CrewError

class CrewErrorMapper {
    /**
     * Map an arbitrary backend [Throwable] to a typed [CrewError]. Raw throwables are first
     * classified into a typed [CrewFault] by [toCrewFault] (the single message-inspection
     * seam — see [CrewFault]); this mapper then translates **by fault type**, never by
     * message, so a Firebase SDK wording change touches only `toCrewFault`.
     */
    fun map(t: Throwable): CrewError = when (t.toCrewFault()) {
        CrewFault.PermissionDenied -> CrewError.Backend.PermissionDenied
        CrewFault.Network -> CrewError.Backend.Network
        CrewFault.NotFound -> CrewError.Membership.NotFound
        // Transient (contention / deadline / resource-exhausted) is retryable by the outbox.
        CrewFault.Transient -> CrewError.Backend.Unavailable
        // Unknown is TERMINAL — never retried forever.
        CrewFault.Unknown -> CrewError.Backend.Unknown
    }
}
