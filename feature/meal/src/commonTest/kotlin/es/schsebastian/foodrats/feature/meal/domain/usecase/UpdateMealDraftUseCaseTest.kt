package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateMealDraftUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    private fun baseDraft() = MealDraft(
        crewId = crew,
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), TimeZone.UTC),
        plate = null,
        dish = null,
        description = Description.EMPTY,
    )

    private suspend fun setup(initial: MealDraft): Pair<UpdateMealDraftUseCase, FakeMealRepository> {
        val repo = FakeMealRepository()
        repo.saveDraft(initial)
        return UpdateMealDraftUseCase(repo) to repo
    }

    @Test fun setDetected_writes_all_three_fields() = runTest {
        val (update, repo) = setup(baseDraft())
        update(UpdateMealDraftCommand.SetDetected(listOf(IngredientSlug.of("tomato").getOrNull()!!), "food101-v1"))
        val updated = repo.observeDraft().first()!!
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!), updated.detectedIngredients)
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!), updated.ingredients)
        assertEquals("food101-v1", updated.classifierVersion)
    }

    @Test fun setIngredients_does_not_touch_detected() = runTest {
        val initial = baseDraft().copy(
            detectedIngredients = listOf(IngredientSlug.of("a").getOrNull()!!),
            ingredients = listOf(IngredientSlug.of("a").getOrNull()!!),
        )
        val (update, repo) = setup(initial)
        update(UpdateMealDraftCommand.SetIngredients(listOf(IngredientSlug.of("a").getOrNull()!!, IngredientSlug.of("b").getOrNull()!!)))
        val updated = repo.observeDraft().first()!!
        assertEquals(listOf(IngredientSlug.of("a").getOrNull()!!), updated.detectedIngredients)
        assertEquals(listOf(IngredientSlug.of("a").getOrNull()!!, IngredientSlug.of("b").getOrNull()!!), updated.ingredients)
    }
}
