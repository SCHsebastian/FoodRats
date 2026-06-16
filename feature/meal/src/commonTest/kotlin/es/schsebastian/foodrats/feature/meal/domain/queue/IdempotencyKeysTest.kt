package es.schsebastian.foodrats.feature.meal.domain.queue

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdempotencyKeysTest {

    private val zone = TimeZone.UTC
    private val crewA = (CrewId.of("crew-a") as Result.Ok).value
    private val crewB = (CrewId.of("crew-b") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val dish = (DishName.of("Pizza") as Result.Ok).value
    private val day = MealDay(LocalDate(2026, 5, 18), zone)

    private fun draft(
        crews: Set<CrewId> = setOf(crewA),
        slot: MealSlot? = MealSlot.Lunch,
    ) = MealDraft(
        audienceCrewIds = crews, authorId = account, day = day,
        plate = Plate(photoBytes = byteArrayOf(1, 2, 3)),
        dish = dish, description = Description.EMPTY, slot = slot,
    )

    @Test
    fun key_is_the_deterministic_meal_id_per_crew() {
        val expected = setOf(MealId.forDaySlot(crewA, account, day, MealSlot.Lunch))
        assertEquals(expected, draft().idempotencyKeys())
    }

    @Test
    fun multi_crew_audience_yields_one_key_per_crew() {
        val keys = draft(crews = setOf(crewA, crewB)).idempotencyKeys()
        assertEquals(
            setOf(
                MealId.forDaySlot(crewA, account, day, MealSlot.Lunch),
                MealId.forDaySlot(crewB, account, day, MealSlot.Lunch),
            ),
            keys,
        )
    }

    @Test
    fun keys_are_stable_across_calls_for_the_same_draft() {
        val d = draft(crews = setOf(crewA, crewB))
        // The whole point of idempotency: a retried publish derives the SAME ids,
        // so the write overwrites rather than duplicates.
        assertEquals(d.idempotencyKeys(), d.idempotencyKeys())
    }

    @Test
    fun no_slot_yields_no_keys() {
        // Without a slot no stable id can be derived (PublishMealUseCase would
        // reject the draft anyway); the key set is empty rather than guessed.
        assertTrue(draft(slot = null).idempotencyKeys().isEmpty())
    }

    @Test
    fun empty_audience_yields_no_keys() {
        assertTrue(draft(crews = emptySet()).idempotencyKeys().isEmpty())
    }
}
