package es.schsebastian.foodrats.feature.meal.presentation

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey

/**
 * Maps every [ClassifierError] leaf to a user-facing [MealStringKey]. Classifier
 * errors surface only inside `ComposePlateScreen`, so the mapper lives in
 * `:feature:meal` (no cross-feature dependency on `:feature:meal-ai`).
 */
fun ClassifierError.toStringKey(): MealStringKey = when (this) {
    ClassifierError.Load.ModelMissing,
    ClassifierError.Load.ModelCorrupt,
    -> MealStringKey.ClassifierBannerLoadFailed

    ClassifierError.Run.DecodeFailed,
    ClassifierError.Run.InferenceFailed,
    ClassifierError.Run.LowConfidence,
    ClassifierError.Run.DishUnmapped,
    -> MealStringKey.ClassifierBannerNoDetection
}
