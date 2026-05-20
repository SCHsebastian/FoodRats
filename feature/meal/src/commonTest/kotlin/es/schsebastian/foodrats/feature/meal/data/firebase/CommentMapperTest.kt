package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentMapperTest {
    private val crewId = (CrewId.of("crew1") as Result.Ok).value
    private val mealId = (MealId.of("meal1") as Result.Ok).value

    @Test fun maps_happy_path() {
        val dto = CommentDto(
            id = "c1", authorId = "uid-a", text = "qué buena pinta",
            createdAtEpochMs = 1_716_192_000_000L,
        )
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Ok)
        val c = (r as Result.Ok).value
        assertEquals("c1", c.id.value)
        assertEquals("uid-a", c.authorId.value)
        assertEquals("qué buena pinta", c.text.value)
    }

    @Test fun rejects_missing_id() {
        val dto = CommentDto(id = null, authorId = "uid-a", text = "hi", createdAtEpochMs = 1L)
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Read.Unavailable, (r as Result.Err).error)
    }

    @Test fun rejects_blank_text() {
        val dto = CommentDto(id = "c1", authorId = "uid-a", text = "   ", createdAtEpochMs = 1L)
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Err)
    }

    @Test fun rejects_malformed_authorId() {
        val dto = CommentDto(id = "c1", authorId = "", text = "hi", createdAtEpochMs = 1L)
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Err)
    }
}
