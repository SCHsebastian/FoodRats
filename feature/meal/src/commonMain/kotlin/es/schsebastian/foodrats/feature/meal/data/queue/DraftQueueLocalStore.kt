package es.schsebastian.foodrats.feature.meal.data.queue

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
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Durable, multi-entry local store for the offline-first publish queue (roadmap §5.2).
 *
 * Distinct from [es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore],
 * which holds the *single* in-flight composer draft. This store holds the full
 * list of [QueuedDraft]s — each with its status, attempt count, and the captured
 * [Plate] bytes (base64) — so a process death (or airplane mode) never loses a
 * queued plate. We persist the whole list as one JSON blob in DataStore
 * Preferences ([Keys.DraftQueueJson]), the same proven mechanism the single-draft
 * store uses; that survives process death without adding a DB dependency.
 *
 * This is a pure (de)serialization + read/modify/write helper. It holds NO
 * `withContext` — the IO boundary lives in [DraftQueueRepository], which owns the
 * one `withContext(dispatchers.io)` per public method (CLAUDE.md rule).
 *
 * A single DataStore `set` is atomic, but the read-modify-write in [mutate] spans
 * a `read()` and a separate `set()`: two concurrent mutations (e.g. an enqueue
 * racing a retry status flip) could both read the same `current` and the later
 * write would clobber the earlier one (lost update). A per-instance [Mutex]
 * serializes the whole read-modify-write so the writes compose instead of racing.
 */
