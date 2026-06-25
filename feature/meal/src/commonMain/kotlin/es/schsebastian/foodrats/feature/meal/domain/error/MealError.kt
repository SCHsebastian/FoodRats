package es.schsebastian.foodrats.feature.meal.domain.error

sealed interface MealError {
    sealed interface Validation : MealError {
        data object Blank : Validation
        data object TooLong : Validation
        data object OutOfRange : Validation
        data object NoPhoto : Validation
        data object DescriptionTooLong : Validation
        data object TooManyIngredients : Validation
    }
    sealed interface Publish : MealError {
        /**
         * The per-crew daily meal cap ([MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY]) is reached
         * in every selected crew. (Name retained for queue/coordinator stability; it once meant
         * "already posted this slot", but slots are no longer unique — the cap is now a flat count.)
         */
        data object AlreadyPostedToday : Publish
        data object NotToday : Publish
        /** DEPRECATED: slot is now optional, so this is never produced. Kept for `when`-exhaustiveness. */
        data object NoSlotSelected : Publish
        /** No crew is selected to share the plate with (the audience set is empty). */
        data object NoCrewSelected : Publish
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
