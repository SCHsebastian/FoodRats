package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactionMapperTest {
    private val crewId = (CrewId.of("crew1") as Result.Ok).value
    private val mealId = (MealId.of("meal1") as Result.Ok).value

    @Test fun maps_happy_path() {
        val dto = ReactionDto(
            reactorId = "uid-a",
            kind = ReactionKind.DailyGlyph.key,
            reactedAtEpochMs = 1_716_192_000_000L,
        )
        val r = dto.toDomainOrNull(crewId, mealId)
        assertEquals("uid-a", r?.reactorId?.value)
        assertEquals(ReactionKind.DailyGlyph, r?.kind)
        assertEquals(mealId, r?.mealId)
        assertEquals(crewId, r?.crewId)
        assertEquals(1_716_192_000_000L, r?.reactedAt?.toEpochMilliseconds())
    }

    @Test fun skips_unknown_kind_for_forward_compat() {
        val dto = ReactionDto(reactorId = "uid-a", kind = "fire", reactedAtEpochMs = 1L)
        assertNull(dto.toDomainOrNull(crewId, mealId))
    }

    @Test fun skips_missing_kind() {
        val dto = ReactionDto(reactorId = "uid-a", kind = null, reactedAtEpochMs = 1L)
        assertNull(dto.toDomainOrNull(crewId, mealId))
    }

    @Test fun skips_blank_reactor_id() {
        val dto = ReactionDto(reactorId = "", kind = ReactionKind.DailyGlyph.key, reactedAtEpochMs = 1L)
        assertNull(dto.toDomainOrNull(crewId, mealId))
    }

    @Test fun defaults_missing_timestamp_to_epoch_zero() {
        val dto = ReactionDto(reactorId = "uid-a", kind = ReactionKind.DailyGlyph.key, reactedAtEpochMs = null)
        val r = dto.toDomainOrNull(crewId, mealId)
        assertEquals(0L, r?.reactedAt?.toEpochMilliseconds())
    }
}
