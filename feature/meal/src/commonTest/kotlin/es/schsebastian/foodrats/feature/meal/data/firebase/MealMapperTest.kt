package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealMapperTest {
    @Test fun toDomain_succeeds_on_complete_dto() {
        val dto = MealDto(
            id = "m-1", authorId = "a-1", authorName = "Sam", authorAvatarUrl = null,
            crewId = "c-1", dayKey = "2026-05-16", photoUrl = "https://x.png",
            dishName = "Pizza", description = "Margherita with basil",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("Margherita with basil", r.value.description.value)
    }

    @Test fun toDomain_succeeds_with_empty_description() {
        val dto = MealDto(
            id = "m-2", authorId = "a-1", authorName = "Sam", authorAvatarUrl = null,
            crewId = "c-1", dayKey = "2026-05-16", photoUrl = "https://x.png",
            dishName = "Pizza",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("", r.value.description.value)
    }
}
