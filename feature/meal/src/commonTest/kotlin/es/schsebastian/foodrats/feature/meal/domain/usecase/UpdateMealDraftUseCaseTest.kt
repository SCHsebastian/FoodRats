package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateMealDraftUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val account = (AccountId.of("acc-1") as Result.Ok).value

    private fun baseDraft() = MealDraft(
        audienceCrewIds = setOf(crew),
        authorId = account,
        day = MealDay(LocalDate(2026, 5, 24), TimeZone.UTC),
        dish = null,
        description = Description.EMPTY,
    )

    private suspend fun setup(initial: MealDraft): Pair<UpdateMealDraftUseCase, FakeMealRepository> {
        val repo = FakeMealRepository()
        repo.saveDraft(initial)
        return UpdateMealDraftUseCase(repo) to repo
    }

    @Test fun setDetected_seeds_detected_and_version_only_never_confirmed() = runTest {
        // Detected ≠ confirmed: a classifier run must NOT stamp the user-confirmed
        // `ingredients` list (that's SetIngredients' job). It only seeds `detectedIngredients`.
        val (update, repo) = setup(baseDraft())
        update(UpdateMealDraftCommand.SetDetected(listOf(IngredientSlug.of("tomato").getOrNull()!!), "pizza", "food101-v1"))
        val updated = repo.observeDraft().first()!!
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!), updated.detectedIngredients)
        assertEquals(emptyList(), updated.ingredients)
        assertEquals("food101-v1", updated.classifierVersion)
        // The detected dish slug is carried for the publish-time cuisine stamp (roadmap §2.2).
        assertEquals("pizza", updated.detectedDishSlug)
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

    // ── multi-photo: AddPhoto / RemovePhotoAt / MovePhoto ─────────────────

    private fun plate(tag: Byte, source: PlateSource = PlateSource.Camera) = Plate(byteArrayOf(tag), source = source)

    @Test fun addPhoto_appends_to_an_empty_list() = runTest {
        val (update, repo) = setup(baseDraft())
        val result = update(UpdateMealDraftCommand.AddPhoto(plate(1)))
        assertTrue(result is Result.Ok)
        assertEquals(listOf(plate(1)), repo.observeDraft().first()!!.plates)
    }

    @Test fun addPhoto_appends_to_the_END_of_an_existing_list_preserving_order() = runTest {
        val (update, repo) = setup(baseDraft().copy(plates = listOf(plate(1), plate(2))))
        update(UpdateMealDraftCommand.AddPhoto(plate(3, PlateSource.Gallery)))
        assertEquals(listOf(plate(1), plate(2), plate(3, PlateSource.Gallery)), repo.observeDraft().first()!!.plates)
    }

    @Test fun addPhoto_past_the_cap_fails_with_TooManyPhotos_and_leaves_the_draft_unchanged() = runTest {
        val full = (1..MealPublishPolicy.MAX_PHOTOS_PER_MEAL).map { plate(it.toByte()) }
        val (update, repo) = setup(baseDraft().copy(plates = full))

        val result = update(UpdateMealDraftCommand.AddPhoto(plate(99)))

        assertEquals(Result.failure(MealError.Validation.TooManyPhotos), result)
        assertEquals(full, repo.observeDraft().first()!!.plates, "a rejected AddPhoto must not mutate the draft")
    }

    @Test fun removePhotoAt_removes_exactly_the_targeted_index() = runTest {
        val (update, repo) = setup(baseDraft().copy(plates = listOf(plate(1), plate(2), plate(3))))
        update(UpdateMealDraftCommand.RemovePhotoAt(1))
        assertEquals(listOf(plate(1), plate(3)), repo.observeDraft().first()!!.plates)
    }

    @Test fun removePhotoAt_out_of_bounds_is_a_noop() = runTest {
        val initial = baseDraft().copy(plates = listOf(plate(1), plate(2)))
        val (update, repo) = setup(initial)

        update(UpdateMealDraftCommand.RemovePhotoAt(-1))
        assertEquals(initial.plates, repo.observeDraft().first()!!.plates)

        update(UpdateMealDraftCommand.RemovePhotoAt(2))
        assertEquals(initial.plates, repo.observeDraft().first()!!.plates)
    }

    @Test fun movePhoto_reorders_from_one_index_to_another() = runTest {
        val (update, repo) = setup(baseDraft().copy(plates = listOf(plate(1), plate(2), plate(3))))
        update(UpdateMealDraftCommand.MovePhoto(fromIndex = 0, toIndex = 2))
        assertEquals(listOf(plate(2), plate(3), plate(1)), repo.observeDraft().first()!!.plates)
    }

    @Test fun movePhoto_with_either_index_out_of_bounds_is_a_noop() = runTest {
        val initial = baseDraft().copy(plates = listOf(plate(1), plate(2)))
        val (update, repo) = setup(initial)

        update(UpdateMealDraftCommand.MovePhoto(fromIndex = -1, toIndex = 0))
        assertEquals(initial.plates, repo.observeDraft().first()!!.plates)

        update(UpdateMealDraftCommand.MovePhoto(fromIndex = 0, toIndex = 5))
        assertEquals(initial.plates, repo.observeDraft().first()!!.plates)
    }
}
