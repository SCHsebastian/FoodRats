package es.schsebastian.foodrats.core.domain.meal

import kotlinx.coroutines.flow.Flow

interface IngredientReadPort {
    fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>>
    suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient>
    suspend fun suggestForDish(dishSlug: String): List<IngredientSlug>
}
