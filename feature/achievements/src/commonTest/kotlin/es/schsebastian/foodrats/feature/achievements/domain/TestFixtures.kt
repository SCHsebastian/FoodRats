package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal fun acct(id: String): AccountId = (AccountId.of(id) as Result.Ok).value
internal fun crew(id: String = "c-1"): CrewId = (CrewId.of(id) as Result.Ok).value
internal fun mid(id: String): MealId = (MealId.of(id) as Result.Ok).value
internal fun dish(name: String = "Dish"): DishName = (DishName.of(name) as Result.Ok).value

private val DAY: LocalDate = LocalDate(2026, 6, 14)

/** Builds a [MealWithRatings] authored by [authorId] with the given [slot]/[ingredients]. */
internal fun meal(
    id: String,
    authorId: String,
    slot: MealSlot = MealSlot.Lunch,
    ingredients: List<String> = emptyList(),
    detectedIngredients: List<String> = emptyList(),
    crewId: CrewId = crew(),
    zone: TimeZone = TimeZone.UTC,
): MealWithRatings {
    val m = Meal(
        id = mid(id),
        author = MealAuthor(acct(authorId), displayName = authorId, avatarUrl = null),
        crewId = crewId,
        day = MealDay(DAY, zone),
        slot = slot,
        photoUrl = "https://example.test/$id.jpg",
        dish = dish(),
        description = Description.EMPTY,
        publishedAt = Instant.fromEpochMilliseconds(0L),
        ingredients = ingredients.map { IngredientSlug.of(it).getOrNull()!! },
        detectedIngredients = detectedIngredients.map { IngredientSlug.of(it).getOrNull()!! },
    )
    return MealWithRatings(m, ratings = emptyList())
}
