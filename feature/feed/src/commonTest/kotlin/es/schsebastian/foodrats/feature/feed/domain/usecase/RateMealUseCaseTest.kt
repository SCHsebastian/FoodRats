package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.feed.presentation.feed.FakeMealRatingPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateMealUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val mealId = (MealId.of("meal-1") as Result.Ok).value
    private val rater = (AccountId.of("rater-1") as Result.Ok).value
    private val score = (Score.of(4) as Result.Ok).value

    @Test fun delegates_to_port_with_raterId() = runTest {
        val port = FakeMealRatingPort()
        val useCase = RateMealUseCase(port)

        val result = useCase(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size)
        val call = port.calls.first()
        assertEquals("crew-1", call.crewId)
        assertEquals("meal-1", call.mealId)
        assertEquals("rater-1", call.raterId)
        assertEquals(4, call.score)
    }

    @Test fun surfaces_port_error() = runTest {
        val port = FakeMealRatingPort().apply {
            nextResult = Result.failure(RateError.CannotRateOwnMeal)
        }
        val useCase = RateMealUseCase(port)

        val result = useCase(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.CannotRateOwnMeal), result)
    }
}
