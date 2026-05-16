package es.schsebastian.foodrats.feature.meal.domain.error

sealed interface MealError {
    sealed interface Validation : MealError {
        data object Blank : Validation
        data object TooLong : Validation
        data object OutOfRange : Validation
        data object NoPhoto : Validation
    }
    sealed interface Publish : MealError {
        data object AlreadyPostedToday : Publish
        data object NotToday : Publish
        data object PublishUnavailable : Publish
        data object PhotoUploadFailed : Publish
    }
    sealed interface Read : MealError {
        data object Unauthorized : Read
        data object CrewNotFound : Read
        data object NotFound : Read
    }
}
