package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StartMealDraftUseCaseTest {
    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    @Test
    fun draft_day_is_set_to_today() = runTest {
        val clock = FixedClock(Instant.parse("2026-05-19T10:00:00Z"))
        val expectedDay = MealDay.today(clock, zone)
        val repo = FakeMealRepository()
        val useCase = StartMealDraftUseCase(repo, clock, zone)

        val result = useCase(crew, account)

        assertTrue(result is Result.Ok)
        assertEquals(expectedDay, (result as Result.Ok).value.day)
    }
}
