package es.schsebastian.foodrats.feature.meal.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.meal.generated.resources.Res
import foodrats.feature.meal.generated.resources.meal_capture_no_perm_headline
import foodrats.feature.meal.generated.resources.meal_capture_no_perm_subtext
import foodrats.feature.meal.generated.resources.meal_capture_tap_to_take_photo
import foodrats.feature.meal.generated.resources.meal_capture_title
import foodrats.feature.meal.generated.resources.meal_compose_title
import foodrats.feature.meal.generated.resources.meal_error_already_posted
import foodrats.feature.meal.generated.resources.meal_error_not_today
import foodrats.feature.meal.generated.resources.meal_error_photo_upload_failed
import foodrats.feature.meal.generated.resources.meal_error_publish_unavailable
import foodrats.feature.meal.generated.resources.meal_error_read_crew_not_found
import foodrats.feature.meal.generated.resources.meal_error_read_not_found
import foodrats.feature.meal.generated.resources.meal_error_read_unauthorized
import foodrats.feature.meal.generated.resources.meal_error_validation_blank
import foodrats.feature.meal.generated.resources.meal_error_validation_no_photo
import foodrats.feature.meal.generated.resources.meal_error_validation_out_of_range
import foodrats.feature.meal.generated.resources.meal_error_validation_too_long
import foodrats.feature.meal.generated.resources.meal_publish_no_slot
import foodrats.feature.meal.generated.resources.meal_publish_title
import foodrats.feature.meal.generated.resources.meal_slot_breakfast
import foodrats.feature.meal.generated.resources.meal_slot_dinner
import foodrats.feature.meal.generated.resources.meal_slot_lunch
import org.jetbrains.compose.resources.StringResource

enum class MealStringKey(override val resourceId: StringResource) : StringKey {
    CaptureTitle(Res.string.meal_capture_title),
    CaptureTapToTakePhoto(Res.string.meal_capture_tap_to_take_photo),
    CaptureNoPermissionHeadline(Res.string.meal_capture_no_perm_headline),
    CaptureNoPermissionSubtext(Res.string.meal_capture_no_perm_subtext),
    ComposeTitle(Res.string.meal_compose_title),
    PublishTitle(Res.string.meal_publish_title),
    SlotBreakfast(Res.string.meal_slot_breakfast),
    SlotLunch(Res.string.meal_slot_lunch),
    SlotDinner(Res.string.meal_slot_dinner),
    MealErrorAlreadyPosted(Res.string.meal_error_already_posted),
    MealErrorNotToday(Res.string.meal_error_not_today),
    MealErrorPublishUnavailable(Res.string.meal_error_publish_unavailable),
    MealErrorPublishNoSlotSelected(Res.string.meal_publish_no_slot),
    MealErrorPhotoUploadFailed(Res.string.meal_error_photo_upload_failed),
    MealErrorValidationBlank(Res.string.meal_error_validation_blank),
    MealErrorValidationTooLong(Res.string.meal_error_validation_too_long),
    MealErrorValidationOutOfRange(Res.string.meal_error_validation_out_of_range),
    MealErrorValidationNoPhoto(Res.string.meal_error_validation_no_photo),
    MealErrorReadUnauthorized(Res.string.meal_error_read_unauthorized),
    MealErrorReadCrewNotFound(Res.string.meal_error_read_crew_not_found),
    MealErrorReadNotFound(Res.string.meal_error_read_not_found),
}
