package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.designsystem.molecules.FrReportSheet
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreStyle
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrVoteBars
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.scoreToEmoji
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrBarTrack
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrFloorTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMetric
import es.schsebastian.foodrats.core.designsystem.structural.FrMetricSize
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScoreDisc
import es.schsebastian.foodrats.core.designsystem.structural.FrScoreTone
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.feed.i18n.FeedPluralKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import es.schsebastian.foodrats.feature.feed.presentation.components.FrLocationMap
import es.schsebastian.foodrats.feature.feed.presentation.components.MealSlotUi
import es.schsebastian.foodrats.feature.feed.presentation.components.RaterVoteUi
import es.schsebastian.foodrats.feature.feed.presentation.components.RelativeTimestamp
import es.schsebastian.foodrats.feature.feed.presentation.components.stablePlateRequest
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import kotlin.math.round
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Structural meal detail. The plate IS the floor — a sharp, fixed, edge-to-edge [FrMediaFloor] that the
 * zero-chrome content plane scrolls over: a tall transparent head where the photo shows through and the
 * dish title floats at its foot, then floating frosted strata (score story, your vote, voters, comments)
 * over a darkened continuation of the same plate. Floating glass chrome (back / share / report / block /
 * delete) hovers over the photo; the comment composer is a glass pill pinned to the bottom.
 *
 * ALL ViewModel wiring (rate / share / report / block / delete / comment intents + every dialog, sheet
 * and toast) is preserved verbatim — only the visual layer changed.
 */
