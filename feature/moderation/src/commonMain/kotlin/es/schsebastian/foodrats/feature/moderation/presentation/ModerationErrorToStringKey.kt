package es.schsebastian.foodrats.feature.moderation.presentation

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey

/**
 * Exhaustive [BlockError] → [ModerationStringKey] mapper. Locked by `ModerationErrorToStringKeyTest`;
 * a new error leaf added to the `:core:domain` [BlockError] tree won't compile here until it gets a
 * string key.
 */
fun BlockError.toStringKey(): ModerationStringKey = when (this) {
    BlockError.Write.SelfBlock -> ModerationStringKey.ErrorBlockSelf
    BlockError.Write.Unavailable -> ModerationStringKey.ErrorBlockUnavailable
    BlockError.Read.Unavailable -> ModerationStringKey.ErrorBlockReadUnavailable
}

/**
 * Exhaustive [ReportError] → [ModerationStringKey] mapper. Locked by `ModerationErrorToStringKeyTest`.
 */
fun ReportError.toStringKey(): ModerationStringKey = when (this) {
    ReportError.Submit.NotSignedIn -> ModerationStringKey.ErrorReportNotSignedIn
    ReportError.Submit.SelfReport -> ModerationStringKey.ErrorReportSelf
    ReportError.Submit.AlreadyReported -> ModerationStringKey.ErrorReportAlready
    ReportError.Submit.Unavailable -> ModerationStringKey.ErrorReportUnavailable
}
