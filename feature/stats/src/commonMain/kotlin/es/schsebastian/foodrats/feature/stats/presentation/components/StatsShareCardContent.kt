package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import es.schsebastian.foodrats.core.designsystem.templates.FrAwardShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrStreakShareCard
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

/**
 * Off-screen content the platform renderer rasterizes for an AWARD share (spec §8.2). Wrapped in
 * [FoodRatsTheme] for the Iron & Ember palette; the on-card chrome — the award banner
 * ([ShareCardStringKey.AwardBestMeal]), the score pill ([StatsStringKey.ShareScoreFormat]) and the
 * brand footer — is resolved HERE (i18n stays out of the ViewModel). [plate] is the decoded bitmap
 * the controller passes in (`null` → branded placeholder).
 */
@Composable
fun AwardShareCardContent(model: AwardShareCardModel, plate: ImageBitmap?) {
    val rounded = formatOneDecimal(model.score.toFloat())
    val scoreLabel = if (model.ratingCount > 0) {
        resolve(StatsStringKey.ShareScoreFormat, rounded, model.ratingCount)
    } else {
        null
    }
    FoodRatsTheme {
        FrAwardShareCard(
            plate = plate,
            awardLabel = resolve(ShareCardStringKey.AwardBestMeal),
            dishName = model.dishName,
            authorName = model.authorName,
            scoreLabel = scoreLabel,
            dayEmote = model.dayEmote,
            footerBrand = resolve(ShareCardStringKey.BrandFooter),
            format = ShareCardFormat.Story,
        )
    }
}

/**
 * Off-screen content the platform renderer rasterizes for a STREAK share (spec §8.2, TRACK B). The
 * streak count is always the hero; [plate] is the decoded plate photo the controller passes in
 * (`null` → the original solid-surface design, matching [model]'s advisory [StreakShareCardModel.photoUrl]
 * — `null` when there was no plate to decode). Headline ([ShareCardStringKey.StreakHeadline], arg =
 * days) / subline are resolved here.
 */
@Composable
fun StreakShareCardContent(model: StreakShareCardModel, plate: ImageBitmap? = null) {
    FoodRatsTheme {
        FrStreakShareCard(
            streakDays = model.streakDays,
            headline = resolve(ShareCardStringKey.StreakHeadline, model.streakDays),
            subline = resolve(ShareCardStringKey.StreakSubline),
            dayEmote = model.dayEmote,
            footerBrand = resolve(ShareCardStringKey.BrandFooter),
            format = ShareCardFormat.Story,
            plate = plate,
        )
    }
}
