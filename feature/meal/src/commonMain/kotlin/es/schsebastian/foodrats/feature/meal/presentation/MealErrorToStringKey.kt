package es.schsebastian.foodrats.feature.meal.presentation

import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

fun MealError.toStringKey(): MealStringKey = when (this) {
    MealError.Publish.AlreadyPostedToday  -> MealStringKey.MealErrorAlreadyPosted
    MealError.Publish.NotToday            -> MealStringKey.MealErrorNotToday
    MealError.Publish.PublishUnavailable  -> MealStringKey.MealErrorPublishUnavailable
    MealError.Publish.PhotoUploadFailed   -> MealStringKey.MealErrorPhotoUploadFailed
    MealError.Validation.Blank            -> MealStringKey.MealErrorValidationBlank
    MealError.Validation.TooLong          -> MealStringKey.MealErrorValidationTooLong
    MealError.Validation.OutOfRange       -> MealStringKey.MealErrorValidationOutOfRange
    MealError.Validation.NoPhoto          -> MealStringKey.MealErrorValidationNoPhoto
    MealError.Read.Unauthorized           -> MealStringKey.MealErrorReadUnauthorized
    MealError.Read.CrewNotFound           -> MealStringKey.MealErrorReadCrewNotFound
    MealError.Read.NotFound               -> MealStringKey.MealErrorReadNotFound
}
