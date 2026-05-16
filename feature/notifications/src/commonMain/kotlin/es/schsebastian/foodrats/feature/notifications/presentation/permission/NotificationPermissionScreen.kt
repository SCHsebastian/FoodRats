package es.schsebastian.foodrats.feature.notifications.presentation.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationPermissionScreen(
    onContinue: () -> Unit,
    vm: NotificationPermissionViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) { NotificationPermissionEffect.Continue -> onContinue() }
        }
    }
    FrScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FrText(text = resolve(NotificationStringKey.PermissionTitle))
            FrText(
                text = resolve(NotificationStringKey.PermissionBody),
                modifier = Modifier.padding(top = Spacing.md),
            )
            if (state.current == NotificationPermission.DeniedForever) {
                FrButton(
                    label = resolve(NotificationStringKey.PermissionSettings),
                    onClick = { vm.onIntent(NotificationPermissionIntent.OpenSettings) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            } else {
                FrButton(
                    label = resolve(NotificationStringKey.PermissionAllow),
                    onClick = { vm.onIntent(NotificationPermissionIntent.Request) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
            FrButton(
                label = resolve(NotificationStringKey.PermissionSkip),
                onClick = { vm.onIntent(NotificationPermissionIntent.Skip) },
                variant = FrButtonVariant.Ghost,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
