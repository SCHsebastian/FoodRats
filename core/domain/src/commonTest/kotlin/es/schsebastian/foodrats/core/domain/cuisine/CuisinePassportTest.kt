package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CuisinePassportTest {
    private fun slug(raw: String): CuisineSlug = (CuisineSlug.of(raw) as Result.Ok).value

    private val italian = slug("italian")
    private val mexican = slug("mexican")
    private val japanese = slug("japanese")

    // Catalog iteration order is the grid order; keep it deterministic with a LinkedHashMap.
    private val catalog: Map<CuisineSlug, Cuisine> = linkedMapOf(
        italian to Cuisine(italian, "Italian", "italian"),
        mexican to Cuisine(mexican, "Mexican", "mexican"),
        japanese to Cuisine(japanese, "Japanese", "japanese"),
    )

    private val author = MealAuthor(
        accountId = (AccountId.of("u1") as Result.Ok).value,
        displayName = "Author",
        avatarUrl = null,
    )

    private fun meal(
        id: String,
        cuisine: CuisineSlug?,
        publishedAt: String,
    ): Meal = Meal(
        id = (MealId.of(id) as Result.Ok).value,
        author = author,
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-05-19"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "https://example.com/p.jpg",
        dish = (DishName.of("Dish") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse(publishedAt),
        cuisine = cuisine,
    )

    @Test fun empty_meals_gives_all_locked_cells_in_catalog_order() {
        val passport = deriveCuisinePassport(catalog, emptyList())
        assertEquals(listOf(italian, mexican, japanese), passport.cells.map { it.cuisine.slug })
        assertTrue(passport.cells.all { !it.collected && it.firstCollectedAt == null })
        assertEquals(0, passport.collectedCount)
        assertEquals(3, passport.totalCount)
    }

    @Test fun collected_cuisine_is_marked_with_first_collected_instant() {
        val passport = deriveCuisinePassport(
            catalog,
            listOf(meal("m1", italian, "2026-05-19T12:00:00Z")),
        )
        val cell = passport.cells.single { it.cuisine.slug == italian }
        assertTrue(cell.collected)
        assertEquals(Instant.parse("2026-05-19T12:00:00Z"), cell.firstCollectedAt)
        assertEquals(1, passport.collectedCount)
    }

    @Test fun not_collected_cuisine_stays_locked() {
        val passport = deriveCuisinePassport(
            catalog,
            listOf(meal("m1", italian, "2026-05-19T12:00:00Z")),
        )
        val locked = passport.cells.single { it.cuisine.slug == mexican }
        assertFalse(locked.collected)
        assertNull(locked.firstCollectedAt)
    }

    @Test fun duplicate_cuisine_keeps_earliest_first_collected_and_counts_once() {
        val passport = deriveCuisinePassport(
            catalog,
            listOf(
                meal("m2", italian, "2026-05-20T12:00:00Z"),
                meal("m1", italian, "2026-05-19T08:00:00Z"), // earlier, listed second
                meal("m3", italian, "2026-05-21T12:00:00Z"),
            ),
        )
        val cell = passport.cells.single { it.cuisine.slug == italian }
        assertEquals(Instant.parse("2026-05-19T08:00:00Z"), cell.firstCollectedAt)
        assertEquals(1, passport.collectedCount)
    }

    @Test fun meal_with_null_cuisine_contributes_nothing() {
        val passport = deriveCuisinePassport(
            catalog,
            listOf(meal("m1", null, "2026-05-19T12:00:00Z")),
        )
        assertEquals(0, passport.collectedCount)
    }

    @Test fun meal_with_cuisine_absent_from_catalog_is_ignored() {
        val unknown = slug("klingon")
        val passport = deriveCuisinePassport(
            catalog,
            listOf(meal("m1", unknown, "2026-05-19T12:00:00Z")),
        )
        // No extra cell, no collection.
        assertEquals(3, passport.totalCount)
        assertEquals(0, passport.collectedCount)
        assertTrue(passport.cells.none { it.cuisine.slug == unknown })
    }

    @Test fun multiple_distinct_cuisines_each_collected() {
        val passport = deriveCuisinePassport(
            catalog,
            listOf(
                meal("m1", italian, "2026-05-19T12:00:00Z"),
                meal("m2", japanese, "2026-05-20T12:00:00Z"),
            ),
        )
        assertEquals(2, passport.collectedCount)
        assertTrue(passport.cells.single { it.cuisine.slug == italian }.collected)
        assertTrue(passport.cells.single { it.cuisine.slug == japanese }.collected)
        assertFalse(passport.cells.single { it.cuisine.slug == mexican }.collected)
    }
}
