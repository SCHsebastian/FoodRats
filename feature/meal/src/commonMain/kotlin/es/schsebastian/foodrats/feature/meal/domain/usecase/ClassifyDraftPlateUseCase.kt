package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.flatMap
import kotlinx.coroutines.flow.first

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
 *
 * Gated by two independent kill conditions:
 * 1. [FeatureFlagPort.isMealAiEnabled] — remote kill-switch (operator-controlled).
 * 2. [AiPreferencePort.enabled] — user opt-out (privacy toggle in Profile → Preferences).
 * When either is off, the classifier is never invoked and the result is an empty
 * [DraftClassification] (no detections) — advisory, so it surfaces no error/banner;
 * publishing is unaffected.
 */
class ClassifyDraftPlateUseCase(
    private val classifier: MealClassifierPort,
    private val ingredients: IngredientReadPort,
    private val featureFlags: FeatureFlagPort,
    private val aiPreference: AiPreferencePort,
) {
    suspend operator fun invoke(jpeg: ByteArray): Result<DraftClassification, ClassifierError> {
        // Kill-switch: skip on-device inference entirely, yield no detections (no error).
        if (!featureFlags.isMealAiEnabled() || !aiPreference.enabled.first()) return Result.Ok(DISABLED)
        return classifier.classify(jpeg).flatMap { labels ->
            val top = labels.firstOrNull()
                ?: return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            if (top.confidence < MIN_CONFIDENCE) {
                return@flatMap Result.Err(ClassifierError.Run.LowConfidence)
            }
            val slugs = ingredients.suggestForDish(top.dishSlug)
            if (slugs.isEmpty()) Result.Err(ClassifierError.Run.DishUnmapped)
            else Result.Ok(DraftClassification(top.dishSlug, slugs, MODEL_VERSION))
        }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.30f
        const val MODEL_VERSION = "food101-v1"

        /** Result yielded when the meal-AI kill-switch is off: a dish-less, ingredient-less run. */
        val DISABLED = DraftClassification(dishSlug = "", ingredients = emptyList(), version = "")
    }
}
