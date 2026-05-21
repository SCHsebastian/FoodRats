package es.schsebastian.foodrats.feature.meal.presentation.publish

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationError
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private object NoopStreakNotificationPort : StreakNotificationPort {
    override suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError> =
        Result.success(Unit)
}

class PublishMealViewModelTest {
    @Test fun publish_emits_Published_effect_on_success() = runTest {
        val repo = FakeMealRepository()
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val zone = TimeZone.UTC
        val crew = (CrewId.of("c") as Result.Ok).value
        val acc = (AccountId.of("a") as Result.Ok).value
        val draft = MealDraft(crew, acc, MealDay.today(clock, zone), Plate(byteArrayOf(1)),
            (DishName.of("Tacos") as Result.Ok).value, Description.EMPTY,
            slot = MealSlot.Lunch)
        repo.saveDraft(draft)
        val vm = PublishMealViewModel(
            ObserveMealDraftUseCase(repo),
            PublishMealUseCase(repo, clock, zone),
            NoopStreakNotificationPort,
            clock,
            zone,
        )

        vm.effects.test {
            vm.onIntent(PublishMealIntent.Load)
            vm.onIntent(PublishMealIntent.Publish)
            assertEquals(PublishMealEffect.Published, awaitItem())
        }
    }
}
