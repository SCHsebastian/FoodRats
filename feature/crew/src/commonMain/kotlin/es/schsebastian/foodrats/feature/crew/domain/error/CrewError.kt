package es.schsebastian.foodrats.feature.crew.domain.error

sealed interface CrewError {

    sealed interface Validation : CrewError {
        data object NameBlank : Validation
        data object NameTooLong : Validation       // > 40 chars
        data object CodeMalformed : Validation     // doesn't match [A-HJ-NP-Z2-9]{6}
        data object DisplayNameBlank : Validation
        data object DisplayNameTooLong : Validation // > 40 chars
        data object TaglineTooLong : Validation    // > 120 chars
        data object WelcomeMessageTooLong : Validation // > 200 chars
        data object WeeklyChallengeTooLong : Validation // > 80 chars
    }

    sealed interface Authorization : CrewError {
        data object NotOwner : Authorization
    }

    /**
     * The signed-in session could not be resolved when a crew mutation required it. Distinct from
     * [Backend] — these route the user to re-authenticate, not to "retry the backend". Produced by
     * mapping [es.schsebastian.foodrats.core.domain.session.SessionError] via `toCrewError()`.
     */
    sealed interface Session : CrewError {
        data object NotSignedIn : Session          // no active session — the root navigator routes to sign-in
        data object Expired : Session              // token expired / account disabled — re-auth required
    }

    sealed interface Membership : CrewError {
        data object NotFound : Membership          // crew doc doesn't exist
        data object Full : Membership              // size == 8
        data object NotInvited : Membership        // tried to access a crew you're not in
        data object AlreadyMember : Membership
        data object NotMember : Membership         // tried to leave a crew you're not in
    }

    sealed interface Invite : CrewError {
        data object CodeUnknown : Invite           // crewCodes/{code} doesn't exist
        data object Expired : Invite               // reserved for future; not used in MVP
        data object AlreadyRequested : Invite      // a pending join request already exists for this crew
    }

    sealed interface Create : CrewError {
        data object CodeCollisionRetriesExhausted : Create
    }

    sealed interface Backend : CrewError {
        data object Network : Backend
        data object PermissionDenied : Backend
        data object Unavailable : Backend          // transient — the outbox retries (network/aborted/deadline/…)
        data object Unknown : Backend              // unclassifiable backend failure — terminal, NOT retried forever
    }

    sealed interface RemoveMember : CrewError {
        data object NotOwner : RemoveMember        // only the crew owner may remove a member
        data object CannotRemoveSelf : RemoveMember // the owner cannot remove themselves (leaving is a separate flow)
        data object MemberNotFound : RemoveMember  // the target is not a member of this crew
    }

    sealed interface Transfer : CrewError {
        data object NotOwner : Transfer            // only the crew owner may transfer ownership
        data object TargetNotMember : Transfer     // can only hand ownership to a current member
        data object CannotTransferToSelf : Transfer // you are already the owner — a no-op hand-off
    }

    sealed interface Banner : CrewError {
        data object UploadFailed : Banner          // storage write failed (network / permission)
        data object DeleteFailed : Banner          // storage delete failed (network / permission)
    }
}
