package es.schsebastian.foodrats.feature.meal.presentation.publish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.FrMealCard
import es.schsebastian.foodrats.feature.meal.presentation.components.MealUi
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PublishMealScreen(onPublished: () -> Unit, vm: PublishMealViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onIntent(PublishMealIntent.Load) }
    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is PublishMealEffect.Published) onPublished() }
    }
    FrScreenScaffold {
        FrFormLayout {
            Column {
                state.draft?.let { draft ->
                    FrMealCard(MealUi(
                        dish = draft.dish?.value ?: "",
                        tags = draft.tags.map { it.label },
                        photoBytes = draft.plate?.photoBytes,
                    ))
                }
                state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                FrButton(
                    label = resolve(MealStringKey.PublishTitle),
                    onClick = { vm.onIntent(PublishMealIntent.Publish) },
                    variant = FrButtonVariant.Primary,
                    enabled = !state.isPublishing && state.isToday && (state.draft?.slot != null),
                )
                if (!state.isToday) {
                    FrText(
                        text = resolve(MealStringKey.MealErrorNotToday),
                        modifier = androidx.compose.ui.Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}
