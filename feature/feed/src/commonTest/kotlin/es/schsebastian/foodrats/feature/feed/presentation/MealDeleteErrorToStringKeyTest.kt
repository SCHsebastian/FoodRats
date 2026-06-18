package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MealDeleteErrorToStringKeyTest {
    @Test fun not_author_or_owner() =
        assertEquals(FeedStringKey.DeleteMealErrorUnauthorized, MealDeleteError.NotAuthorOrOwner.toStringKey())

    @Test fun not_found() =
        assertEquals(FeedStringKey.DeleteMealErrorNotFound, MealDeleteError.NotFound.toStringKey())

    @Test fun unavailable() =
        assertEquals(FeedStringKey.DeleteMealErrorUnavailable, MealDeleteError.Unavailable.toStringKey())

    /**
     * Locks exhaustiveness: the `when` over every [MealDeleteError] leaf fails to compile if a
     * new arm is added without a mapping. Each arm must resolve to a distinct, dedicated key
     * (no reuse of the comment-error strings — that was the original TODO).
     */
    @Test fun every_leaf_maps_to_a_dedicated_key() {
        val errors: List<MealDeleteError> = listOf(
            MealDeleteError.NotAuthorOrOwner,
            MealDeleteError.NotFound,
            MealDeleteError.Unavailable,
        )
        errors.forEach { error ->
            val key: FeedStringKey = when (error) {
                MealDeleteError.NotAuthorOrOwner -> FeedStringKey.DeleteMealErrorUnauthorized
                MealDeleteError.NotFound         -> FeedStringKey.DeleteMealErrorNotFound
                MealDeleteError.Unavailable      -> FeedStringKey.DeleteMealErrorUnavailable
            }
            assertEquals(key, error.toStringKey())
        }
    }
}
