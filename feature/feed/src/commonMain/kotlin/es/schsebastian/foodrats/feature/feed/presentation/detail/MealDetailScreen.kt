package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.feature.feed.presentation.components.FrCommentRow
import es.schsebastian.foodrats.feature.feed.presentation.components.FrLocationMap
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
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
    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(FeedStringKey.DetailTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = FrIcons.Back,
                            contentDescription = resolve(FeedStringKey.DetailBackCta),
                        )
                    }
                },
                actions = {
                    if (state.canDeleteMeal) {
                        IconButton(
                            onClick = { showDeleteMealDialog = true },
                            enabled = !state.isDeletingMeal,
                        ) {
                            Icon(
                                imageVector = FrIcons.Delete,
                                contentDescription = resolve(FeedStringKey.DeleteMealCta),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FrProgressIndicator()
                }
            }
            state.error != null -> {
                val err: FeedError = state.error!!
                FrErrorBanner(text = resolve(err.toStringKey()))
            }
            state.notFound || state.meal == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(Spacing.md), contentAlignment = Alignment.Center) {
                    FrText(text = resolve(FeedStringKey.DetailNotFound))
                }
            }
            else -> MealDetailBody(
                state = state,
                onIntent = vm::onIntent,
            )
        }
        state.rateError?.let { err ->
            FrErrorBanner(text = resolve(err.toStringKey()))
        }
        state.mealDeleteError?.let { err ->
            FrErrorBanner(text = resolve(err.toStringKey()))
        }
        state.commentDeleteError?.let { err ->
            FrErrorBanner(text = resolve(err.toStringKey()))
        }
    }

    if (showDeleteMealDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMealDialog = false },
            title = { Text(resolve(FeedStringKey.DeleteMealConfirmTitle)) },
            text = { Text(resolve(FeedStringKey.DeleteMealConfirmBody)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteMealDialog = false
                    vm.onIntent(MealDetailIntent.DeleteMeal)
                }) { Text(resolve(FeedStringKey.DeleteConfirmCta)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMealDialog = false }) {
                    Text(resolve(FeedStringKey.DeleteCancelCta))
                }
            },
        )
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun MealDetailBody(
    state: MealDetailState,
    onIntent: (MealDetailIntent) -> Unit,
) {
    val meal = state.meal ?: return
    var pendingDeleteCommentId by remember { mutableStateOf<MealCommentId?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val photoShape = RoundedCornerShape(16.dp)
        if (meal.photoUrl.isNotBlank()) {
            AsyncImage(
                model = meal.photoUrl,
                contentDescription = meal.dishName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(photoShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(photoShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        val lat = meal.latitude
        val lon = meal.longitude
        if (lat != null && lon != null) {
            FrLocationMap(
                latitude = lat,
                longitude = lon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(photoShape),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrAvatar(initials = meal.authorName)
            Column(modifier = Modifier.weight(1f)) {
                FrText(
                    text = meal.authorName,
                    style = MaterialTheme.typography.titleMedium,
                )
                FrText(
                    text = meal.dayEmote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val avg = meal.averageScore
            if (avg != null && meal.ratingCount > 0) {
                FrScoreBadge(score = avg.toInt().coerceIn(1, 10))
            }
        }

        FrText(
            text = meal.dishName,
            style = MaterialTheme.typography.headlineSmall,
        )

        if (meal.description.isNotBlank()) {
            FrText(
                text = meal.description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (meal.ingredients.isNotEmpty()) {
            FrText(
                text = resolve(FeedStringKey.IngredientsHeading),
                style = MaterialTheme.typography.titleSmall,
            )
            meal.ingredients.forEach { ingredient ->
                FrText(
                    text = ingredient,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (meal.averageScore != null && meal.ratingCount > 0) {
            val avgRounded = (kotlin.math.round(meal.averageScore * 10) / 10.0).toString()
            FrText(
                text = resolve(FeedStringKey.RatingSummary, avgRounded, meal.ratingCount),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            FrText(
                text = resolve(FeedStringKey.NoVotesYet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (meal.votes.isNotEmpty()) {
            FrText(
                text = resolve(FeedStringKey.VotesHeading),
                style = MaterialTheme.typography.titleSmall,
            )
            meal.votes.forEach { v ->
                FrText(text = resolve(FeedStringKey.VoterScore, v.raterName, v.score))
            }
        }

        when {
            meal.viewerRating != null -> {
                FrText(
                    text = resolve(FeedStringKey.YourVote, meal.viewerRating),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            meal.canRate -> {
                FrText(
                    text = resolve(FeedStringKey.RateThisMeal),
                    style = MaterialTheme.typography.titleMedium,
                )
                FrStarRatingPicker(
                    onSelect = { score -> onIntent(MealDetailIntent.RateMeal(score)) },
                    enabled = !state.pendingRate,
                )
            }
            else -> Unit
        }

        // === Comments section ===
        FrText(
            text = resolve(FeedStringKey.CommentsTitle),
            style = MaterialTheme.typography.titleMedium,
        )

        when {
            state.commentsLoading && state.commentRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    FrProgressIndicator()
                }
            }
            state.commentRows.isEmpty() && state.commentReadError == null -> {
                FrEmptyState(
                    icon = FrIcons.Settings,
                    headline = resolve(FeedStringKey.CommentsEmpty),
                    subtext = "",
                )
            }
            state.commentReadError != null -> {
                FrErrorBanner(text = resolve(state.commentReadError.toStringKey()))
            }
            else -> {
                state.commentRows.forEach { c ->
                    FrCommentRow(
                        displayName = c.displayName,
                        avatarUrl = c.avatarUrl,
                        text = c.text,
                        relative = c.relative,
                        loading = c.loading,
                        isDeleted = c.isDeleted,
                        canDelete = c.canDelete,
                        onDelete = { pendingDeleteCommentId = c.id },
                    )
                }
            }
        }

        if (state.commentWriteError != null) {
            FrErrorBanner(text = resolve(state.commentWriteError.toStringKey()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrTextField(
                value = state.commentInput,
                onValueChange = { onIntent(MealDetailIntent.CommentInputChanged(it)) },
                label = resolve(FeedStringKey.CommentsInputPlaceholder),
                enabled = !state.isPostingComment,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            FrButton(
                label = resolve(FeedStringKey.CommentsSendCta),
                onClick = { onIntent(MealDetailIntent.PostComment) },
                enabled = !state.isPostingComment && state.commentInput.isNotBlank(),
            )
        }

        pendingDeleteCommentId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteCommentId = null },
                title = { Text(resolve(FeedStringKey.DeleteCommentConfirmTitle)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteCommentId = null
                        onIntent(MealDetailIntent.DeleteComment(id))
                    }) { Text(resolve(FeedStringKey.DeleteConfirmCta)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteCommentId = null }) {
                        Text(resolve(FeedStringKey.DeleteCancelCta))
                    }
                },
            )
        }
    }
}
