package es.schsebastian.foodrats.feature.meal.data.sync

import kotlin.time.Instant

/**
 * Port for durable persistence of per-crew last-synced timestamps (L3).
 *
 * Implemented by [MealSyncTimestampStore] in production and by an in-memory fake in tests.
 * The concrete implementation owns the single `withContext(io)` boundary; [MealSyncEngine]
 * calls [load] and [save] as plain suspend functions with no dispatcher awareness.
 */
internal interface SyncTimestampPort {
    /**
     * Loads the persisted map (crewId → [Instant]). Returns empty map if nothing stored yet.
     */
    suspend fun load(): Map<String, Instant>

    /**
     * Persists [stamps] (crewId → [Instant]) durably.
     */
    suspend fun save(stamps: Map<String, Instant>)
}
