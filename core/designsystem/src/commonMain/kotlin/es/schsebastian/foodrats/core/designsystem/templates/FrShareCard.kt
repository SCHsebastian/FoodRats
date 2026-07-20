package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Aspect ratio of a share card. Presentation-only enum — templates never see domain types.
 *
 * - [Square] → 1:1, the in-app preview / square-export variant (Meta square asset).
 * - [Story] → 9:16, the Instagram-Stories export variant (Meta's recommended Stories asset).
 *
 * The platform renderer (`StoryCardRenderer`, `:core:data`, Wave 3 platform task) captures the
 * card off-screen at a fixed pixel size (1080×1080 / 1080×1920); the ratio here keeps the
 * on-screen catalog preview faithful to what gets exported. The layout is deterministic — no
 * scroll, no animation — so a one-shot off-screen capture renders the whole tree.
 */
enum class ShareCardFormat { Square, Story }

private fun ShareCardFormat.ratio(): Float = when (this) {
    ShareCardFormat.Square -> 1f
    ShareCardFormat.Story -> 9f / 16f
}

/**
 * A branded, share-to-Stories highlight of a single plate (spec §4.1).
 *
 * Pure design-system template: takes **primitives only** — never a domain type. The owning feature
 * maps `FeedMealUi` → these props ([FeedMealUi.toPlateCard] etc.) and supplies the [plate] as an
 * already-decoded [ImageBitmap] (the platform renderer pre-decodes the signed URL off-screen, since
 * an async `AsyncImage` can't resolve inside a one-shot capture — spec §5). A null [plate] renders a
 * branded placeholder (the [dayEmote] motif on a solid surface), never a broken image.
 *
 * All on-card chrome text is pre-resolved by the caller and passed in (this layer holds no
 * `StringKey`s): [scoreLabel] is assembled at the call site via `FeedStringKey.RatingSummary`,
 * [footerBrand] via `ShareCardStringKey.BrandFooter`.
 *
 * @param plate the decoded plate photo; null → branded placeholder.
 * @param dishName the dish title (large headline).
 * @param authorName who cooked / posted it.
 * @param scoreLabel pre-formatted "8.4 ★ · 5"; null hides the score chip (no ratings yet).
 * @param dayEmote the per-day brand motif glyph (`DailyEmote.forDay(day)`).
 * @param footerBrand the on-card wordmark footer (routed through i18n by the caller).
 * @param format [ShareCardFormat.Square] or [ShareCardFormat.Story].
 */
@Composable
fun FrPlateShareCard(
    plate: ImageBitmap?,
    dishName: String,
    authorName: String,
    scoreLabel: String?,
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
) {
    ShareCardSurface(format = format, modifier = modifier) {
        PlateBackdrop(plate = plate, dayEmote = dayEmote)
        ScrimColumn {
            DayEmote(dayEmote)
            Spacer(Modifier.size(Spacing.sm))
            CardHeadline(dishName)
            CardSubline(authorName)
            scoreLabel?.let {
                Spacer(Modifier.size(Spacing.sm))
                ScorePill(it)
            }
            Spacer(Modifier.size(Spacing.lg))
            BrandFooter(footerBrand)
        }
    }
}

/**
 * A branded share card for an award / achievement on a plate ("Best meal", "Best cook" — spec §4.1).
 *
 * Same primitive-only contract as [FrPlateShareCard], plus an [awardLabel] banner painted in the
 * `celebration` semantic color. The caller resolves `ShareCardStringKey.AwardBestMeal` /
 * `AwardBestCook` and passes the finished label in.
 *
 * @param awardLabel the celebratory banner text (e.g. resolve(ShareCardStringKey.AwardBestMeal)).
 */
@Composable
fun FrAwardShareCard(
    plate: ImageBitmap?,
    awardLabel: String,
    dishName: String,
    authorName: String,
    scoreLabel: String?,
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    ShareCardSurface(format = format, modifier = modifier) {
        PlateBackdrop(plate = plate, dayEmote = dayEmote)
        ScrimColumn {
            AwardBanner(label = awardLabel, container = semantic.celebration, onContainer = semantic.onCelebration)
            Spacer(Modifier.size(Spacing.sm))
            CardHeadline(dishName)
            CardSubline(authorName)
            scoreLabel?.let {
                Spacer(Modifier.size(Spacing.sm))
                ScorePill(it)
            }
            Spacer(Modifier.size(Spacing.lg))
            BrandFooter(footerBrand)
        }
    }
}

/**
 * A branded share card for a streak milestone (spec §4.1).
 *
 * [plate] is `null` by default → the original solid-surface design: the streak count is the hero,
 * painted in the `streakHot` semantic color on a solid branded surface, exactly as before this param
 * was added. When [plate] is non-null (a decoded plate photo, e.g. the week's best/most-recent meal),
 * the card instead renders full-bleed over that photo — [PlateBackdrop] + a scrim, giant numeral and
 * headline/subline/footer restyled for on-scrim (white) legibility — mirroring [FrPlateShareCard]'s
 * photo treatment. [headline] / [subline] are pre-resolved
 * (`ShareCardStringKey.StreakHeadline(streakDays)` / `StreakSubline`); [streakDays] is kept for the
 * giant numeral the headline references.
 *
 * @param streakDays the streak length, shown as the giant numeral.
 * @param headline the pre-resolved streak headline ("14-day streak 🔥").
 * @param subline the pre-resolved supporting line ("Keep it cooking").
 * @param plate an already-decoded plate photo for the full-bleed variant; `null` → the solid-surface
 *   design.
 */
@Composable
fun FrStreakShareCard(
    streakDays: Int,
    headline: String,
    subline: String,
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
    plate: ImageBitmap? = null,
) {
    val semantic = LocalFrSemanticColors.current
    if (plate == null) {
        ShareCardSurface(
            format = format,
            background = MaterialTheme.colorScheme.surface,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayEmote,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(Spacing.md))
                Text(
                    text = streakDays.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = semantic.streakHot,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(Spacing.xl))
                BrandFooter(footerBrand, onScrim = false)
            }
        }
    } else {
        ShareCardSurface(format = format, modifier = modifier) {
            PlateBackdrop(plate = plate, dayEmote = dayEmote)
            ScrimColumn {
                DayEmote(dayEmote)
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = streakDays.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = semantic.streakHot,
                    maxLines = 1,
                )
                Spacer(Modifier.size(Spacing.sm))
                CardHeadline(headline)
                CardSubline(subline)
                Spacer(Modifier.size(Spacing.lg))
                BrandFooter(footerBrand)
            }
        }
    }
}

