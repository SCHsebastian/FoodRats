package es.schsebastian.foodrats.feature.moderation.data.firebase

import es.schsebastian.foodrats.core.domain.moderation.ReportError

/**
 * Maps an arbitrary backend [Throwable] to a typed [ReportError.Submit]. Raw throwables are first
 * classified into a typed [ModerationFault] by [toModerationFault] (the single message-inspection
 * seam); this mapper then translates **by fault type**, never by message.
 *
 * The deterministic report-doc id (`{reporterUid}_{targetKey}`) makes a re-report an
 * `ALREADY_EXISTS` collision, which surfaces as [ReportError.Submit.AlreadyReported]. The other guards
 * — [ReportError.Submit.NotSignedIn] / [ReportError.Submit.SelfReport] — are pre-flight checks in the
 * repository, never produced here.
 */
class ReportErrorMapper {
    fun map(t: Throwable): ReportError.Submit = when (t.toModerationFault()) {
        ModerationFault.AlreadyExists -> ReportError.Submit.AlreadyReported
        ModerationFault.PermissionDenied -> ReportError.Submit.Unavailable
        ModerationFault.Network -> ReportError.Submit.Unavailable
        ModerationFault.Unavailable -> ReportError.Submit.Unavailable
    }
}
