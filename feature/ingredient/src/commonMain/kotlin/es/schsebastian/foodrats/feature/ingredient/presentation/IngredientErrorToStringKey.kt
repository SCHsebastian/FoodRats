package es.schsebastian.foodrats.feature.ingredient.presentation

import es.schsebastian.foodrats.feature.ingredient.domain.IngredientError
import es.schsebastian.foodrats.feature.ingredient.i18n.IngredientStringKey

fun IngredientError.toStringKey(): IngredientStringKey = when (this) {
    IngredientError.Load.Offline -> IngredientStringKey.CatalogLoadFailed
    IngredientError.Load.Empty   -> IngredientStringKey.CatalogEmpty
}
