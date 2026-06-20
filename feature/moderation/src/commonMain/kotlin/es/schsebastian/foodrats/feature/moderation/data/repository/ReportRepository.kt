package es.schsebastian.foodrats.feature.moderation.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportAlreadyExistsException
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportErrorMapper
import es.schsebastian.foodrats.feature.moderation.data.firebase.targetKey
import es.schsebastian.foodrats.feature.moderation.data.firebase.toDto
import kotlinx.coroutines.withContext

/**
 * Firestore-backed [ReportPort] (UGC compliance §4). Owns the single dispatcher boundary
 * (`withContext(dispatchers.io)`) and the vendor-throwable → typed-[ReportError] translation via
 * [ReportErrorMapper]. Mirrors `FirebaseCrewRepository`.
 *
 * Pre-flight guards (cheap, vendor-free, before the IO boundary):
 *  - account self-report → [ReportError.Submit.SelfReport];
 *  - the deterministic id `{reporter}|{targetKey}` makes a re-report collide, surfaced as
 *    [ReportError.Submit.AlreadyReported] by the data source.
 *
 * [ReportError.Submit.NotSignedIn] is produced by the call-site ViewModel (which resolves the
 * reporter from the session); the port takes an explicit [reporter], so the repository never needs the
 * session itself.
 */
internal class ReportRepository(
    private val dataSource: ReportDataSource,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: ReportErrorMapper,
    private val clock: Clock,
) : ReportPort {

    override suspend fun report(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, ReportError> {
        if (target is ReportTarget.Account && target.accountId == reporter) {
            return Result.failure(ReportError.Submit.SelfReport)
        }
        return withContext(dispatchers.io) {
            val docId = "${reporter.value}|${target.targetKey()}"
            val dto = target.toDto(reporter.value, reason, clock.now().toEpochMilliseconds())
            runCatching { dataSource.create(docId, dto) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { t ->
                    Result.failure(
                        when (t) {
                            ReportAlreadyExistsException -> ReportError.Submit.AlreadyReported
                            else -> errorMapper.map(t)
                        },
                    )
                },
            )
        }
    }
}
