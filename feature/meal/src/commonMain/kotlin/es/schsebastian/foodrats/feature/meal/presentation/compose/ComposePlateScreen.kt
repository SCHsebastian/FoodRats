package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrScorePicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrTagChipRow
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.time.SystemClock
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.DailyEmoteBadge
import es.schsebastian.foodrats.feature.meal.presentation.components.SlotPicker
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

private val CURATED_TAGS = listOf("breakfast", "lunch", "dinner", "snack", "brunch", "dessert", "drink", "other")

@Composable
fun ComposePlateScreen(onComposed: () -> Unit, vm: ComposePlateViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { MealDay.today(SystemClock(), TimeZone.currentSystemDefault()) }
    val emote = remember(today) { DailyEmote.forDay(today) }
    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is ComposePlateEffect.NavigateToPublish) onComposed() }
    }
    FrScreenScaffold {
        FrFormLayout {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DailyEmoteBadge(emote = emote, modifier = Modifier.padding(bottom = Spacing.sm))
                SlotPicker(
                    selected = state.selectedSlot,
                    taken = state.takenSlots,
                    onSelect = { slot -> vm.onIntent(ComposePlateIntent.SelectSlot(slot)) },
                )
                FrLabeledTextField(
                    label = resolve(MealStringKey.ComposeTitle),
                    value = state.dish,
                    onValueChange = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
                    isError = state.error is MealError.Validation,
                )
                FrScorePicker(value = state.score, onChange = { vm.onIntent(ComposePlateIntent.ScoreChanged(it)) }, modifier = Modifier.padding(top = Spacing.md))
                FrTagChipRow(tags = CURATED_TAGS, selected = state.selectedTags, onToggle = { vm.onIntent(ComposePlateIntent.TagToggled(it)) }, modifier = Modifier.padding(top = Spacing.md))
                state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                FrButton(label = resolve(CommonStringKey.Continue), onClick = { vm.onIntent(ComposePlateIntent.Continue) }, variant = FrButtonVariant.Primary)
            }
        }
    }
}
