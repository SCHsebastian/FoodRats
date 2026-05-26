package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import kotlinx.coroutines.flow.Flow

class ObserveCatalogUseCase(private val port: IngredientReadPort) {
    operator fun invoke(): Flow<Map<IngredientSlug, Ingredient>> = port.observeCatalog()
}
