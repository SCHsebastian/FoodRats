package es.schsebastian.foodrats.core.domain.meal

import kotlinx.coroutines.flow.Flow

/**
 * Ingredient slugs of the in-progress meal draft, exposed so the ingredient
 * picker (`:feature:ingredient`) can read and edit them without depending on
 * `:feature:meal`. The owning feature implements this on its draft repository.
 *
 * - [selected]: the user-confirmed list the picker mutates.
 * - [detected]: the raw classifier prediction, shown pre-marked; read-only here.
 */
data class DraftIngredients(
    val selected: List<IngredientSlug>,
    val detected: List<IngredientSlug>,
)

interface MealDraftIngredientsPort {
    fun observeDraftIngredients(): Flow<DraftIngredients?>
    suspend fun setIngredients(slugs: List<IngredientSlug>)
}
