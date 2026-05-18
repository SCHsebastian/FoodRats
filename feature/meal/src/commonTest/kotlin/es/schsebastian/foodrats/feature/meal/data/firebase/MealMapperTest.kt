package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertTrue

class MealMapperTest {
    @Test fun toDomain_succeeds_on_complete_dto() {
        val dto = MealDto(
            id = "m-1", authorId = "a-1", authorName = "Sam", authorAvatarUrl = null,
            crewId = "c-1", dayKey = "2026-05-16", photoUrl = "https://x.png",
            score = 8, dishName = "Pizza", tags = listOf("dinner"),
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
    }

    @Test fun toDomain_fails_on_invalid_score() {
        val dto = MealDto("m", "a", "n", null, "c", "2026-05-16", "lunch", "url", 99, "Pizza", emptyList(), 0L)
        assertTrue(dto.toDomain() is Result.Err)
    }
}
