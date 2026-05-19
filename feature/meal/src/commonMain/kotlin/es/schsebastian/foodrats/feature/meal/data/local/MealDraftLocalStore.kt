package es.schsebastian.foodrats.feature.meal.data.local

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrElse
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

@Serializable
private data class MealDraftJson(
    val crewId: String,
    val authorId: String,
    val dayIso: String,
    val zoneId: String,
    val photoBase64: String?,
    val overlayApplied: Boolean,
    val dish: String?,
    val tags: List<String>,
    val slot: String? = null,
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
        val crew = CrewId.of(crewId).getOrElse { return null }
        val acc  = AccountId.of(authorId).getOrElse { return null }
        val day  = runCatching { LocalDate.parse(dayIso) }.getOrNull() ?: return null
        val zone = runCatching { TimeZone.of(zoneId) }.getOrNull() ?: TimeZone.UTC
        val plate = photoBase64?.let { Plate(Base64.decode(it), overlayApplied) }
        val d = dish?.let { DishName.of(it).getOrElse { return null } }
        val mappedTags: List<FoodTag> = tags.map { raw ->
            FoodTag.Curated.entries.firstOrNull { it.label == raw }
                ?: FoodTag.custom(raw).getOrElse { return null }
        }
        return MealDraft(
            crewId = crew,
            authorId = acc,
            day = MealDay(day, zone),
            plate = plate,
            dish = d,
            tags = mappedTags,
            slot = slot?.let(MealSlot::fromKey),
        )
    }

    private companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun MealDraftJson.Companion.from(d: MealDraft) = MealDraftJson(
            crewId = d.crewId.value,
            authorId = d.authorId.value,
            dayIso = d.day.date.toString(),
            zoneId = d.day.zone.id,
            photoBase64 = d.plate?.photoBytes?.let { Base64.encode(it) },
            overlayApplied = d.plate?.overlayApplied ?: false,
            dish = d.dish?.value,
            tags = d.tags.map { it.label },
            slot = d.slot?.key(),
        )
    }
}
