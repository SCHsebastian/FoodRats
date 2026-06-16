package es.schsebastian.foodrats.app.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import org.koin.compose.viewmodel.koinViewModel

/**
 * First-run analytics-consent gate (GDPR opt-in). Explains what is and isn't collected, then offers an
 * affirmative [ConsentIntent.Grant] and an explicit [ConsentIntent.Deny]. Both decisions are durably
 * written through `ConsentPort`; navigation away is driven by `RootNavViewModel` observing that write
 * (it advances the stage machine off `NeedsConsent`), so [onDecided] is a no-op hook kept for symmetry
 * with the other onboarding screens.
 */
@Composable
fun ConsentScreen(
    onDecided: () -> Unit = {},
    vm: ConsentViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) { ConsentEffect.Decided -> onDecided() }
        }
    }
    FrScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FrLogo(size = 72.dp)
            FrText(
                text = resolve(SharedStringKey.ConsentTitle),
                style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = Spacing.lg),
            )
            FrText(
                text = resolve(SharedStringKey.ConsentBody),
                style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            FrText(
                text = resolve(SharedStringKey.ConsentPrivacyNote),
                style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            FrButton(
                label = resolve(SharedStringKey.ConsentAllow),
                onClick = { vm.onIntent(ConsentIntent.Grant) },
                variant = FrButtonVariant.Primary,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            )
            FrButton(
                label = resolve(SharedStringKey.ConsentDeny),
                onClick = { vm.onIntent(ConsentIntent.Deny) },
                variant = FrButtonVariant.Ghost,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}
