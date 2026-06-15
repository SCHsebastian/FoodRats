package es.schsebastian.foodrats.feature.achievements.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Vendor-free seam for unlocked-achievement persistence, implemented by [AchievementFirestoreDataSource].
 * The repository depends on this abstraction so it stays host-testable (Firestore can't run in a
 * JVM host test); the Firestore impl is the only place GitLive types appear.
 */
internal interface AchievementUnlockStore {
    /** Cold snapshot stream of every persisted unlock for [uid], keyed by raw id → unlock epoch-ms. */
    fun observeUnlocks(uid: String): Flow<Map<String, Long>>

    /** Writes the given id → epoch-ms pairs in one batch. */
    suspend fun recordUnlocks(uid: String, unlocks: Map<String, Long>)
}

/**
 * The only Firestore-touching adapter for unlocked-achievement persistence. Targets the
 * `accounts/{uid}/achievements/{achievementId}` subcollection; the doc id is the raw achievement id
 * (spec §6.2). Returns vendor-mapped values to the repository, which owns the typed-error
 * translation and the dispatcher boundary.
 */
internal class AchievementFirestoreDataSource(
    private val firestore: FirebaseFirestore,
) : AchievementUnlockStore {

    private fun col(uid: String) =
        firestore.collection("accounts").document(uid).collection("achievements")

    /**
     * Cold snapshot stream of every persisted unlock for [uid], keyed by the raw achievement id →
     * unlock epoch-ms. No `withContext` here — matching the `MealReadPort`/reactions read paths,
     * the dispatcher boundary lives only on the one-shot write.
     */
    override fun observeUnlocks(uid: String): Flow<Map<String, Long>> =
        col(uid).snapshots.map { snap ->
            snap.documents.associate { it.id to it.data<AchievementUnlockDto>().unlockedAtEpochMs }
        }

    /** Writes the given id → epoch-ms pairs in one batch (overwriting an existing doc is harmless). */
    override suspend fun recordUnlocks(uid: String, unlocks: Map<String, Long>) {
        val column = col(uid)
        firestore.batch().apply {
            unlocks.forEach { (id, ms) -> set(column.document(id), AchievementUnlockDto(ms)) }
        }.commit()
    }
}
