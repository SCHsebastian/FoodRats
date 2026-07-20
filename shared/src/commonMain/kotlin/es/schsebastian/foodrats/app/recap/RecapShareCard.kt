package es.schsebastian.foodrats.app.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.templates.FrPlateShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrStreakShareCard
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.toFixed

/**
 * Maps a [RecapScene] to a shareable story-card model + renders it off-screen (spec §8.1 row 4, the
 * weekly-digest-story share CTA). The recap scenes were DESIGNED to double as share-card content
 * (w2-weekly-digest handoff), so this reuses the existing `Fr*ShareCard` templates rather than
 * inventing recap-only cards:
 *  - [RecapScene.TopMeal]  → [FrPlateShareCard] (the week's best plate, photo + score)
 *  - [RecapScene.Streak]   → [FrStreakShareCard] (the personal posting streak)
 *  - [RecapScene.YourWeek] → [FrStreakShareCard] (the closing summary, streak as hero)
 *
 * Other scenes (cover, best-cook, most-prolific, badges, cuisines) are NOT shareable — they either
 * carry another member's data (PII concerns) or have no first-class card; [RecapScene.toShareCard]
 * returns `null` for them and the screen hides the CTA. The `kind.wire` slug is the analytics
 * `item_id` (PII-free) on the reused `share` event.
 *
 * The on-card chrome is resolved INSIDE the composable handed to the renderer (the renderer composes
 * it off-screen, where `resolve` works) — i18n stays out of the ViewModel, mirroring the feed/stats
 * share-content pattern.
 */
sealed interface RecapShareCard {
    /** Whether this card needs a plate photo decoded before render (null for the streak cards). */
    val plateUrl: String?

    /** The week's top plate. */
    data class Plate(
        override val plateUrl: String,
        val dishName: String,
        val authorName: String,
        val score: Double,
        val ratingCount: Int,
        val dayEmote: String,
    ) : RecapShareCard

    /**
     * A streak milestone / the closing "your week" summary. The count is always the hero;
     * [plateUrl] (TRACK B) is the scene's advisory plate photo — `null` renders the original
     * solid-surface card, matching [RecapScene.Streak.photoUrl]/[RecapScene.YourWeek.photoUrl].
     */
    data class Streak(
        val streakDays: Int,
        val dayEmote: String,
        override val plateUrl: String? = null,
    ) : RecapShareCard
}

/**
 * Maps a recap [RecapScene] to a [RecapShareCard], or `null` when the scene is not shareable.
 * [todayEmote] is the brand day-motif glyph the caller resolves once (`DailyEmote.forDay(today)`).
 */
fun RecapScene.toShareCard(todayEmote: String): RecapShareCard? = when (this) {
    is RecapScene.TopMeal -> RecapShareCard.Plate(
        plateUrl = photoUrl,
        dishName = dishName,
        authorName = authorName,
        score = score,
        ratingCount = ratingCount,
        dayEmote = todayEmote,
    )
    is RecapScene.Streak -> RecapShareCard.Streak(streakDays = streakDays, dayEmote = todayEmote, plateUrl = photoUrl)
    is RecapScene.YourWeek -> RecapShareCard.Streak(streakDays = streakDays, dayEmote = todayEmote, plateUrl = photoUrl)
    is RecapScene.Cover,
    is RecapScene.BestCook,
    is RecapScene.MostProlific,
    is RecapScene.Badges,
    is RecapScene.Cuisines,
    -> null
}

/** Convenience: is there a share card for this scene at all (drives the CTA's visibility). */
fun RecapScene.isShareable(): Boolean = when (this) {
    is RecapScene.TopMeal, is RecapScene.Streak, is RecapScene.YourWeek -> true
    is RecapScene.Cover, is RecapScene.BestCook, is RecapScene.MostProlific,
    is RecapScene.Badges, is RecapScene.Cuisines -> false
}

/**
 * The off-screen content the platform renderer rasterizes for a recap share. Wrapped in
 * [FoodRatsTheme] so the Iron & Ember palette resolves during the one-shot capture; the chrome
 * (score label, brand footer, streak headline/subline) is resolved here. [plate] is the decoded
 * bitmap the controller passes in (`null` → branded placeholder; always `null` for the streak card).
 */
@Composable
fun RecapShareCardContent(card: RecapShareCard, plate: ImageBitmap?) {
    FoodRatsTheme {
        when (card) {
            is RecapShareCard.Plate -> FrPlateShareCard(
                plate = plate,
                dishName = card.dishName,
                authorName = card.authorName,
                scoreLabel = if (card.ratingCount > 0) {
                    resolve(SharedStringKey.RecapTopMealScore, card.score.toFixed(1), card.ratingCount)
                } else {
                    null
                },
                dayEmote = card.dayEmote,
                footerBrand = resolve(ShareCardStringKey.BrandFooter),
                format = ShareCardFormat.Story,
            )
            is RecapShareCard.Streak -> FrStreakShareCard(
                streakDays = card.streakDays,
                headline = resolve(ShareCardStringKey.StreakHeadline, card.streakDays),
                subline = resolve(ShareCardStringKey.StreakSubline),
                dayEmote = card.dayEmote,
                footerBrand = resolve(ShareCardStringKey.BrandFooter),
                format = ShareCardFormat.Story,
                plate = plate,
            )
        }
    }
}
