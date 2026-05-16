package es.schsebastian.foodrats.feature.crew.domain.error

sealed interface CrewError {

    sealed interface Validation : CrewError {
        data object NameBlank : Validation
        data object NameTooLong : Validation       // > 40 chars
        data object CodeMalformed : Validation     // doesn't match [A-HJ-NP-Z2-9]{6}
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
    }

    sealed interface Create : CrewError {
        data object CodeCollisionRetriesExhausted : Create
    }

    sealed interface Backend : CrewError {
        data object Network : Backend
        data object PermissionDenied : Backend
        data object Unavailable : Backend
    }
}