@OptIn(ExperimentalEncodingApi::class)
class DraftQueueLocalStore(
    private val prefs: AppPreferences,
    private val json: Json = Json,
) {

    /** Serializes the read-modify-write in [mutate] against itself. */
    private val mutateLock = Mutex()

    /** Observe the full queue, newest-last by `createdAt`. Empty when nothing queued or unparseable. */
    fun observe(): Flow<List<QueuedDraft>> = prefs.observe(Keys.DraftQueueJson).map { raw ->
        decode(raw)
    }

    /** Read the current queue once. */
    suspend fun read(): List<QueuedDraft> = decode(prefs.observe(Keys.DraftQueueJson).first())

    /** Append [entry] to the queue (no-op-safe on duplicate id: the new one replaces it). */
    suspend fun add(entry: QueuedDraft) = mutate { current ->
        current.filterNot { it.id == entry.id } + entry
    }

    /** Replace the entry with [QueuedDraft.id] == [id] via [transform]; no-op if absent. */
    suspend fun update(id: QueueEntryId, transform: (QueuedDraft) -> QueuedDraft) = mutate { current ->
        current.map { if (it.id == id) transform(it) else it }
    }

    /** Remove the entry [id]; no-op if absent. */
    suspend fun remove(id: QueueEntryId) = mutate { current ->
        current.filterNot { it.id == id }
    }

    /**
     * Atomic read-modify-write of the whole list through one DataStore string key.
     * Held under [mutateLock] so concurrent mutations serialize and compose rather
     * than racing (a later write clobbering an earlier one — lost update).
     */
    private suspend fun mutate(transform: (List<QueuedDraft>) -> List<QueuedDraft>) = mutateLock.withLock {
        val current = read()
        val next = transform(current).sortedBy { it.createdAt }
        prefs.set(Keys.DraftQueueJson, json.encodeToString(serializer<List<QueuedDraftJson>>(), next.map { it.toJson() }))
    }

    private fun decode(raw: String?): List<QueuedDraft> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<QueuedDraftJson>>(raw) }
            .getOrNull()
            ?.mapNotNull { it.toDomain() }
            ?.sortedBy { it.createdAt }
            ?: emptyList()
    }

    // ── serialization shape ───────────────────────────────────────────────

    @Serializable
    private data class QueuedDraftJson(
        val id: String,
        val draft: MealDraftJson,
        val status: StatusJson,
        val attemptCount: Int,
        val createdAtEpochMs: Long,
        val lastAttemptAtEpochMs: Long? = null,
    )

    @Serializable
    private data class StatusJson(
        val kind: String,                 // "pending" | "uploading" | "succeeded" | "failed"
        val errorKey: String? = null,     // failed only
        val retryable: Boolean = false,   // failed only
    )

    /** One photo entry in [MealDraftJson.plates] — mirrors [Plate]. */
    @Serializable
    private data class PlateJson(
        val photoBase64: String,
        val overlayApplied: Boolean = false,
        // PlateSource.key(); null reads back as camera.
        val source: String? = null,
    )

    @Serializable
    private data class MealDraftJson(
        val audienceCrewIds: List<String> = emptyList(),
        val authorId: String,
        val dayIso: String,
        val zoneId: String,
        // Ordered photos. Written by every current enqueue; an entry queued before multi-photo
        // existed has this empty and is read from the legacy fields below instead.
        val plates: List<PlateJson> = emptyList(),
        // Legacy single-photo fields — READ FALLBACK ONLY (an entry queued before `plates`
        // existed). Never written going forward — only [plates] is written.
        val photoBase64: String? = null,
        val overlayApplied: Boolean = false,
        val dish: String? = null,
        val description: String = "",
        val slot: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val ingredients: List<String> = emptyList(),
        val detectedIngredients: List<String> = emptyList(),
        val detectedDishSlug: String? = null,
        val classifierVersion: String? = null,
        // PlateSource.key(); null (entries queued before the marker existed) reads back as camera.
        val plateSource: String? = null,
    )

    private fun QueuedDraft.toJson() = QueuedDraftJson(
        id = id.value,
        draft = draft.toJson(),
        status = status.toJson(),
        attemptCount = attemptCount,
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        lastAttemptAtEpochMs = lastAttemptAt?.toEpochMilliseconds(),
    )

    private fun QueuedDraftStatus.toJson() = when (this) {
        QueuedDraftStatus.Pending   -> StatusJson(kind = "pending")
        QueuedDraftStatus.Uploading -> StatusJson(kind = "uploading")
        QueuedDraftStatus.Succeeded -> StatusJson(kind = "succeeded")
        is QueuedDraftStatus.Failed -> StatusJson(kind = "failed", errorKey = errorKey, retryable = retryable)
    }

    private fun MealDraft.toJson() = MealDraftJson(
        audienceCrewIds = audienceCrewIds.map { it.value },
        authorId = authorId.value,
        dayIso = day.date.toString(),
        zoneId = day.zone.id,
        // WRITE only the new array field — the legacy photoBase64/overlayApplied/plateSource
        // fields are read-fallback only and stay at their defaults (skipped by the default
        // Json config, which doesn't serialize default-valued fields).
        plates = plates.map { PlateJson(Base64.encode(it.photoBytes), it.overlayApplied, it.source.key()) },
        dish = dish?.value,
        description = description.value,
        slot = slot?.key(),
        latitude = coordinates?.latitude,
        longitude = coordinates?.longitude,
        ingredients = ingredients.map { it.value },
        detectedIngredients = detectedIngredients.map { it.value },
        detectedDishSlug = detectedDishSlug,
        classifierVersion = classifierVersion,
    )

    private fun QueuedDraftJson.toDomain(): QueuedDraft? {
        val entryId = runCatching { QueueEntryId(id) }.getOrNull() ?: return null
        val domainDraft = draft.toDomain() ?: return null
        val domainStatus = status.toDomain() ?: return null
        return QueuedDraft(
            id = entryId,
            draft = domainDraft,
            status = domainStatus,
            attemptCount = attemptCount,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
            lastAttemptAt = lastAttemptAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
    }

    private fun StatusJson.toDomain(): QueuedDraftStatus? = when (kind) {
        "pending"   -> QueuedDraftStatus.Pending
        "uploading" -> QueuedDraftStatus.Uploading
        "succeeded" -> QueuedDraftStatus.Succeeded
        "failed"    -> QueuedDraftStatus.Failed(errorKey = errorKey ?: "meal.upload.unknown", retryable = retryable)
        else        -> null
    }

    private fun MealDraftJson.toDomain(): MealDraft? {
        val audience = audienceCrewIds.mapNotNull { CrewId.of(it).getOrNull() }.toSet()
        if (audience.isEmpty()) return null
        val acc = AccountId.of(authorId).getOrElse { return null }
        val day = runCatching { LocalDate.parse(dayIso) }.getOrNull() ?: return null
        val zone = runCatching { TimeZone.of(zoneId) }.getOrNull() ?: TimeZone.UTC
        // The new array format wins when present; an entry queued before `plates` existed (empty
        // array) falls back to the legacy single photoBase64 field as a 1-item list.
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
}
