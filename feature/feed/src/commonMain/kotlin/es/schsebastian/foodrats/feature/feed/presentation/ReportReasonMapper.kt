package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.domain.moderation.ReportReason

/** Maps the presentation reason option to the domain [ReportReason]. */
internal fun FrReportReasonOption.toReason(): ReportReason = when (this) {
    FrReportReasonOption.CHILD_SAFETY -> ReportReason.ChildSafety
    FrReportReasonOption.SPAM       -> ReportReason.Spam
    FrReportReasonOption.HARASSMENT -> ReportReason.Harassment
    FrReportReasonOption.HATE       -> ReportReason.Hate
    FrReportReasonOption.SEXUAL     -> ReportReason.Sexual
    FrReportReasonOption.VIOLENCE   -> ReportReason.Violence
    FrReportReasonOption.OTHER      -> ReportReason.Other
}
