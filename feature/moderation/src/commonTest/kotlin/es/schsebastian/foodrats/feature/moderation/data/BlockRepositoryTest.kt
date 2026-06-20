package es.schsebastian.foodrats.feature.moderation.data

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockErrorMapper
import es.schsebastian.foodrats.feature.moderation.data.repository.BlockRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider

/**
 * Unit tests for [BlockRepository]: self-block guard, fail-open observeBlocked, success paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockRepositoryTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val owner = accountId("u-owner")
    private val target = accountId("u-target")
    private val clock = FixedClock(Instant.parse("2026-05-20T10:00:00Z"))

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.Default
    }

    open class FakeBlockDataSource : BlockDataSource {
        val blocked = MutableStateFlow<Set<String>>(emptySet())
        val blockCalls = mutableListOf<Triple<String, String, Long>>()
        val unblockCalls = mutableListOf<Pair<String, String>>()
        var throwOnBlock: Throwable? = null

        override fun observeBlocked(owner: String): Flow<Set<String>> = blocked

        override suspend fun block(owner: String, target: String, nowMs: Long) {
            throwOnBlock?.let { throw it.also { throwOnBlock = null } }
            blockCalls += Triple(owner, target, nowMs)
            blocked.value = blocked.value + target
        }

        override suspend fun unblock(owner: String, target: String) {
            unblockCalls += owner to target
            blocked.value = blocked.value - target
        }
    }

    private fun repo(ds: FakeBlockDataSource = FakeBlockDataSource()) =
        BlockRepository(ds, dispatchers, BlockErrorMapper(), clock)

    // ─── self-block guard ──────────────────────────────────────────────────

    @Test fun block_self_returns_SelfBlock_without_calling_datasource() = runTest {
        val ds = FakeBlockDataSource()
        val r = repo(ds).block(owner, owner)
        assertIs<Result.Err<BlockError>>(r)
        assertEquals(BlockError.Write.SelfBlock, r.error)
        assertTrue(ds.blockCalls.isEmpty())
    }

    // ─── fail-open observeBlocked ──────────────────────────────────────────

    @Test fun observeBlocked_emits_empty_set_on_upstream_error() = runTest {
        val ds = object : FakeBlockDataSource() {
            override fun observeBlocked(owner: String): Flow<Set<String>> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("PERMISSION_DENIED") }
        }
        val flow = repo(ds).observeBlocked(owner)
        // The fail-open catch() in BlockRepository must swallow and emit emptySet().
        val result = flow.first()
        assertEquals(emptySet<AccountId>(), result)
    }

    @Test fun observeBlocked_maps_uid_strings_to_AccountIds() = runTest {
        val ds = FakeBlockDataSource()
        ds.blocked.value = setOf(target.value)
        val flow = repo(ds).observeBlocked(owner)
        val result = flow.first()
        assertEquals(setOf(target), result)
    }

    @Test fun observeBlocked_drops_invalid_uid_strings_silently() = runTest {
        val ds = FakeBlockDataSource()
        ds.blocked.value = setOf(target.value, "") // empty string is not a valid AccountId
        val flow = repo(ds).observeBlocked(owner)
        val result = flow.first()
        // Only the valid uid survives the mapNotNull.
        assertEquals(setOf(target), result)
    }

    // ─── success paths ────────────────────────────────────────────────────

    @Test fun block_success_calls_datasource_with_correct_args() = runTest {
        val ds = FakeBlockDataSource()
        val r = repo(ds).block(owner, target)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(1, ds.blockCalls.size)
        val (o, t, _) = ds.blockCalls.single()
        assertEquals(owner.value, o)
        assertEquals(target.value, t)
    }

    @Test fun block_failure_maps_to_BlockError_Unavailable() = runTest {
        val ds = FakeBlockDataSource().also {
            it.throwOnBlock = RuntimeException("network error")
        }
        val r = repo(ds).block(owner, target)
        assertIs<Result.Err<BlockError>>(r)
        assertEquals(BlockError.Write.Unavailable, r.error)
    }

    @Test fun unblock_success_calls_datasource() = runTest {
        val ds = FakeBlockDataSource()
        ds.blocked.value = setOf(target.value)
        val r = repo(ds).unblock(owner, target)
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(1, ds.unblockCalls.size)
    }

    private fun accountId(v: String) = (AccountId.of(v) as Result.Ok).value
}
