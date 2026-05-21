package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.time.SystemClock
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.DailyEmoteBadge
import es.schsebastian.foodrats.feature.meal.presentation.components.SlotPicker
import es.schsebastian.foodrats.feature.meal.presentation.components.decodeImageBitmap
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                DailyEmoteBadge(emote = emote, modifier = Modifier.padding(bottom = Spacing.sm))
                state.photoBytes?.let { bytes ->
                    val img = remember(bytes) { decodeImageBitmap(bytes) }
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Spacing.sm))
                                .padding(bottom = Spacing.md),
                        )
                    }
                }
                SlotPicker(
                    selected = state.selectedSlot,
                    taken = state.takenSlots,
                    onSelect = { slot -> vm.onIntent(ComposePlateIntent.SelectSlot(slot)) },
                )
                FrLabeledTextField(
                    label = resolve(MealStringKey.ComposeTitle),
                    value = state.dish,
                    onValueChange = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
                    isError = state.error is MealError.Validation &&
                        state.error !is MealError.Validation.DescriptionTooLong,
                )
                FrTextField(
                    value = state.descriptionInput,
                    onValueChange = { vm.onIntent(ComposePlateIntent.DescriptionChanged(it)) },
                    label = resolve(MealStringKey.ComposeDescriptionPlaceholder),
                    isError = state.descriptionTooLong,
                    singleLine = false,
                    modifier = Modifier.padding(top = Spacing.md).fillMaxWidth(),
                )
                FrText(
                    text = resolve(
                        MealStringKey.ComposeDescriptionCounter,
                        state.descriptionInput.trim().length,
                        Description.MAX_LEN,
                    ),
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                FrButton(
                    label = resolve(CommonStringKey.Continue),
                    onClick = { vm.onIntent(ComposePlateIntent.Continue) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.lg),
                )
            }
        }
    }
}
