package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.molecules.scoreToEmoji
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrBentoGrid
import es.schsebastian.foodrats.core.designsystem.structural.FrBentoItem
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMetric
import es.schsebastian.foodrats.core.designsystem.structural.FrMetricSize
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScrim
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedPluralKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import es.schsebastian.foodrats.feature.feed.presentation.components.MealSlotUi
import es.schsebastian.foodrats.feature.feed.presentation.components.stablePlateRequest
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import kotlin.math.round
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.koin.compose.viewmodel.koinViewModel

/** Space reserved at the bottom of the content plane so the last tile clears the floating dock. */
private val DOCK_CLEARANCE = 104.dp

/**
 * Structural Feed — the flagship of the "structural" look. A continuous edge-to-edge [FrMediaFloor]
 * (the crew banner, else the day's top plate, else a warm Iron & Ember brush) sits behind a
 * zero-chrome scrolling content plane: an oversized crew switcher, a floating day strip, and an
 * asymmetric [FrBentoGrid] of meals where tile size = score rank. Header chrome (crew name + avatar)
 * is supplied by the Main scaffold so this feature gains no new cross-context dependency.
 *
 * Tapping a tile opens the meal detail (where rate / react / report / block live). The feed-level
 * report/block overflow of the matte design is intentionally dropped — every action is one tap deeper
 * on the detail screen, which keeps Apple G1.2 report/block reachable while the tiles stay clean.
 */
