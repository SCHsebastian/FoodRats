package es.schsebastian.foodrats.feature.ingredient.presentation.select

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

data class SelectIngredientsState(
    val query: String = "",
    val catalog: List<Ingredient> = emptyList(),
    val detected: Set<IngredientSlug> = emptySet(),
    val selected: Set<IngredientSlug> = emptySet(),
    // Every group is open on entry so the user can browse and find all of them; an
    // active search additionally force-expands matches in the screen (see SelectIngredientsScreen).
    val expandedCategories: Set<IngredientCategory> = IngredientCategory.all.toSet(),
    val loading: Boolean = true,
) : MviState {
    val capReached: Boolean get() = selected.size >= MAX

    companion object {
        const val MAX = 30
    }
}

sealed interface SelectIngredientsIntent : MviIntent {
    data class QueryChanged(val query: String) : SelectIngredientsIntent
    data class Toggle(val slug: IngredientSlug) : SelectIngredientsIntent
    data class ToggleCategory(val category: IngredientCategory) : SelectIngredientsIntent
    data object ConfirmAndExit : SelectIngredientsIntent
}

sealed interface SelectIngredientsEffect : MviEffect {
    data object NavigateBack : SelectIngredientsEffect
}
