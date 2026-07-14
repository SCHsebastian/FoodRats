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
    @Test fun write_objectionable()  = assertEquals(FeedStringKey.CommentsErrorObjectionable, CommentError.Write.Objectionable.toStringKey())
    @Test fun write_unavailable()    = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Write.Unavailable.toStringKey())
    @Test fun delete_not_author_or_owner() = assertEquals(FeedStringKey.CommentsErrorUnauthorized, CommentError.Delete.NotAuthorOrOwner.toStringKey())
    @Test fun delete_not_found()     = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Delete.NotFound.toStringKey())
    @Test fun delete_unavailable()   = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Delete.Unavailable.toStringKey())
    @Test fun edit_not_author()      = assertEquals(FeedStringKey.CommentsErrorUnauthorized, CommentError.Edit.NotAuthor.toStringKey())
    @Test fun edit_not_found()       = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Edit.NotFound.toStringKey())
    @Test fun edit_blank()           = assertEquals(FeedStringKey.CommentsErrorBlank,        CommentError.Edit.Blank.toStringKey())
    @Test fun edit_too_long()        = assertEquals(FeedStringKey.CommentsErrorTooLong,      CommentError.Edit.TooLong.toStringKey())
    @Test fun edit_objectionable()   = assertEquals(FeedStringKey.CommentsErrorObjectionable, CommentError.Edit.Objectionable.toStringKey())
    @Test fun edit_unavailable()     = assertEquals(FeedStringKey.CommentsErrorUnavailable,  CommentError.Edit.Unavailable.toStringKey())
}
