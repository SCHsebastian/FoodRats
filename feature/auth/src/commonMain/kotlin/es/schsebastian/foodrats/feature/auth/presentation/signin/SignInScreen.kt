package es.schsebastian.foodrats.feature.auth.presentation.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrDivider
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
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
        // Subtle ember + olive radial washes — the design-system "worked example" for
        // landing/sign-in accents. Drawn on a fixed parent so they don't scroll with the form.
        Box(modifier = Modifier.fillMaxSize().signInBackdrop()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.xl))
                HeroBadge()
                Spacer(Modifier.height(Spacing.lg))
                FrText(
                    text = resolve(AuthStringKey.SignInTitle),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                )
                Spacer(Modifier.height(Spacing.sm))
                FrText(
                    text = resolve(AuthStringKey.SignInSubtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.lg))
                FeatureHighlights()

                Spacer(Modifier.height(Spacing.lg))
                EmailPasswordForm(state = state, onIntent = vm::onIntent)

                Spacer(Modifier.height(Spacing.md))
                OrDivider()
                Spacer(Modifier.height(Spacing.md))
                FrButton(
                    label = resolve(AuthStringKey.ContinueWithGoogle),
                    onClick = { vm.onIntent(SignInIntent.ContinueWithGoogle) },
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )

                Spacer(Modifier.height(Spacing.sm))
                ToggleModeLink(state.mode, onClick = { vm.onIntent(SignInIntent.ToggleMode) })

                state.error?.let { err ->
                    Spacer(Modifier.height(Spacing.sm))
                    FrErrorBanner(text = resolve(err.toStringKey()))
                }

                Spacer(Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(Spacing.lg))
                FrText(
                    text = resolve(AuthStringKey.Footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Two soft radial gradients — ember from the top-trailing corner, olive from the bottom-leading
 * corner, each at 8% opacity over the concrete surface. Density-independent: the gradients are
 * rebuilt against the actual draw size and painted behind content.
 */
@Composable
private fun Modifier.signInBackdrop(): Modifier {
    val ember = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    val olive = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    return this.drawWithCache {
        val emberWash = Brush.radialGradient(
            colors = listOf(ember, Color.Transparent),
            center = Offset(size.width, 0f),
            radius = size.maxDimension * 0.7f,
        )
        val oliveWash = Brush.radialGradient(
            colors = listOf(olive, Color.Transparent),
            center = Offset(0f, size.height),
            radius = size.maxDimension * 0.7f,
        )
        onDrawBehind {
            drawRect(emberWash)
            drawRect(oliveWash)
        }
    }
}

@Composable
private fun HeroBadge() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        FrIcon(
            image = FrIcons.Camera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun FeatureHighlights() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        HighlightRow(icon = FrIcons.Camera,        label = resolve(AuthStringKey.HighlightShare))
        HighlightRow(icon = FrIcons.Stats,         label = resolve(AuthStringKey.HighlightRate))
        HighlightRow(icon = FrIcons.Home,          label = resolve(AuthStringKey.HighlightFeed))
    }
}

@Composable
private fun HighlightRow(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(
                image = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        FrText(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmailPasswordForm(
    state: SignInState,
    onIntent: (SignInIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrTextField(
            value = state.email,
            onValueChange = { onIntent(SignInIntent.UpdateEmail(it)) },
            label = resolve(AuthStringKey.FieldEmail),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            isError = state.emailError != null,
            supportingText = state.emailError?.let { resolve((it as AuthError).toStringKey()) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        FrTextField(
            value = state.password,
            onValueChange = { onIntent(SignInIntent.UpdatePassword(it)) },
            label = resolve(AuthStringKey.FieldPassword),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { resolve((it as AuthError).toStringKey()) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
                FrProgressIndicator()
            }
        } else {
            val ctaKey = if (state.mode == SignInMode.SignIn) AuthStringKey.ModeSignInCta else AuthStringKey.ModeSignUpCta
            FrButton(
                label = resolve(ctaKey),
                onClick = { onIntent(SignInIntent.SubmitEmail) },
                variant = FrButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        FrText(
            text = resolve(AuthStringKey.OrDivider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        FrDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ToggleModeLink(mode: SignInMode, onClick: () -> Unit) {
    val key = if (mode == SignInMode.SignIn) AuthStringKey.ToggleToSignUp else AuthStringKey.ToggleToSignIn
    FrText(
        text = resolve(key),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(Spacing.sm)
            .clickable(onClick = onClick),
    )
}
