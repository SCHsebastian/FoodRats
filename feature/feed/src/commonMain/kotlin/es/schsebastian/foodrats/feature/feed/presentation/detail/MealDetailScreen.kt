package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrTagChipRow
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                meal = state.meal!!,
                pendingRate = state.pendingRate,
                onRate = { score -> vm.onIntent(MealDetailIntent.RateMeal(score)) },
            )
        }
        state.rateError?.let { err ->
            FrErrorBanner(text = resolve(err.toStringKey()))
        }
    }
}

@Composable
private fun MealDetailBody(
    meal: FeedMealUi,
    pendingRate: Boolean,
    onRate: (Int) -> Unit,
) {
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

        if (meal.tags.isNotEmpty()) {
            FrTagChipRow(
                tags = meal.tags,
                selected = emptySet(),
                onToggle = {},
            )
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
                val minotauroLabel = resolve(FeedStringKey.MinotauroLabel)
                FrStarRatingPicker(
                    onSelect = onRate,
                    enabled = !pendingRate,
                    starLabel = { i -> if (i == 6) minotauroLabel else i.toString() },
                )
            }
            else -> Unit
        }
    }
}
