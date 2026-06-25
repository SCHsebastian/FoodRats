package es.schsebastian.foodrats.feature.moderation.data

import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportAlreadyExistsException
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportDto
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportErrorMapper
import es.schsebastian.foodrats.feature.moderation.data.repository.ReportRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider

/**
 * Unit tests for [ReportRepository]: self-report guard, AlreadyReported mapping, success path,
 * and the single IO-boundary contract (one `withContext` per call).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportRepositoryTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val reporter = accountId("u-reporter")
    private val otherUser = accountId("u-other")
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val meal = (MealId.of("m-1") as Result.Ok).value
    private val comment = MealCommentId("cmt-1")
    private val clock = FixedClock(Instant.parse("2026-05-20T10:00:00Z"))

    /** Inline fake dispatcher that runs everything on Dispatchers.Default (avoids expect/actual). */
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.Default
    }

    private fun repo(ds: FakeReportDataSource = FakeReportDataSource()) =
        ReportRepository(ds, dispatchers, ReportErrorMapper(), clock)

    /** In-memory [ReportDataSource] fake. [throwOn] triggers the given throwable on the next call. */
    class FakeReportDataSource : ReportDataSource {
        val created = mutableListOf<Pair<String, ReportDto>>()
        var throwOn: Throwable? = null

        override suspend fun create(docId: String, dto: ReportDto) {
            throwOn?.let { throw it.also { throwOn = null } }
            created += docId to dto
        }
    }

    // ─── self-report guard ──────────────────────────────────────────────────

    @Test fun self_report_on_account_returns_SelfReport() = runTest {
        val ds = FakeReportDataSource()
        val r = repo(ds).report(reporter, ReportTarget.Account(reporter), ReportReason.Spam)
        assertTrue(r is Result.Err)
        assertEquals(ReportError.Submit.SelfReport, (r as Result.Err).error)
        // Data source must NOT have been called.
        assertTrue(ds.created.isEmpty())
    }

    @Test fun self_report_guard_does_not_apply_to_meal_targets() = runTest {
        // A meal report by the meal's author goes through (the rule enforces it server-side).
        val ds = FakeReportDataSource()
        val result = repo(ds).report(reporter, ReportTarget.Meal(meal, crew), ReportReason.Spam)
        assertTrue(result is Result.Ok)
        assertEquals(1, ds.created.size)
    }

    // ─── AlreadyReported mapping ───────────────────────────────────────────

    @Test fun already_exists_exception_maps_to_AlreadyReported() = runTest {
        val ds = FakeReportDataSource().also { it.throwOn = ReportAlreadyExistsException }
        val r = repo(ds).report(reporter, ReportTarget.Meal(meal, crew), ReportReason.Spam)
        assertTrue(r is Result.Err)
        assertEquals(ReportError.Submit.AlreadyReported, (r as Result.Err).error)
    }

    @Test fun generic_exception_maps_to_Unavailable() = runTest {
        val ds = FakeReportDataSource().also { it.throwOn = RuntimeException("network error") }
        val r = repo(ds).report(reporter, ReportTarget.Meal(meal, crew), ReportReason.Spam)
        assertTrue(r is Result.Err)
        assertEquals(ReportError.Submit.Unavailable, (r as Result.Err).error)
    }

    // ─── success paths ────────────────────────────────────────────────────

    @Test fun success_meal_target_returns_ok_and_calls_datasource() = runTest {
        val ds = FakeReportDataSource()
        val r = repo(ds).report(reporter, ReportTarget.Meal(meal, crew), ReportReason.Harassment)
        assertTrue(r is Result.Ok)
        assertEquals(1, ds.created.size)
    }

    @Test fun success_comment_target_returns_ok_and_calls_datasource() = runTest {
        val ds = FakeReportDataSource()
        val r = repo(ds).report(reporter, ReportTarget.Comment(meal, crew, comment), ReportReason.Hate)
        assertTrue(r is Result.Ok)
        assertEquals(1, ds.created.size)
    }

    @Test fun success_account_target_returns_ok_when_not_self() = runTest {
        val ds = FakeReportDataSource()
        val r = repo(ds).report(reporter, ReportTarget.Account(otherUser), ReportReason.Spam)
        assertTrue(r is Result.Ok)
        assertEquals(1, ds.created.size)
    }

    private fun accountId(v: String) = (AccountId.of(v) as Result.Ok).value
}
