package es.schsebastian.foodrats.feature.crew.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.schsebastian.foodrats.core.database.Crew as CrewRow
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import es.schsebastian.foodrats.feature.crew.data.firebase.MemberDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The local read source-of-truth for the crew list / picker (offline-first P3b §P3b-T7). Wraps the
 * SQLDelight `crew` queries; the [FirebaseCrewRepository][es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository]
 * now sources `observeMyCrews` from here (not the merged DataStore-cache + Firestore stream), and the
 * [CrewSyncEngine][es.schsebastian.foodrats.feature.crew.data.sync.CrewSyncEngine] mirrors the
 * server's crew list in via [replaceAll].
 *
 * READS are a reactive [Flow] of rebuilt [CrewDto]s (the IO boundary for a flow is `mapToList(io)`).
 * The repository runs the EXACT same `CrewDto.toDomain()` mapping it used for the live Firestore
 * snapshot, so the offline list is identical to the online one. [replaceAll] owns the single
 * `withContext(io)`.
 *
 * The `crew` table stores only what the picker needs (id, name, owner, blind-voting, member-id set,
 * created-at) — display names / per-member join times are NOT persisted; identity resolves live as
 * today. Because the table carries no invite code, the rebuilt [CrewDto] uses a deterministic
 * placeholder [SYNTHETIC_CODE] purely so `CrewDto.toDomain()` succeeds; the `observeMyCrews` path
 * never surfaces `Crew.code` (the picker uses only name + member count), so the placeholder is inert.
 */
open class CrewLocalStore(
    private val database: FoodRatsDatabase?,
    private val dispatchers: DispatcherProvider?,
) {
    /**
     * No-DB constructor for an override-only test double: feature:crew commonTest has no
     * cross-platform SQLDelight driver, so the real JVM-backed store is exercised in androidHostTest.
     * Production always supplies a real [FoodRatsDatabase] via the primary constructor (Koin binds it
     * non-null), so the `!!` getters below never fire outside the override-only fake.
     */
    protected constructor() : this(null, null)

    private val queries get() = database!!.crewQueries
    private val io get() = dispatchers!!.io

    /** The signed-in member's crews, newest-created first, as rebuilt wire [CrewDto]s. */
    open fun observeMyCrews(): Flow<List<CrewDto>> =
        queries.selectAll()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toCrewDto() } }

    /**
     * Replaces the entire local crew set with exactly [crews] (the latest server snapshot), in ONE
     * transaction: clear then upsert. A full replace (not delete-by-absence) is correct here — the
     * `crew` table holds only the signed-in member's crews, and `observeMyCrews` is the whole set,
     * so anything absent from the snapshot is no longer a crew of theirs (left / removed / deleted).
     */
    open suspend fun replaceAll(crews: List<CrewDto>) = withContext(io) {
        val rows = crews.mapNotNull { it.toUpsert() }
        queries.transaction {
            queries.deleteAll()
            rows.forEach { row ->
                queries.upsert(
                    crewId = row.crewId,
                    name = row.name,
                    ownerId = row.ownerId,
                    blindVoting = row.blindVoting,
                    memberIdsCsv = row.memberIdsCsv,
                    createdAtEpochMs = row.createdAtEpochMs,
                )
            }
        }
    }

    internal companion object {
        /**
         * Inert placeholder invite code for the rebuilt [CrewDto] (see the class KDoc). Must satisfy
         * [es.schsebastian.foodrats.feature.crew.domain.model.CrewCode.of]: 6 chars from the
         * unambiguous alphabet. `observeMyCrews` never reads `Crew.code`, so this value is never shown.
         */
        const val SYNTHETIC_CODE = "AAAAAA"
    }
}

/** The column values to upsert one crew row; `null` if the DTO is missing a non-null column. */
private data class CrewUpsert(
    val crewId: String,
    val name: String,
    val ownerId: String,
    val blindVoting: Long,
    val memberIdsCsv: String,
    val createdAtEpochMs: Long,
)

private fun CrewDto.toUpsert(): CrewUpsert? = CrewUpsert(
    crewId = id ?: return null,
    name = name ?: return null,
    ownerId = ownerId ?: return null,
    blindVoting = if (blindVoting) 1L else 0L,
    memberIdsCsv = memberIds.filter { it.isNotBlank() }.joinToString(","),
    createdAtEpochMs = createdAtEpochMs ?: return null,
)

/**
 * Rebuilds a wire [CrewDto] from the locally-stored row so the repository's existing
 * `CrewDto.toDomain()` enrichment runs unchanged. Members are reconstructed from the CSV with their
 * join time stamped to the crew's creation time (per-member join times aren't persisted — the picker
 * doesn't display them). The placeholder [CrewLocalStore.SYNTHETIC_CODE] keeps `toDomain()` happy.
 */
private fun CrewRow.toCrewDto(): CrewDto {
    val ids = memberIdsCsv.split(",").filter { it.isNotBlank() }
    return CrewDto(
        id = crewId,
        name = name,
        code = CrewLocalStore.SYNTHETIC_CODE,
        ownerId = ownerId,
        createdAtEpochMs = createdAtEpochMs,
        memberIds = ids,
        members = ids.associateWith { MemberDto(joinedAtEpochMs = createdAtEpochMs) },
        blindVoting = blindVoting != 0L,
    )
}
