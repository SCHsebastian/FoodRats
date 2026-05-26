package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
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
    val dish = DishName.of(dishName ?: "").getOrElse { return Result.failure(MealError.Read.NotFound) }
    val desc = Description.of(description).getOrElse { return Result.failure(MealError.Read.NotFound) }
    val slot = MealSlot.fromKey(slot) ?: return Result.failure(MealError.Read.NotFound)
    val coords = parseCoordinates(latitude, longitude)
    return Result.success(
        Meal(
            id = mealId,
            author = MealAuthor(account, authorName ?: "", authorAvatarUrl),
            crewId = crew,
            day = MealDay(day, TimeZone.UTC),
            slot = slot,
            photoUrl = photoUrl ?: "",
            dish = dish,
            description = desc,
            publishedAt = Instant.fromEpochMilliseconds(publishedAtEpochMs ?: 0L),
            coordinates = coords,
            ingredients = ingredients.toSlugs(),
            detectedIngredients = detectedIngredients.toSlugs(),
            classifierVersion = classifierVersion,
        )
    )
}

/**
 * Maps a domain [Meal] to its Firestore DTO. The publish path builds its DTO
 * inline from a draft (auth-derived author fields); this factory is the inverse
 * of [toDomain] for the already-published aggregate.
 */
fun MealDto.Companion.from(meal: Meal): MealDto = MealDto(
    id = meal.id.value,
    authorId = meal.author.accountId.value,
    authorName = meal.author.displayName,
    authorAvatarUrl = meal.author.avatarUrl,
    crewId = meal.crewId.value,
    dayKey = meal.day.toKey(),
    slot = meal.slot.key(),
    photoUrl = meal.photoUrl,
    dishName = meal.dish.value,
    description = meal.description.value,
    latitude = meal.coordinates?.latitude,
    longitude = meal.coordinates?.longitude,
    publishedAtEpochMs = meal.publishedAt.toEpochMilliseconds(),
    ingredients = meal.ingredients.map { it.value },
    detectedIngredients = meal.detectedIngredients.map { it.value },
    classifierVersion = meal.classifierVersion,
)

/** Drops blanks and any slug that fails [IngredientSlug]'s invariants; unknown-but-valid slugs survive. */
private fun List<String>.toSlugs(): List<IngredientSlug> =
    mapNotNull { runCatching { IngredientSlug(it) }.getOrNull() }

private fun parseCoordinates(lat: Double?, lon: Double?): Coordinates? {
    if (lat == null || lon == null) return null
    return (Coordinates.of(lat, lon) as? Result.Ok)?.value
}
