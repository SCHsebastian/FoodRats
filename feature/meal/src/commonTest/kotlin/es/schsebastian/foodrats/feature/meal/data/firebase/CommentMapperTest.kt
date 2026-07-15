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

    @Test fun maps_mentions_to_account_ids() {
        val dto = CommentDto(
            id = "c1", authorId = "uid-a", text = "hey @b @c",
            createdAtEpochMs = 1L, mentions = listOf("uid-b", "uid-c"),
        )
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Ok)
        val c = (r as Result.Ok).value
        assertEquals(listOf("uid-b", "uid-c"), c.mentions.map { it.value })
    }

    @Test fun null_mentions_maps_to_empty_list() {
        val dto = CommentDto(id = "c1", authorId = "uid-a", text = "hi", createdAtEpochMs = 1L, mentions = null)
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Ok)
        assertEquals(emptyList(), (r as Result.Ok).value.mentions)
    }

    @Test fun blank_or_invalid_mentions_are_dropped() {
        val dto = CommentDto(
            id = "c1", authorId = "uid-a", text = "hi",
            createdAtEpochMs = 1L, mentions = listOf("uid-b", "   ", ""),
        )
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Ok)
        assertEquals(listOf("uid-b"), (r as Result.Ok).value.mentions.map { it.value })
    }

    @Test fun author_name_is_not_mapped_into_the_domain_model() {
        // authorName is a server-push snapshot only (see the DTO's KDoc); it must never leak into
        // the domain MealComment — MealComment has no authorName field at all, so this test simply
        // locks that the mapper still succeeds when authorName is present (nothing to assert on it).
        val dto = CommentDto(
            id = "c1", authorId = "uid-a", text = "hi", createdAtEpochMs = 1L, authorName = "Sebas",
        )
        val r = dto.toDomain(crewId, mealId)
        assertTrue(r is Result.Ok)
    }
}
