package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient

class SearchIngredientsUseCase {
    operator fun invoke(catalog: Collection<Ingredient>, query: String): List<Ingredient> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return catalog.toList()
        return catalog.filter { ing ->
            ing.displayName.lowercase().contains(q) ||
                ing.aliases.any { it.lowercase().contains(q) }
        }
    }
}
