package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealKind
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.result.getOrNull
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
    // Slot is optional: "" (no slot) or an unknown value reads back as null, never a read failure.
    val slot = slot.takeIf { it.isNotBlank() }?.let { MealSlot.fromKey(it) }
    val coords = parseCoordinates(latitude, longitude)
    // Tolerant discriminator read (spec §6.2): "solo" → Solo; missing/unknown (incl. a future
    // "together" doc seen by a not-yet-updated client) collapses to Solo until the deferred
    // Together build replaces `else` with an explicit "together" arm + an exhaustiveness test.
    val mealKind = when (kind) {
        "solo" -> MealKind.Solo
        else -> MealKind.Solo
    }
    return Result.success(
        Meal(
            id = mealId,
            // Author avatar resolves live via AccountReadPort (the meal-feed enrichment
            // overrides this); the meal doc no longer denormalizes it.
            author = MealAuthor(account, authorName ?: "", avatarUrl = null),
            crewId = crew,
            day = MealDay(day, TimeZone.UTC),
            slot = slot,
            // `photoUrl`/`thumbnailUrl` carry the Storage paths here; the feed enrichment resolves
            // them to signed URLs before display (see FirebaseMealRepository.crewStream).
            photoUrl = platePath ?: "",
            thumbnailUrl = thumbnailPath ?: "",
            // The base64 ThumbHash is passed through verbatim — decoded into a placeholder bitmap
            // in the presentation layer (no domain dependency on a graphics stack).
            thumbHash = thumbHash,
            dish = dish,
            description = desc,
            publishedAt = Instant.fromEpochMilliseconds(publishedAtEpochMs ?: 0L),
            coordinates = coords,
            // A published Meal carries only the user-confirmed ingredients; the raw
            // detection was never persisted, so `detectedIngredients` stays empty here.
            ingredients = ingredients.toSlugs(),
            classifierVersion = classifierVersion,
            // Drop-on-read for a malformed/blank slug (same tolerance as ingredients): an
            // unparseable cuisine just becomes "unstamped", never a read failure.
            cuisine = cuisine?.let { CuisineSlug.of(it).getOrNull() },
            kind = mealKind,
        )
    )
}

/** Maps a domain [MealKind] to its persisted string discriminator (spec §6.3). */
fun MealKind.toDiscriminator(): String = when (this) {
    MealKind.Solo -> "solo"
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
    crewId = meal.crewId.value,
    dayKey = meal.day.toKey(),
    slot = meal.slot?.key() ?: "",
    // Persist the deterministic plate PATH, derived from the ids — never `meal.photoUrl`,
    // which at this layer holds a (resolved, expiring) signed URL.
    platePath = "crews/${meal.crewId.value}/meals/${meal.id.value}.jpg",
    // `thumbHash`/`thumbnailPath` are OWNED BY THE SERVER pipeline (the storage rule forbids the
    // client writing them), so this inverse never mints `thumbnailPath`; it only carries the hash
    // through for a faithful round-trip.
    thumbHash = meal.thumbHash,
    dishName = meal.dish.value,
    description = meal.description.value,
    latitude = meal.coordinates?.latitude,
    longitude = meal.coordinates?.longitude,
    publishedAtEpochMs = meal.publishedAt.toEpochMilliseconds(),
    ingredients = meal.ingredients.map { it.value },
    classifierVersion = meal.classifierVersion,
    cuisine = meal.cuisine?.value,
    kind = meal.kind.toDiscriminator(),
)

/** Drops blanks and any slug that fails [IngredientSlug]'s invariants; unknown-but-valid slugs survive. */
private fun List<String>.toSlugs(): List<IngredientSlug> =
    mapNotNull { IngredientSlug.of(it).getOrNull() }

private fun parseCoordinates(lat: Double?, lon: Double?): Coordinates? {
    if (lat == null || lon == null) return null
    return (Coordinates.of(lat, lon) as? Result.Ok)?.value
}
