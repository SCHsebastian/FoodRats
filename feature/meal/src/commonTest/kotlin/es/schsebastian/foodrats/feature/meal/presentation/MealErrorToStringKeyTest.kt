package es.schsebastian.foodrats.feature.meal.presentation

import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MealErrorToStringKeyTest {
    @Test
    fun maps_AlreadyPostedToday_to_MealErrorAlreadyPosted() {
        assertEquals(MealStringKey.MealErrorAlreadyPosted, MealError.Publish.AlreadyPostedToday.toStringKey())
    }

    @Test
    fun maps_NotToday_to_MealErrorNotToday() {
        assertEquals(MealStringKey.MealErrorNotToday, MealError.Publish.NotToday.toStringKey())
    }

    @Test
    fun maps_NoSlotSelected_to_MealErrorPublishNoSlotSelected() {
        assertEquals(MealStringKey.MealErrorPublishNoSlotSelected, MealError.Publish.NoSlotSelected.toStringKey())
    }

    @Test fun maps_all_publish_errors() {
        assertEquals(MealStringKey.MealErrorAlreadyPosted, (MealError.Publish.AlreadyPostedToday as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorNotToday, (MealError.Publish.NotToday as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorPublishUnavailable, (MealError.Publish.PublishUnavailable as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorPhotoUploadFailed, (MealError.Publish.PhotoUploadFailed as MealError).toStringKey())
    }
    @Test fun maps_all_validation_errors() {
        assertEquals(MealStringKey.MealErrorValidationBlank, (MealError.Validation.Blank as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorValidationTooLong, (MealError.Validation.TooLong as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorValidationOutOfRange, (MealError.Validation.OutOfRange as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorValidationNoPhoto, (MealError.Validation.NoPhoto as MealError).toStringKey())
    }
    @Test fun maps_all_read_errors() {
        assertEquals(MealStringKey.MealErrorReadUnauthorized, (MealError.Read.Unauthorized as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorReadCrewNotFound, (MealError.Read.CrewNotFound as MealError).toStringKey())
        assertEquals(MealStringKey.MealErrorReadNotFound, (MealError.Read.NotFound as MealError).toStringKey())
    }
}