@Composable
fun FeedScreen(
    crewName: String?,
    avatarInitials: String,
    avatarUrl: String?,
    onPickCrewClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCrewSettingsClick: () -> Unit,
    onMealClick: (mealId: String, dayIso: String) -> Unit,
    vm: FeedViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val meals = state.meals
    // C9 — signed banner URL to open in the full-screen viewer (null = closed).
    var bannerToView by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — edge-to-edge media floor. Crew banner wins (the owner's deliberate hero); else the
        // day's highest-scoring plate; else a warm atmospheric brush.
        val heroMeal = meals.filter { it.feedImageUrl.isNotBlank() }
            .maxByOrNull { it.averageScore ?: -1.0 }
        val floorModel = when {
            state.bannerImageUrl != null -> stablePlateRequest(state.bannerImageUrl!!, "")
            heroMeal != null -> stablePlateRequest(heroMeal.feedImageUrl, heroMeal.feedImageCacheKey)
            else -> null
        }
        // The blurred edge-to-edge photo floor is a DARK-mode flourish: in light mode its dark dim +
        // scrim force a near-black backdrop that clashes with the light UI (user report 2026-06-23).
        // In light mode fall back to the light atmospheric floor — the meal photos still appear in the
        // bento tiles and the crew banner still shows as its sharp hero. Photos stay the floor in dark.
        val onPhotoFloor = floorModel != null && !StructuralColors.isLight
        if (onPhotoFloor) {
            FrMediaFloor(
                painter = rememberAsyncImagePainter(model = floorModel),
                blur = StructuralBlur.Heavy,
                dim = 0.52f,
                scrim = FrScrimStyle.Standard,
            )
        } else {
            FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft)
        }

        if (state.error is FeedError.Session.NoActiveCrew) {
            NoCrewPlane(onPickCrewClick = onPickCrewClick)
        } else {
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { vm.onIntent(FeedIntent.Refresh) },
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .frSafeHorizontalPadding()
                        .frContentWidth(Breakpoints.contentMax)
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Spacer(Modifier.height(Spacing.xs))

                    FeedHeader(
                        crewName = crewName,
                        avatarInitials = avatarInitials,
                        avatarUrl = avatarUrl,
                        onMediaFloor = onPhotoFloor,
                        onPickCrewClick = onPickCrewClick,
                        onProfileClick = onProfileClick,
                        onCrewSettingsClick = onCrewSettingsClick,
                    )

                    // C9 — crew banner hero: the owner's cover image shown sharp (the media floor
                    // already carries a blurred echo of it). Honors the owner's vertical focal point;
                    // tap opens the full-screen viewer. Hidden when no banner is set.
                    state.bannerImageUrl?.let { url ->
                        CrewBannerHero(
                            url = url,
                            focalY = state.bannerFocalY,
                            onClick = { bannerToView = url },
                        )
                    }

                    val date = state.day?.day?.date
                    val today = state.today
                    val isToday = date != null && date == today
                    val isYesterday = date != null && today != null &&
                        date == today.minus(DatePeriod(days = 1))
                    val dayPrimary = when {
                        isToday -> resolve(FeedStringKey.Title)
                        isYesterday -> resolve(FeedStringKey.Yesterday)
                        else -> date?.toString().orEmpty()
                    }
                    val daySecondary = if (isToday || isYesterday) date?.toString().orEmpty() else ""
                    FeedDayStrip(
                        primary = dayPrimary,
                        secondary = daySecondary,
                        canGoPrev = state.canGoPrev,
                        canGoNext = state.canGoNext,
                        onMediaFloor = onPhotoFloor,
                        onPrev = { vm.onIntent(FeedIntent.PrevDay) },
                        onNext = { vm.onIntent(FeedIntent.NextDay) },
                    )

                    FeedChallengeRow(challenge = state.weeklyChallenge, plateCount = meals.size, onMediaFloor = onPhotoFloor)

                    // Offline-first indicators + transient errors, kept functional over the floor.
                    state.error?.let { err ->
                        if (err !is FeedError.Session.NoActiveCrew) {
                            FrText(
                                text = resolve(err.toStringKey()),
                                style = StructuralType.body,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    state.rateError?.let { FrText(resolve(it.toStringKey()), style = StructuralType.body, color = MaterialTheme.colorScheme.error) }
                    state.reactError?.let { FrText(resolve(it.toStringKey()), style = StructuralType.body, color = MaterialTheme.colorScheme.error) }

                    // C6 — pinned crew welcome message.
                    state.welcomeMessage?.let { msg ->
                        FrGlassTile(depth = FrTileDepth.Near) {
                            FrText(text = msg, style = StructuralType.body, color = StructuralColors.foreground)
                            Spacer(Modifier.height(Spacing.sm))
                            FrGlassButton(
                                label = resolve(FeedStringKey.WelcomeDismiss),
                                onClick = { vm.onIntent(FeedIntent.DismissWelcomeBanner) },
                                tone = FrButtonTone.Ghost,
                                compact = true,
                            )
                        }
                    }

                    when {
                        state.isLoading && meals.isEmpty() -> FeedSkeleton()
                        meals.isEmpty() -> EmptyDayTile(
                            viewingToday = state.today == null || state.day?.day?.date == state.today,
                        )
                        else -> FeedBento(
                            meals = meals,
                            scoreStyle = state.scoreStyle,
                            onMealClick = { id -> onMealClick(id, state.day?.day?.date?.toString().orEmpty()) },
                            onReactClick = { id -> vm.onIntent(FeedIntent.ReactMeal(id)) },
                        )
                    }

                    Spacer(Modifier.height(DOCK_CLEARANCE))
                }
            }
        }

        // C9 — full-screen crew-banner viewer, opened by tapping the banner hero.
        bannerToView?.let { url ->
            CrewBannerViewer(url = url, onDismiss = { bannerToView = null })
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Header
// ----------------------------------------------------------------------------------------------

@Composable
private fun FeedHeader(
    crewName: String?,
    avatarInitials: String,
    avatarUrl: String?,
    onMediaFloor: Boolean,
    onPickCrewClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCrewSettingsClick: () -> Unit,
) {
    // Over a photo/banner floor the header sits on dark media → white; over the light atmospheric floor
    // (empty feed in light mode) it must flip to dark ink.
    val planeFg = if (onMediaFloor) StructuralColors.onMedia else StructuralColors.foreground
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Oversized zero-chrome crew switcher.
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.md))
                .clickable(onClick = onPickCrewClick)
                .padding(start = Spacing.xs, top = Spacing.xxs, end = Spacing.sm),
        ) {
            FrEyebrow(
                text = resolve(FeedStringKey.YourCrewEyebrow).uppercase(),
                color = if (onMediaFloor) StructuralColors.onMedia.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
            )
            FrText(
                text = crewName.orEmpty(),
                style = StructuralType.titleXl,
                color = planeFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Settings,
                onClick = onCrewSettingsClick,
                contentDescription = resolve(FeedStringKey.CrewSettingsCd),
                size = 42.dp,
            )
            Box(
                modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onProfileClick),
            ) {
                FrGlassAvatar(
                    initials = avatarInitials,
                    image = avatarUrl?.let { rememberAsyncImagePainter(it) },
                    ring = FrAvatarRing.Moss,
                    size = 40.dp,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Day strip + challenge
// ----------------------------------------------------------------------------------------------

@Composable
private fun FeedDayStrip(
    primary: String,
    secondary: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onMediaFloor: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val planeFg = if (onMediaFloor) StructuralColors.onMedia else StructuralColors.foreground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrGlassCircleButton(
            icon = FrIcons.ChevronLeft,
            onClick = onPrev,
            contentDescription = resolve(FeedStringKey.PrevDay),
            size = 34.dp,
            enabled = canGoPrev,
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            FrText(text = primary, style = StructuralType.titleMd, color = planeFg, maxLines = 1)
            if (secondary.isNotBlank()) {
                FrText(text = secondary, style = StructuralType.microMono, color = planeFg.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        FrGlassCircleButton(
            icon = FrIcons.ChevronRight,
            onClick = onNext,
            contentDescription = resolve(FeedStringKey.NextDay),
            size = 34.dp,
            enabled = canGoNext,
        )
    }
}

@Composable
private fun FeedChallengeRow(challenge: String?, plateCount: Int, onMediaFloor: Boolean) {
    val planeFg = if (onMediaFloor) StructuralColors.onMedia else StructuralColors.foreground
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (challenge != null) {
            FrStructuralChip(
                label = resolve(FeedStringKey.WeeklyChallengeLabel, challenge),
                tone = FrChipTone.Ember,
                leadingIcon = FrIcons.Trophy,
            )
        } else {
            Spacer(Modifier.height(1.dp))
        }
        FrText(
            text = resolvePlural(FeedPluralKey.PlatesCount, plateCount),
            style = StructuralType.micro,
            color = planeFg.copy(alpha = 0.72f),
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Bento + meal tiles
// ----------------------------------------------------------------------------------------------

@Composable
private fun FeedBento(
    meals: List<FeedMealUi>,
    scoreStyle: FrScoreStyle,
    onMealClick: (mealId: String) -> Unit,
    onReactClick: (mealId: String) -> Unit,
) {
    // Sort by score so size = rank: the top plate is the wide hero, the lowest is the narrow tile.
    val ranked = meals.sortedByDescending { it.averageScore ?: -1.0 }
    val items = ranked.mapIndexed { index, ui ->
        val span = when (index) {
            0 -> 6
            1 -> 4
            2 -> 2
            else -> 3
        }
        val tileHeight: Dp = when (index) {
            0 -> 230.dp
            1, 2 -> 158.dp
            else -> 150.dp
        }
        FrBentoItem(colSpan = span) {
            StructuralMealTile(
                ui = ui,
                heightDp = tileHeight,
                isHero = index == 0,
                // B4 — the narrowest (sub-3-span) tile has no room for the dish name; at <3 cols it
                // collapses to ~3 chars + an unreadable ellipsis ("Pla…"). Drop the name there — the
                // photo, slot chip, score, and cook avatar already identify the plate; the wider
                // tiles (span 3/4/6) keep it.
                showDishName = span >= 3,
                scoreStyle = scoreStyle,
                onClick = { onMealClick(ui.mealId) },
                onReactClick = { onReactClick(ui.mealId) },
            )
        }
    }
    FrBentoGrid(items = items, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StructuralMealTile(
    ui: FeedMealUi,
    heightDp: Dp,
    isHero: Boolean,
    showDishName: Boolean,
    scoreStyle: FrScoreStyle,
    onClick: () -> Unit,
    onReactClick: () -> Unit,
) {
    val avg = ui.averageScore
    val hasVotes = avg != null && ui.ratingCount > 0
    val rounded = avg?.let { round(it).toInt().coerceIn(1, 5) }
    val avgRounded = avg?.let { (round(it * 10) / 10.0).toString() }
    val metricValue = when {
        !hasVotes || rounded == null || avgRounded == null -> null
        scoreStyle == FrScoreStyle.Emoji -> scoreToEmoji(rounded)
        else -> avgRounded
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(dishBrushFor(ui.slot))
            .clickable(onClick = onClick),
    ) {
        if (ui.bentoImageUrl.isNotBlank()) {
            AsyncImage(
                // Full plate (not the 512px thumbnail) — the bento tiles render large and the thumb
                // looks soft scaled up to fill them. See FeedMealUi.bentoImageUrl.
                model = stablePlateRequest(ui.bentoImageUrl, ui.bentoImageCacheKey),
                contentDescription = ui.dishName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        FrScrim(style = FrScrimStyle.Photo)

        // Top — slot chip (start) + react pill (end). Top-END keeps the react affordance clear of
        // the slot chip and the bottom identity/metric row.
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Slot chip — only when tagged (slot is optional). The empty Spacer keeps the react
            // pill at the row's end (SpaceBetween) when there's no chip.
            ui.slot?.let { FrStructuralChip(label = resolve(it.labelKey()).uppercase(), compact = true) }
                ?: Spacer(Modifier)
            ReactPill(
                glyph = ui.dayEmote,
                count = ui.reactionCount,
                reacted = ui.viewerReacted,
                onReact = onReactClick,
            )
        }

        // Bottom — identity + metric.
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FrGlassAvatar(
                        initials = if (ui.authorMasked) "" else ui.authorName,
                        image = if (ui.authorMasked) null else ui.authorAvatarUrl?.let { rememberAsyncImagePainter(it) },
                        ring = FrAvatarRing.None,
                        size = if (isHero) 30.dp else 24.dp,
                    )
                    if (showDishName) {
                        FrText(
                            text = ui.dishName,
                            style = if (isHero) StructuralType.titleMd else StructuralType.body,
                            color = StructuralColors.onMedia,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val author = if (ui.authorMasked) resolve(FeedStringKey.BlindAuthor) else ui.authorName
                val time = resolve(
                    FeedStringKey.TimeOfDay,
                    ui.publishedHour.toString().padStart(2, '0'),
                    ui.publishedMinute.toString().padStart(2, '0'),
                )
                FrMicroRow(
                    items = if (metricValue == null) {
                        listOf(author, time, resolve(FeedStringKey.NoVotesYet).uppercase())
                    } else {
                        listOf(author, time)
                    },
                    color = StructuralColors.onMedia.copy(alpha = 0.72f),
                )
            }
            if (metricValue != null) {
                FrMetric(
                    value = metricValue,
                    size = if (isHero) FrMetricSize.Lg else FrMetricSize.Md,
                    color = StructuralColors.onMedia,
                )
            }
        }
    }
}

/**
 * Compact structural react affordance, restored on the meal tile (the matte design's
 * `FrFeedMealRow.ReactionButton`, dropped in the structural port). A frosted glass pill carrying the
 * meal-day [glyph] + live reaction [count]; tapping toggles the viewer's reaction. When [reacted] it
 * swaps to a celebration tint (the only role color used — `StructuralColors` has no celebration, so
 * this reads `LocalFrSemanticColors`, exactly like the matte button). Its own [clickable] swallows the
 * tap before the tile's onClick. contentDescription/count reuse the same `FeedStringKey`s the matte
 * button used.
 */
@Composable
private fun ReactPill(
    glyph: String,
    count: Int,
    reacted: Boolean,
    onReact: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    val container = if (reacted) semantic.celebration.copy(alpha = 0.22f) else StructuralColors.chip
    val contentColor = if (reacted) semantic.celebration else StructuralColors.foreground
    val cd =
        if (count > 0) resolvePlural(FeedPluralKey.ReactionsLabel, count, count) else resolve(FeedStringKey.ReactionCta)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = cd,
                onClick = onReact,
            )
            .semantics {
                role = Role.Button
                selected = reacted
                contentDescription = cd
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(text = glyph, style = StructuralType.micro, color = contentColor)
        if (count > 0) {
            FrText(
                text = resolve(FeedStringKey.ReactionCount, count),
                style = StructuralType.microMono,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Empty / loading / no-crew
// ----------------------------------------------------------------------------------------------

@Composable
private fun EmptyDayTile(viewingToday: Boolean) {
    FrGlassTile(depth = FrTileDepth.Default, modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            FrText(
                text = resolve(FeedStringKey.EmptyHeadline),
                style = StructuralType.titleLg,
                color = StructuralColors.foreground,
            )
            Spacer(Modifier.height(Spacing.xs))
            FrText(
                text = resolve(if (viewingToday) FeedStringKey.EmptySubtext else FeedStringKey.EmptySubtextPast),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FeedSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(220.dp)) {}
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.weight(1f).height(140.dp)) {}
            FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.weight(1f).height(140.dp)) {}
        }
    }
}

@Composable
private fun NoCrewPlane(onPickCrewClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.widthIn(max = 420.dp)) {
            FrText(
                text = resolve(FeedStringKey.NoActiveCrewHeadline),
                style = StructuralType.titleLg,
                color = StructuralColors.foreground,
            )
            Spacer(Modifier.height(Spacing.xs))
            FrText(
                text = resolve(FeedStringKey.NoActiveCrewSubtext),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(Spacing.md))
            FrGlassButton(
                label = resolve(FeedStringKey.PickCrewCta),
                onClick = onPickCrewClick,
                tone = FrButtonTone.Primary,
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Crew banner (C9)
// ----------------------------------------------------------------------------------------------

/**
 * The crew banner cover, shown sharp as a full-width rounded hero at the top of the content plane
 * (the media floor already carries a blurred echo of it). Crops to a fixed strip height around the
 * owner's chosen vertical focal point ([focalY] 0=top..1=bottom); tap opens the full-screen viewer.
 */
@Composable
private fun CrewBannerHero(url: String, focalY: Float, onClick: () -> Unit) {
    AsyncImage(
        model = stablePlateRequest(url, ""),
        contentDescription = resolve(FeedStringKey.CrewBannerCd),
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(horizontalBias = 0f, verticalBias = focalY * 2f - 1f),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClickLabel = resolve(FeedStringKey.CrewBannerCd), onClick = onClick),
    )
}

/**
 * Full-screen crew-banner viewer: the banner fitted on a near-opaque scrim, dismissed by tapping the
 * backdrop, the close button, or system back ([Dialog] consumes back). [url] is the already-resolved
 * signed URL from the feed hero.
 */
@Composable
private fun CrewBannerViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = resolve(FeedStringKey.CrewBannerCloseCd),
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = stablePlateRequest(url, ""),
                contentDescription = resolve(FeedStringKey.CrewBannerCd),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            FrGlassCircleButton(
                icon = FrIcons.Close,
                onClick = onDismiss,
                contentDescription = resolve(FeedStringKey.CrewBannerCloseCd),
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// helpers
// ----------------------------------------------------------------------------------------------

/** Appetizing brush shown behind a tile while its photo loads (or when it has none). */
private fun dishBrushFor(slot: MealSlotUi?): Brush = when (slot) {
    MealSlotUi.Breakfast -> StructuralColors.dishSalad
    MealSlotUi.Brunch -> StructuralColors.dishTacos
    MealSlotUi.Lunch -> StructuralColors.dishMackerel
    MealSlotUi.Snack -> StructuralColors.dishTacos
    MealSlotUi.Merienda -> StructuralColors.dishSalad
    MealSlotUi.Dinner -> StructuralColors.dishRamen
    null -> StructuralColors.dishMackerel
}

