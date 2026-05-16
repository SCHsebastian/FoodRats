package es.schsebastian.foodrats.feature.feed.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.feed.generated.resources.Res
import foodrats.feature.feed.generated.resources.feed_capture_cta
import foodrats.feature.feed.generated.resources.feed_empty_headline
import foodrats.feature.feed.generated.resources.feed_empty_subtext
import foodrats.feature.feed.generated.resources.feed_error_crew_not_found
import foodrats.feature.feed.generated.resources.feed_error_not_signed_in
import foodrats.feature.feed.generated.resources.feed_error_unauthorized
import foodrats.feature.feed.generated.resources.feed_error_unavailable
import foodrats.feature.feed.generated.resources.feed_next_day
import foodrats.feature.feed.generated.resources.feed_no_active_crew_headline
import foodrats.feature.feed.generated.resources.feed_no_active_crew_subtext
import foodrats.feature.feed.generated.resources.feed_prev_day
import foodrats.feature.feed.generated.resources.feed_title
import org.jetbrains.compose.resources.StringResource

enum class FeedStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.feed_title),
    EmptyHeadline(Res.string.feed_empty_headline),
    EmptySubtext(Res.string.feed_empty_subtext),
    CaptureCta(Res.string.feed_capture_cta),
    PrevDay(Res.string.feed_prev_day),
    NextDay(Res.string.feed_next_day),
    NoActiveCrewHeadline(Res.string.feed_no_active_crew_headline),
    NoActiveCrewSubtext(Res.string.feed_no_active_crew_subtext),
    ErrorNotSignedIn(Res.string.feed_error_not_signed_in),
    ErrorUnauthorized(Res.string.feed_error_unauthorized),
    ErrorCrewNotFound(Res.string.feed_error_crew_not_found),
    ErrorUnavailable(Res.string.feed_error_unavailable),
}
