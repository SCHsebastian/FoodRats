package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.local.LocalMeal
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * [CachePruner] owns no IO boundary itself — [MealLocalStore.pruneOlderThan] does — so what's
 * testable here (no SQLDelight driver in commonTest, per [FakeMealLocalStore][es.schsebastian.foodrats.feature.meal.data.test.FakeMealLocalStore]'s
 * doc comment) is the ORCHESTRATION contract: the cutoff key it computes from [Clock][es.schsebastian.foodrats.core.domain.time.Clock]/zone,
 * that it calls [MealLocalStore.pruneOlderThan] exactly once per [CachePruner.start], and that a
 * failed prune is swallowed rather than propagated (fire-and-forget housekeeping must not crash
 * boot). The retention-window row-eviction behavior itself (which rows a given cutoff actually
 * deletes) lives in the real SQL query and is exercised against a real driver in androidHostTest —
 * out of scope for this commonTest-only file.
 */
class CachePrunerTest {

    /** Records every [pruneOlderThan] call; no real DB (mirrors [FakeMealLocalStore]'s no-arg ctor). */
    private class RecordingMealLocalStore : MealLocalStore() {
        val pruneCalls = mutableListOf<String>()
        var fault: Throwable? = null

        override suspend fun pruneOlderThan(beforeDayKey: String) {
            pruneCalls += beforeDayKey
            fault?.let { throw it }
        }

        override fun observeFeed(crewId: String, dayKey: String): Flow<List<LocalMeal>> = flowOf(emptyList())
        override fun observeRange(crewId: String, fromKey: String, toKey: String): Flow<List<LocalMeal>> = flowOf(emptyList())
        override suspend fun upsertAll(dtos: List<MealDto>) {}
        override suspend fun replaceCrewWindow(crewId: String, fromKey: String, toKey: String, dtos: List<MealDto>) {}
    }

    private val zone = TimeZone.UTC

    @Test fun start_prunes_using_a_cutoff_90_days_before_today() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))
        val local = RecordingMealLocalStore()
        val pruner = CachePruner(local, clock, zone, this)

        pruner.start()
        advanceUntilIdle()

        assertEquals(listOf("2026-03-16"), local.pruneCalls)
    }

    @Test fun start_cutoff_correctly_crosses_a_year_boundary() = runTest {
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        val local = RecordingMealLocalStore()
        val pruner = CachePruner(local, clock, zone, this)

        pruner.start()
        advanceUntilIdle()

        assertEquals(listOf("2025-10-03"), local.pruneCalls)
    }

    @Test fun start_calls_prune_exactly_once() = runTest {
        val clock = FixedClock(Instant.parse("2026-07-15T12:00:00Z"))
        val local = RecordingMealLocalStore()
        val pruner = CachePruner(local, clock, zone, this)

        pruner.start()
        advanceUntilIdle()

        assertEquals(1, local.pruneCalls.size)
    }

    @Test fun start_is_a_safe_no_op_when_there_is_nothing_to_prune() = runTest {
        // A store with no aged-out rows still gets exactly one best-effort prune call, and the
        // launched job completes cleanly — no hang, nothing left running after advanceUntilIdle.
        val clock = FixedClock(Instant.parse("2026-07-15T12:00:00Z"))
        val local = RecordingMealLocalStore()
        val pruner = CachePruner(local, clock, zone, this)

        pruner.start()
        advanceUntilIdle()

        assertEquals(1, local.pruneCalls.size)
    }

    @Test fun start_swallows_a_prune_failure_instead_of_crashing_boot() = runTest {
        // If the failure escaped CachePruner's runCatching, it would propagate through the
        // launched child job and fail this test (runTest rethrows uncaught child exceptions) —
        // the test passing IS the assertion that the failure was swallowed.
        val clock = FixedClock(Instant.parse("2026-07-15T12:00:00Z"))
        val local = RecordingMealLocalStore()
        local.fault = RuntimeException("db unavailable")
        val pruner = CachePruner(local, clock, zone, this)

        pruner.start()
        advanceUntilIdle()

        assertEquals(1, local.pruneCalls.size)
    }
}
