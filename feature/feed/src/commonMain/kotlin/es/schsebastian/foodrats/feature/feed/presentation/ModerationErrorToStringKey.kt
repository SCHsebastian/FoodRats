package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

/**
 * Maps the `:core:domain` report/block errors to feed string keys. Feed renders the report sheet and
 * block action on meal detail (UGC compliance §4/§5) and dispatches them through the `:core:domain`
 * ports, so it owns the user-facing message here. Exhaustive `when` (no `else`) — a new error leaf
 * forces an update; `ModerationErrorToStringKeyTest` locks it.
 */
fun ReportError.toStringKey(): StringKey = when (this) {
    // Both pre-flight guards (you can't report yourself / you must be signed in) and the backend bucket
    // collapse to the generic "couldn't send" message — they're not reachable from the UI (the report
    // action is hidden on your own content and only shown while signed in).
    ReportError.Submit.NotSignedIn     -> FeedStringKey.ReportErrorUnavailable
    ReportError.Submit.SelfReport      -> FeedStringKey.ReportErrorUnavailable
    ReportError.Submit.AlreadyReported -> FeedStringKey.ReportErrorAlreadyReported
    ReportError.Submit.Unavailable     -> FeedStringKey.ReportErrorUnavailable
}

fun BlockError.toStringKey(): StringKey = when (this) {
    // SelfBlock is a pre-flight guard (the block action is hidden on your own content), so it collapses
    // to the generic "couldn't block" message alongside the connectivity bucket.
    BlockError.Write.SelfBlock   -> FeedStringKey.BlockErrorUnavailable
    BlockError.Write.Unavailable -> FeedStringKey.BlockErrorUnavailable
    BlockError.Read.Unavailable  -> FeedStringKey.BlockErrorUnavailable
}
