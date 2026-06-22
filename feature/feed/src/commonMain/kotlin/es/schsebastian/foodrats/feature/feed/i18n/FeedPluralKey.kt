package es.schsebastian.foodrats.feature.feed.i18n

import es.schsebastian.foodrats.core.i18n.PluralStringKey
import foodrats.feature.feed.generated.resources.Res
import foodrats.feature.feed.generated.resources.feed_plates_count
import foodrats.feature.feed.generated.resources.feed_rating_summary_votes
import foodrats.feature.feed.generated.resources.feed_reactions_label
import foodrats.feature.feed.generated.resources.feed_score_summary_votes
import org.jetbrains.compose.resources.PluralStringResource

/**
 * Sibling of [FeedStringKey] for quantity-aware feed strings. Backed by `<plurals>`
 * resources and resolved via `resolvePlural` so "1 plate"/"2 plates" and
 * "1 vote"/"2 votes" follow each locale's CLDR plural rules instead of a hardcoded
 * plural noun.
 */
enum class FeedPluralKey(override val resourceId: PluralStringResource) : PluralStringKey {
    PlatesCount(Res.plurals.feed_plates_count),
    RatingSummaryVotes(Res.plurals.feed_rating_summary_votes),
    /**
     * C8b — glyph-free score-summary caption for the meal-detail ScoreStoryCard when
     * [FrScoreStyle] ≠ [FrScoreStyle.Stars]. Takes %1$s = pre-rendered score string
     * (e.g. "3.5", "😋") and %2$d = vote count.
     */
    ScoreSummaryVotes(Res.plurals.feed_score_summary_votes),

    /** Reaction-count a11y label — "1 reaction" / "N reactions". Takes %1$d = count. */
    ReactionsLabel(Res.plurals.feed_reactions_label),
}
