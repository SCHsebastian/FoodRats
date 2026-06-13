package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
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
    private val dish = (DishName.of("Pizza") as Result.Ok).value

    private fun draftForDay(day: MealDay) = MealDraft(
        crewId = crew, authorId = account, day = day,
        plate = Plate(photoBytes = byteArrayOf(1, 2, 3)),
        dish = dish, description = Description.EMPTY,
        slot = MealSlot.Lunch,
    )

    private fun sampleDraft(
        day: MealDay = MealDay(LocalDate(2026, 5, 18), zone),
        slot: MealSlot? = MealSlot.Lunch,
    ) = MealDraft(
        crewId = crew, authorId = account, day = day,
        plate = Plate(photoBytes = byteArrayOf(1, 2, 3)),
        dish = dish, description = Description.EMPTY,
        slot = slot,
    )

    private fun sampleMeal(
        day: MealDay = MealDay(LocalDate(2026, 5, 18), zone),
        slot: MealSlot = MealSlot.Lunch,
    ) = Meal(
        id = (MealId.of("fake-id") as Result.Ok).value,
        author = MealAuthor(account, "Fake", null),
        crewId = crew,
        day = day,
        slot = slot,
        photoUrl = "fake://photo",
        dish = dish,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-05-18T12:00:00Z"),
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

    @Test
    fun publish_fails_with_AlreadyPostedToday_when_slot_already_taken() = runTest {
        val instant = Instant.parse("2026-05-18T12:00:00Z")
        val today = MealDay.today(FixedClock(instant), TimeZone.UTC)
        val draft = sampleDraft().copy(day = today, slot = MealSlot.Lunch)
        val repo = FakeMealRepository().apply {
            markSlotTaken(draft.crewId, today, MealSlot.Lunch)
        }
        val useCase = PublishMealUseCase(repo, FixedClock(instant), TimeZone.UTC)

        val result = useCase(draft)

        assertEquals(Result.failure(MealError.Publish.AlreadyPostedToday), result)
    }

    @Test
    fun publish_succeeds_when_different_slot_is_taken() = runTest {
        val instant = Instant.parse("2026-05-18T12:00:00Z")
        val today = MealDay.today(FixedClock(instant), TimeZone.UTC)
        val draft = sampleDraft().copy(day = today, slot = MealSlot.Lunch)
        val repo = FakeMealRepository().apply {
            markSlotTaken(draft.crewId, today, MealSlot.Breakfast)
            publishResultOverride = Result.success(sampleMeal().copy(day = today, slot = MealSlot.Lunch))
        }
        val useCase = PublishMealUseCase(repo, FixedClock(instant), TimeZone.UTC)

        val result = useCase(draft)

        assertTrue(result is Result.Ok)
    }

    @Test
    fun publish_fails_with_NoSlotSelected_when_slot_is_null() = runTest {
        val instant = Instant.parse("2026-05-18T12:00:00Z")
        val today = MealDay.today(FixedClock(instant), TimeZone.UTC)
        val draft = sampleDraft().copy(day = today, slot = null)
        val useCase = PublishMealUseCase(FakeMealRepository(), FixedClock(instant), TimeZone.UTC)

        val result = useCase(draft)

        assertEquals(Result.failure(MealError.Publish.NoSlotSelected), result)
    }

    @Test
    fun publishes_no_ingredients_when_user_confirms_none_despite_detections() = runTest {
        // Detected ≠ confirmed: AI found ingredients but the user confirmed nothing,
        // so the published meal must record NO ingredients (never the detections).
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val today = MealDay.today(clock, zone)
        val draft = draftForDay(today).copy(
            detectedIngredients = listOf(IngredientSlug.of("bacon").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!),
            ingredients = emptyList(),
        )
        val repo = FakeMealRepository()
        val useCase = PublishMealUseCase(repo, clock, zone)

        val result = useCase(draft)

        assertTrue(result is Result.Ok)
        assertEquals(emptyList(), repo.publishedDrafts.single().ingredients)
    }

    @Test
    fun publishes_exactly_the_confirmed_subset_not_the_union_with_detections() = runTest {
        // The user confirmed a subset of the detections; the published meal records
        // exactly that subset — not the union of confirmed + detected.
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val today = MealDay.today(clock, zone)
        val draft = draftForDay(today).copy(
            detectedIngredients = listOf(IngredientSlug.of("bacon").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!, IngredientSlug.of("egg").getOrNull()!!),
            ingredients = listOf(IngredientSlug.of("egg").getOrNull()!!),
        )
        val repo = FakeMealRepository()
        val useCase = PublishMealUseCase(repo, clock, zone)

        val result = useCase(draft)

        assertTrue(result is Result.Ok)
        assertEquals(listOf(IngredientSlug.of("egg").getOrNull()!!), repo.publishedDrafts.single().ingredients)
    }
}
