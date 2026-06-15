package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReaction
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.FirebaseFault
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionDto
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomainOrNull
import es.schsebastian.foodrats.feature.meal.data.firebase.toFirebaseFault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [MealReactionPort] over the `crews/{crewId}/meals/{mealId}/reactions/{uid}` subcollection.
 *
 * Mirrors [FirebaseCommentRepository]: observe maps the DTO stream into the [MealReactions] read
 * model (unknown reaction kinds are skipped — forward-compat), and the single write method
 * ([toggle]) does its read-then-write/delete inside exactly one `withContext(dispatchers.io)`
 * (CHARTER rule 4). Vendor failures are bucketed via [toFirebaseFault] and mapped to the typed
 * [ReactionError] tree — never inspected by message anywhere but that one seam.
 *
 * The one-reaction-per-member invariant is enforced authoritatively by the doc ID == uid + the
 * security rule; [toggle] is idempotent-by-intent (present → remove, absent → add).
 */
internal class FirebaseReactionRepository(
    private val firestore: ReactionFirestore,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : MealReactionPort {

    override fun observe(
        crewId: CrewId,
        mealId: MealId,
    ): Flow<Result<MealReactions, ReactionError.Read>> =
        firestore.observe(crewId, mealId)
            .map<List<ReactionDto>, Result<MealReactions, ReactionError.Read>> { dtos ->
                val reactions: List<MealReaction> =
                    dtos.mapNotNull { it.toDomainOrNull(crewId, mealId) }
                Result.success(MealReactions(mealId, reactions))
            }
            .catch { t ->
                val mapped = when (t.toFirebaseFault()) {
                    FirebaseFault.PermissionDenied,
                    FirebaseFault.Unauthenticated -> ReactionError.Read.Unauthorized
                    else -> ReactionError.Read.Unavailable
                }
                emit(Result.failure(mapped))
            }
            .flowOn(dispatchers.io)

    override suspend fun toggle(
        crewId: CrewId,
        mealId: MealId,
        reactorId: AccountId,
        kind: ReactionKind,
    ): Result<ReactionToggle, ReactionError.Toggle> = withContext(dispatchers.io) {
        runCatching<Result<ReactionToggle, ReactionError.Toggle>> {
            if (!firestore.mealExists(crewId, mealId)) {
                return@runCatching Result.failure(ReactionError.Toggle.MealNotFound)
            }
            val existing = firestore.reactionOf(crewId, mealId, reactorId.value)
            // Idempotent-by-intent: same kind already present → remove; otherwise add (a different
            // kind overwrites, since only one doc per member is allowed). The parked set is a single
            // kind, so in practice this is a plain on/off toggle. A present doc with a null/unknown
            // kind (corruption or a forward-compat partial) is also treated as "present → remove",
            // so a malformed doc can't strand the member's one-per-member slot and break the toggle.
            if (existing != null && (existing.kind == null || existing.kind == kind.key)) {
                firestore.remove(crewId, mealId, reactorId.value)
                Result.success(ReactionToggle.Removed)
            } else {
                firestore.put(
                    crewId, mealId,
                    ReactionDto(
                        reactorId = reactorId.value,
                        kind = kind.key,
                        reactedAtEpochMs = clock.now().toEpochMilliseconds(),
                    ),
                )
                Result.success(ReactionToggle.Added)
            }
        }.getOrElse { t ->
            val mapped = when (t.toFirebaseFault()) {
                FirebaseFault.PermissionDenied,
                FirebaseFault.Unauthenticated -> ReactionError.Toggle.Unauthorized
                FirebaseFault.NotFound -> ReactionError.Toggle.MealNotFound
                FirebaseFault.Unavailable -> ReactionError.Toggle.Offline
                else -> ReactionError.Toggle.Unavailable
            }
            Result.failure(mapped)
        }
    }
}
