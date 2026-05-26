package es.schsebastian.foodrats.feature.mealai.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.flatMap

data class ClassificationOutcome(val dishSlug: String, val slugs: List<IngredientSlug>)

/**
 * Classifies a plate photo into a dish, then maps that dish to its default
 * ingredient slugs. Thresholds on the top label's confidence and fails with a
 * typed [ClassifierError] when nothing usable comes back.
 */
class ClassifyPlateUseCase(
    private val classifier: MealClassifierPort,
    private val ingredients: IngredientReadPort,
) {
    companion object {
        const val MIN_CONFIDENCE = 0.30f
    }

    suspend operator fun invoke(jpeg: ByteArray): Result<ClassificationOutcome, ClassifierError> =
        classifier.classify(jpeg).flatMap { labels ->
            val top = labels.firstOrNull()
                ?: return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            if (top.confidence < MIN_CONFIDENCE) {
                return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            }
            val slugs = ingredients.suggestForDish(top.dishSlug)
            if (slugs.isEmpty()) Result.Err(ClassifierError.Run.DishUnmapped)
            else Result.Ok(ClassificationOutcome(top.dishSlug, slugs))
        }
}
