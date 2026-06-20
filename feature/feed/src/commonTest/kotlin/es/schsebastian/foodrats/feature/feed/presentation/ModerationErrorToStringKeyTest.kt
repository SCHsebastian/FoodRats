package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the feed-owned report/block error → string-key mappers (UGC compliance §4/§5). */
class ModerationErrorToStringKeyTest {
    @Test fun report_not_signed_in()    = assertEquals(FeedStringKey.ReportErrorUnavailable, ReportError.Submit.NotSignedIn.toStringKey())
    @Test fun report_self_report()      = assertEquals(FeedStringKey.ReportErrorUnavailable, ReportError.Submit.SelfReport.toStringKey())
    @Test fun report_already_reported() = assertEquals(FeedStringKey.ReportErrorAlreadyReported, ReportError.Submit.AlreadyReported.toStringKey())
    @Test fun report_unavailable()      = assertEquals(FeedStringKey.ReportErrorUnavailable, ReportError.Submit.Unavailable.toStringKey())

    @Test fun block_self_block()        = assertEquals(FeedStringKey.BlockErrorUnavailable, BlockError.Write.SelfBlock.toStringKey())
    @Test fun block_write_unavailable() = assertEquals(FeedStringKey.BlockErrorUnavailable, BlockError.Write.Unavailable.toStringKey())
    @Test fun block_read_unavailable()  = assertEquals(FeedStringKey.BlockErrorUnavailable, BlockError.Read.Unavailable.toStringKey())
}
