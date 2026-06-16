package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import es.schsebastian.foodrats.core.designsystem.templates.FrPlateShareCard
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.math.round

/**
 * The off-screen content the platform renderer rasterizes for a plate share (spec §8.2). Wrapped in
 * [FoodRatsTheme] so the Iron & Ember palette + `FrSemanticColors` resolve during the one-shot
 * capture; the on-card chrome — `scoreLabel` (via [FeedStringKey.RatingSummary]) and the brand
 * footer (via [ShareCardStringKey.BrandFooter]) — is resolved HERE: `resolve` is `@Composable`, and
 * this lambda is composed by the renderer off-screen, so i18n stays out of the ViewModel.
 *
 * The domain→props mapping ([FeedMealUi.toPlateCard]) also runs here (the feature presentation
 * layer), fed the just-resolved score label; the mapper itself is unit-tested separately (§12).
 *
 * [plate] is the decoded bitmap the controller passes in (`null` on decode failure → branded
 * placeholder). The ViewModel hands `{ plate -> PlateShareCardContent(meal, plate) }` to
 * `StoryShareController.share(...)`.
 */
@Composable
fun PlateShareCardContent(meal: FeedMealUi, plate: ImageBitmap?) {
    val model = meal.toPlateCard(scoreLabel = scoreLabelFor(meal))
    FoodRatsTheme {
        FrPlateShareCard(
            plate = plate,
            dishName = model.dishName,
            authorName = model.authorName,
            scoreLabel = model.scoreLabel,
            dayEmote = model.dayEmote,
            footerBrand = resolve(ShareCardStringKey.BrandFooter),
            format = ShareCardFormat.Story,
        )
    }
}

/** "8.4 ★ · 5" when there are ratings, else null (hides the score pill). */
@Composable
private fun scoreLabelFor(meal: FeedMealUi): String? {
    val avg = meal.averageScore
    if (avg == null || meal.ratingCount <= 0) return null
    val rounded = (round(avg * 10) / 10.0).toString()
    return resolve(FeedStringKey.RatingSummary, rounded, meal.ratingCount)
}
