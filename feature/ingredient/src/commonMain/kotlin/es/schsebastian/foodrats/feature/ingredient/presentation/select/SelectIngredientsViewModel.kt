package es.schsebastian.foodrats.feature.ingredient.presentation.select

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.ObserveCatalogUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the ingredient picker. Reads/writes the meal draft's ingredient slugs
 * through [MealDraftIngredientsPort] (declared in `:core:domain`) so this module
 * never depends on `:feature:meal`. The selection cap is enforced here — the UI
 * is the cap contract (spec §7.2).
 */
class SelectIngredientsViewModel(
    private val observeCatalog: ObserveCatalogUseCase,
    private val draftIngredients: MealDraftIngredientsPort,
) : MviViewModel<SelectIngredientsState, SelectIngredientsIntent, SelectIngredientsEffect>(
    initial = SelectIngredientsState(),
) {
    init {
        viewModelScope.launch {
            observeCatalog().collect { catalog ->
                update { it.copy(catalog = catalog.values.toList(), loading = false) }
            }
        }
        viewModelScope.launch {
            val draft = draftIngredients.observeDraftIngredients().first() ?: return@launch
            update { it.copy(detected = draft.detected.toSet(), selected = draft.selected.toSet()) }
        }
    }

    override suspend fun handle(intent: SelectIngredientsIntent) = when (intent) {
        is SelectIngredientsIntent.QueryChanged -> update { it.copy(query = intent.query) }
        is SelectIngredientsIntent.Toggle -> toggle(intent.slug)
        is SelectIngredientsIntent.ToggleCategory -> toggleCategory(intent.category)
        SelectIngredientsIntent.ConfirmAndExit -> {
            draftIngredients.setIngredients(currentState.selected.toList())
            emit(SelectIngredientsEffect.NavigateBack)
        }
    }

    private fun toggle(slug: IngredientSlug) {
        update { state ->
            val newSelection = when {
                slug in state.selected -> state.selected - slug
                state.selected.size >= SelectIngredientsState.MAX -> state.selected
                else -> state.selected + slug
            }
            state.copy(selected = newSelection)
        }
    }

    private fun toggleCategory(category: IngredientCategory) {
        update { state ->
            val expanded = if (category in state.expandedCategories) {
                state.expandedCategories - category
            } else {
                state.expandedCategories + category
            }
            state.copy(expandedCategories = expanded)
        }
    }
}
