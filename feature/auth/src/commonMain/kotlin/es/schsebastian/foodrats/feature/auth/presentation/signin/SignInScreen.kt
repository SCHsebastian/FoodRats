package es.schsebastian.foodrats.feature.auth.presentation.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import es.schsebastian.foodrats.feature.auth.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInScreen(onSignedIn: () -> Unit, vm: SignInViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is SignInEffect.SignedIn) onSignedIn() }
    }
    FrScreenScaffold {
        Column(modifier = Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            FrText(text = resolve(AuthStringKey.SignInTitle))
            FrText(text = resolve(AuthStringKey.SignInSubtitle), modifier = Modifier.padding(top = Spacing.sm))
            if (state.isLoading) FrProgressIndicator(modifier = Modifier.padding(top = Spacing.md))
            else FrButton(
                label = resolve(AuthStringKey.ContinueWithGoogle),
                onClick = { vm.onIntent(SignInIntent.ContinueWithGoogle) },
                variant = FrButtonVariant.Primary,
                modifier = Modifier.padding(top = Spacing.md),
            )
            state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
        }
    }
}
