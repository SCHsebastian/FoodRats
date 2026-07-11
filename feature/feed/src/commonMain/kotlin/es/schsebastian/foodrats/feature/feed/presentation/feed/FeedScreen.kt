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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.runtime.mutableIntStateOf
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
import es.schsebastian.foodrats.core.designsystem.structural.FrBentoItem
import es.schsebastian.foodrats.core.designsystem.structural.frBentoItems
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
import es.schsebastian.foodrats.feature.feed.presentation.components.FrSyncStatusBar
import es.schsebastian.foodrats.feature.feed.presentation.components.FrUploadQueueBar
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
 * asymmetric bento of meals (emitted lazily via [frBentoItems]) where tile size = score rank. Header chrome (crew name + avatar)
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
    // BUG FIX (2026-07-12): the FeedHeader's measured height, so the pinned upload/sync overlay
    // below can reserve exactly that much space instead of drawing on top of it (see the overlay's
    // own comment further down for the full story).
    var headerHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — edge-to-edge media floor. Crew banner wins (the owner's deliberate hero); else the
        // day's highest-scoring plate; else a warm atmospheric brush. Memoized on `meals` so the
        // filter+max only re-runs when the day's meals actually change, not on every feed-state churn.
        val heroMeal = remember(meals) {
            meals.filter { it.feedImageUrl.isNotBlank() }
                .maxByOrNull { it.averageScore ?: -1.0 }
        }
        val floorModel = when {
            state.bannerImageUrl != null -> stablePlateRequest(state.bannerImageUrl!!, state.bannerCacheKey)
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

            // Bento boundary lambdas + day-strip labels, hoisted above the lazy list. The lambdas are
            // memoized so they stay stable across feed-state churn/scroll — fresh lambdas every
            // recomposition mark the (now @Immutable) tiles unstable and block recomposition-skipping.
            // `onTileClick` re-keys on the current day so taps still navigate with the correct dayIso.
            val dayIso = state.day?.day?.date?.toString().orEmpty()
            val onTileClick = remember(onMealClick, dayIso) { { id: String -> onMealClick(id, dayIso) } }
            // BUG FIX (2026-07-12): crewId is threaded through from the tapped tile's FeedMealUi
            // (see feedBentoItems below), not re-read from the live active-crew provider — see
            // FeedIntent.RateMeal's kdoc for why.
            val onTileReact = remember(vm) { { id: String, crewId: String -> vm.onIntent(FeedIntent.ReactMeal(id, crewId)) } }
            val bentoItems = feedBentoItems(meals, state.scoreStyle, onTileClick, onTileReact)

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

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { vm.onIntent(FeedIntent.Refresh) },
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Lazy content plane: the header, day strip, challenge, welcome banner AND each bento
                // row are all LazyColumn items, so OFF-SCREEN bento rows (and their tile image decodes)
                // are never composed. The prior `Column(verticalScroll)` composed and decoded every
                // tile of the day at once. Inter-item spacing (Spacing.sm) matches the old Column's
                // `spacedBy` and the bento's verticalGap, so the layout is unchanged.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .frSafeHorizontalPadding()
                        .frContentWidth(Breakpoints.contentMax)
                        .padding(horizontal = Spacing.md),
                    // The status-bar inset is CONTENT padding, not a viewport modifier, so content
                    // scrolls immersively under the translucent status bar over the edge-to-edge media
                    // floor — exactly as the old `verticalScroll().statusBarsPadding()` (inset inside the
                    // scroll) did. A viewport `statusBarsPadding()` would instead clip scrolling at the
                    // status bar and break the zero-chrome feel.
                    contentPadding = WindowInsets.statusBars.asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item(key = "top-spacer") { Spacer(Modifier.height(Spacing.xs)) }

                    item(key = "header") {
                        FeedHeader(
                            crewName = crewName,
                            avatarInitials = avatarInitials,
                            avatarUrl = avatarUrl,
                            onMediaFloor = onPhotoFloor,
                            onPickCrewClick = onPickCrewClick,
                            onProfileClick = onProfileClick,
                            onCrewSettingsClick = onCrewSettingsClick,
                            // BUG FIX (2026-07-12): measured so the pinned overlay below can clear it.
                            modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height },
                        )
                    }

                    // C9 — crew banner hero: the owner's cover image shown sharp (the media floor
                    // already carries a blurred echo of it). Honors the owner's vertical focal point;
                    // tap opens the full-screen viewer. Hidden when no banner is set.
                    state.bannerImageUrl?.let { url ->
                        item(key = "banner") {
                            CrewBannerHero(
                                url = url,
                                cacheKey = state.bannerCacheKey,
                                focalY = state.bannerFocalY,
                                onClick = { bannerToView = url },
                            )
                        }
                    }

                    item(key = "day-strip") {
                        FeedDayStrip(
                            primary = dayPrimary,
                            secondary = daySecondary,
                            canGoPrev = state.canGoPrev,
                            canGoNext = state.canGoNext,
                            onMediaFloor = onPhotoFloor,
                            onPrev = { vm.onIntent(FeedIntent.PrevDay) },
                            onNext = { vm.onIntent(FeedIntent.NextDay) },
                        )
                    }

                    item(key = "challenge") {
                        FeedChallengeRow(challenge = state.weeklyChallenge, plateCount = meals.size, onMediaFloor = onPhotoFloor)
                    }

                    // Offline-first indicators + transient errors, kept functional over the floor.
                    state.error?.let { err ->
                        if (err !is FeedError.Session.NoActiveCrew) {
                            item(key = "feed-error") {
                                FrText(
                                    text = resolve(err.toStringKey()),
                                    style = StructuralType.body,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    state.rateError?.let { err ->
                        item(key = "rate-error") {
                            FrText(resolve(err.toStringKey()), style = StructuralType.body, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.reactError?.let { err ->
                        item(key = "react-error") {
                            FrText(resolve(err.toStringKey()), style = StructuralType.body, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // C6 — pinned crew welcome message.
                    state.welcomeMessage?.let { msg ->
                        item(key = "welcome") {
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
                    }

                    when {
                        state.isLoading && meals.isEmpty() -> item(key = "skeleton") { FeedSkeleton() }
                        meals.isEmpty() -> item(key = "empty") {
                            EmptyDayTile(
                                viewingToday = state.today == null || state.day?.day?.date == state.today,
                            )
                        }
                        else -> frBentoItems(bentoItems)
                    }

                    item(key = "dock-clearance") { Spacer(Modifier.height(DOCK_CLEARANCE)) }
                }
            }
        }

        // Offline-first indicators, PINNED over the content plane (mirrors StatsScreen's
        // FrUploadProgressBar overlay): the publish-queue bar (spinner pill the instant a publish
        // upload starts + danger tile for terminal-failed drafts) and the write-outbox sync bar.
        // Overlay — not a list item — so the feedback is visible immediately when the user lands on
        // the feed right after tapping publish, at any scroll position, and even on the no-crew plane.
        //
        // BUG FIX (2026-07-12): this overlay used to sit at the SAME on-screen rect as the
        // FeedHeader (both start ~4dp below the status bar), so a just-published terminal failure's
        // Retry/Dismiss buttons could steal taps meant for the crew switcher / Settings gear / profile
        // avatar underneath. Fixed two ways: (1) it's now composed at all ONLY while at least one bar
        // has something to show — idle means it's absent from the tree, not just invisible; (2) while
        // present it reserves the FeedHeader's MEASURED height (via `headerHeightPx`, set by
        // `onGloballyPositioned` on the header item — the header can wrap to more than one line
        // depending on crew-name length) plus the LazyColumn's own top-spacer + inter-item gap, so the
        // two rectangles never intersect. The header itself doesn't scroll away fast enough to make
        // re-tracking scroll offset worthwhile — a fixed reservation is the simplest correct fix and
        // matches the audit's recommended remedy.
        val anyIndicatorVisible = state.isUploadActive || state.queuedPending > 0 || state.queuedFailed > 0 ||
            state.syncPending > 0 || state.syncFailed > 0
        if (anyIndicatorVisible) {
            // No FeedHeader exists on the no-crew plane, so nothing to clear there — otherwise a
            // stale measurement from a prior crew-having render would leave an empty gap up top.
            val headerHeightDp = if (state.error is FeedError.Session.NoActiveCrew) {
                0.dp
            } else {
                with(LocalDensity.current) { headerHeightPx.toDp() }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    // Clears the LazyColumn's top-spacer (Spacing.xs) + the spacedBy gap before the
                    // header item (Spacing.sm) + the header's own measured height.
                    .padding(top = Spacing.xs + Spacing.sm + headerHeightDp)
                    .frSafeHorizontalPadding()
                    .frContentWidth(Breakpoints.contentMax)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FrUploadQueueBar(
                    pending = state.queuedPending,
                    failed = state.queuedFailed,
                    uploading = state.isUploadActive,
                    onRetry = { vm.onIntent(FeedIntent.RetryQueuedDrafts) },
                    onDismiss = { vm.onIntent(FeedIntent.DismissQueuedDrafts) },
                )
                FrSyncStatusBar(
                    pending = state.syncPending,
                    failed = state.syncFailed,
                    onRetry = { vm.onIntent(FeedIntent.RetrySyncOutbox) },
                    onDismiss = { vm.onIntent(FeedIntent.DismissSyncOutbox) },
                )
            }
        }

        // C9 — full-screen crew-banner viewer, opened by tapping the banner hero.
        bannerToView?.let { url ->
            CrewBannerViewer(url = url, cacheKey = state.bannerCacheKey, onDismiss = { bannerToView = null })
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
    modifier: Modifier = Modifier,
) {
    // Over a photo/banner floor the header sits on dark media → white; over the light atmospheric floor
    // (empty feed in light mode) it must flip to dark ink.
    val planeFg = if (onMediaFloor) StructuralColors.onMedia else StructuralColors.foreground
    Row(
        modifier = modifier.fillMaxWidth(),
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

/**
 * Builds the bento items for the day's meals: sort by score so tile size = rank (the top plate is the
 * wide hero, the lowest the narrow tile). Returned to [FeedScreen] and emitted as lazy rows via
 * [frBentoItems] so off-screen tiles never compose. The sort is memoized; the `FrBentoItem` list (with
 * its composable tile-content lambdas) is built each recomposition, exactly as the prior `FeedBento`
 * did — emitting it lazily, not memoizing the list, is what avoids composing off-screen tiles.
 */
@Composable
private fun feedBentoItems(
    meals: List<FeedMealUi>,
    scoreStyle: FrScoreStyle,
    onMealClick: (mealId: String) -> Unit,
    onReactClick: (mealId: String, crewId: String) -> Unit,
): List<FrBentoItem> {
    val ranked = remember(meals) { meals.sortedByDescending { it.averageScore ?: -1.0 } }
    return ranked.mapIndexed { index, ui ->
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
                // IMAGE-1 — only the wide tiles (hero span-6 + the span-4 #2) render large enough to
                // need the full 1280px plate; the narrow tiles (span < 4) load the 512px server
                // thumbnail instead, cutting their image egress + decode without a visible quality hit.
                wide = span >= 4,
                // B4 — the narrowest (sub-3-span) tile has no room for the dish name; at <3 cols it
                // collapses to ~3 chars + an unreadable ellipsis ("Pla…"). Drop the name there — the
                // photo, slot chip, score, and cook avatar already identify the plate; the wider
                // tiles (span 3/4/6) keep it.
                showDishName = span >= 3,
                scoreStyle = scoreStyle,
                onClick = { onMealClick(ui.mealId) },
                onReactClick = { onReactClick(ui.mealId, ui.crewId) },
            )
        }
    }
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
    // IMAGE-1 — wide tiles (span >= 4) load the full plate; narrow tiles the 512px thumb. Defaults
    // to `true` so any other caller keeps the full-plate behaviour.
    wide: Boolean = true,
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
        // IMAGE-1 — wide tiles render large, so they load the FULL plate (bentoImageUrl) for crisp
        // edges; narrow tiles (span < 4) are small enough that the 512px server thumbnail
        // (feedImageUrl) is indistinguishable while costing far less egress + decode. The cache keys
        // differ (thumb path vs plate path), so the detail screen's full-plate load is unaffected.
        val imageUrl = if (wide) ui.bentoImageUrl else ui.feedImageUrl
        val imageCacheKey = if (wide) ui.bentoImageCacheKey else ui.feedImageCacheKey
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = stablePlateRequest(imageUrl, imageCacheKey),
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
private fun CrewBannerHero(url: String, cacheKey: String, focalY: Float, onClick: () -> Unit) {
    AsyncImage(
        model = stablePlateRequest(url, cacheKey),
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
 * signed URL from the feed hero; [cacheKey] keys Coil on the stable versioned path so the viewer
 * reuses the hero's already-decoded bytes instead of re-downloading.
 */
@Composable
private fun CrewBannerViewer(url: String, cacheKey: String, onDismiss: () -> Unit) {
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
                model = stablePlateRequest(url, cacheKey),
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

