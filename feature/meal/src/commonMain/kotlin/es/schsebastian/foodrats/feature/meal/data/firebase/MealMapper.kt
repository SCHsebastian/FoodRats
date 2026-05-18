package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

fun MealDto.toDomain(): Result<Meal, MealError.Read> {
    val mealId = MealId.of(id ?: return Result.failure(MealError.Read.NotFound))
        .getOrElse { return Result.failure(MealError.Read.NotFound) }
    val account = AccountId.of(authorId ?: return Result.failure(MealError.Read.NotFound))
        .getOrElse { return Result.failure(MealError.Read.NotFound) }
    val crew = CrewId.of(crewId ?: return Result.failure(MealError.Read.CrewNotFound))
        .getOrElse { return Result.failure(MealError.Read.CrewNotFound) }
    val day = runCatching { LocalDate.parse(dayKey ?: "") }.getOrNull()
        ?: return Result.failure(MealError.Read.NotFound)
    val score = Score.of(score ?: -1).getOrElse { return Result.failure(MealError.Read.NotFound) }
    val dish = DishName.of(dishName ?: "").getOrElse { return Result.failure(MealError.Read.NotFound) }
    val resolvedTags: List<FoodTag> = tags.map { raw ->
        FoodTag.Curated.entries.firstOrNull { it.label == raw }
            ?: FoodTag.custom(raw).getOrElse { return Result.failure(MealError.Read.NotFound) }
    }
    return Result.success(
        Meal(
            id = mealId,
            author = MealAuthor(account, authorName ?: "", authorAvatarUrl),
            crewId = crew,
            day = MealDay(day, TimeZone.UTC),
            slot = MealSlot.Lunch,
            photoUrl = photoUrl ?: "",
            score = score,
            dish = dish,
            tags = resolvedTags,
            publishedAt = Instant.fromEpochMilliseconds(publishedAtEpochMs ?: 0L),
        )
    )
}
