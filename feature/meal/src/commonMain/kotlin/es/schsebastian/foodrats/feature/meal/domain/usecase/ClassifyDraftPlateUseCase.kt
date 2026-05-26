package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.flatMap

/** The dish detected for a plate plus the default ingredient slugs it maps to. */
data class DraftClassification(
    val dishSlug: String,
    val ingredients: List<IngredientSlug>,
    val version: String,
)

/**
 * Classifies a captured plate photo into a dish, maps it to default ingredient
 * slugs, and tags the run with the model version.
 *
 * Lives in `:feature:meal` (not `:feature:meal-ai`) so the composer depends only
 * on the `:core:domain` ports [MealClassifierPort] + [IngredientReadPort] — never
 * on another feature (cross-feature ban; design spec §7.3/§9). The orchestration
 * mirrors `:feature:meal-ai`'s `ClassifyPlateUseCase`, which serves that module.
 */
class ClassifyDraftPlateUseCase(
    private val classifier: MealClassifierPort,
    private val ingredients: IngredientReadPort,
) {
    suspend operator fun invoke(jpeg: ByteArray): Result<DraftClassification, ClassifierError> =
        classifier.classify(jpeg).flatMap { labels ->
            val top = labels.firstOrNull()
                ?: return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            if (top.confidence < MIN_CONFIDENCE) {
                return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            }
            val slugs = ingredients.suggestForDish(top.dishSlug)
            if (slugs.isEmpty()) Result.Err(ClassifierError.Run.DishUnmapped)
            else Result.Ok(DraftClassification(top.dishSlug, slugs, MODEL_VERSION))
        }

    companion object {
        const val MIN_CONFIDENCE = 0.30f
        const val MODEL_VERSION = "food101-v1"
    }
}
