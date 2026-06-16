package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.atoms.FrGlassPill
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.image.rememberThumbHashPainter
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrVoteBars
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import es.schsebastian.foodrats.feature.feed.presentation.components.FrCommentRow
import es.schsebastian.foodrats.feature.feed.presentation.components.FrLocationMap
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MealDetailScreen(
    mealId: String,
    dayIso: String,
    onBack: () -> Unit,
    vm: MealDetailViewModel = koinViewModel(parameters = { parametersOf(mealId, dayIso) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDeleteMealDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.mealDeleted) { if (state.mealDeleted) onBack() }

    FrScreenScaffold {
        when {
            state.isLoading -> CenteredState(onBack) { FrProgressIndicator() }
            state.error != null -> {
                val err: FeedError = state.error!!
                CenteredState(onBack) { FrErrorBanner(text = resolve(err.toStringKey())) }
            }
            state.notFound || state.meal == null ->
                CenteredState(onBack) { FrText(text = resolve(FeedStringKey.DetailNotFound)) }
            else -> MealDetailBody(
                state = state,
                onIntent = vm::onIntent,
                onBack = onBack,
                onRequestDeleteMeal = { showDeleteMealDialog = true },
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
}

/** Transient states (loading / error / not-found) still need a back affordance. */
@Composable
private fun CenteredState(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        FrGlassPill(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(FeedStringKey.DetailBackCta),
            modifier = Modifier.align(Alignment.TopStart),
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun MealDetailBody(
    state: MealDetailState,
    onIntent: (MealDetailIntent) -> Unit,
    onBack: () -> Unit,
    onRequestDeleteMeal: () -> Unit,
) {
    val meal = state.meal ?: return
    var pendingDeleteCommentId by remember { mutableStateOf<MealCommentId?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PhotoHero(
                meal = meal,
                canDelete = state.canDeleteMeal,
                deleteEnabled = !state.isDeletingMeal,
                isPreparingShare = state.isPreparingShare,
                onBack = onBack,
                onDelete = onRequestDeleteMeal,
                onShare = { onIntent(MealDetailIntent.ShareTapped) },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                AuthorRow(meal)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FrText(text = meal.dishName, style = MaterialTheme.typography.headlineMedium)
                    if (meal.description.isNotBlank()) {
                        FrText(text = meal.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (meal.ingredients.isNotEmpty()) IngredientChips(meal.ingredients)

                LocationSection(meal)

                ScoreStoryCard(meal)

                RatingSection(meal = meal, pendingRate = state.pendingRate, onIntent = onIntent)

                if (meal.votes.isNotEmpty()) VotersCard(meal)

                CommentsSection(
                    state = state,
                    onRequestDeleteComment = { pendingDeleteCommentId = it },
                )

                // Spacer so the last content clears the sticky composer.
                Spacer(Modifier.height(Sizes.touchTarget + Spacing.lg))
            }
        }

        StickyCommentComposer(
            value = state.commentInput,
            enabled = !state.isPostingComment,
            sendEnabled = !state.isPostingComment && state.commentInput.isNotBlank(),
            onChange = { onIntent(MealDetailIntent.CommentInputChanged(it)) },
            onSend = { onIntent(MealDetailIntent.PostComment) },
            modifier = Modifier.align(Alignment.BottomCenter),
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

@Composable
private fun PhotoHero(
    meal: FeedMealUi,
    canDelete: Boolean,
    deleteEnabled: Boolean,
    isPreparingShare: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    // Pinch-to-zoom that snaps back on release. Two-finger only, so single-finger drags still
    // scroll the detail page; on the last finger up the scale + pan spring back to rest.
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                scope.launch { scale.snapTo((scale.value * zoom).coerceIn(1f, 4f)) }
                                scope.launch { offset.snapTo(offset.value + pan) }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        scope.launch { scale.animateTo(1f, spring()) }
                        scope.launch { offset.animateTo(Offset.Zero, spring()) }
                    }
                }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                },
        ) {
            if (meal.photoUrl.isNotBlank()) {
                // Detail always loads the FULL plate; the ThumbHash blur is the placeholder while
                // it streams in (the feed thumbnail is already cached, so the hand-off is smooth).
                val placeholder = rememberThumbHashPainter(meal.thumbHash)
                AsyncImage(
                    model = meal.photoUrl,
                    contentDescription = meal.dishName,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        // Top dim — keeps the back/delete pills legible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(semantic.scrim.copy(alpha = 0.45f), Color.Transparent))),
        )
        // Bottom protection gradient — single black→transparent wash.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, semantic.scrim.copy(alpha = 0.62f)))),
        )
        FrGlassPill(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(FeedStringKey.DetailBackCta),
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isPreparingShare) {
                FrProgressIndicator()
            } else {
                FrGlassPill(
                    icon = FrIcons.Share,
                    onClick = onShare,
                    contentDescription = resolve(FeedStringKey.ShareMeal),
                )
            }
            if (canDelete && deleteEnabled) {
                FrGlassPill(
                    icon = FrIcons.Delete,
                    onClick = onDelete,
                    contentDescription = resolve(FeedStringKey.DeleteMealCta),
                )
            }
        }
    }
}

/** Tappable mini-map; FrLocationMap opens the native maps app (Apple/Google) on tap. */
@Composable
private fun LocationSection(meal: FeedMealUi) {
    val lat = meal.latitude
    val lon = meal.longitude
    if (lat == null || lon == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionEyebrow(resolve(FeedStringKey.LocationLabel))
        val mapLabel = resolve(FeedStringKey.LocationMapCta)
        FrLocationMap(
            latitude = lat,
            longitude = lon,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(RoundedCornerShape(Radius.md))
                .semantics {
                    contentDescription = mapLabel
                    role = Role.Button
                },
        )
    }
}

@Composable
private fun AuthorRow(meal: FeedMealUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // decorative — the adjacent author-name label carries the identity for screen readers.
        FrAvatar(initials = meal.authorName, imageUrl = meal.authorAvatarUrl)
        Column(modifier = Modifier.weight(1f)) {
            FrText(text = meal.authorName, style = MaterialTheme.typography.titleMedium)
            FrText(
                text = meal.dayEmote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChips(ingredients: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ingredients.forEach { name -> FrChip(label = name, onClick = {}) }
    }
}

@Composable
private fun ScoreStoryCard(meal: FeedMealUi) {
    val avg = meal.averageScore
    if (avg == null || meal.ratingCount <= 0) {
        FrText(
            text = resolve(FeedStringKey.NoVotesYet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val avgRounded = (kotlin.math.round(avg * 10) / 10.0).toString()
    FrCard(modifier = Modifier.fillMaxWidth()) {
        SectionEyebrow(resolve(FeedStringKey.CrewScoreLabel))
        Spacer(Modifier.height(Spacing.xs))
        FrText(text = avgRounded, style = FrTextStyles.statNumberLarge, color = MaterialTheme.colorScheme.primary)
        FrText(
            text = resolve(FeedStringKey.RatingSummary, avgRounded, meal.ratingCount),
            style = FrTextStyles.statNumberSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (meal.votes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            FrVoteBars(
                votes = meal.votes.groupingBy { it.score.coerceIn(Score.MIN, Score.MAX) }.eachCount(),
                maxScore = Score.MAX,
                hotThreshold = Score.MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RatingSection(meal: FeedMealUi, pendingRate: Boolean, onIntent: (MealDetailIntent) -> Unit) {
    val celebration = LocalFrSemanticColors.current.celebration
    // Captured while still on the picker stage; stays null until the viewer votes this session.
    val initialRating = remember { meal.viewerRating }
    when {
        meal.viewerRating != null -> {
            val justVoted = initialRating == null
            val pop = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                if (justVoted) {
                    pop.snapTo(0.7f)
                    pop.animateTo(1f, animationSpec = tween(Motion.medium, easing = Motion.Emphasized))
                }
            }
            Row(
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FrIcon(image = FrIcons.Star, tint = celebration, modifier = Modifier.size(Sizes.iconSm))
                SectionEyebrow(resolve(FeedStringKey.YourVote, meal.viewerRating))
            }
        }
        meal.canRate -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionEyebrow(resolve(FeedStringKey.RateThisMeal))
            FrCard(modifier = Modifier.fillMaxWidth()) {
                FrStarRatingPicker(
                    onSelect = { score -> onIntent(MealDetailIntent.RateMeal(score)) },
                    enabled = !pendingRate,
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun VotersCard(meal: FeedMealUi) {
    val semantic = LocalFrSemanticColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionEyebrow(resolve(FeedStringKey.VotersLabel))
        FrCard(modifier = Modifier.fillMaxWidth()) {
            meal.votes.sortedByDescending { it.score }.forEachIndexed { index, v ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(modifier = Modifier.width(Sizes.iconMd), contentAlignment = Alignment.Center) {
                        if (index == 0) {
                            FrIcon(image = FrIcons.Crown, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.iconSm))
                        } else {
                            FrText(
                                text = (index + 1).toString(),
                                style = FrTextStyles.statNumberSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // decorative — the adjacent rater-name label carries the identity for screen readers.
                    FrAvatar(initials = v.raterName, imageUrl = v.raterAvatarUrl, size = Sizes.avatarSm)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FrText(text = v.raterName, style = MaterialTheme.typography.bodyMedium)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Spacing.xs)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(v.score.coerceIn(Score.MIN, Score.MAX) / Score.MAX.toFloat())
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(if (v.score >= Score.MAX - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                    FrText(
                        text = resolve(FeedStringKey.VoterScoreCompact, v.score),
                        style = FrTextStyles.statNumberSmall,
                        color = if (v.score >= Score.MAX - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentsSection(
    state: MealDetailState,
    onRequestDeleteComment: (MealCommentId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionEyebrow(resolve(FeedStringKey.CommentsTitle))
        when {
            state.commentsLoading && state.commentRows.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) { FrProgressIndicator() }
            state.commentRows.isEmpty() && state.commentReadError == null ->
                FrEmptyState(icon = FrIcons.Comment, headline = resolve(FeedStringKey.CommentsEmpty))
            state.commentReadError != null ->
                FrErrorBanner(text = resolve(state.commentReadError.toStringKey()))
            else -> state.commentRows.forEach { c ->
                FrCommentRow(
                    displayName = c.displayName,
                    avatarUrl = c.avatarUrl,
                    text = c.text,
                    relative = c.relative,
                    loading = c.loading,
                    isDeleted = c.isDeleted,
                    canDelete = c.canDelete,
                    onDelete = { onRequestDeleteComment(c.id) },
                )
            }
        }
        if (state.commentWriteError != null) {
            FrErrorBanner(text = resolve(state.commentWriteError.toStringKey()))
        }
    }
}

@Composable
private fun StickyCommentComposer(
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
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                ),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrTextField(
            value = value,
            onValueChange = onChange,
            label = resolve(FeedStringKey.CommentsInputPlaceholder),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        FrIconButton(
            icon = FrIcons.ChevronRight,
            onClick = onSend,
            contentDescription = resolve(FeedStringKey.CommentsSendCta),
            enabled = sendEnabled,
        )
    }
}

/**
 * Brief, auto-dismissing bottom toast for a share outcome (spec §10). No system Toast primitive
 * exists in the design system, so this is a small in-app overlay built from `Fr*` atoms; it clears
 * itself after a short window via [onDismiss].
 */
@Composable
private fun ShareOutcomeToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        FrCard {
            FrText(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    FrText(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
