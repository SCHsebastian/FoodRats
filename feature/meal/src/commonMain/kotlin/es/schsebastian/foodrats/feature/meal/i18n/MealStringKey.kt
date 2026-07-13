package es.schsebastian.foodrats.feature.meal.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.meal.generated.resources.Res
import foodrats.feature.meal.generated.resources.meal_capture_choose_gallery
import foodrats.feature.meal.generated.resources.meal_capture_draft_failed
import foodrats.feature.meal.generated.resources.meal_capture_failed_title
import foodrats.feature.meal.generated.resources.meal_capture_fallback_title
import foodrats.feature.meal.generated.resources.meal_capture_no_crews
import foodrats.feature.meal.generated.resources.meal_capture_photo_failed
import foodrats.feature.meal.generated.resources.meal_capture_retry_camera
import foodrats.feature.meal.generated.resources.meal_capture_saving_plate
import foodrats.feature.meal.generated.resources.meal_capture_session_error
import foodrats.feature.meal.generated.resources.meal_compose_gallery_chip
import foodrats.feature.meal.generated.resources.meal_compose_gallery_chip_a11y
import foodrats.feature.meal.generated.resources.meal_classifier_load_failed
import foodrats.feature.meal.generated.resources.meal_classifier_no_detection
import foodrats.feature.meal.generated.resources.meal_compose_add_location
import foodrats.feature.meal.generated.resources.meal_compose_all_slots_taken
import foodrats.feature.meal.generated.resources.meal_compose_audience_all
import foodrats.feature.meal.generated.resources.meal_compose_audience_label
import foodrats.feature.meal.generated.resources.meal_compose_clear_location
import foodrats.feature.meal.generated.resources.meal_compose_coordinates_format
import foodrats.feature.meal.generated.resources.meal_compose_counter_a11y
import foodrats.feature.meal.generated.resources.meal_compose_description_counter
import foodrats.feature.meal.generated.resources.meal_compose_description_label
import foodrats.feature.meal.generated.resources.meal_compose_dish_counter
import foodrats.feature.meal.generated.resources.meal_compose_dish_label
import foodrats.feature.meal.generated.resources.meal_compose_eyebrow
import foodrats.feature.meal.generated.resources.meal_compose_locating
import foodrats.feature.meal.generated.resources.meal_compose_photo_description
import foodrats.feature.meal.generated.resources.meal_compose_slot_label
import foodrats.feature.meal.generated.resources.meal_compose_slot_optional
import foodrats.feature.meal.generated.resources.meal_description_moderation_warning
import foodrats.feature.meal.generated.resources.meal_dish_moderation_warning
import foodrats.feature.meal.generated.resources.meal_compose_title
import foodrats.feature.meal.generated.resources.meal_error_already_posted
import foodrats.feature.meal.generated.resources.meal_error_location_permission
import foodrats.feature.meal.generated.resources.meal_error_location_timeout
import foodrats.feature.meal.generated.resources.meal_error_location_unavailable
import foodrats.feature.meal.generated.resources.meal_error_no_crew_selected
import foodrats.feature.meal.generated.resources.meal_error_not_today
import foodrats.feature.meal.generated.resources.meal_error_photo_upload_failed
import foodrats.feature.meal.generated.resources.meal_error_publish_unavailable
import foodrats.feature.meal.generated.resources.meal_error_read_crew_not_found
import foodrats.feature.meal.generated.resources.meal_error_read_not_found
import foodrats.feature.meal.generated.resources.meal_error_read_unauthorized
import foodrats.feature.meal.generated.resources.meal_error_validation_blank
import foodrats.feature.meal.generated.resources.meal_error_validation_description_too_long
import foodrats.feature.meal.generated.resources.meal_error_validation_no_photo
import foodrats.feature.meal.generated.resources.meal_error_validation_out_of_range
import foodrats.feature.meal.generated.resources.meal_error_validation_too_long
import foodrats.feature.meal.generated.resources.meal_error_validation_too_many_ingredients
import foodrats.feature.meal.generated.resources.meal_error_validation_too_many_photos
import foodrats.feature.meal.generated.resources.meal_ingredients_classifying
import foodrats.feature.meal.generated.resources.meal_ingredients_row_add
import foodrats.feature.meal.generated.resources.meal_ingredients_row_summary
import foodrats.feature.meal.generated.resources.meal_publish_confirm_cta
import foodrats.feature.meal.generated.resources.meal_publish_confirm_message
import foodrats.feature.meal.generated.resources.meal_publish_confirm_title
import foodrats.feature.meal.generated.resources.meal_publish_no_slot
import foodrats.feature.meal.generated.resources.meal_slot_breakfast
import foodrats.feature.meal.generated.resources.meal_slot_brunch
import foodrats.feature.meal.generated.resources.meal_slot_dinner
import foodrats.feature.meal.generated.resources.meal_slot_lunch
import foodrats.feature.meal.generated.resources.meal_slot_merienda
import foodrats.feature.meal.generated.resources.meal_slot_snack
import org.jetbrains.compose.resources.StringResource

