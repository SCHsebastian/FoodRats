package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.molecules.scoreToEmoji
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMetric
import es.schsebastian.foodrats.core.designsystem.structural.FrMetricSize
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScrim
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrTileTone
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage
import es.schsebastian.foodrats.feature.stats.domain.model.MemberCount
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import es.schsebastian.foodrats.feature.stats.i18n.StatsPluralKey
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.formatOneDecimal
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import kotlin.math.round
import org.koin.compose.viewmodel.koinViewModel

/** Space reserved so the last tile clears the floating dock. */
private val DOCK_CLEARANCE = 104.dp

/**
 * Structural Stats — a competitive bento over a blurred media floor (the week's top plate, else an
 * olive Iron & Ember field). Zero-chrome: an oversized "your stats" header, the one loud **ember streak
 * hero**, a structural tab strip, two big metrics, and the awards (top plate · most voted · top cook ·
 * roast) as floating frosted strata. Reads the stats snapshot directly; all VM wiring (tab select,
 * share streak/award, weekly recap, refresh/retry, upload progress, share toast) is preserved.
 */
@Composable
fun StatsScreen(
    vm: StatsViewModel = koinViewModel(),
    onOpenRecap: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — media floor: the week's best plate (blurred), else the olive field brush. The blurred
        // photo floor is a DARK-mode flourish; in light mode its dark dim + scrim force a near-black
        // backdrop (and the theme-aware `foreground` header text would vanish into it). In light mode
        // use the light atmospheric floor so the screen reads light. (User report 2026-06-23.)
        val floorPhoto = state.snapshot?.week?.bestMeal?.photoUrl
            ?: state.snapshot?.week?.mostVotedMeal?.photoUrl
        if (floorPhoto != null && !StructuralColors.isLight) {
            FrMediaFloor(
                painter = rememberAsyncImagePainter(model = floorPhoto),
                blur = StructuralBlur.Heavy,
                dim = 0.55f,
                scrim = FrScrimStyle.Even,
            )
        } else {
            FrMediaFloor(brush = StructuralColors.oliveFloor, blur = StructuralBlur.Soft, scrim = FrScrimStyle.Even)
        }

        Box(modifier = Modifier.fillMaxSize().frSafeHorizontalPadding().frContentWidth(Breakpoints.contentMax)) {
            FrUploadProgressBar(visible = state.isUploadActive)
            when {
                state.snapshot == null && state.error == null -> LoadingSkeleton()
                state.error != null -> ErrorState(
                    message = resolve(state.error!!.toStringKey()),
                    onRetry = { vm.onIntent(StatsIntent.Refresh) },
                )
                else -> StatsContent(
                    snapshot = state.snapshot!!,
                    selectedTab = state.selectedTab,
                    historicError = state.historicError,
                    historicLoading = state.historicLoading,
                    scoreStyle = state.scoreStyle,
                    sharePreparing = state.isPreparingShare,
                    onSelectTab = { vm.onIntent(StatsIntent.SelectTab(it)) },
                    onOpenRecap = onOpenRecap,
                    onShareStreak = { vm.onIntent(StatsIntent.ShareStreakTapped) },
                    onShareAward = { mealId -> vm.onIntent(StatsIntent.ShareAwardTapped(mealId)) },
                )
            }

            // Share-outcome toast (spec §10).
            state.shareOutcome?.let { outcome ->
                val message = resolve(
                    when (outcome) {
                        ShareOutcomeUi.Succeeded   -> ShareCardStringKey.ShareSucceeded
                        ShareOutcomeUi.OpenedSheet -> ShareCardStringKey.ShareOpenedSheet
                        ShareOutcomeUi.Failed      -> ShareCardStringKey.ShareFailed
                    },
                )
                ShareOutcomeToast(
                    message = message,
                    onDismiss = { vm.onIntent(StatsIntent.DismissShareOutcome) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Content
// ----------------------------------------------------------------------------------------------

@Composable
private fun StatsContent(
    snapshot: StatsSnapshot,
    selectedTab: Tab,
    historicError: StatsError?,
    historicLoading: Boolean,
    scoreStyle: FrScoreStyle,
    sharePreparing: Boolean,
    onSelectTab: (Tab) -> Unit,
    onOpenRecap: () -> Unit,
    onShareStreak: () -> Unit,
    onShareAward: (String) -> Unit,
) {
    val snap = snapshot
    // Selected window.
    val window: WindowStats? = when (selectedTab) {
        Tab.Week -> snap.week
        Tab.Month -> snap.month
        Tab.Historic -> snap.historic
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .frSafeHorizontalPadding()
            .padding(horizontal = Spacing.lg),
        // The old Column applied statusBarsPadding INSIDE the scroll (it scrolled away); carry it as
        // scroll-away contentPadding to match that inset behavior exactly. Horizontal insets stay on
        // the modifier (they don't scroll). The media floor stays a sibling in the parent Box.
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "top-spacer") { Spacer(Modifier.height(Spacing.xs)) }

        // Header — eyebrow + oversized title + (share streak when there is one).
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FrEyebrow(text = resolve(StatsStringKey.YourStatsEyebrow).uppercase())
                    FrText(text = resolve(StatsStringKey.Title), style = StructuralType.titleXl, color = StructuralColors.foreground)
                }
                if (snap.hero.personalStreak.days > 0) {
                    FrGlassCircleButton(
                        icon = FrIcons.Share,
                        onClick = onShareStreak,
                        contentDescription = resolve(StatsStringKey.ShareAward),
                    )
                }
            }
        }

        // Weekly-recap entry (roadmap §2.4).
        item(key = "weekly-recap") {
            FrGlassButton(
                label = resolve(StatsStringKey.WeeklyRecapCta),
                onClick = onOpenRecap,
                tone = FrButtonTone.Ghost,
                fillWidth = true,
            )
        }

        // The one loud element — the ember streak hero.
        item(key = "streak-hero") { StreakHero(hero = snap.hero) }

        // Structural tab strip.
        item(key = "tab-strip") { StatTabStrip(selected = selectedTab, onSelect = onSelectTab) }

        if (selectedTab == Tab.Historic && historicError != null) {
            item(key = "window-error") {
                FrText(text = resolve(historicError.toStringKey()), style = StructuralType.body, color = LocalFrSemanticColors.current.danger)
            }
        } else if (window == null || historicLoading) {
            // `historicLoading` is the VM's explicit "Historic is being pulled/recomputed" flag —
            // without it, a tab switch would keep rendering the previous tab's stale numbers with
            // no indication (reading as wrong data). Shimmer skeletons stand in for the tab body.
            item(key = "window-loading") { HistoricLoading() }
        } else {
            tabBody(
                window = window,
                scoreStyle = scoreStyle,
                sharePreparing = sharePreparing,
                onShareAward = onShareAward,
            )
        }

        item(key = "dock-clearance") { Spacer(Modifier.height(DOCK_CLEARANCE)) }
    }
}

@Composable
private fun StreakHero(hero: HeroStats) {
    val days = hero.personalStreak.days
    FrGlassTile(depth = FrTileDepth.Near, tone = FrTileTone.Ember, modifier = Modifier.fillMaxWidth()) {
        FrEyebrow(text = resolve(StatsStringKey.YourStreakEyebrow).uppercase(), color = StructuralColors.foreground)
        Spacer(Modifier.height(Spacing.xs))
        if (days == 0) {
            // Zero state — no number to celebrate; show the call-to-action instead of a flat "0 days".
            FrText(
                text = resolve(StatsStringKey.HeroNoStreak),
                style = StructuralType.titleLg,
                color = StructuralColors.foreground,
            )
        } else {
            // Structural Xl metric, but the displayed phrase pluralizes: the HeroPersonalStreak
            // plural ("%1$d day streak" / "%1$d-day streak") carries the count + inflected unit, so
            // it renders as the whole metric value (unit = null) — never the bare "1 days".
            FrMetric(
                value = resolvePlural(StatsPluralKey.HeroPersonalStreak, days, days),
                size = FrMetricSize.Xl,
                color = StructuralColors.foreground,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        FrText(
            text = resolvePlural(StatsPluralKey.HeroCrewStreak, hero.crewStreak.days),
            style = StructuralType.body,
            color = StructuralColors.foreground.copy(alpha = 0.92f),
        )
        // "You posted today ✓" on its own line — separator glyph stays out of Kotlin (i18n rule).
        if (hero.iPostedToday) {
            FrText(
                text = resolve(StatsStringKey.HeroIPostedToday),
                style = StructuralType.micro,
                color = StructuralColors.foreground.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun StatTabStrip(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TabChip(label = resolve(StatsStringKey.TabWeek), selected = selected == Tab.Week, onClick = { onSelect(Tab.Week) })
        TabChip(label = resolve(StatsStringKey.TabMonth), selected = selected == Tab.Month, onClick = { onSelect(Tab.Month) })
        TabChip(label = resolve(StatsStringKey.TabHistoric), selected = selected == Tab.Historic, onClick = { onSelect(Tab.Historic) })
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FrStructuralChip(label = label.uppercase(), selected = selected, onClick = onClick)
}

// ----------------------------------------------------------------------------------------------
// Tab body
// ----------------------------------------------------------------------------------------------

private fun LazyListScope.tabBody(
    window: WindowStats,
    scoreStyle: FrScoreStyle,
    sharePreparing: Boolean,
    onShareAward: (String) -> Unit,
) {
    if (window.totalMeals == 0) {
        item(key = "tab-empty") {
            FrGlassTile(depth = FrTileDepth.Default, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    FrText(text = resolve(emptyKeyFor(window.window.tab)), style = StructuralType.titleLg, color = StructuralColors.foreground)
                    Spacer(Modifier.height(Spacing.xs))
                    FrText(text = resolve(StatsStringKey.EmptySubtext), style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.7f))
                }
            }
        }
        return
    }

    // Two big metrics — plates + per day.
    item(key = "tab-metrics") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                value = window.totalMeals.toString(),
                label = resolve(StatsStringKey.SummaryTotalPlatesLabel),
                icon = FrIcons.Restaurant,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = formatOneDecimal(window.avgPerDay.toFloat()),
                label = resolve(StatsStringKey.SummaryAvgPerDayLabel),
                icon = FrIcons.Stats,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Awards — eyebrow + best-plate tile + share button move together as one item to keep their
    // Spacing.md gaps identical (the LazyColumn's spacedBy supplies the same gap between items).
    window.bestMeal?.let { award ->
        item(key = "award-best") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrEyebrow(text = resolve(StatsStringKey.AwardsEyebrow).uppercase())
                AwardPlateTile(
                    award = award,
                    titleLabel = resolve(StatsStringKey.BestPlateTitle),
                    metric = scoreHeadline(award.score, scoreStyle),
                    tone = FrChipTone.Ember,
                )
                FrGlassButton(
                    label = resolve(StatsStringKey.ShareAward),
                    onClick = { onShareAward(award.mealId.value) },
                    tone = FrButtonTone.Glass,
                    enabled = !sharePreparing,
                    compact = true,
                )
            }
        }
    }
    window.mostVotedMeal?.let { award ->
        item(key = "award-most-voted") {
            AwardPlateTile(
                award = award,
                titleLabel = resolve(StatsStringKey.MostVotedPlateTitle),
                metric = null,
                caption = resolvePlural(StatsPluralKey.MostVotedPlateVoters, award.ratingCount),
                tone = FrChipTone.Glass,
            )
        }
    }

    // Cooks.
    if (window.bestCook != null || window.mostProlific != null || window.mostCriticized != null) {
        item(key = "cooks-eyebrow") {
            FrEyebrow(text = resolve(StatsStringKey.CooksSectionTitle).uppercase())
        }
    }
    window.bestCook?.let { cook ->
        item(key = "cook-best") {
            CookTile(
                title = resolve(StatsStringKey.BestCookTitle),
                name = cook.displayName,
                avatarUrl = cook.avatarUrl,
                metric = cookMetric(cook, scoreStyle, StatsStringKey.BestCookMetricFormat, StatsStringKey.BestCookMetricFormatGlyphFree),
                tone = FrTileTone.Olive,
            )
        }
    }
    window.mostProlific?.let { cook ->
        item(key = "cook-prolific") {
            CookTile(
                title = resolve(StatsStringKey.MostProlificTitle),
                name = cook.displayName,
                avatarUrl = cook.avatarUrl,
                metric = resolvePlural(StatsPluralKey.MostProlificMetric, cook.mealCount),
                tone = FrTileTone.Glass,
            )
        }
    }
    window.mostCriticized?.let { roast ->
        item(key = "cook-roast") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrEyebrow(text = resolve(StatsStringKey.RoastSectionTitle).uppercase())
                CookTile(
                    title = resolve(StatsStringKey.MostCriticizedTitle),
                    name = roast.displayName,
                    avatarUrl = roast.avatarUrl,
                    metric = roastMetric(roast, scoreStyle),
                    tone = FrTileTone.Ember,
                )
            }
        }
    }

    // Ingredients — crew-wide most-used, hidden when absent (same guard as the matte cards).
    window.mostUsedIngredient?.let { usage ->
        item(key = "ingredient-most-used") {
            FrGlassTile(depth = FrTileDepth.Default, tone = FrTileTone.Olive, modifier = Modifier.fillMaxWidth()) {
                FrEyebrow(text = resolve(StatsStringKey.MostUsedIngredientTitle).uppercase(), color = StructuralColors.foreground)
                Spacer(Modifier.height(Spacing.xs))
                FrText(
                    text = resolvePlural(
                        StatsPluralKey.MostUsedIngredientMetric,
                        usage.mealCount,
                        usage.displayName,
                        usage.mealCount,
                    ),
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                )
            }
        }
    }

    // Per-member signature ingredient — hidden when the list is empty (same guard as HEAD).
    // Kept as a SINGLE keyed item wrapping the shared FrGlassTile + forEach rather than
    // `items(window.topByMember)`: the rows share one frosted card, so splitting them into per-row
    // items would dissolve that card into N cards (a visual/behavior change). The list is
    // crew-bounded (one row per member), so a single item is the faithful, behavior-preserving form.
    if (window.topByMember.isNotEmpty()) {
        item(key = "member-ingredients") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrEyebrow(text = resolve(StatsStringKey.TopIngredientByMemberTitle).uppercase())
                FrGlassTile(depth = FrTileDepth.Default, modifier = Modifier.fillMaxWidth()) {
                    window.topByMember.forEach { member ->
                        key(member.accountId.value) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                FrGlassAvatar(
                                    initials = member.displayName,
                                    image = member.avatarUrl?.let { rememberAsyncImagePainter(it) },
                                    ring = FrAvatarRing.None,
                                    size = 36.dp,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                    FrText(
                                        text = member.displayName,
                                        style = StructuralType.titleMd,
                                        color = StructuralColors.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    FrText(
                                        text = resolvePlural(
                                            StatsPluralKey.MemberTopIngredientMetric,
                                            member.mealCount,
                                            member.ingredientName,
                                            member.mealCount,
                                        ),
                                        style = StructuralType.micro,
                                        color = StructuralColors.foreground.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    FrGlassTile(depth = FrTileDepth.Default, modifier = modifier.height(124.dp)) {
        FrIcon(
            image = icon,
            tint = StructuralColors.foreground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.weight(1f))
        FrMetric(value = value, size = FrMetricSize.Md, color = StructuralColors.foreground)
        FrText(text = label.uppercase(), style = StructuralType.micro, color = StructuralColors.foreground.copy(alpha = 0.7f))
    }
}

@Composable
private fun AwardPlateTile(
    award: MealAward,
    titleLabel: String,
    metric: String?,
    tone: FrChipTone,
    caption: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(StructuralColors.dishRamen),
    ) {
        AsyncImage(
            model = award.photoUrl,
            contentDescription = resolve(StatsStringKey.PlatePhotoFormat, award.dish.value),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        FrScrim(style = FrScrimStyle.Photo)
        Row(modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md)) {
            FrStructuralChip(label = titleLabel.uppercase(), tone = tone, leadingIcon = FrIcons.Crown, compact = true)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                FrText(text = award.dish.value, style = StructuralType.titleLg, color = StructuralColors.onMedia, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FrMicroRow(items = listOf(resolve(StatsStringKey.BestPlateAuthorFormat, award.author.displayName)), color = StructuralColors.onMedia.copy(alpha = 0.72f))
                if (caption != null) {
                    FrText(text = caption, style = StructuralType.micro, color = StructuralColors.onMedia.copy(alpha = 0.72f))
                }
            }
            if (metric != null) {
                FrMetric(value = metric, size = FrMetricSize.Lg, color = StructuralColors.onMedia)
            }
        }
    }
}

@Composable
private fun CookTile(title: String, name: String, avatarUrl: String?, metric: String, tone: FrTileTone) {
    FrGlassTile(depth = FrTileDepth.Default, tone = tone, modifier = Modifier.fillMaxWidth()) {
        FrEyebrow(text = title.uppercase(), color = StructuralColors.foreground)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrGlassAvatar(
                initials = name,
                image = avatarUrl?.let { rememberAsyncImagePainter(it) },
                ring = FrAvatarRing.None,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                FrText(text = name, style = StructuralType.titleMd, color = StructuralColors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FrText(text = metric, style = StructuralType.micro, color = StructuralColors.foreground.copy(alpha = 0.85f))
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Score helpers (replicate the matte cards' data→string mapping)
// ----------------------------------------------------------------------------------------------

private fun emojiForScore(score: Double): String = scoreToEmoji(round(score).toInt().coerceIn(1, 5))

/** The award's headline number, style-aware (Stars/Numeric → one-decimal; Emoji → glyph). */
@Composable
private fun scoreHeadline(score: Double, style: FrScoreStyle): String {
    val s = formatOneDecimal(score.toFloat())
    return when (style) {
        FrScoreStyle.Stars -> s
        FrScoreStyle.Numeric -> s
        FrScoreStyle.Emoji -> emojiForScore(score)
    }
}

@Composable
private fun cookMetric(cook: MemberAverage, style: FrScoreStyle, starsKey: StatsStringKey, glyphFreeKey: StatsStringKey): String {
    val s = formatOneDecimal(cook.averageScore.toFloat())
    return when (style) {
        FrScoreStyle.Stars -> resolve(starsKey, s, cook.postCount)
        FrScoreStyle.Numeric -> resolve(glyphFreeKey, s, cook.postCount)
        FrScoreStyle.Emoji -> resolve(glyphFreeKey, emojiForScore(cook.averageScore), cook.postCount)
    }
}

@Composable
private fun roastMetric(roast: MemberAverage, style: FrScoreStyle): String {
    val s = formatOneDecimal(roast.averageScore.toFloat())
    return when (style) {
        FrScoreStyle.Stars -> resolve(StatsStringKey.MostCriticizedMetricFormat, s)
        FrScoreStyle.Numeric -> resolve(StatsStringKey.MostCriticizedMetricFormatGlyphFree, s)
        FrScoreStyle.Emoji -> resolve(StatsStringKey.MostCriticizedMetricFormatGlyphFree, emojiForScore(roast.averageScore))
    }
}

// ----------------------------------------------------------------------------------------------
// Transient states
// ----------------------------------------------------------------------------------------------

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
            FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
            Spacer(Modifier.height(Spacing.md))
            FrGlassButton(label = resolve(StatsStringKey.Retry), onClick = onRetry, tone = FrButtonTone.Primary)
        }
    }
}

/**
 * Skeleton for the tab body while a historic window (Month/All-time) is pulled/recomputed —
 * shimmering placeholders mirroring the two metric tiles + the award plate, on the same frosted
 * strata (structural look) the loaded body uses.
 */
@Composable
private fun HistoricLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            ShimmerTile(modifier = Modifier.weight(1f).height(124.dp))
            ShimmerTile(modifier = Modifier.weight(1f).height(124.dp))
        }
        ShimmerTile(modifier = Modifier.fillMaxWidth().height(188.dp))
    }
}

/** A deep frosted tile whose body shimmers — the structural stand-in for a loading section. */
@Composable
private fun ShimmerTile(modifier: Modifier = Modifier) {
    FrGlassTile(depth = FrTileDepth.Deep, modifier = modifier) {
        FrShimmerBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(Radius.sm))
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        FrShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(34.dp), shape = RoundedCornerShape(Radius.sm))
        FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(150.dp)) {}
        FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(188.dp)) {}
    }
}

/** Brief auto-dismissing bottom toast for a share outcome (spec §10). */
@Composable
private fun ShareOutcomeToast(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.widthIn(max = 420.dp)) {
            FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
        }
    }
}

private fun emptyKeyFor(tab: Tab): StatsStringKey = when (tab) {
    Tab.Week -> StatsStringKey.WindowEmptyWeek
    Tab.Month -> StatsStringKey.WindowEmptyMonth
    Tab.Historic -> StatsStringKey.WindowEmptyHistoric
}
