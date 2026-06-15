package es.schsebastian.foodrats.core.i18n

import foodrats.core.i18n.generated.resources.Res
import foodrats.core.i18n.generated.resources.share_card_award_best_cook
import foodrats.core.i18n.generated.resources.share_card_award_best_meal
import foodrats.core.i18n.generated.resources.share_card_brand_footer
import foodrats.core.i18n.generated.resources.share_card_streak_headline
import foodrats.core.i18n.generated.resources.share_card_streak_subline
import foodrats.core.i18n.generated.resources.share_failed
import foodrats.core.i18n.generated.resources.share_opened_sheet
import foodrats.core.i18n.generated.resources.share_succeeded
import org.jetbrains.compose.resources.StringResource

/**
 * On-card chrome + share-outcome toast strings for the shareable story cards (spec §10).
 *
 * Lives in `:core:i18n` (cross-feature) so both `:feature:feed` (plate share) and `:feature:stats`
 * (award/streak share) — and the `shared` recap player — resolve the SAME card text without
 * duplicating it. The design-system `Fr*ShareCard` templates take FINISHED strings (they hold no
 * i18n surface); features resolve these keys and pass the results in.
 *
 * The card-chrome keys ([BrandFooter], [AwardBestMeal], [AwardBestCook], [StreakHeadline],
 * [StreakSubline]) are resolved INSIDE the `@Composable` content lambda handed to
 * `StoryCardRenderer.renderToPng` (the renderer composes that lambda off-screen, where `resolve`
 * works). The toast keys ([ShareSucceeded], [ShareOpenedSheet], [ShareFailed]) are resolved on the
 * screen when the ViewModel surfaces a share outcome.
 */
enum class ShareCardStringKey(override val resourceId: StringResource) : StringKey {
    BrandFooter(Res.string.share_card_brand_footer),
    AwardBestMeal(Res.string.share_card_award_best_meal),
    AwardBestCook(Res.string.share_card_award_best_cook),

    /** Streak headline; takes the streak-day count as `%1$d`. */
    StreakHeadline(Res.string.share_card_streak_headline),
    StreakSubline(Res.string.share_card_streak_subline),

    ShareSucceeded(Res.string.share_succeeded),
    ShareOpenedSheet(Res.string.share_opened_sheet),
    ShareFailed(Res.string.share_failed),
}
