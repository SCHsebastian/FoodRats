package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class MealPlatesTest {
    private val author = MealAuthor(
        accountId = (AccountId.of("u-author") as Result.Ok).value,
        displayName = "Author",
        avatarUrl = null,
    )

    private fun meal(plates: List<MealPlate> = emptyList()): Meal = Meal(
        id = (MealId.of("m1") as Result.Ok).value,
        author = author,
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-07-13"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "https://example.com/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-07-13T12:00:00Z"),
        plates = plates,
    )

    @Test
    fun meal_constructed_without_plates_defaults_to_empty() {
        // The seam is behaviorally inert: every existing construction site compiles unchanged
        // and reads an empty list (the legacy single-photo shape).
        assertTrue(meal().plates.isEmpty())
    }

    @Test
    fun photoCount_is_1_for_the_legacy_empty_plates_shape() {
        assertEquals(1, meal(plates = emptyList()).photoCount)
    }

    @Test
    fun photoCount_reports_the_plates_size_when_non_empty() {
        val plates = listOf(
            MealPlate(photoUrl = "https://example.com/p1.jpg", source = PlateSource.Camera),
            MealPlate(photoUrl = "https://example.com/p2.jpg", source = PlateSource.Gallery),
            MealPlate(photoUrl = "https://example.com/p3.jpg", source = PlateSource.Gallery),
        )
        assertEquals(3, meal(plates = plates).photoCount)
    }
}
