package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

fun CommentError.toStringKey(): StringKey = when (this) {
    CommentError.Read.Unauthorized  -> FeedStringKey.CommentsErrorUnauthorized
    CommentError.Read.Unavailable   -> FeedStringKey.CommentsErrorUnavailable
    CommentError.Write.Unauthorized -> FeedStringKey.CommentsErrorUnauthorized
    CommentError.Write.Blank        -> FeedStringKey.CommentsErrorBlank
    CommentError.Write.TooLong      -> FeedStringKey.CommentsErrorTooLong
    CommentError.Write.Objectionable -> FeedStringKey.CommentsErrorObjectionable
    CommentError.Write.Unavailable  -> FeedStringKey.CommentsErrorUnavailable
    CommentError.Delete.NotAuthorOrOwner -> FeedStringKey.CommentsErrorUnauthorized
    CommentError.Delete.NotFound         -> FeedStringKey.CommentsErrorUnavailable
    CommentError.Delete.Unavailable      -> FeedStringKey.CommentsErrorUnavailable
    CommentError.Edit.NotAuthor          -> FeedStringKey.CommentsErrorUnauthorized
    CommentError.Edit.NotFound           -> FeedStringKey.CommentsErrorUnavailable
    CommentError.Edit.Blank              -> FeedStringKey.CommentsErrorBlank
    CommentError.Edit.TooLong            -> FeedStringKey.CommentsErrorTooLong
    CommentError.Edit.Objectionable      -> FeedStringKey.CommentsErrorObjectionable
    CommentError.Edit.Unavailable        -> FeedStringKey.CommentsErrorUnavailable
}
