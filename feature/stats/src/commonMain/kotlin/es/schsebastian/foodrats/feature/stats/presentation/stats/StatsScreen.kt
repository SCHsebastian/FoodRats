package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey
import es.schsebastian.foodrats.feature.stats.presentation.components.FrDishTallyRow
import es.schsebastian.foodrats.feature.stats.presentation.components.FrLeaderboardRow
import es.schsebastian.foodrats.feature.stats.presentation.components.FrStatTile
import es.schsebastian.foodrats.feature.stats.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatsScreen(vm: StatsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg).verticalScroll(rememberScrollState())) {
            FrText(text = resolve(StatsStringKey.Title))
            when {
                state.isLoading && state.snapshot == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FrProgressIndicator() }
                }
                state.error != null -> {
                    FrErrorBanner(text = resolve(state.error!!.toStringKey()))
                }
                state.snapshot == null -> {
                    FrEmptyState(
                        icon = FrIcons.Settings,
                        headline = resolve(StatsStringKey.EmptyHeadline),
                        subtext = resolve(StatsStringKey.EmptySubtext),
                    )
                }
                else -> {
                    val s = state.snapshot!!
                    val crewLabel = resolve(StatsStringKey.CrewStreakLabel)
                    val personalLabel = resolve(StatsStringKey.PersonalStreakLabel)
                    val unit = if (s.crewStreak.days == 1) resolve(StatsStringKey.StreakUnitSingular, s.crewStreak.days)
                               else resolve(StatsStringKey.StreakUnitPlural, s.crewStreak.days)
                    val personalUnit = if (s.personalStreak.days == 1) resolve(StatsStringKey.StreakUnitSingular, s.personalStreak.days)
                                       else resolve(StatsStringKey.StreakUnitPlural, s.personalStreak.days)
                    FrStatTile(label = crewLabel, value = unit, modifier = Modifier.padding(top = Spacing.md))
                    FrStatTile(label = personalLabel, value = personalUnit, modifier = Modifier.padding(top = Spacing.sm))

                    if (s.topDishes.isNotEmpty()) {
                        FrText(text = resolve(StatsStringKey.TopDishesSection), modifier = Modifier.padding(top = Spacing.lg))
                        s.topDishes.forEach { tally -> FrDishTallyRow(tally) }
                    }

                    if (s.leaderboard.entries.isNotEmpty()) {
                        FrText(text = resolve(StatsStringKey.LeaderboardSection), modifier = Modifier.padding(top = Spacing.lg))
                        s.leaderboard.entries.forEach { row -> FrLeaderboardRow(row) }
                    }

                    FrText(
                        text = resolve(StatsStringKey.TagVarietyValue, s.tagVarietyCount),
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                    FrText(
                        text = resolve(StatsStringKey.MealsConsidered, s.mealsConsidered),
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}
