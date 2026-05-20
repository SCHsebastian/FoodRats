package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentErrorToStringKeyTest {
    @Test fun read_unauthorized()    = assertEquals(FeedStringKey.CommentsErrorUnauthorized, CommentError.Read.Unauthorized.toStringKey())
    @Test fun read_unavailable()     = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Read.Unavailable.toStringKey())
    @Test fun write_unauthorized()   = assertEquals(FeedStringKey.CommentsErrorUnauthorized, CommentError.Write.Unauthorized.toStringKey())
    @Test fun write_blank()          = assertEquals(FeedStringKey.CommentsErrorBlank,        CommentError.Write.Blank.toStringKey())
    @Test fun write_too_long()       = assertEquals(FeedStringKey.CommentsErrorTooLong,      CommentError.Write.TooLong.toStringKey())
    @Test fun write_unavailable()    = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Write.Unavailable.toStringKey())
}
