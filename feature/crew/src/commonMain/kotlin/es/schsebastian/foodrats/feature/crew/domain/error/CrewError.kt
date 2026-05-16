package es.schsebastian.foodrats.feature.crew.domain.error

sealed interface CrewError {
    sealed interface Membership : CrewError {
        data object NotFound : Membership
        data object Full : Membership
        data object NotInvited : Membership
    }
    sealed interface Invite : CrewError {
        data object Expired : Invite
        data object Unavailable : Invite
    }
}
