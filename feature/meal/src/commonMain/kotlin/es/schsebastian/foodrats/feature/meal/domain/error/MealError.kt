package es.schsebastian.foodrats.feature.meal.domain.error

sealed interface MealError {
    sealed interface Validation : MealError {
        data object Blank : Validation
        data object TooLong : Validation
        data object OutOfRange : Validation
        data object NoPhoto : Validation
        data object DescriptionTooLong : Validation
    }
    sealed interface Publish : MealError {
        data object AlreadyPostedToday : Publish
        data object NotToday : Publish
        data object NoSlotSelected : Publish
        data object PublishUnavailable : Publish
        data object PhotoUploadFailed : Publish
    }
    sealed interface Location : MealError {
        data object PermissionDenied : Location
        data object Unavailable : Location
        data object Timeout : Location
    }
    sealed interface Read : MealError {
        data object Unauthorized : Read
        data object CrewNotFound : Read
        data object NotFound : Read
    }
}