@Composable
fun MealDetailScreen(
    mealId: String,
    dayIso: String,
    onBack: () -> Unit,
    vm: MealDetailViewModel = koinViewModel(parameters = { parametersOf(mealId, dayIso) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDeleteMealDialog by remember { mutableStateOf(false) }
    // UGC compliance §5 — block-confirmation dialog.
    // [pendingBlockAccountId] carries the raw account-id string of whoever is about to be blocked.
    // [pendingBlockIsMealAuthor] distinguishes the two intents: true → BlockAuthor (meal-level,
    // dispatch removes meal content reactively), false → BlockCommentAuthor(id) (comment-level).
    var pendingBlockAccountId by remember { mutableStateOf<String?>(null) }
    var pendingBlockIsMealAuthor by remember { mutableStateOf(false) }
    LaunchedEffect(state.mealDeleted) { if (state.mealDeleted) onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        val error = state.error
        when {
            state.isLoading -> DetailLoadingSkeleton(onBack)
            error != null ->
                CenteredState(onBack) { FrText(text = resolve(error.toStringKey()), color = StructuralColors.foreground) }
            state.notFound || state.meal == null ->
                CenteredState(onBack) { FrText(text = resolve(FeedStringKey.DetailNotFound), color = StructuralColors.foreground) }
            else -> MealDetailBody(
                state = state,
                onIntent = vm::onIntent,
                onBack = onBack,
                onRequestDeleteMeal = { showDeleteMealDialog = true },
                // UGC §5 — set pending block target; the confirm dialog is rendered at screen level so
                // it overlays the full surface cleanly. pendingBlockIsMealAuthor selects the correct
                // intent on confirm so BlockAuthor and BlockCommentAuthor both reach their VM paths.
                onRequestBlockAuthor = {
                    pendingBlockIsMealAuthor = true
                    pendingBlockAccountId = state.meal?.authorId
                },
                onRequestBlockCommentAuthor = { authorId ->
                    pendingBlockIsMealAuthor = false
                    pendingBlockAccountId = authorId
                },
            )
        }
    }

    // Share-outcome toast (spec §10). Resolved here; cleared after a short window.
    state.shareOutcome?.let { outcome ->
        val message = resolve(
            when (outcome) {
                ShareOutcomeUi.Succeeded  -> ShareCardStringKey.ShareSucceeded
                ShareOutcomeUi.OpenedSheet -> ShareCardStringKey.ShareOpenedSheet
                ShareOutcomeUi.Failed     -> ShareCardStringKey.ShareFailed
            },
        )
        ShareOutcomeToast(message = message, onDismiss = { vm.onIntent(MealDetailIntent.DismissShareOutcome) })
    }

    if (showDeleteMealDialog) {
        FrConfirmDialog(
            title = resolve(FeedStringKey.DeleteMealConfirmTitle),
            message = resolve(FeedStringKey.DeleteMealConfirmBody),
            confirmLabel = resolve(FeedStringKey.DeleteConfirmCta),
            dismissLabel = resolve(FeedStringKey.DeleteCancelCta),
            onConfirm = {
                showDeleteMealDialog = false
                vm.onIntent(MealDetailIntent.DeleteMeal)
            },
            onDismiss = { showDeleteMealDialog = false },
            destructive = true,
        )
    }

    // UGC compliance §5 — block confirmation dialog (author or comment author).
    pendingBlockAccountId?.let { accountId ->
        FrConfirmDialog(
            title = resolve(FeedStringKey.BlockConfirmTitle),
            message = resolve(FeedStringKey.BlockConfirmBody),
            confirmLabel = resolve(FeedStringKey.BlockConfirmCta),
            dismissLabel = resolve(FeedStringKey.DeleteCancelCta),
            onConfirm = {
                pendingBlockAccountId = null
                val intent = if (pendingBlockIsMealAuthor) {
                    MealDetailIntent.BlockAuthor
                } else {
                    MealDetailIntent.BlockCommentAuthor(accountId)
                }
                vm.onIntent(intent)
            },
            onDismiss = { pendingBlockAccountId = null },
            destructive = true,
        )
    }

    // Report sheet (UGC compliance §4).
    state.reportTarget?.let { target ->
        val title = resolve(
            when (target) {
                ReportTargetUi.Author       -> FeedStringKey.ReportUserCta
                is ReportTargetUi.Comment   -> FeedStringKey.ReportCommentCta
                ReportTargetUi.Meal         -> FeedStringKey.ReportMealCta
            },
        )
        val submitLabel = resolve(
            when (target) {
                ReportTargetUi.Author       -> FeedStringKey.ReportSubmitUser
                is ReportTargetUi.Comment   -> FeedStringKey.ReportSubmitComment
                ReportTargetUi.Meal         -> FeedStringKey.ReportSubmitMeal
            },
        )
        FrReportSheet(
            title = title,
            reasonLabels = reportReasonLabels(),
            submitLabel = submitLabel,
            cancelLabel = resolve(FeedStringKey.DeleteCancelCta),
            submitting = state.reportSubmitting,
            onSubmit = { reason -> vm.onIntent(MealDetailIntent.SubmitReport(reason)) },
            onDismiss = { vm.onIntent(MealDetailIntent.DismissReport) },
        )
    }

    // Report-accepted confirmation toast.
    if (state.reportSuccess) {
        ShareOutcomeToast(
            message = resolve(FeedStringKey.ReportSuccess),
            onDismiss = { vm.onIntent(MealDetailIntent.DismissReportSuccess) },
        )
    }
    // Report / block failure toast.
    state.reportError?.let { err ->
        ShareOutcomeToast(
            message = resolve(err.toStringKey()),
            onDismiss = { vm.onIntent(MealDetailIntent.DismissReport) },
        )
    }
    state.blockError?.let { err ->
        ShareOutcomeToast(
            message = resolve(err.toStringKey()),
            onDismiss = { vm.onIntent(MealDetailIntent.DismissError) },
        )
    }
    // Block-success toast (UGC §5).
    if (state.blockSuccess) {
        ShareOutcomeToast(
            message = resolve(FeedStringKey.BlockSuccess),
            onDismiss = { vm.onIntent(MealDetailIntent.DismissBlockSuccess) },
        )
    }
}

/** Resolves the report-reason labels for [FrReportSheet], Child-safety first (UGC compliance §4). */
@Composable
private fun reportReasonLabels(): Map<FrReportReasonOption, String> {
    // resolve() is a composable read (locale-dependent), so call each one in composition; only the
    // Map construction is hoisted into remember, keyed on the resolved strings. The map is rebuilt
    // exactly when a label changes (e.g. locale switch) and reused across all other recompositions.
    val childSafety = resolve(FeedStringKey.ReportReasonChildSafety)
    val spam = resolve(FeedStringKey.ReportReasonSpam)
    val harassment = resolve(FeedStringKey.ReportReasonHarassment)
    val hate = resolve(FeedStringKey.ReportReasonHate)
    val sexual = resolve(FeedStringKey.ReportReasonSexual)
    val violence = resolve(FeedStringKey.ReportReasonViolence)
    val other = resolve(FeedStringKey.ReportReasonOther)
    return remember(childSafety, spam, harassment, hate, sexual, violence, other) {
        mapOf(
            FrReportReasonOption.CHILD_SAFETY to childSafety,
            FrReportReasonOption.SPAM       to spam,
            FrReportReasonOption.HARASSMENT to harassment,
            FrReportReasonOption.HATE       to hate,
            FrReportReasonOption.SEXUAL     to sexual,
            FrReportReasonOption.VIOLENCE   to violence,
            FrReportReasonOption.OTHER      to other,
        )
    }
}

/** Space reserved so the last content clears the sticky composer. */
private val COMPOSER_CLEARANCE = Sizes.touchTarget + Spacing.xl

/**
 * The plate header fills ~2/3 of the screen height so the meal photo dominates the detail view;
 * the title floats at its foot and the rest scrolls below. Computed from the live window height
 * (KMP-safe via [LocalWindowInfo]); falls back to a sane fixed height before the size is known.
 */
private const val HEAD_HEIGHT_FRACTION = 0.66f
private val HEAD_HEIGHT_FALLBACK = 340.dp

@Composable
private fun rememberHeadHeight(): Dp {
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return remember(containerHeightPx, density) {
        if (containerHeightPx <= 0) {
            HEAD_HEIGHT_FALLBACK
        } else {
            with(density) { (containerHeightPx * HEAD_HEIGHT_FRACTION).toDp() }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Transient states (loading / error / not-found) — still need a back affordance + a floor.
// ----------------------------------------------------------------------------------------------

@Composable
private fun CenteredState(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloorBrush()
        Box(modifier = Modifier.fillMaxSize().frSafeHorizontalPadding().padding(Spacing.lg), contentAlignment = Alignment.Center) { content() }
        FrGlassCircleButton(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(FeedStringKey.DetailBackCta),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.md),
        )
    }
}

/** Loading placeholder: a frosted floor + a back pill + a couple of shimmer strata. */
@Composable
private fun DetailLoadingSkeleton(onBack: () -> Unit) {
    val headHeight = rememberHeadHeight()
    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloorBrush()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .frSafeHorizontalPadding()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Spacer(Modifier.height(headHeight - Spacing.xl))
            FrShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(34.dp), shape = RoundedCornerShape(Radius.sm))
            FrShimmerBox(modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(Radius.lg))
            FrShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(Radius.lg))
        }
        FrGlassCircleButton(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(FeedStringKey.DetailBackCta),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.md),
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Body
// ----------------------------------------------------------------------------------------------

@Composable
private fun MealDetailBody(
    state: MealDetailState,
    onIntent: (MealDetailIntent) -> Unit,
    onBack: () -> Unit,
    onRequestDeleteMeal: () -> Unit,
    onRequestBlockAuthor: () -> Unit = {},
    onRequestBlockCommentAuthor: (String) -> Unit = {},
) {
    val meal = state.meal ?: return
    val headHeight = rememberHeadHeight()
    // FIREST-2: the live comment listener is bounded to the newest [commentLimit] comments. When the
    // visible rows fill that window there are (probably) older ones beyond it, so offer "load older".
    val canLoadOlder = state.commentRows.size >= state.commentLimit
    var pendingDeleteCommentId by remember { mutableStateOf<MealCommentId?>(null) }
    // Full-screen zoomable photo viewer (opened by tapping the header plate).
    var showPhotoViewer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — a solid, warm-concrete page floor. The plate is NO LONGER the full-screen floor: it's
        // bounded to the header at the top of the scrolling plane (below), so everything under the
        // header sits on an opaque, comfortable surface instead of a darkened full-bleed photo.
        Box(Modifier.fillMaxSize().background(StructuralColors.stageFloor))

        // Z2 — scrolling content plane. RENDER-3: the comment list is virtualized via a LazyColumn so
        // a popular meal's comments compose/recycle on demand instead of all rendering up front. The
        // fixed header sections above stay as one-shot item {} blocks in the same order. Each content
        // item carries the original content column's horizontal gutter (full-bleed header excepted) and
        // a leading top gap that reproduces the old spacedBy(lg) sections / spacedBy(md) comment rows.
        val sectionModifier = Modifier
            .fillMaxWidth()
            .frSafeHorizontalPadding()
            .frContentWidth(Breakpoints.contentMax)
            .padding(horizontal = Spacing.lg)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Header: the plate, bounded to the top. The dish title floats at its foot over a scrim.
            // clipToBounds() is essential: FrMediaFloor over-scales the image (.scale(1.06f)), which
            // would otherwise bleed the bright bottom edge of the photo down over the description below.
            // Tapping the plate opens the full-screen zoomable viewer (only when there's a real photo).
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headHeight)
                        .clipToBounds()
                        .then(
                            if (meal.photoUrl.isNotBlank()) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = resolve(FeedStringKey.MealPhotoOpenCd),
                                    onClick = { showPhotoViewer = true },
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    // The plate, confined to the header. Standard scrim keeps it crisp; OnMedia keeps the
                    // dark wash in light theme so the white title/author stay legible over it.
                    if (meal.photoUrl.isNotBlank()) {
                        FrMediaFloor(
                            painter = rememberAsyncImagePainter(model = stablePlateRequest(meal.photoUrl, meal.plateCacheKey)),
                            blur = StructuralBlur.None,
                            dim = 0.18f,
                            scrim = FrScrimStyle.Standard,
                            tone = FrFloorTone.OnMedia,
                        )
                    } else {
                        FrMediaFloor(brush = dishBrushFor(meal.slot), blur = StructuralBlur.Soft, dim = 0.3f, tone = FrFloorTone.OnMedia)
                    }
                    // Bottom-anchored gradient scrim so the white title/author stay readable over a
                    // bright plate (the Standard media-floor scrim is transparent at the head foot).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headHeight * 0.55f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Slot chip — only when tagged (slot is optional).
                        meal.slot?.let { slot ->
                            FrStructuralChip(label = resolve(slot.labelKey()).uppercase())
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        FrText(
                            text = meal.dishName,
                            style = StructuralType.titleXl,
                            color = StructuralColors.onMedia,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        AuthorRow(meal)
                    }
                }
            }

            // Content plane on the solid floor — a top gap clears the photo header so the description
            // sits comfortably on the floor instead of crowding the photo's bottom edge.
            if (meal.description.isNotBlank()) {
                item {
                    FrText(
                        text = meal.description,
                        style = StructuralType.body,
                        color = StructuralColors.foreground.copy(alpha = 0.92f),
                        modifier = sectionModifier.padding(top = Spacing.lg),
                    )
                }
            }

            if (meal.ingredients.isNotEmpty()) {
                item { Box(sectionModifier.padding(top = Spacing.lg)) { IngredientChips(meal.ingredients) } }
            }

            // Guarded so a meal without a location does not leave an empty item slot (which would add a
            // phantom gap); LocationSection itself early-returns on the same condition.
            if (meal.latitude != null && meal.longitude != null) {
                item { Box(sectionModifier.padding(top = Spacing.lg)) { LocationSection(meal) } }
            }

            item { Box(sectionModifier.padding(top = Spacing.lg)) { ScoreStoryCard(meal, state.scoreStyle) } }

            // Guarded to mirror RatingSection's own emit condition (it renders nothing otherwise, e.g. on
            // your own meal) so an empty slot never adds a phantom gap.
            if (meal.viewerRating != null || meal.canRate || state.voteEditMode) {
                item {
                    Box(sectionModifier.padding(top = Spacing.lg)) {
                        RatingSection(
                            meal = meal,
                            pendingRate = state.pendingRate,
                            scoreStyle = state.scoreStyle,
                            voteEditMode = state.voteEditMode,
                            showChangeVoteConfirm = state.showChangeVoteConfirm,
                            onIntent = onIntent,
                        )
                    }
                }
            }

            if (meal.votes.isNotEmpty()) {
                item { Box(sectionModifier.padding(top = Spacing.lg)) { VotersCard(meal, state.scoreStyle) } }
            }

            // Comments — heading is a fixed item; the rows virtualize via items(key = stable id).
            item {
                Box(sectionModifier.padding(top = Spacing.lg)) {
                    FrEyebrow(
                        text = resolve(FeedStringKey.CommentsTitle).uppercase(),
                        color = StructuralColors.foreground.copy(alpha = 0.85f),
                    )
                }
            }
            // FIREST-2: "load older comments" sits ABOVE the comment rows (older = above) and only
            // appears once the visible rows fill the current window. Tapping it expands the listener.
            if (canLoadOlder) {
                item {
                    Box(sectionModifier.padding(top = Spacing.md), contentAlignment = Alignment.Center) {
                        FrGlassButton(
                            label = resolve(FeedStringKey.LoadOlderComments),
                            onClick = { onIntent(MealDetailIntent.LoadOlderComments) },
                            tone = FrButtonTone.Glass,
                            compact = true,
                        )
                    }
                }
            }
            when {
                state.commentsLoading && state.commentRows.isEmpty() ->
                    item { Box(sectionModifier.padding(top = Spacing.md)) { CommentsLoadingSkeleton() } }
                state.commentRows.isEmpty() && state.commentReadError == null ->
                    item {
                        Box(sectionModifier.padding(top = Spacing.md)) {
                            FrText(
                                text = resolve(FeedStringKey.CommentsEmpty),
                                style = StructuralType.body,
                                color = StructuralColors.foreground.copy(alpha = 0.6f),
                            )
                        }
                    }
                state.commentReadError != null ->
                    item {
                        Box(sectionModifier.padding(top = Spacing.md)) {
                            FrText(
                                text = resolve(state.commentReadError.toStringKey()),
                                style = StructuralType.body,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                else -> items(state.commentRows, key = { it.id.value }) { c ->
                    val editing = state.editingCommentId == c.id
                    Box(sectionModifier.padding(top = Spacing.md)) {
                        StructuralCommentRow(
                            isOwn = c.isOwnComment,
                            displayName = c.displayName,
                            avatarUrl = c.avatarUrl,
                            text = c.text,
                            relative = c.relative,
                            loading = c.loading,
                            isDeleted = c.isDeleted,
                            isEdited = c.isEdited,
                            canDelete = c.canDelete,
                            onDelete = { pendingDeleteCommentId = c.id },
                            canEdit = c.canEdit,
                            onEdit = { onIntent(MealDetailIntent.StartEditComment(c.id)) },
                            canModerate = c.canModerate,
                            onReport = { onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Comment(c.id))) },
                            onBlock = { onRequestBlockCommentAuthor(c.authorId) },
                            editing = editing,
                            editInput = state.commentEditInput,
                            isSavingEdit = state.isEditingComment,
                            editError = if (editing) state.commentEditError?.let { resolve(it.toStringKey()) } else null,
                            onEditInputChange = { onIntent(MealDetailIntent.EditCommentInputChanged(it)) },
                            onEditSave = { onIntent(MealDetailIntent.SubmitEditComment) },
                            onEditCancel = { onIntent(MealDetailIntent.CancelEditComment) },
                        )
                    }
                }
            }
            if (state.commentWriteError != null) {
                item {
                    Box(sectionModifier.padding(top = Spacing.md)) {
                        FrText(
                            text = resolve(state.commentWriteError.toStringKey()),
                            style = StructuralType.body,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Final item: clears the sticky composer so the last comment is never hidden behind it.
            item { Spacer(Modifier.height(COMPOSER_CLEARANCE)) }
        }

        // Floating chrome over the plate (fixed; always tappable).
        FrGlassCircleButton(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(FeedStringKey.DetailBackCta),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.md),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().frSafeHorizontalPadding().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.isPreparingShare) {
                FrProgressIndicator()
            } else {
                FrGlassCircleButton(
                    icon = FrIcons.Share,
                    onClick = { onIntent(MealDetailIntent.ShareTapped) },
                    contentDescription = resolve(FeedStringKey.ShareMeal),
                )
            }
            // Report/block the author (UGC §4/§5). Hidden on your own meal + while blind voting masks it.
            if (state.canModerateMeal && !meal.authorMasked) {
                FrGlassCircleButton(
                    icon = FrIcons.Flag,
                    onClick = { onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Meal)) },
                    contentDescription = resolve(FeedStringKey.ReportMealCta),
                )
                FrGlassCircleButton(
                    icon = FrIcons.Block,
                    onClick = onRequestBlockAuthor,
                    contentDescription = resolve(FeedStringKey.BlockAuthorCta),
                )
            }
            if (state.canDeleteMeal && !state.isDeletingMeal) {
                FrGlassCircleButton(
                    icon = FrIcons.Delete,
                    onClick = onRequestDeleteMeal,
                    contentDescription = resolve(FeedStringKey.DeleteMealCta),
                    danger = true,
                )
            }
        }

        // Sticky comment composer — a glass pill pinned to the bottom; rises with the IME.
        StructuralCommentComposer(
            value = state.commentInput,
            enabled = !state.isPostingComment,
            sendEnabled = !state.isPostingComment && state.commentInput.isNotBlank(),
            onChange = { onIntent(MealDetailIntent.CommentInputChanged(it)) },
            onSend = { onIntent(MealDetailIntent.PostComment) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showPhotoViewer && meal.photoUrl.isNotBlank()) {
        MealPhotoViewer(
            photoUrl = meal.photoUrl,
            cacheKey = meal.plateCacheKey,
            onDismiss = { showPhotoViewer = false },
        )
    }

    pendingDeleteCommentId?.let { id ->
        FrConfirmDialog(
            title = resolve(FeedStringKey.DeleteCommentConfirmTitle),
            confirmLabel = resolve(FeedStringKey.DeleteConfirmCta),
            dismissLabel = resolve(FeedStringKey.DeleteCancelCta),
            onConfirm = {
                pendingDeleteCommentId = null
                onIntent(MealDetailIntent.DeleteComment(id))
            },
            onDismiss = { pendingDeleteCommentId = null },
            destructive = true,
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Head — author
// ----------------------------------------------------------------------------------------------

@Composable
private fun AuthorRow(meal: FeedMealUi) {
    // Blind voting: mask the author exactly like the feed — name → "Hidden until you rate", no avatar.
    val authorLabel = if (meal.authorMasked) resolve(FeedStringKey.BlindAuthor) else meal.authorName
    val time = resolve(
        FeedStringKey.TimeOfDay,
        meal.publishedHour.toString().padStart(2, '0'),
        meal.publishedMinute.toString().padStart(2, '0'),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrGlassAvatar(
            initials = if (meal.authorMasked) "" else meal.authorName,
            image = if (meal.authorMasked) null else meal.authorAvatarUrl?.let { rememberAsyncImagePainter(it) },
            ring = FrAvatarRing.Rust,
            size = 40.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = authorLabel,
                style = StructuralType.titleMd,
                color = StructuralColors.onMedia,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FrMicroRow(
                // Slot is optional — drop it from the micro row when the author tagged none.
                items = listOfNotNull(time, meal.slot?.let { resolve(it.labelKey()).uppercase() }),
                color = StructuralColors.onMedia.copy(alpha = 0.72f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChips(ingredients: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ingredients.forEach { name -> FrStructuralChip(label = name, compact = true) }
    }
}

/** Tappable mini-map; [FrLocationMap] opens the native maps app on tap. */
@Composable
private fun LocationSection(meal: FeedMealUi) {
    val lat = meal.latitude
    val lon = meal.longitude
    if (lat == null || lon == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FrEyebrow(text = resolve(FeedStringKey.LocationLabel).uppercase(), color = StructuralColors.foreground.copy(alpha = 0.85f))
        val mapLabel = resolve(FeedStringKey.LocationMapCta)
        FrLocationMap(
            latitude = lat,
            longitude = lon,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(RoundedCornerShape(Radius.lg))
                .semantics {
                    contentDescription = mapLabel
                    role = Role.Button
                },
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Score story — the oversized metric
// ----------------------------------------------------------------------------------------------

@Composable
private fun ScoreStoryCard(meal: FeedMealUi, scoreStyle: FrScoreStyle) {
    val avg = meal.averageScore
    if (avg == null || meal.ratingCount <= 0) {
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
            FrEyebrow(text = resolve(FeedStringKey.CrewScoreLabel).uppercase())
            Spacer(Modifier.height(Spacing.sm))
            FrText(
                text = resolve(FeedStringKey.NoVotesYet),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.7f),
            )
        }
        return
    }
    val avgRounded = (round(avg * 10) / 10.0).toString()
    val avgRoundedInt = round(avg).toInt().coerceIn(1, 5)
    // C8b: the headline number adapts to the crew's score style.
    val headlineScore = when (scoreStyle) {
        FrScoreStyle.Stars   -> avgRounded
        FrScoreStyle.Emoji   -> scoreToEmoji(avgRoundedInt)
        FrScoreStyle.Numeric -> avgRounded
    }
    val captionText = when (scoreStyle) {
        FrScoreStyle.Stars   -> resolve(FeedStringKey.RatingSummary, avgRounded, meal.ratingCount)
        FrScoreStyle.Emoji   -> resolvePlural(FeedPluralKey.ScoreSummaryVotes, meal.ratingCount, scoreToEmoji(avgRoundedInt), meal.ratingCount)
        FrScoreStyle.Numeric -> resolvePlural(FeedPluralKey.ScoreSummaryVotes, meal.ratingCount, avgRounded, meal.ratingCount)
    }
    FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
        FrEyebrow(text = resolve(FeedStringKey.CrewScoreLabel).uppercase())
        Spacer(Modifier.height(Spacing.xs))
        FrMetric(value = headlineScore, size = FrMetricSize.Xl, color = MaterialTheme.colorScheme.primary)
        FrText(
            text = captionText,
            style = StructuralType.micro,
            color = StructuralColors.foreground.copy(alpha = 0.72f),
        )
        if (meal.votes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            val voteHistogram = remember(meal.votes) {
                meal.votes.groupingBy { it.score.coerceIn(Score.MIN, Score.MAX) }.eachCount()
            }
            FrVoteBars(
                votes = voteHistogram,
                maxScore = Score.MAX,
                hotThreshold = Score.MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Your vote
// ----------------------------------------------------------------------------------------------

@Composable
private fun RatingSection(
    meal: FeedMealUi,
    pendingRate: Boolean,
    scoreStyle: FrScoreStyle,
    voteEditMode: Boolean,
    showChangeVoteConfirm: Boolean,
    onIntent: (MealDetailIntent) -> Unit,
) {
    when {
        // Already voted and NOT actively changing → the locked tile, plus the one-time-only
        // "Change my vote" affordance while [FeedMealUi.canChangeVote] (voted, not yet edited,
        // window open). Once the change is used the affordance is gone and the tile is final.
        meal.viewerRating != null && !voteEditMode -> {
            val voteText = when (scoreStyle) {
                FrScoreStyle.Stars   -> resolve(FeedStringKey.YourVote, meal.viewerRating)
                FrScoreStyle.Emoji   -> scoreToEmoji(meal.viewerRating.coerceIn(1, 5))
                FrScoreStyle.Numeric -> meal.viewerRating.toString()
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrEyebrow(text = resolve(FeedStringKey.YourVoteLockedEyebrow).uppercase(), color = StructuralColors.foreground.copy(alpha = 0.85f))
                FrGlassTile(depth = FrTileDepth.Default, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (scoreStyle == FrScoreStyle.Stars) {
                            FrIcon(image = FrIcons.Star, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.iconMd))
                        }
                        FrText(text = voteText, style = StructuralType.titleLg, color = StructuralColors.foreground)
                    }
                }
                if (meal.canChangeVote) {
                    FrGlassButton(
                        label = resolve(FeedStringKey.ChangeVoteCta),
                        onClick = { onIntent(MealDetailIntent.RequestChangeVote) },
                        tone = FrButtonTone.Glass,
                        compact = true,
                        enabled = !pendingRate,
                    )
                }
            }
        }
        // First vote OR an in-progress change → the picker (pre-filled with the current score in
        // edit mode so the user can adjust from where they were).
        meal.canRate || voteEditMode -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrEyebrow(
                text = resolve(if (voteEditMode) FeedStringKey.ChangeVoteCta else FeedStringKey.RateThisMeal).uppercase(),
                color = StructuralColors.foreground.copy(alpha = 0.85f),
            )
            FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
                FrStarRatingPicker(
                    onSelect = { score -> onIntent(MealDetailIntent.RateMeal(score)) },
                    value = if (voteEditMode) (meal.viewerRating ?: 0) else 0,
                    enabled = !pendingRate,
                    style = scoreStyle,
                )
            }
        }
        else -> Unit
    }

    // One-time-only confirmation before the single allowed change.
    if (showChangeVoteConfirm) {
        FrConfirmDialog(
            title = resolve(FeedStringKey.ChangeVoteConfirmTitle),
            message = resolve(FeedStringKey.ChangeVoteConfirmBody),
            confirmLabel = resolve(FeedStringKey.ChangeVoteConfirmCta),
            dismissLabel = resolve(FeedStringKey.DeleteCancelCta),
            onConfirm = { onIntent(MealDetailIntent.ConfirmChangeVote) },
            onDismiss = { onIntent(MealDetailIntent.CancelChangeVote) },
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Voters
// ----------------------------------------------------------------------------------------------

@Composable
private fun VotersCard(meal: FeedMealUi, scoreStyle: FrScoreStyle) {
    val sortedVotes = remember(meal.votes) { meal.votes.sortedByDescending { it.score } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FrEyebrow(text = resolve(FeedStringKey.VotersLabel).uppercase(), color = StructuralColors.foreground.copy(alpha = 0.85f))
        FrGlassTile(depth = FrTileDepth.Default, modifier = Modifier.fillMaxWidth()) {
            sortedVotes.forEachIndexed { index, v ->
                if (index > 0) Spacer(Modifier.height(Spacing.md))
                VoterRow(index = index, vote = v, scoreStyle = scoreStyle)
            }
        }
    }
}

@Composable
private fun VoterRow(index: Int, vote: RaterVoteUi, scoreStyle: FrScoreStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(modifier = Modifier.width(Sizes.iconMd), contentAlignment = Alignment.Center) {
            if (index == 0) {
                FrIcon(image = FrIcons.Crown, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.iconSm))
            } else {
                FrText(
                    text = (index + 1).toString(),
                    style = StructuralType.microMono,
                    color = StructuralColors.foreground.copy(alpha = 0.6f),
                )
            }
        }
        FrGlassAvatar(
            initials = vote.raterName,
            image = vote.raterAvatarUrl?.let { rememberAsyncImagePainter(it) },
            ring = FrAvatarRing.None,
            size = 32.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrText(text = vote.raterName, style = StructuralType.body, color = StructuralColors.foreground)
            FrBarTrack(
                progress = vote.score.coerceIn(Score.MIN, Score.MAX) / Score.MAX.toFloat(),
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        VoterScoreBadge(score = vote.score, scoreStyle = scoreStyle)
    }
}

/** Trailing score glyph for a voter row: a structural score disc, or the emoji when that style is set. */
@Composable
private fun VoterScoreBadge(score: Int, scoreStyle: FrScoreStyle) {
    val hot = score >= Score.MAX - 1
    when (scoreStyle) {
        FrScoreStyle.Emoji -> FrText(
            text = scoreToEmoji(score.coerceIn(1, 5)),
            style = StructuralType.titleLg,
        )
        else -> FrScoreDisc(
            score = score.coerceIn(1, 10),
            tone = if (hot) FrScoreTone.Hot else FrScoreTone.Olive,
            size = 34.dp,
            contentDescription = resolve(FeedStringKey.VoterScoreCompact, score),
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Comments
// ----------------------------------------------------------------------------------------------
// The comments section is no longer a single composable: RENDER-3 dissolved it into the body's
// LazyColumn (heading item + items(state.commentRows, key = id)) so the list virtualizes/recycles.
// StructuralCommentRow below is the per-row stratum, called verbatim from those items.

// Chat-bubble width split: the bubble takes 5 parts, a flexible gutter on the opposite side takes 1,
// so each comment occupies ~4/5 of the row and clearly hugs its side.
private const val COMMENT_BUBBLE_WEIGHT = 5f
private const val COMMENT_GUTTER_WEIGHT = 1f

/**
 * A comment as a structural stratum: avatar + frosted bubble (name · time, text, moderation chrome).
 * Chat-bubble layout — the viewer's own comments hug the right (avatar trailing); everyone else's hug
 * the left (avatar leading), each leaving a flexible gutter on the far side.
 */
@Composable
private fun StructuralCommentRow(
    isOwn: Boolean,
    displayName: String,
    avatarUrl: String?,
    text: String,
    relative: RelativeTimestamp,
    loading: Boolean,
    isDeleted: Boolean,
    isEdited: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    canModerate: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    editing: Boolean,
    editInput: String,
    isSavingEdit: Boolean,
    editError: String?,
    onEditInputChange: (String) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
) {
    val nameLabel = when {
        isDeleted -> resolve(FeedStringKey.DeletedAuthor)
        loading   -> "…"
        else      -> displayName
    }
    val avatarInitials = when {
        isDeleted -> "?"
        loading   -> "·"
        else      -> displayName.ifBlank { "?" }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val avatar: @Composable () -> Unit = {
        FrGlassAvatar(
            initials = avatarInitials,
            image = if (isDeleted || loading) null else avatarUrl?.let { rememberAsyncImagePainter(it) },
            ring = FrAvatarRing.None,
            size = 32.dp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        // Own comments hug the right: a leading flexible gutter pushes the bubble + trailing avatar over.
        if (isOwn) Spacer(Modifier.weight(COMMENT_GUTTER_WEIGHT))
        if (!isOwn) avatar()
        FrGlassTile(
            depth = FrTileDepth.Deep,
            modifier = Modifier.weight(COMMENT_BUBBLE_WEIGHT),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrText(
                    text = nameLabel,
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FrText(
                        text = resolve(relative.key, relative.amount),
                        style = StructuralType.microMono,
                        color = StructuralColors.foreground.copy(alpha = 0.6f),
                    )
                    if (isEdited) {
                        FrText(
                            text = resolve(FeedStringKey.CommentEdited),
                            style = StructuralType.microMono,
                            color = StructuralColors.foreground.copy(alpha = 0.5f),
                        )
                    }
                    // The overflow menu is suppressed while this row is in edit mode.
                    if (!editing && (canModerate || canDelete || canEdit)) {
                        Box {
                            FrGlassCircleButton(
                                icon = FrIcons.MoreVert,
                                onClick = { menuExpanded = true },
                                contentDescription = resolve(FeedStringKey.OverflowMenuCd),
                                size = 30.dp,
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { FrText(resolve(FeedStringKey.EditCommentCta)) },
                                        onClick = { menuExpanded = false; onEdit() },
                                        leadingIcon = { FrIcon(FrIcons.Edit, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    )
                                }
                                if (canModerate) {
                                    DropdownMenuItem(
                                        text = { FrText(resolve(FeedStringKey.ReportCommentCta)) },
                                        onClick = { menuExpanded = false; onReport() },
                                        leadingIcon = { FrIcon(FrIcons.Flag, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    )
                                    DropdownMenuItem(
                                        text = { FrText(resolve(FeedStringKey.BlockUserCta)) },
                                        onClick = { menuExpanded = false; onBlock() },
                                        leadingIcon = { FrIcon(FrIcons.Block, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    )
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = { FrText(resolve(FeedStringKey.DeleteCommentCta), color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuExpanded = false; onDelete() },
                                        leadingIcon = { FrIcon(FrIcons.Delete, tint = MaterialTheme.colorScheme.error) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            if (editing) {
                FrUnderlineField(
                    value = editInput,
                    onValueChange = onEditInputChange,
                    placeholder = resolve(FeedStringKey.CommentsInputPlaceholder),
                    singleLine = false,
                    enabled = !isSavingEdit,
                )
                if (editError != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    FrText(text = editError, style = StructuralType.body, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrGlassButton(
                        label = resolve(FeedStringKey.DeleteCancelCta),
                        onClick = onEditCancel,
                        tone = FrButtonTone.Ghost,
                        enabled = !isSavingEdit,
                        compact = true,
                    )
                    FrGlassButton(
                        label = resolve(FeedStringKey.EditCommentSaveCta),
                        onClick = onEditSave,
                        tone = FrButtonTone.Primary,
                        enabled = !isSavingEdit && editInput.isNotBlank(),
                        compact = true,
                    )
                }
            } else {
                FrText(text = text, style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.92f))
            }
        }
        if (isOwn) avatar()
        // Everyone else's comments hug the left: the flexible gutter sits on the right.
        if (!isOwn) Spacer(Modifier.weight(COMMENT_GUTTER_WEIGHT))
    }
}

@Composable
private fun CommentsLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
        repeat(3) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrShimmerBox(modifier = Modifier.size(Sizes.avatarSm), shape = CircleShape)
                FrShimmerBox(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(Radius.lg))
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Full-screen photo viewer
// ----------------------------------------------------------------------------------------------

/**
 * Full-screen, zoomable plate viewer. The photo is fitted on a near-opaque black scrim and can be
 * pinch-zoomed (1×–5×), double-tapped to toggle 1×⇄2.5×, and panned while zoomed (pan is clamped so
 * the image can't be dragged fully off-screen). A single tap while at 1× — or the close button, the
 * backdrop, or system back — dismisses it ([Dialog] consumes back).
 */
@Composable
private fun MealPhotoViewer(photoUrl: String, cacheKey: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val minScale = 1f
        val maxScale = 5f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = stablePlateRequest(photoUrl, cacheKey)),
                contentDescription = resolve(FeedStringKey.MealPhotoCd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(minScale, maxScale)
                            if (scale > 1f) {
                                // Clamp pan so the (scaled) image edges can't cross the screen centre.
                                val maxX = (containerSize.width * (scale - 1f)) / 2f
                                val maxY = (containerSize.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1f) onDismiss() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            FrGlassCircleButton(
                icon = FrIcons.Close,
                onClick = onDismiss,
                contentDescription = resolve(FeedStringKey.MealPhotoCloseCd),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.md),
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Sticky composer
// ----------------------------------------------------------------------------------------------

@Composable
private fun StructuralCommentComposer(
    value: String,
    enabled: Boolean,
    sendEnabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Sticky bar: feather the top edge but land fully opaque so the scrolling comment
            // list cannot bleed through the composer.
            .background(Brush.verticalGradient(listOf(Color.Transparent, StructuralColors.tileSolid)))
            .navigationBarsPadding()
            .imePadding()
            .frSafeHorizontalPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Opaque input surface — the underline field writes straight onto the media floor by
        // design, so give it its own solid tile here or text overlaps whatever scrolls behind.
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.lg))
                .background(StructuralColors.tileSolid)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            FrUnderlineField(
                value = value,
                onValueChange = onChange,
                placeholder = resolve(FeedStringKey.CommentsInputPlaceholder),
                singleLine = false,
                enabled = enabled,
            )
        }
        if (sendEnabled) {
            FrGlassCircleButton(
                icon = FrIcons.ChevronRight,
                onClick = onSend,
                contentDescription = resolve(FeedStringKey.CommentsSendCta),
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Toast
// ----------------------------------------------------------------------------------------------

/** Brief, auto-dismissing bottom toast built from structural strata (spec §10). */
@Composable
private fun ShareOutcomeToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.widthIn(max = 420.dp)) {
            FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
        }
    }
}

// ----------------------------------------------------------------------------------------------
// helpers
// ----------------------------------------------------------------------------------------------

/** A warm Iron & Ember brush floor for the transient (loading / error) states. */
@Composable
private fun FrMediaFloorBrush() {
    FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft)
}

/** Appetizing brush shown behind the plate while it loads (or when the meal has none). */
private fun dishBrushFor(slot: MealSlotUi?): Brush = when (slot) {
    MealSlotUi.Breakfast -> StructuralColors.dishSalad
    MealSlotUi.Brunch -> StructuralColors.dishTacos
    MealSlotUi.Lunch -> StructuralColors.dishMackerel
    MealSlotUi.Snack -> StructuralColors.dishTacos
    MealSlotUi.Merienda -> StructuralColors.dishSalad
    MealSlotUi.Dinner -> StructuralColors.dishRamen
    null -> StructuralColors.dishMackerel
}
