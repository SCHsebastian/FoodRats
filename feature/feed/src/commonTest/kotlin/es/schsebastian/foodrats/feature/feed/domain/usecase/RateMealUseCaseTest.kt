package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
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

    private fun useCase(
        port: FakeMealRatingPort = FakeMealRatingPort(),
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = RateMealUseCase(port, connectivity, outbox)

    @Test fun delegates_to_port_with_raterId_when_online() = runTest {
        val port = FakeMealRatingPort()
        val outbox = RecordingOutboxPort()

        val result = useCase(port = port, outbox = outbox)(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size)
        val call = port.calls.first()
        assertEquals("crew-1", call.crewId)
        assertEquals("meal-1", call.mealId)
        assertEquals("rater-1", call.raterId)
        assertEquals(4, call.score)
        assertTrue(outbox.enqueued.isEmpty(), "online path must not touch the outbox")
    }

    @Test fun surfaces_non_connectivity_port_error() = runTest {
        val port = FakeMealRatingPort().apply {
            nextResult = Result.failure(RateError.CannotRateOwnMeal)
        }
        val outbox = RecordingOutboxPort()

        val result = useCase(port = port, outbox = outbox)(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.CannotRateOwnMeal), result)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun offline_enqueues_and_returns_ok_without_calling_port() = runTest {
        val port = FakeMealRatingPort()
        val outbox = RecordingOutboxPort()

        val result = useCase(
            port = port,
            connectivity = FakeConnectivityPort(online = false),
            outbox = outbox,
        )(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertTrue(port.calls.isEmpty(), "offline must not hit the direct port")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RateMeal(crew, mealId, rater, score)),
            outbox.enqueued,
        )
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val port = FakeMealRatingPort().apply { nextResult = Result.failure(RateError.RateUnavailable) }
        val outbox = RecordingOutboxPort()

        val result = useCase(port = port, outbox = outbox)(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size, "online path attempts the direct write first")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RateMeal(crew, mealId, rater, score)),
            outbox.enqueued,
        )
    }
}
