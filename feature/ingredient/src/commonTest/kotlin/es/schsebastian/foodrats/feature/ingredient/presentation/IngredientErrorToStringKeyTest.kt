package es.schsebastian.foodrats.feature.ingredient.presentation

import es.schsebastian.foodrats.feature.ingredient.domain.IngredientError
import kotlin.test.Test
import kotlin.test.assertNotNull

class IngredientErrorToStringKeyTest {
    @Test fun all_leaves_mapped() {
        val leaves = listOf<IngredientError>(IngredientError.Load.Offline, IngredientError.Load.Empty)
        leaves.forEach { assertNotNull(it.toStringKey()) }
    }
}
