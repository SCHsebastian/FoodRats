package es.schsebastian.foodrats.feature.meal.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.meal.generated.resources.Res
import foodrats.feature.meal.generated.resources.meal_classifier_load_failed
import foodrats.feature.meal.generated.resources.meal_classifier_no_detection
import foodrats.feature.meal.generated.resources.meal_compose_add_location
import foodrats.feature.meal.generated.resources.meal_compose_clear_location
import foodrats.feature.meal.generated.resources.meal_compose_coordinates_format
import foodrats.feature.meal.generated.resources.meal_compose_description_counter
import foodrats.feature.meal.generated.resources.meal_compose_description_placeholder
import foodrats.feature.meal.generated.resources.meal_compose_dish_label
import foodrats.feature.meal.generated.resources.meal_compose_locating
import foodrats.feature.meal.generated.resources.meal_compose_title
import foodrats.feature.meal.generated.resources.meal_error_already_posted
import foodrats.feature.meal.generated.resources.meal_error_location_permission
import foodrats.feature.meal.generated.resources.meal_error_location_timeout
import foodrats.feature.meal.generated.resources.meal_error_location_unavailable
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
import foodrats.feature.meal.generated.resources.meal_ingredients_classifying
import foodrats.feature.meal.generated.resources.meal_ingredients_row_add
import foodrats.feature.meal.generated.resources.meal_ingredients_row_summary
import foodrats.feature.meal.generated.resources.meal_publish_confirm_cta
import foodrats.feature.meal.generated.resources.meal_publish_confirm_message
import foodrats.feature.meal.generated.resources.meal_publish_confirm_title
import foodrats.feature.meal.generated.resources.meal_publish_no_slot
import foodrats.feature.meal.generated.resources.meal_publish_title
import foodrats.feature.meal.generated.resources.meal_slot_breakfast
import foodrats.feature.meal.generated.resources.meal_slot_dinner
import foodrats.feature.meal.generated.resources.meal_slot_lunch
import org.jetbrains.compose.resources.StringResource

enum class MealStringKey(override val resourceId: StringResource) : StringKey {
    ComposeTitle(Res.string.meal_compose_title),
    ComposeDishLabel(Res.string.meal_compose_dish_label),
    ComposeDescriptionPlaceholder(Res.string.meal_compose_description_placeholder),
    ComposeDescriptionCounter(Res.string.meal_compose_description_counter),
    ComposeAddLocation(Res.string.meal_compose_add_location),
    ComposeLocating(Res.string.meal_compose_locating),
    ComposeClearLocation(Res.string.meal_compose_clear_location),
    ComposeCoordinatesFormat(Res.string.meal_compose_coordinates_format),
    PublishTitle(Res.string.meal_publish_title),
    PublishConfirmTitle(Res.string.meal_publish_confirm_title),
    PublishConfirmMessage(Res.string.meal_publish_confirm_message),
    PublishConfirmCta(Res.string.meal_publish_confirm_cta),
    SlotBreakfast(Res.string.meal_slot_breakfast),
    SlotLunch(Res.string.meal_slot_lunch),
    SlotDinner(Res.string.meal_slot_dinner),
    IngredientsClassifying(Res.string.meal_ingredients_classifying),
    ClassifierBannerNoDetection(Res.string.meal_classifier_no_detection),
    ClassifierBannerLoadFailed(Res.string.meal_classifier_load_failed),
    IngredientsRowAdd(Res.string.meal_ingredients_row_add),
    IngredientsRowSummary(Res.string.meal_ingredients_row_summary),
    MealErrorAlreadyPosted(Res.string.meal_error_already_posted),
    MealErrorNotToday(Res.string.meal_error_not_today),
    MealErrorPublishUnavailable(Res.string.meal_error_publish_unavailable),
    MealErrorPublishNoSlotSelected(Res.string.meal_publish_no_slot),
    MealErrorPhotoUploadFailed(Res.string.meal_error_photo_upload_failed),
    MealErrorValidationBlank(Res.string.meal_error_validation_blank),
    MealErrorValidationTooLong(Res.string.meal_error_validation_too_long),
    MealErrorValidationDescriptionTooLong(Res.string.meal_error_validation_description_too_long),
    MealErrorValidationTooManyIngredients(Res.string.meal_error_validation_too_many_ingredients),
    MealErrorValidationOutOfRange(Res.string.meal_error_validation_out_of_range),
    MealErrorValidationNoPhoto(Res.string.meal_error_validation_no_photo),
    MealErrorReadUnauthorized(Res.string.meal_error_read_unauthorized),
    MealErrorReadCrewNotFound(Res.string.meal_error_read_crew_not_found),
    MealErrorReadNotFound(Res.string.meal_error_read_not_found),
    MealErrorLocationPermission(Res.string.meal_error_location_permission),
    MealErrorLocationUnavailable(Res.string.meal_error_location_unavailable),
    MealErrorLocationTimeout(Res.string.meal_error_location_timeout),
}
