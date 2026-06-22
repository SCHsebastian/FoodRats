package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.molecules.FrProfileBadge
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.molecules.scoreToEmoji
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.image.rememberThumbHashPainter
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.feed.i18n.FeedPluralKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.math.round

/**
 * Feed list "card row": square thumbnail with an outset [FrScoreBadge] overlay, the dish title,
 * an identity line (avatar + author + slot) and a meta line (time + score summary). When the
 * viewer has voted, a celebration-tinted "your vote" line is appended. A compact, low-emphasis
 * [IngredientStrip] (when the meal has resolved ingredients) sits just above the react button.
 *
 * The card lifts + scales on press — that behaviour comes from [FrCard]'s built-in press
 * feedback, so this row carries no bespoke animation. List padding/spacing is owned by the
 * caller (the feed's LazyColumn), so the row only fills width.
 */
@Composable
fun FrFeedMealRow(
    ui: FeedMealUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReact: () -> Unit = {},
    /** Open the report sheet targeting the meal (UGC compliance §4). */
    onReportMeal: () -> Unit = {},
    /** Open the report sheet targeting the meal's author (UGC compliance §4). */
    onReportAuthor: () -> Unit = {},
    /**
     * Request to block the meal's author; the screen must present a [FrConfirmDialog] before this
     * actually fires the block write (UGC compliance §5).
     */
    onBlockAuthor: () -> Unit = {},
    /** Active crew's chosen Score display vocabulary (C8). Defaults to Stars for pre-C8 crews. */
    scoreStyle: FrScoreStyle = FrScoreStyle.Stars,
) {
    val semantic = LocalFrSemanticColors.current
    val avg = ui.averageScore
    val hasVotes = avg != null && ui.ratingCount > 0
    val avgRounded = avg?.let { (round(it * 10) / 10.0).toString() }
    var overflowExpanded by remember { mutableStateOf(false) }

    FrCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(Radius.lg),
        contentPadding = PaddingValues(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(Sizes.feedRowThumbnail)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val imageUrl = ui.feedImageUrl
                    if (imageUrl.isNotBlank()) {
                        // ThumbHash placeholder (instant blur) while the thumbnail loads; falls
                        // back to the flat surfaceVariant box behind it when no hash is present.
                        val placeholder = rememberThumbHashPainter(ui.thumbHash)
                        // Key the disk/memory cache on the STABLE Storage path, not the rotating
                        // signed URL, so cached bytes survive URL re-mints and render offline
                        // (offline P1-T3). A blank key (path unknown) falls back to URL keying.
                        val request = stablePlateRequest(imageUrl, ui.feedImageCacheKey)
                        AsyncImage(
                            model = request,
                            contentDescription = ui.dishName,
                            contentScale = ContentScale.Crop,
                            placeholder = placeholder,
                            error = placeholder,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (avg != null && avgRounded != null && ui.ratingCount > 0) {
                    val rounded = round(avg).toInt().coerceIn(1, 5)
                    ScoreBadge(
                        score = rounded,
                        style = scoreStyle,
                        contentDescription = resolve(FeedStringKey.RatingSummary, avgRounded, ui.ratingCount),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = Spacing.xs, y = Spacing.xs),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                // Dish title + overflow UGC menu on the same row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FrText(
                        text = ui.dishName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box {
                        FrIconButton(
                            icon = FrIcons.MoreVert,
                            onClick = { overflowExpanded = true },
                            contentDescription = resolve(FeedStringKey.OverflowMenuCd),
                        )
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { FrText(resolve(FeedStringKey.ReportMealCta)) },
                                onClick = { overflowExpanded = false; onReportMeal() },
                                leadingIcon = { FrIcon(FrIcons.Flag, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            )
                            DropdownMenuItem(
                                text = { FrText(resolve(FeedStringKey.ReportUserCta)) },
                                onClick = { overflowExpanded = false; onReportAuthor() },
                                leadingIcon = { FrIcon(FrIcons.Flag, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            )
                            DropdownMenuItem(
                                text = { FrText(resolve(FeedStringKey.BlockAuthorCta), color = MaterialTheme.colorScheme.error) },
                                onClick = { overflowExpanded = false; onBlockAuthor() },
                                leadingIcon = { FrIcon(FrIcons.Block, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // Blind voting: hide the author's identity until the viewer has rated. The
                    // placeholder avatar gets blank initials + no image (generic), and the name
                    // line shows the masked label instead of the real name.
                    val authorLabel =
                        if (ui.authorMasked) resolve(FeedStringKey.BlindAuthor) else ui.authorName
                    // decorative — the adjacent author-name label carries the identity for screen readers.
                    FrAvatar(
                        initials = if (ui.authorMasked) "" else ui.authorName,
                        imageUrl = if (ui.authorMasked) null else ui.authorAvatarUrl,
                        size = Sizes.avatarSm,
                    )
                    FrText(
                        text = authorLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // U5b — achievement badge chip, hidden under blind voting (authorMasked=true
                    // means ui.authorBadgeId is already null — see toFeedUi).
                    val badgeId = ui.authorBadgeId
                    if (!ui.authorMasked && badgeId != null) {
                        val badgeLabel = feedBadgeLabel(badgeId)
                        if (badgeLabel != null) {
                            FrProfileBadge(badgeId = badgeId, label = badgeLabel)
                        }
                    }
                    Dot()
                    FrText(
                        text = resolve(ui.slot.labelKey()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                // U5b — author bio line, hidden under blind voting (authorMasked=true means
                // ui.authorBio is already null — see toFeedUi). Single line, ellipsized.
                val authorBio = ui.authorBio
                if (!ui.authorMasked && !authorBio.isNullOrBlank()) {
                    FrText(
                        text = authorBio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FrText(
                        text = resolve(
                            FeedStringKey.TimeOfDay,
                            ui.publishedHour.toString().padStart(2, '0'),
                            ui.publishedMinute.toString().padStart(2, '0'),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Dot()
                    if (hasVotes && avgRounded != null && avg != null) {
                        val rounded = round(avg).toInt().coerceIn(1, 5)
                        // Stars keeps the ★-baked plural; Emoji/Numeric use the glyph-free
                        // ScoreSummaryVotes so the ★ does not leak next to the emoji/number.
                        val scoreSummary = when (scoreStyle) {
                            FrScoreStyle.Stars   -> resolvePlural(FeedPluralKey.RatingSummaryVotes, ui.ratingCount, avgRounded, ui.ratingCount)
                            FrScoreStyle.Emoji   -> resolvePlural(FeedPluralKey.ScoreSummaryVotes, ui.ratingCount, scoreToEmoji(rounded), ui.ratingCount)
                            FrScoreStyle.Numeric -> resolvePlural(FeedPluralKey.ScoreSummaryVotes, ui.ratingCount, avgRounded, ui.ratingCount)
                        }
                        FrText(
                            text = scoreSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    } else {
                        FrText(
                            text = resolve(FeedStringKey.NoVotesYet),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                ui.viewerRating?.let { viewerRating ->
                    // Honor the crew's score style here too; Stars keeps the ★ form, Emoji/Numeric
                    // use the glyph-free "Tu voto: %s" so no ★ leaks next to the emoji/number.
                    val yourVoteText = when (scoreStyle) {
                        FrScoreStyle.Stars   -> resolve(FeedStringKey.YourVote, viewerRating)
                        FrScoreStyle.Emoji   -> resolve(FeedStringKey.YourVoteGlyphFree, scoreToEmoji(viewerRating))
                        FrScoreStyle.Numeric -> resolve(FeedStringKey.YourVoteGlyphFree, viewerRating.toString())
                    }
                    FrText(
                        text = yourVoteText,
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.celebration,
                        maxLines = 1,
                    )
                }

                if (ui.ingredients.isNotEmpty()) {
                    IngredientStrip(ingredients = ui.ingredients)
                }

                ReactionButton(
                    glyph = ui.dayEmote,
                    count = ui.reactionCount,
                    reacted = ui.viewerReacted,
                    onReact = onReact,
                )
            }
        }
    }
}

/**
 * The daily-emote react affordance: a pill carrying the meal-day's [glyph], its toggled state, and
 * the live reaction [count]. Tapping toggles the viewer's reaction; the count doubles as the
 * compact "who reacted" presentation (roadmap §1.3 — names-or-count, we show the count). It sits
 * inside the clickable card, so its own [clickable] swallows the tap before the card's onClick.
 */
@Composable
private fun ReactionButton(
    glyph: String,
    count: Int,
    reacted: Boolean,
    onReact: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    val containerColor =
        if (reacted) semantic.celebration.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (reacted) semantic.celebration else MaterialTheme.colorScheme.onSurfaceVariant
    val label =
        if (count > 0) resolvePlural(FeedPluralKey.ReactionsLabel, count, count) else resolve(FeedStringKey.ReactionCta)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(containerColor)
            .then(
                if (reacted) {
                    Modifier.border(1.dp, semantic.celebration, RoundedCornerShape(Radius.pill))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onReact)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .semantics {
                this.role = Role.Button
                this.selected = reacted
                this.contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(text = glyph, style = MaterialTheme.typography.labelMedium)
        if (count > 0) {
            FrText(
                text = resolve(FeedStringKey.ReactionCount, count),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** Max ingredient chips shown inline before collapsing the remainder into a "+N" chip. */
private const val MAX_INGREDIENT_CHIPS = 3

/**
 * A compact, low-emphasis ingredient strip under the meta line: up to [MAX_INGREDIENT_CHIPS]
 * surface-variant chips plus a "+N" overflow chip when there are more. Display-only (no tap
 * affordance, no per-chip semantics) so it never competes with the dish title, score badge, or
 * react button for attention. A single clipped [Row] — never wraps to a second line, never scrolls;
 * a long label ellipsizes within its own chip so the row keeps its silhouette. The whole strip is
 * hidden by the caller when the meal has no resolved ingredients.
 */
@Composable
private fun IngredientStrip(ingredients: List<String>) {
    val shown = ingredients.take(MAX_INGREDIENT_CHIPS)
    val overflow = ingredients.size - shown.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { name ->
            // weight(…, fill = false) lets a chip shrink (and its label ellipsize) under
            // pressure instead of pushing the overflow chip off-row.
            IngredientChip(label = name, modifier = Modifier.weight(1f, fill = false))
        }
        if (overflow > 0) {
            IngredientChip(label = resolve(FeedStringKey.MoreIngredients, overflow))
        }
    }
}

/** One pill in the [IngredientStrip] — snug padding, single-line, onSurfaceVariant on surfaceVariant. */
@Composable
private fun IngredientChip(label: String, modifier: Modifier = Modifier) {
    FrText(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    )
}

/**
 * Score badge overlaid on the feed thumbnail. Rendering varies by [style]:
 * - [FrScoreStyle.Stars] — star-shaped, celebration-tinted (legacy appearance).
 * - [FrScoreStyle.Emoji] — circular chip showing the emoji for [score].
 * - [FrScoreStyle.Numeric] — circular chip showing the raw numeric value.
 *
 * All three variants share the same [Sizes.scoreStar] footprint so the thumbnail
 * layout is undisturbed by a crew's style choice.
 */
@Composable
private fun ScoreBadge(
    score: Int,
    style: FrScoreStyle,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    when (style) {
        FrScoreStyle.Stars -> {
            Box(
                modifier = modifier
                    .size(Sizes.scoreStar)
                    .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                FrIcon(
                    image = FrIcons.Star,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(Sizes.scoreStar),
                )
                FrIcon(
                    image = FrIcons.Star,
                    tint = semantic.celebration,
                    modifier = Modifier.size(Sizes.scoreStar - Spacing.xs),
                )
                FrText(
                    text = score.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = semantic.onCelebration,
                    // Nudge up to the star's optical centre — the lower points pull weight down.
                    modifier = Modifier.offset(y = -Spacing.xxs),
                )
            }
        }
        FrScoreStyle.Emoji -> {
            Box(
                modifier = modifier
                    .size(Sizes.scoreStar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                FrText(
                    text = scoreToEmoji(score),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        FrScoreStyle.Numeric -> {
            Box(
                modifier = modifier
                    .size(Sizes.scoreStar)
                    .clip(CircleShape)
                    .background(semantic.celebration)
                    .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                FrText(
                    text = score.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = semantic.onCelebration,
                )
            }
        }
    }
}

/**
 * Maps a [badgeId] to its feed-scoped i18n label. Returns `null` for unrecognised ids so
 * the chip is silently hidden. Feed cannot import `:feature:auth`'s `resolveBadgeLabel` —
 * each feature owns its `<Feature>StringKey` (i18n rule).
 */
@Composable
private fun feedBadgeLabel(badgeId: String): String? = when (badgeId) {
    "first"   -> resolve(FeedStringKey.BadgeFirst)
    "ten"     -> resolve(FeedStringKey.BadgeTen)
    "fifty"   -> resolve(FeedStringKey.BadgeFifty)
    "hundred" -> resolve(FeedStringKey.BadgeHundred)
    else      -> null
}

/** Neutral round bullet used as an inline separator between meta segments. */
@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(Spacing.xs)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
