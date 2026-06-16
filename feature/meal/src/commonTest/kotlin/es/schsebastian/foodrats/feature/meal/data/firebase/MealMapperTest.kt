package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealKind
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealMapperTest {
    @Test fun toDomain_succeeds_on_complete_dto() {
        val dto = MealDto(
            id = "m-1", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-1.jpg",
            dishName = "Pizza", description = "Margherita with basil",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("Margherita with basil", r.value.description.value)
    }

    @Test fun toDomain_succeeds_with_empty_description() {
        val dto = MealDto(
            id = "m-2", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-2.jpg",
            dishName = "Pizza",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("", r.value.description.value)
    }

    @Test fun toDomain_reads_stamped_cuisine_slug() {
        val dto = MealDto(
            id = "m-3", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-3.jpg",
            dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
            cuisine = "italian",
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("italian", r.value.cuisine?.value)
    }

    @Test fun toDomain_drops_blank_cuisine_to_null() {
        val dto = MealDto(
            id = "m-4", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-4.jpg",
            dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
            cuisine = "   ",
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(null, r.value.cuisine)
    }

    @Test fun toDomain_reads_solo_kind_discriminator() {
        val dto = soloDtoTemplate.copy(id = "m-5", kind = "solo")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun toDomain_defaults_missing_kind_to_solo() {
        // Old/pre-seam doc: no `kind` field. MealDto's default is "solo".
        val dto = soloDtoTemplate.copy(id = "m-6")
        assertEquals("solo", dto.kind)
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun toDomain_maps_unknown_kind_to_solo() {
        // Forward-compat: a future "together" (or any unknown) value seen by a not-yet-updated
        // client collapses to Solo rather than failing the read.
        val dto = soloDtoTemplate.copy(id = "m-7", kind = "together")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun from_writes_solo_discriminator() {
        val meal = (soloDtoTemplate.copy(id = "m-8").toDomain() as Result.Ok).value
        val dto = MealDto.from(meal)
        assertEquals("solo", dto.kind)
    }

    private val soloDtoTemplate = MealDto(
        id = "m-0", authorId = "a-1", authorName = "Sam",
        crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-0.jpg",
        dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
    )
}
