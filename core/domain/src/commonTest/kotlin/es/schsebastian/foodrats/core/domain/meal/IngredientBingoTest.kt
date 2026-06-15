package es.schsebastian.foodrats.core.domain.meal

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

class IngredientBingoTest {
    private fun slug(raw: String): IngredientSlug = (IngredientSlug.of(raw) as Result.Ok).value

    private val tomato = slug("tomato")
    private val basil = slug("basil")
    private val garlic = slug("garlic")

    private fun ingredient(slug: IngredientSlug, name: String) =
        Ingredient(slug, name, IngredientCategory.Vegetable, iconKey = null)

    // Catalog iteration order is the grid order; keep it deterministic with a LinkedHashMap.
    private val catalog: Map<IngredientSlug, Ingredient> = linkedMapOf(
        tomato to ingredient(tomato, "Tomato"),
        basil to ingredient(basil, "Basil"),
        garlic to ingredient(garlic, "Garlic"),
    )

    private val author = MealAuthor(
        accountId = (AccountId.of("u1") as Result.Ok).value,
        displayName = "Author",
        avatarUrl = null,
    )

    private fun meal(
        id: String,
        ingredients: List<IngredientSlug> = emptyList(),
        detectedIngredients: List<IngredientSlug> = emptyList(),
        publishedAt: String = "2026-05-19T12:00:00Z",
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
        ingredients = ingredients,
        detectedIngredients = detectedIngredients,
    )

    @Test fun empty_meals_gives_all_locked_cells_in_catalog_order() {
        val bingo = deriveIngredientBingo(catalog, emptyList())
        assertEquals(listOf(tomato, basil, garlic), bingo.cells.map { it.ingredient.slug })
        assertTrue(bingo.cells.all { !it.collected && it.firstCollectedAt == null })
        assertEquals(0, bingo.collectedCount)
        assertEquals(3, bingo.totalCount)
    }

    @Test fun confirmed_ingredient_is_marked_with_first_collected_instant() {
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(meal("m1", ingredients = listOf(tomato), publishedAt = "2026-05-19T12:00:00Z")),
        )
        val cell = bingo.cells.single { it.ingredient.slug == tomato }
        assertTrue(cell.collected)
        assertEquals(Instant.parse("2026-05-19T12:00:00Z"), cell.firstCollectedAt)
        assertEquals(1, bingo.collectedCount)
    }

    @Test fun not_confirmed_ingredient_stays_locked() {
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(meal("m1", ingredients = listOf(tomato))),
        )
        val locked = bingo.cells.single { it.ingredient.slug == basil }
        assertFalse(locked.collected)
        assertNull(locked.firstCollectedAt)
    }

    @Test fun unconfirmed_AI_detected_ingredient_is_NOT_credited() {
        // basil appears ONLY as an AI detection — it must stay locked (the §2.3 honesty rule).
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(
                meal(
                    "m1",
                    ingredients = listOf(tomato),
                    detectedIngredients = listOf(basil, garlic),
                ),
            ),
        )
        assertTrue(bingo.cells.single { it.ingredient.slug == tomato }.collected)
        assertFalse(bingo.cells.single { it.ingredient.slug == basil }.collected)
        assertFalse(bingo.cells.single { it.ingredient.slug == garlic }.collected)
        assertEquals(1, bingo.collectedCount)
    }

    @Test fun duplicate_ingredient_keeps_earliest_first_collected_and_counts_once() {
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(
                meal("m2", ingredients = listOf(tomato), publishedAt = "2026-05-20T12:00:00Z"),
                meal("m1", ingredients = listOf(tomato), publishedAt = "2026-05-19T08:00:00Z"), // earlier, listed second
                meal("m3", ingredients = listOf(tomato), publishedAt = "2026-05-21T12:00:00Z"),
            ),
        )
        val cell = bingo.cells.single { it.ingredient.slug == tomato }
        assertEquals(Instant.parse("2026-05-19T08:00:00Z"), cell.firstCollectedAt)
        assertEquals(1, bingo.collectedCount)
    }

    @Test fun ingredient_absent_from_catalog_is_ignored() {
        val unknown = slug("dragonfruit")
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(meal("m1", ingredients = listOf(unknown))),
        )
        assertEquals(3, bingo.totalCount)
        assertEquals(0, bingo.collectedCount)
        assertTrue(bingo.cells.none { it.ingredient.slug == unknown })
    }

    @Test fun multiple_distinct_ingredients_each_collected() {
        val bingo = deriveIngredientBingo(
            catalog,
            listOf(
                meal("m1", ingredients = listOf(tomato, basil), publishedAt = "2026-05-19T12:00:00Z"),
                meal("m2", ingredients = listOf(garlic), publishedAt = "2026-05-20T12:00:00Z"),
            ),
        )
        assertEquals(3, bingo.collectedCount)
        assertTrue(bingo.cells.all { it.collected })
    }
}
