package es.schsebastian.foodrats.feature.meal.data.local

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** One photo entry in [MealDraftJson.plates] — mirrors [es.schsebastian.foodrats.feature.meal.domain.model.Plate]. */
@Serializable
private data class PlateJson(
    val photoBase64: String,
    val overlayApplied: Boolean = false,
    // PlateSource.key(); null reads back as camera.
    val source: String? = null,
)

@Serializable
private data class MealDraftJson(
    // The chosen publish audience. `crewId` (singular) is the legacy field a pre-multi-crew
    // draft may still carry on disk; it's read as a one-element audience for back-compat.
    val audienceCrewIds: List<String> = emptyList(),
    val crewId: String? = null,
    val authorId: String,
    val dayIso: String,
    val zoneId: String,
    // Ordered photos. Written by every current save; a draft persisted before multi-photo
    // existed has this empty and is read from the legacy fields below instead.
    val plates: List<PlateJson> = emptyList(),
    // Legacy single-photo fields — READ FALLBACK ONLY (a draft persisted before `plates`
    // existed). Never written by [MealDraftJson.from] — only [plates] is written going forward.
    val photoBase64: String? = null,
    val overlayApplied: Boolean = false,
    // PlateSource.key(); null (legacy persisted drafts) reads back as camera.
    val plateSource: String? = null,
    val dish: String?,
    val description: String = "",
    val slot: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ingredients: List<String> = emptyList(),
    val detectedIngredients: List<String> = emptyList(),
    val detectedDishSlug: String? = null,
    val classifierVersion: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
class MealDraftLocalStore(private val prefs: AppPreferences, private val json: Json = Json) {

    fun observe(): Flow<MealDraft?> = prefs.observe(Keys.MealDraftJson).map { raw ->
        raw?.let { json.decodeFromString<MealDraftJson>(it).toDomain() }
    }

    suspend fun save(draft: MealDraft) {
        prefs.set(Keys.MealDraftJson, json.encodeToString(serializer<MealDraftJson>(), MealDraftJson.from(draft)))
    }

    suspend fun clear() = prefs.clear(Keys.MealDraftJson)

    @OptIn(ExperimentalEncodingApi::class)
    private fun MealDraftJson.toDomain(): MealDraft? {
        // Prefer the multi-crew audience; fall back to the legacy singular `crewId`.
        val rawAudience = audienceCrewIds.ifEmpty { listOfNotNull(crewId) }
        val audience = rawAudience.mapNotNull { CrewId.of(it).getOrNull() }.toSet()
        if (audience.isEmpty()) return null
        val acc  = AccountId.of(authorId).getOrElse { return null }
        val day  = runCatching { LocalDate.parse(dayIso) }.getOrNull() ?: return null
        val zone = runCatching { TimeZone.of(zoneId) }.getOrNull() ?: TimeZone.UTC
        // The new array format wins when present; a draft persisted before `plates` existed
        // (empty array) falls back to the legacy single photoBase64 field as a 1-item list.
        val platesList = plates.ifEmpty {
            photoBase64?.let { listOf(PlateJson(it, overlayApplied, plateSource)) }.orEmpty()
        }.map { Plate(Base64.decode(it.photoBase64), it.overlayApplied, PlateSource.fromKey(it.source)) }
        val d = dish?.let { DishName.of(it).getOrElse { return null } }
        val desc = Description.of(description).getOrElse { Description.EMPTY }
        val coords = if (latitude != null && longitude != null) {
            (Coordinates.of(latitude, longitude) as? Result.Ok)?.value
        } else null
        return MealDraft(
            audienceCrewIds = audience,
            authorId = acc,
            day = MealDay(day, zone),
            plates = platesList,
            dish = d,
            description = desc,
            slot = slot?.let(MealSlot::fromKey),
            coordinates = coords,
            ingredients = ingredients.mapNotNull { IngredientSlug.of(it).getOrNull() },
            detectedIngredients = detectedIngredients.mapNotNull { IngredientSlug.of(it).getOrNull() },
            detectedDishSlug = detectedDishSlug,
            classifierVersion = classifierVersion,
        )
    }

    private companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun MealDraftJson.Companion.from(d: MealDraft) = MealDraftJson(
            audienceCrewIds = d.audienceCrewIds.map { it.value },
            authorId = d.authorId.value,
            dayIso = d.day.date.toString(),
            zoneId = d.day.zone.id,
            // WRITE only the new array field — the legacy photoBase64/overlayApplied/plateSource
            // fields above are read-fallback only and stay at their defaults (never serialized;
            // the default Json config skips default-valued fields).
            plates = d.plates.map { PlateJson(Base64.encode(it.photoBytes), it.overlayApplied, it.source.key()) },
            dish = d.dish?.value,
            description = d.description.value,
            slot = d.slot?.key(),
            latitude = d.coordinates?.latitude,
            longitude = d.coordinates?.longitude,
            ingredients = d.ingredients.map { it.value },
            detectedIngredients = d.detectedIngredients.map { it.value },
            detectedDishSlug = d.detectedDishSlug,
            classifierVersion = d.classifierVersion,
        )
    }
}
