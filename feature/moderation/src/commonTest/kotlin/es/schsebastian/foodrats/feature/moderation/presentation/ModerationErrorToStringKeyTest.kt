package es.schsebastian.foodrats.feature.moderation.presentation

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the exhaustiveness of both moderation error mappers. A new leaf added to either
 * `:core:domain` error tree ([BlockError] / [ReportError]) breaks `toStringKey`'s `when` at compile
 * time; these per-leaf assertions guard against an accidental wrong mapping.
 */
class ModerationErrorToStringKeyTest {

    // BlockError
    @Test fun blockSelf() =
        assertEquals(ModerationStringKey.ErrorBlockSelf, BlockError.Write.SelfBlock.toStringKey())

    @Test fun blockWriteUnavailable() =
        assertEquals(ModerationStringKey.ErrorBlockUnavailable, BlockError.Write.Unavailable.toStringKey())

    @Test fun blockReadUnavailable() =
        assertEquals(ModerationStringKey.ErrorBlockReadUnavailable, BlockError.Read.Unavailable.toStringKey())

    // ReportError
    @Test fun reportNotSignedIn() =
        assertEquals(ModerationStringKey.ErrorReportNotSignedIn, ReportError.Submit.NotSignedIn.toStringKey())

    @Test fun reportSelf() =
        assertEquals(ModerationStringKey.ErrorReportSelf, ReportError.Submit.SelfReport.toStringKey())

    @Test fun reportAlready() =
        assertEquals(ModerationStringKey.ErrorReportAlready, ReportError.Submit.AlreadyReported.toStringKey())

    @Test fun reportUnavailable() =
        assertEquals(ModerationStringKey.ErrorReportUnavailable, ReportError.Submit.Unavailable.toStringKey())
}