enum class MealStringKey(override val resourceId: StringResource) : StringKey {
    ComposeEyebrow(Res.string.meal_compose_eyebrow),
    ComposeTitle(Res.string.meal_compose_title),
    ComposeSlotLabel(Res.string.meal_compose_slot_label),
    ComposeSlotOptional(Res.string.meal_compose_slot_optional),
    ComposeDishLabel(Res.string.meal_compose_dish_label),
    ComposeDescriptionLabel(Res.string.meal_compose_description_label),
    ComposeDescriptionCounter(Res.string.meal_compose_description_counter),
    ComposeDishCounter(Res.string.meal_compose_dish_counter),
    ComposeCounterA11y(Res.string.meal_compose_counter_a11y),
    ComposePhotoDescription(Res.string.meal_compose_photo_description),
    ComposeAddLocation(Res.string.meal_compose_add_location),
    ComposeAllSlotsTaken(Res.string.meal_compose_all_slots_taken),
    ComposeAudienceLabel(Res.string.meal_compose_audience_label),
    ComposeAudienceAll(Res.string.meal_compose_audience_all),
    ComposeLocating(Res.string.meal_compose_locating),
    ComposeClearLocation(Res.string.meal_compose_clear_location),
    ComposeCoordinatesFormat(Res.string.meal_compose_coordinates_format),
    PublishConfirmTitle(Res.string.meal_publish_confirm_title),
    PublishConfirmMessage(Res.string.meal_publish_confirm_message),
    PublishConfirmCta(Res.string.meal_publish_confirm_cta),
    SlotBreakfast(Res.string.meal_slot_breakfast),
    SlotBrunch(Res.string.meal_slot_brunch),
    SlotLunch(Res.string.meal_slot_lunch),
    SlotSnack(Res.string.meal_slot_snack),
    SlotMerienda(Res.string.meal_slot_merienda),
    SlotDinner(Res.string.meal_slot_dinner),
    IngredientsClassifying(Res.string.meal_ingredients_classifying),
    ClassifierBannerNoDetection(Res.string.meal_classifier_no_detection),
    ClassifierBannerLoadFailed(Res.string.meal_classifier_load_failed),
    IngredientsRowAdd(Res.string.meal_ingredients_row_add),
    IngredientsRowSummary(Res.string.meal_ingredients_row_summary),
    // UGC compliance §3 — HARD-BLOCK moderation banners for dish and description.
    DescriptionModerationWarning(Res.string.meal_description_moderation_warning),
    DishModerationWarning(Res.string.meal_dish_moderation_warning),
    MealErrorAlreadyPosted(Res.string.meal_error_already_posted),
    MealErrorPublishNoCrewSelected(Res.string.meal_error_no_crew_selected),
    MealErrorNotToday(Res.string.meal_error_not_today),
    MealErrorPublishUnavailable(Res.string.meal_error_publish_unavailable),
    MealErrorPublishNoSlotSelected(Res.string.meal_publish_no_slot),
    MealErrorPhotoUploadFailed(Res.string.meal_error_photo_upload_failed),
    MealErrorValidationBlank(Res.string.meal_error_validation_blank),
    MealErrorValidationTooLong(Res.string.meal_error_validation_too_long),
    MealErrorValidationDescriptionTooLong(Res.string.meal_error_validation_description_too_long),
    MealErrorValidationTooManyIngredients(Res.string.meal_error_validation_too_many_ingredients),
    MealErrorValidationTooManyPhotos(Res.string.meal_error_validation_too_many_photos),
    MealErrorValidationOutOfRange(Res.string.meal_error_validation_out_of_range),
    MealErrorValidationNoPhoto(Res.string.meal_error_validation_no_photo),
    MealErrorReadUnauthorized(Res.string.meal_error_read_unauthorized),
    MealErrorReadCrewNotFound(Res.string.meal_error_read_crew_not_found),
    MealErrorReadNotFound(Res.string.meal_error_read_not_found),
    MealErrorLocationPermission(Res.string.meal_error_location_permission),
    MealErrorLocationUnavailable(Res.string.meal_error_location_unavailable),
    MealErrorLocationTimeout(Res.string.meal_error_location_timeout),
    CaptureSessionError(Res.string.meal_capture_session_error),
    CaptureNoCrews(Res.string.meal_capture_no_crews),
    CaptureDraftFailed(Res.string.meal_capture_draft_failed),
    CapturePhotoFailed(Res.string.meal_capture_photo_failed),
    CaptureSavingPlate(Res.string.meal_capture_saving_plate),
    // Camera-dismissed fallback: retry the camera or pick the plate from the photo library.
    CaptureFallbackTitle(Res.string.meal_capture_fallback_title),
    // Same chooser, but reached via PhotoPickResult.Failed — tells the user the pick FAILED
    // (rather than the generic "add your photo" of a plain camera dismiss).
    CaptureFailedTitle(Res.string.meal_capture_failed_title),
    CaptureRetryCamera(Res.string.meal_capture_retry_camera),
    CaptureChooseFromGallery(Res.string.meal_capture_choose_gallery),
    // Non-removable provenance marker on the composer preview for a gallery-sourced plate.
    ComposeGalleryChip(Res.string.meal_compose_gallery_chip),
    ComposeGalleryChipA11y(Res.string.meal_compose_gallery_chip_a11y),
}
