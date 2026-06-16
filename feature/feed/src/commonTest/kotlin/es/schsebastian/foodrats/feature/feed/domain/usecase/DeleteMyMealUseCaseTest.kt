package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteMyMealUseCaseTest {

    private fun crewId(raw: String): CrewId = (CrewId.of(raw) as Result.Ok).value
    private val authorId = (AccountId.of("author-1") as Result.Ok).value
    private val day = MealDay(LocalDate.parse("2026-05-20"), TimeZone.UTC)
    private val slot = MealSlot.Lunch

    // Recording fake: captures the crew set / author / day / slot the use case forwards,
    // and returns a configurable result so we can drive both success and error paths.
    private class RecordingMealDeletePort(
        var nextResult: Result<Unit, MealDeleteError> = Result.success(Unit),
    ) : MealDeletePort {
        data class Call(
            val crewIds: Set<CrewId>,
            val authorId: AccountId,
            val day: MealDay,
            val slot: MealSlot,
        )

        val calls = mutableListOf<Call>()

        override suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError> =
            Result.success(Unit)

        override suspend fun deleteFromAllCrews(
            crewIds: Set<CrewId>,
            authorId: AccountId,
            day: MealDay,
            slot: MealSlot,
        ): Result<Unit, MealDeleteError> {
            calls += Call(crewIds, authorId, day, slot)
            return nextResult
        }
    }

    private class RecordingCrewMembership(
        private val crews: List<CrewSummary> = emptyList(),
    ) : CrewMembershipPort {
        val observedFor = mutableListOf<AccountId>()

        override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> {
            observedFor += accountId
            return flowOf(crews)
        }
    }

    @Test fun fetches_my_crews_for_the_author() = runTest {
        val crews = RecordingCrewMembership(
            listOf(CrewSummary(crewId("crew-1"), "Crew One")),
        )
        val useCase = DeleteMyMealUseCase(RecordingMealDeletePort(), crews)

        useCase(authorId, day, slot)

        assertEquals(listOf(authorId), crews.observedFor)
    }

    @Test fun deletes_from_all_fetched_crews() = runTest {
        val crews = RecordingCrewMembership(
            listOf(
                CrewSummary(crewId("crew-1"), "Crew One"),
                CrewSummary(crewId("crew-2"), "Crew Two"),
            ),
        )
        val meals = RecordingMealDeletePort()
        val useCase = DeleteMyMealUseCase(meals, crews)

        val result = useCase(authorId, day, slot)

        assertTrue(result is Result.Ok)
        assertEquals(1, meals.calls.size)
        val call = meals.calls.single()
        assertEquals(setOf(crewId("crew-1"), crewId("crew-2")), call.crewIds)
        assertEquals(authorId, call.authorId)
        assertEquals(day, call.day)
        assertEquals(slot, call.slot)
    }

    @Test fun surfaces_delete_port_error() = runTest {
        val meals = RecordingMealDeletePort(
            nextResult = Result.failure(MealDeleteError.Unavailable),
        )
        val useCase = DeleteMyMealUseCase(
            meals,
            RecordingCrewMembership(listOf(CrewSummary(crewId("crew-1"), "Crew One"))),
        )

        val result = useCase(authorId, day, slot)

        assertEquals(Result.failure(MealDeleteError.Unavailable), result)
    }
}
