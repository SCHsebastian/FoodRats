package es.schsebastian.foodrats.feature.notifications.domain.error

sealed interface NotificationError {
    sealed interface Permission : NotificationError {
        data object Denied : Permission
        data object DeniedForever : Permission
    }
    sealed interface Token : NotificationError {
        data object Unavailable : Token
        data object NotSignedIn : Token
    }
}
