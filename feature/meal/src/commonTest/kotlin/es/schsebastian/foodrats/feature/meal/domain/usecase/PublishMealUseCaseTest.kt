package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublishMealUseCaseTest {
    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val score = (Score.of(7) as Result.Ok).value
    private val dish = (DishName.of("Pizza") as Result.Ok).value

    private fun draftForDay(day: MealDay) = MealDraft(
        crewId = crew, authorId = account, day = day,
        plate = Plate(photoBytes = byteArrayOf(1, 2, 3)),
        score = score, dish = dish, tags = emptyList(),
    )

    @Test fun publishes_when_draft_day_is_today() = runTest {
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val today = MealDay.today(clock, zone)
        val repo = FakeMealRepository()
        val useCase = PublishMealUseCase(repo, clock, zone)

        val result = useCase(draftForDay(today))

        assertTrue(result is Result.Ok)
        assertEquals(1, repo.publishedDrafts.size)
    }

    @Test fun rejects_publish_when_draft_day_is_not_today() = runTest {
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val yesterday = MealDay(LocalDate(2026, 5, 15), zone)
        val repo = FakeMealRepository()
        val useCase = PublishMealUseCase(repo, clock, zone)

        val result = useCase(draftForDay(yesterday))

        assertTrue(result is Result.Err)
        assertEquals(MealError.Publish.NotToday, (result as Result.Err).error)
        assertEquals(0, repo.publishedDrafts.size)
    }

    @Test fun propagates_repository_failure() = runTest {
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val today = MealDay.today(clock, zone)
        val repo = FakeMealRepository().apply { publishResultOverride = Result.failure(MealError.Publish.PublishUnavailable) }
        val useCase = PublishMealUseCase(repo, clock, zone)

        val result = useCase(draftForDay(today))

        assertTrue(result is Result.Err)
        assertEquals(MealError.Publish.PublishUnavailable, (result as Result.Err).error)
    }
}
