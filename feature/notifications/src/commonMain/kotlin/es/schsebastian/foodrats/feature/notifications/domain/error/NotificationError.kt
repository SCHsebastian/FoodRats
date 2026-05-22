package es.schsebastian.foodrats.feature.notifications.domain.error

sealed interface NotificationError {
    sealed interface Permission : NotificationError {
        data object Denied : Permission
        data object DeniedForever : Permission
        data object Unavailable : Permission
    }
    sealed interface Token : NotificationError {
        data object Unavailable : Token
        data object PersistFailed : Token
    }
    sealed interface Schedule : NotificationError {
        data object Failed : Schedule
        data object HasPostedCheckFailed : Schedule
    }
    sealed interface PayloadParse : NotificationError {
        data object MissingKey : PayloadParse
        data object UnknownKind : PayloadParse
        data object MalformedFields : PayloadParse
    }
}
