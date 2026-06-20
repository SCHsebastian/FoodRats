package es.schsebastian.foodrats.feature.moderation.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.moderation.generated.resources.Res
import foodrats.feature.moderation.generated.resources.moderation_blocked_back_cta
import foodrats.feature.moderation.generated.resources.moderation_blocked_empty_headline
import foodrats.feature.moderation.generated.resources.moderation_blocked_empty_subtext
import foodrats.feature.moderation.generated.resources.moderation_blocked_member_deleted
import foodrats.feature.moderation.generated.resources.moderation_blocked_title
import foodrats.feature.moderation.generated.resources.moderation_blocked_unblock_cta
import foodrats.feature.moderation.generated.resources.moderation_unblock_success
import foodrats.feature.moderation.generated.resources.moderation_error_block_read_unavailable
import foodrats.feature.moderation.generated.resources.moderation_error_block_self
import foodrats.feature.moderation.generated.resources.moderation_error_block_unavailable
import foodrats.feature.moderation.generated.resources.moderation_error_report_already
import foodrats.feature.moderation.generated.resources.moderation_error_report_not_signed_in
import foodrats.feature.moderation.generated.resources.moderation_error_report_self
import foodrats.feature.moderation.generated.resources.moderation_error_report_unavailable
import org.jetbrains.compose.resources.StringResource

enum class ModerationStringKey(override val resourceId: StringResource) : StringKey {
    BlockedTitle(Res.string.moderation_blocked_title),
    BlockedBackCta(Res.string.moderation_blocked_back_cta),
    BlockedEmptyHeadline(Res.string.moderation_blocked_empty_headline),
    BlockedEmptySubtext(Res.string.moderation_blocked_empty_subtext),
    BlockedUnblockCta(Res.string.moderation_blocked_unblock_cta),
    BlockedMemberDeleted(Res.string.moderation_blocked_member_deleted),
    /** Transient toast shown after a successful unblock (UGC compliance §5 success feedback). */
    UnblockSuccess(Res.string.moderation_unblock_success),

    // BlockError
    ErrorBlockSelf(Res.string.moderation_error_block_self),
    ErrorBlockUnavailable(Res.string.moderation_error_block_unavailable),
    ErrorBlockReadUnavailable(Res.string.moderation_error_block_read_unavailable),

    // ReportError
    ErrorReportNotSignedIn(Res.string.moderation_error_report_not_signed_in),
    ErrorReportSelf(Res.string.moderation_error_report_self),
    ErrorReportAlready(Res.string.moderation_error_report_already),
    ErrorReportUnavailable(Res.string.moderation_error_report_unavailable),
}
