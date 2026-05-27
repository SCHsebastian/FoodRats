package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrVoteBars
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import es.schsebastian.foodrats.feature.feed.presentation.components.FrCommentRow
import es.schsebastian.foodrats.feature.feed.presentation.components.FrLocationMap
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
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
                onBack = onBack,
                onDelete = onRequestDeleteMeal,
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
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        if (meal.photoUrl.isNotBlank()) {
            AsyncImage(
                model = meal.photoUrl,
                contentDescription = meal.dishName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
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
        if (canDelete && deleteEnabled) {
            FrGlassPill(
                icon = FrIcons.Delete,
                onClick = onDelete,
                contentDescription = resolve(FeedStringKey.DeleteMealCta),
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            )
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
        FrLocationMap(
            latitude = lat,
            longitude = lon,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(RoundedCornerShape(Radius.md)),
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
    when {
        meal.viewerRating != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionEyebrow(resolve(FeedStringKey.YourVote, meal.viewerRating))
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

@Composable
private fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    FrText(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
