package es.schsebastian.foodrats.app.legal

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.i18n.resolve
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Re-acceptance gate shown when [CURRENT_EULA_VERSION] was bumped since the user's last
 * acceptance (UGC compliance §6). The "Accept & Continue" button calls [EulaPort.accept];
 * once persisted, the `acceptedVersion` flow re-emits and the stage machine clears
 * [es.schsebastian.foodrats.app.root.RootStage.NeedsEulaGate] → `Ready` → `NavigateTopLevel(Main)`.
 * No back navigation: the gate is non-skippable by design (Apple G1.2 requires embedded EULA).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EulaGateScreen(
    onReadEula: () -> Unit = {},
    onReadGuidelines: () -> Unit = {},
    eulaPort: EulaPort = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var accepting by remember { mutableStateOf(false) }

    // Non-skippable gate (Apple G1.2 requires embedded EULA). Consume the back gesture as a
    // no-op so the user cannot pop to Main without accepting — the stage machine won't re-gate
    // because the stage is already NeedsEulaGate and applyStage short-circuits on same-stage.
    BackHandler(enabled = true) {}

    FrScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .frContentWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FrText(
                text = resolve(SharedStringKey.LegalEulaGateTitle),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            FrText(
                text = resolve(SharedStringKey.LegalEulaGateBody),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            // Apple G1.2: the full legal docs must be accessible at the acceptance point so the user
            // can read what they are agreeing to. These Ghost buttons navigate forward to the doc
            // screens; back returns to the gate (BackHandler only blocks back ON the gate itself).
            FrButton(
                label = resolve(SharedStringKey.LegalEulaGateReadEulaCta),
                onClick = onReadEula,
                variant = FrButtonVariant.Ghost,
            )
            FrButton(
                label = resolve(SharedStringKey.LegalEulaGateReadGuidelinesCta),
                onClick = onReadGuidelines,
                variant = FrButtonVariant.Ghost,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            FrButton(
                label = resolve(SharedStringKey.LegalEulaAcceptCta),
                onClick = {
                    if (!accepting) {
                        accepting = true
                        scope.launch {
                            eulaPort.accept(CURRENT_EULA_VERSION)
                            // The EulaPort.acceptedVersion flow re-emits; RootNavViewModel's
                            // combine detects needsEulaAcceptance = false and transitions to Ready.
                            accepting = false
                        }
                    }
                },
                enabled = !accepting,
            )
        }
    }
}
