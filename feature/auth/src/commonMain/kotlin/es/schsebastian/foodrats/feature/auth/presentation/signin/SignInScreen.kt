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
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
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
                    .frContentWidth(Breakpoints.formMax)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top-down assembly: the hero badge "develops in" focally (frRevealScale), then the
                // title / subtitle / highlights / form rise in on an increasing cascade so the screen
                // composes itself from the top down. Delays are tight (one step ≈ 60ms) to stay snappy.
                Spacer(Modifier.height(Spacing.lg))
                HeroBadge(modifier = Modifier.frRevealScale())
                Spacer(Modifier.height(Spacing.md))
                FrText(
                    text = resolve(AuthStringKey.SignInTitle),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.frRiseIn(delayMillis = 80),
                )
                Spacer(Modifier.height(Spacing.xs))
                FrText(
                    text = resolve(AuthStringKey.SignInSubtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.frRiseIn(delayMillis = 140),
                )

                Spacer(Modifier.height(Spacing.lg))
                FeatureHighlights()

                Spacer(Modifier.height(Spacing.lg))
                EmailPasswordForm(
                    state = state,
                    onIntent = vm::onIntent,
                    modifier = Modifier.frRiseIn(delayMillis = 380),
                )

                Spacer(Modifier.height(Spacing.lg))
                OrDivider(modifier = Modifier.frRiseIn(delayMillis = 440))
                Spacer(Modifier.height(Spacing.lg))
                FrButton(
                    label = resolve(AuthStringKey.ContinueWithGoogle),
                    onClick = { vm.onIntent(SignInIntent.ContinueWithGoogle) },
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).frRiseIn(delayMillis = 500),
                )

                // Sign-in-with-Apple is only offered where it's actually implemented (iOS today).
                // On Android the client is still a stub, so the button is hidden rather than shown
                // as a dead "coming soon" — see platformSupportsAppleSignIn.
                if (platformSupportsAppleSignIn) {
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(AuthStringKey.ContinueWithApple),
                        onClick = { vm.onIntent(SignInIntent.ContinueWithApple) },
                        variant = FrButtonVariant.Secondary,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    )
                    if (state.appleComingSoon) {
                        Spacer(Modifier.height(Spacing.sm))
                        FrText(
                            text = resolve(AuthStringKey.ErrorAppleComingSoon),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalFrSemanticColors.current.info,
                        )
                    }
                }

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
private fun HeroBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(Radius.xl))
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
    // Each highlight rides the same cascade as the form below it (200/260/320ms), so the value
    // props arrive between the headline and the inputs — the screen reads top-down as it assembles.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        HighlightRow(icon = FrIcons.Camera, label = resolve(AuthStringKey.HighlightShare), delayMillis = 200)
        HighlightRow(icon = FrIcons.Stats,  label = resolve(AuthStringKey.HighlightRate),  delayMillis = 260)
        HighlightRow(icon = FrIcons.Home,   label = resolve(AuthStringKey.HighlightFeed),  delayMillis = 320)
    }
}

@Composable
private fun HighlightRow(icon: ImageVector, label: String, delayMillis: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = delayMillis),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        FrText(
            text = resolve(AuthStringKey.OrDivider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
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
