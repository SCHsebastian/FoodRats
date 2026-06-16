package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionDto
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behavioral fake [ReactionFirestore]: an in-memory `reactions/{uid}` map per meal, plus a
 * `mealPresent` flag and an optional throwable to exercise the vendor-fault → typed-error seam.
 */
private class FakeReactionFirestore(
    var mealPresent: Boolean = true,
    var failWith: Throwable? = null,
) : ReactionFirestore {
    val docs: MutableStateFlow<Map<String, ReactionDto>> = MutableStateFlow(emptyMap())

    override fun observe(crewId: CrewId, mealId: MealId): Flow<List<ReactionDto>> =
        kotlinx.coroutines.flow.flow {
            failWith?.let { throw it }
            docs.collect { map -> emit(map.values.toList()) }
        }

    override suspend fun reactionOf(crewId: CrewId, mealId: MealId, reactorUid: String): ReactionDto? {
        failWith?.let { throw it }
        return docs.value[reactorUid]
    }

    override suspend fun mealExists(crewId: CrewId, mealId: MealId): Boolean {
        failWith?.let { throw it }
        return mealPresent
    }

    override suspend fun put(crewId: CrewId, mealId: MealId, dto: ReactionDto) {
        failWith?.let { throw it }
        docs.value = docs.value + (dto.reactorId!! to dto)
    }

    override suspend fun remove(crewId: CrewId, mealId: MealId, reactorUid: String) {
        failWith?.let { throw it }
        docs.value = docs.value - reactorUid
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseReactionRepositoryTest {
    private val crewId = (CrewId.of("crew1") as Result.Ok).value
    private val mealId = (MealId.of("meal1") as Result.Ok).value
    private val viewer = (AccountId.of("uid-viewer") as Result.Ok).value
    private val other = (AccountId.of("uid-other") as Result.Ok).value

    private fun repository(fake: FakeReactionFirestore): FirebaseReactionRepository {
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
        }
        return FirebaseReactionRepository(
            firestore = fake,
            clock = FixedClock(Instant.parse("2026-06-14T12:00:00Z")),
            dispatchers = dispatchers,
        )
    }

    @Test fun toggle_adds_when_absent_then_removes_when_present() = runTest {
        val fake = FakeReactionFirestore()
        val repo = repository(fake)

        val first = repo.toggle(crewId, mealId, viewer, ReactionKind.DailyGlyph)
        assertTrue(first is Result.Ok)
        assertEquals(ReactionToggle.Added, (first as Result.Ok).value)
        assertEquals(1, fake.docs.value.size)
        assertEquals(ReactionKind.DailyGlyph.key, fake.docs.value["uid-viewer"]?.kind)

        val second = repo.toggle(crewId, mealId, viewer, ReactionKind.DailyGlyph)
        assertTrue(second is Result.Ok)
        assertEquals(ReactionToggle.Removed, (second as Result.Ok).value)
        assertEquals(0, fake.docs.value.size)
    }

    @Test fun observe_aggregates_count_and_viewer_reaction() = runTest {
        val fake = FakeReactionFirestore()
        val repo = repository(fake)
        repo.toggle(crewId, mealId, viewer, ReactionKind.DailyGlyph)
        repo.toggle(crewId, mealId, other, ReactionKind.DailyGlyph)

        val emitted = repo.observe(crewId, mealId).first()
        assertTrue(emitted is Result.Ok)
        val agg = (emitted as Result.Ok).value
        assertEquals(2, agg.count)
        assertTrue(agg.hasReacted(viewer))
        assertTrue(agg.hasReacted(other))
        assertEquals(ReactionKind.DailyGlyph, agg.reactionBy(viewer)?.kind)
    }

    @Test fun observe_unknown_kind_doc_is_skipped() = runTest {
        val fake = FakeReactionFirestore()
        fake.docs.value = mapOf(
            "uid-viewer" to ReactionDto("uid-viewer", ReactionKind.DailyGlyph.key, 1L),
            "uid-future" to ReactionDto("uid-future", "fire", 1L),
        )
        val repo = repository(fake)

        val agg = (repo.observe(crewId, mealId).first() as Result.Ok).value
        assertEquals(1, agg.count) // the unknown "fire" kind is dropped
        assertTrue(agg.hasReacted(viewer))
    }

    @Test fun toggle_on_missing_meal_returns_meal_not_found() = runTest {
        val fake = FakeReactionFirestore(mealPresent = false)
        val repo = repository(fake)

        val r = repo.toggle(crewId, mealId, viewer, ReactionKind.DailyGlyph)
        assertTrue(r is Result.Err)
        assertEquals(ReactionError.Toggle.MealNotFound, (r as Result.Err).error)
        assertEquals(0, fake.docs.value.size)
    }

    @Test fun toggle_permission_denied_maps_to_unauthorized() = runTest {
        val fake = FakeReactionFirestore(failWith = RuntimeException("PERMISSION_DENIED: nope"))
        val repo = repository(fake)

        val r = repo.toggle(crewId, mealId, viewer, ReactionKind.DailyGlyph)
        assertTrue(r is Result.Err)
        assertEquals(ReactionError.Toggle.Unauthorized, (r as Result.Err).error)
    }

    @Test fun observe_failure_maps_to_unavailable() = runTest {
        val fake = FakeReactionFirestore(failWith = RuntimeException("network unreachable"))
        val repo = repository(fake)

        val emitted = repo.observe(crewId, mealId).first()
        assertTrue(emitted is Result.Err)
        assertEquals(ReactionError.Read.Unavailable, (emitted as Result.Err).error)
    }
}