// region — shared building blocks (private; not part of the public DS surface)

/**
 * The fixed-ratio rounded surface every share card sits on. [content] is a Box scope so cards can
 * stack a full-bleed backdrop behind a foreground column.
 */
@Composable
private fun ShareCardSurface(
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(format.ratio())
            .clip(RoundedCornerShape(Radius.lg)),
        color = background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** Full-bleed plate photo, or a branded placeholder (the day motif on a solid surface). */
@Composable
private fun PlateBackdrop(plate: ImageBitmap?, dayEmote: String) {
    if (plate != null) {
        Image(
            bitmap = plate,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = dayEmote, style = MaterialTheme.typography.displayLarge)
        }
    }
}

/**
 * A bottom-anchored column over a top-transparent → bottom-scrim gradient so white-on-photo text
 * stays legible regardless of the underlying plate (the protection-gradient convention).
 */
@Composable
private fun ScrimColumn(content: @Composable () -> Unit) {
    val scrim = LocalFrSemanticColors.current.scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to scrim.copy(alpha = 0.25f),
                    1f to scrim.copy(alpha = 0.85f),
                ),
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xl)) { content() }
    }
}

@Composable
private fun DayEmote(emote: String) {
    Text(text = emote, style = MaterialTheme.typography.displaySmall)
}

@Composable
private fun AwardBanner(label: String, container: Color, onContainer: Color) {
    Surface(shape = RoundedCornerShape(Radius.pill), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun CardHeadline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = LocalFrSemanticColors.current.onScrim,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardSubline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalFrSemanticColors.current.onScrim.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ScorePill(label: String) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = LocalFrSemanticColors.current.scrim.copy(alpha = 0.55f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalFrSemanticColors.current.onScrim,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/**
 * On-card wordmark footer. Over a photo scrim the text sits on the protection gradient ([onScrim]
 * true); on the solid streak surface it uses the muted on-surface role.
 */
@Composable
private fun BrandFooter(brand: String, onScrim: Boolean = true) {
    Text(
        text = brand,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (onScrim) {
            LocalFrSemanticColors.current.onScrim
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

// endregion
