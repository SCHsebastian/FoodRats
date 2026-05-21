package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal fun acct(id: String): AccountId = (AccountId.of(id) as Result.Ok).value
internal fun crew(id: String = "c-1"): CrewId = (CrewId.of(id) as Result.Ok).value
internal fun mid(id: String): MealId = (MealId.of(id) as Result.Ok).value
internal fun dish(name: String = "Dish"): DishName = (DishName.of(name) as Result.Ok).value
internal fun score(n: Int): Score = (Score.of(n) as Result.Ok).value

internal fun mealWithRatings(
    id: String,
    authorId: String,
    date: LocalDate,
    ratings: List<Int> = emptyList(),
    zone: TimeZone = TimeZone.UTC,
    crewId: CrewId = crew(),
): MealWithRatings {
    val meal = Meal(
        id = mid(id),
        author = MealAuthor(acct(authorId), displayName = authorId, avatarUrl = null),
        crewId = crewId,
        day = MealDay(date, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://example.test/$id.jpg",
        dish = dish(),
        description = Description.EMPTY,
        publishedAt = Instant.fromEpochMilliseconds(0L),
    )
    val mealRatings = ratings.mapIndexed { i, s ->
        MealRating(
            raterId = acct("rater-$id-$i"),
            raterDisplayName = "rater$i",
            raterAvatarUrl = null,
            score = score(s),
            ratedAt = Instant.fromEpochMilliseconds(0L),
        )
    }
    return MealWithRatings(meal, mealRatings)
}
