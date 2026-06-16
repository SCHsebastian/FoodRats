package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveCatalogUseCaseTest {
    private val tomatoSlug = IngredientSlug.of("tomato").getOrNull()!!
    private val catalog = mapOf(
        tomatoSlug to Ingredient(tomatoSlug, "Tomate", IngredientCategory.Vegetable),
    )

    @Test fun delegates_to_port_unchanged() = runTest {
        val port = object : IngredientReadPort {
            override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = flowOf(catalog)
            override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
            override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> = emptyList()
        }
        ObserveCatalogUseCase(port)().test {
            assertEquals(catalog, awaitItem())
            awaitComplete()
        }
    }
}
