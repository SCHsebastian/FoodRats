package es.schsebastian.foodrats.core.domain.meal

/**
 * One photo within a [Meal]'s ordered [Meal.plates] list (up to
 * [MealPublishPolicy.MAX_PHOTOS_PER_MEAL]). Each photo carries its OWN [PlateSource] — a meal can
 * mix a live camera shot with gallery picks in the same ordered list, unlike the legacy
 * single-photo [Meal.plateSource] which describes only the one (first) photo.
 */
data class MealPlate(
    val photoUrl: String,
    val source: PlateSource = PlateSource.Camera,
)
