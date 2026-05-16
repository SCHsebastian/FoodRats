package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.components.FrCrewMemberRow
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CrewSettingsScreen(
    crewId: String,
    onLeft: () -> Unit,
    vm: CrewSettingsViewModel = koinViewModel(parameters = { parametersOf((CrewId.of(crewId) as es.schsebastian.foodrats.core.domain.result.Result.Ok).value) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) { CrewSettingsEffect.Left -> onLeft() }
        }
    }
    FrScreenScaffold {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
            FrText(text = resolve(CrewStringKey.SettingsTitle))
            state.crew?.let { crew ->
                FrText(
                    text = resolve(CrewStringKey.SettingsShareCode, crew.code.value),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                FrText(
                    text = resolve(CrewStringKey.SettingsMembersSection),
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                LazyColumn {
                    items(crew.members, key = { it.accountId.value }) { m ->
                        FrCrewMemberRow(displayName = m.displayName, avatarUrl = m.avatarUrl)
                    }
                }
                FrButton(
                    label = resolve(CrewStringKey.SettingsLeaveCta),
                    onClick = { vm.onIntent(CrewSettingsIntent.Leave) },
                    variant = FrButtonVariant.Secondary,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
            state.error?.let { err ->
                FrErrorBanner(
                    text = resolve(err.toStringKey()),
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}
