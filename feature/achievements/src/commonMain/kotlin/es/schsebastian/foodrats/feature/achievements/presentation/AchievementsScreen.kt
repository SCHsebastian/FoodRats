package es.schsebastian.foodrats.feature.achievements.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey
import es.schsebastian.foodrats.feature.achievements.presentation.components.FrAchievementCard
import es.schsebastian.foodrats.feature.achievements.presentation.components.formatEpochDay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    vm: AchievementsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Celebration overlay driven by the Unlocked effect (spec §8.3). Holds the most recent
    // unlocked title key; cleared when the user dismisses.
    var celebration by remember { mutableStateOf<AchievementStringKey?>(null) }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is AchievementsEffect.Unlocked -> celebration = effect.titleKey
            }
        }
    }

    FrScreenScaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    FrText(
                        text = resolve(AchievementStringKey.ScreenTitle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(AchievementStringKey.DetailCloseCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.error?.let { error ->
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    FrErrorBanner(text = resolve(error.toStringKey()))
                }
            }
            when {
                state.statuses.isEmpty() && state.error == null && !state.isLoading -> FrEmptyState(
                    icon = FrIcons.Trophy,
                    headline = resolve(AchievementStringKey.ScreenTitle),
                    subtext = resolve(AchievementStringKey.EmptySubtext),
                )
                state.statuses.isNotEmpty() -> BadgeGrid(
                    statuses = state.statuses,
                    onSelect = { vm.onIntent(AchievementsIntent.SelectBadge(it.achievement.id)) },
                )
                else -> Unit
            }
        }
    }

    state.selected?.let { selected ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { vm.onIntent(AchievementsIntent.DismissDetail) },
            sheetState = sheetState,
        ) {
            BadgeDetail(selected)
        }
    }

    celebration?.let { titleKey ->
        // Bespoke firework celebration (see AchievementCelebration.kt) — tap anywhere to dismiss.
        AchievementUnlockedCelebration(
            titleKey = titleKey,
            onDismiss = { celebration = null },
        )
    }
}

@Composable
private fun BadgeGrid(
    statuses: List<AchievementStatus>,
    onSelect: (AchievementStatus) -> Unit,
) {
    // Partition once per distinct list (single pass), not twice on every recomposition.
    val (earned, locked) = remember(statuses) {
        statuses.partition { it.unlockedAtEpochMs != null }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (earned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(resolve(AchievementStringKey.EarnedSectionTitle))
            }
            itemsIndexed(earned, key = { _, status -> status.achievement.id.value }) { index, status ->
                FrAchievementCard(
                    status = status,
                    onClick = { onSelect(status) },
                    modifier = Modifier.popIn(order = index),
                )
            }
        }
        if (locked.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(resolve(AchievementStringKey.LockedSectionTitle))
            }
            itemsIndexed(locked, key = { _, status -> status.achievement.id.value }) { index, status ->
                FrAchievementCard(
                    status = status,
                    onClick = { onSelect(status) },
                    modifier = Modifier.popIn(order = earned.size + index),
                )
            }
        }
    }
}

/**
 * Bespoke staggered entrance: each badge springs up from 65 % scale + 36 dp below with a soft
 * overshoot, fading in. The delay is `order` modulo a small window so the opening screen cascades
 * left-to-right yet badges scrolled into view later still pop in promptly (no multi-second wait).
 */
@Composable
private fun Modifier.popIn(order: Int): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((order % 8) * 45L)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        )
    }
    return graphicsLayer {
        val p = anim.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.65f + 0.35f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * 36f
    }
}

@Composable
private fun SectionHeader(text: String) {
    FrText(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun BadgeDetail(status: AchievementStatus) {
    val unlockedAt = status.unlockedAtEpochMs
    val earned = unlockedAt != null
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrText(
            text = resolve(status.achievement.titleKey),
            style = MaterialTheme.typography.headlineSmall,
        )
        // "How you earned it" / "How to earn it" eyebrow framing the criterion. The requirement text
        // is the achievement's own descriptionKey (the single source of truth — e.g. "Publish 10
        // meals."), so nothing is duplicated and it can't drift from the catalog.
        FrText(
            text = resolve(
                if (earned) AchievementStringKey.DetailHowEarnedLabel
                else AchievementStringKey.DetailHowToEarnLabel,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FrText(
            text = resolve(status.achievement.descriptionKey),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (unlockedAt != null) {
            FrText(
                text = resolve(AchievementStringKey.EarnedOnFormat, formatEpochDay(unlockedAt)),
                style = MaterialTheme.typography.labelLarge,
                color = LocalFrSemanticColors.current.celebration,
            )
        } else {
            FrText(
                text = resolve(AchievementStringKey.ProgressFormat, status.progress.current, status.progress.target),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FrText(
                text = resolve(AchievementStringKey.DetailLockedLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
