package es.schsebastian.foodrats.feature.meal.data.local

import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateEntryDto
import es.schsebastian.foodrats.feature.meal.data.firebase.RatingEntryDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The local read source-of-truth projection of a meal (offline-first P3a §3.1). It is NEITHER a
 * domain type ([Meal][es.schsebastian.foodrats.core.domain.meal.Meal]) NOR the wire DTO
 * ([MealDto]) — it carries exactly what the SQLDelight `meal` + `mealRating` tables hold:
 * Storage object **paths** (never signed URLs) and the denormalized per-rater ratings, plus the
 * offline-write bookkeeping ([pending]/[idempotencyKey]).
 *
 * [toMealDto] rebuilds a [MealDto] so the EXISTING `MealDto.toMealWithRatings(lookup)` enrichment
 * (signed-URL minting + live identity resolution) is reused byte-for-byte by the repository — the
 * local store never duplicates that logic. The store carries `authorName` only as a non-blank
 * fallback; the enrichment overrides it with live identity.
 */
data class LocalMeal(
    val mealId: String,
    val crewId: String,
    val authorId: String,
    val authorName: String?,
    val dayKey: String,
    val slot: String,
    val platePath: String?,
    val thumbnailPath: String?,
    val thumbHash: String?,
    val dishName: String?,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAtEpochMs: Long,
    val ratingSum: Long,
    val voterCount: Long,
    val ingredientsCsv: String,
    val classifierVersion: String?,
    val cuisine: String?,
    val kind: String,
    /** PlateSource.key() ("camera"/"gallery"); `null` (rows synced pre-marker) reads back as camera. */
    val plateSource: String? = null,
    /**
     * JSON-encoded `List<PlateEntryDto>` (see [MealDto.plates]) — the multi-photo mirror of
     * [platePath]/[plateSource]. `null` (rows synced pre-multi-photo, or a legacy single-photo
     * meal) means no extra photos; readers fall back to [platePath]/[plateSource] for the one
     * photo, exactly like [MealDto.plates] empty/absent.
     */
    val platesJson: String? = null,
    val pending: Long,
    val idempotencyKey: String?,
    val ratings: List<LocalRating>,
)

/** One denormalized rating row (mirror of the `mealRating` table) for a [LocalMeal]. */
data class LocalRating(
    val raterId: String,
    val score: Int,
    val atMs: Long,
    val pending: Boolean,
    /** Mirrors [RatingEntryDto.edited]: `true` once the rater has used their single vote change. */
    val edited: Boolean = false,
)

/**
 * Rebuilds the wire [MealDto] from the locally-stored row + its ratings so the repository's
 * existing `toMealWithRatings(lookup)` enrichment runs unchanged. Paths are passed straight
 * through (the enrichment resolves them to signed URLs); the per-rater ratings become the
 * denormalized `ratings` map keyed by accountId.
 */
fun LocalMeal.toMealDto(): MealDto = MealDto(
    id = mealId,
    authorId = authorId,
    authorName = authorName,
    crewId = crewId,
    dayKey = dayKey,
    slot = slot,
    platePath = platePath,
    thumbHash = thumbHash,
    thumbnailPath = thumbnailPath,
    dishName = dishName,
    description = description,
    latitude = latitude,
    longitude = longitude,
    publishedAtEpochMs = publishedAtEpochMs,
    ratings = ratings.associate { it.raterId to RatingEntryDto(score = it.score, atMs = it.atMs, edited = it.edited) },
    ratingSum = ratingSum.toInt(),
    voterCount = voterCount.toInt(),
    ingredients = ingredientsCsv.toIngredientList(),
    classifierVersion = classifierVersion,
    cuisine = cuisine,
    kind = kind,
    plateSource = plateSource,
    plates = platesJson.toPlateEntries(),
)

/**
 * The column values to upsert a [MealDto] into the `meal` table (sync side). `pending = 0` /
 * `idempotencyKey = null` — server-confirmed rows are never in flight; the optimistic-write path
 * (P3b) flips those independently. Stores the Storage PATH ([MealDto.platePath]), never a URL, and
 * folds the confirmed ingredient slugs into a comma-joined CSV.
 */
data class MealUpsert(
    val mealId: String,
    val crewId: String,
    val authorId: String,
    val authorName: String?,
    val dayKey: String,
    val slot: String,
    val platePath: String?,
    val thumbnailPath: String?,
    val thumbHash: String?,
    val dishName: String?,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAtEpochMs: Long,
    val ratingSum: Long,
    val voterCount: Long,
    val ingredientsCsv: String,
    val classifierVersion: String?,
    val cuisine: String?,
    val kind: String,
    /** PlateSource.key() ("camera"/"gallery"); `null` mirrors a doc without the marker. */
    val plateSource: String? = null,
    /** JSON-encoded `List<PlateEntryDto>`; `null` mirrors an empty/absent [MealDto.plates]. */
    val platesJson: String? = null,
    val pending: Long,
    val idempotencyKey: String?,
    val ratings: List<LocalRating>,
)

fun MealDto.toLocalUpsert(): MealUpsert = MealUpsert(
    mealId = id.orEmpty(),
    crewId = crewId.orEmpty(),
    authorId = authorId.orEmpty(),
    authorName = authorName,
    dayKey = dayKey.orEmpty(),
    slot = slot,
    platePath = platePath,
    thumbnailPath = thumbnailPath,
    thumbHash = thumbHash,
    dishName = dishName,
    description = description,
    latitude = latitude,
    longitude = longitude,
    publishedAtEpochMs = publishedAtEpochMs ?: 0L,
    ratingSum = ratingSum.toLong(),
    voterCount = voterCount.toLong(),
    ingredientsCsv = ingredients.toIngredientCsv(),
    classifierVersion = classifierVersion,
    cuisine = cuisine,
    kind = kind,
    plateSource = plateSource,
    platesJson = plates.toPlatesJson(),
    pending = 0L,
    idempotencyKey = null,
    ratings = ratings.map { (raterId, entry) ->
        LocalRating(raterId = raterId, score = entry.score, atMs = entry.atMs, pending = false, edited = entry.edited)
    },
)

/** Joins confirmed ingredient slugs with ',' — no slug contains a comma (P3a §2). Empty list → "". */
private fun List<String>.toIngredientCsv(): String = filter { it.isNotBlank() }.joinToString(",")

/** Inverse of [toIngredientCsv]: splits the CSV, dropping empties so "" → emptyList. */
private fun String.toIngredientList(): List<String> =
    if (isBlank()) emptyList() else split(",").filter { it.isNotBlank() }

private val platesJsonFormat = Json

/** Empty list serializes to `null` (not `"[]"`) so a legacy/single-photo row stays a clean NULL column. */
private fun List<PlateEntryDto>.toPlatesJson(): String? =
    if (isEmpty()) null else platesJsonFormat.encodeToString(serializer<List<PlateEntryDto>>(), this)

/** Tolerant decode: blank/unparseable JSON degrades to an empty list rather than crashing a read. */
private fun String?.toPlateEntries(): List<PlateEntryDto> =
    if (isNullOrBlank()) emptyList()
    else runCatching { platesJsonFormat.decodeFromString(serializer<List<PlateEntryDto>>(), this) }.getOrElse { emptyList() }
