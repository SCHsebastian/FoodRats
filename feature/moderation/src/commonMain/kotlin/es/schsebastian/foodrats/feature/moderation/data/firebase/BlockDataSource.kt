package es.schsebastian.foodrats.feature.moderation.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Vendor-free seam for the per-account block list, implemented by [BlockFirestoreDataSource]. The
 * [es.schsebastian.foodrats.feature.moderation.data.repository.BlockRepository] depends on this
 * abstraction so its mapping / error-classification / dispatcher boundary stay host-testable
 * (Firestore can't run in a JVM host test); the Firestore impl is the only place GitLive types appear.
 *
 * Same role the eventual Firebase→own-server swap depends on.
 */
internal interface BlockDataSource {
    /** Cold snapshot stream of every blocked uid for [owner]. */
    fun observeBlocked(owner: String): Flow<Set<String>>

    /** Writes (or overwrites) a block doc for [target] under [owner]. */
    suspend fun block(owner: String, target: String, nowMs: Long)

    /** Deletes the block doc for [target] under [owner] (deleting a missing doc is harmless). */
    suspend fun unblock(owner: String, target: String)
}

/**
 * The only Firestore-touching adapter for the block list. Targets the owner-private
 * `accounts/{owner}/blocks/{blockedUid}` subcollection (UGC compliance §5); the doc id is the blocked
 * account's uid. Returns vendor-mapped values to the repository, which owns the typed-error
 * translation and the dispatcher boundary.
 */
internal class BlockFirestoreDataSource(
    private val firestore: FirebaseFirestore,
) : BlockDataSource {

    private fun col(owner: String) =
        firestore.collection("accounts").document(owner).collection("blocks")

    /**
     * Cold snapshot stream of every blocked uid for [owner]. No `withContext` here — matching the
     * achievements / reactions read paths, the dispatcher boundary lives only on the one-shot writes.
     */
    override fun observeBlocked(owner: String): Flow<Set<String>> =
        col(owner).snapshots.map { snap -> snap.documents.map { it.id }.toSet() }

    override suspend fun block(owner: String, target: String, nowMs: Long) {
        col(owner).document(target).set(BlockDto(createdAtEpochMs = nowMs))
    }

    override suspend fun unblock(owner: String, target: String) {
        col(owner).document(target).delete()
    }
}
