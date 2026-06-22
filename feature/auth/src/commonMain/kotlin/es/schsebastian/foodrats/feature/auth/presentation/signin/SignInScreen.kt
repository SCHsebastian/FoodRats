package es.schsebastian.foodrats.feature.auth.presentation.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import es.schsebastian.foodrats.feature.auth.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * Structural sign-in. A warm Iron & Ember [FrMediaFloor] under a zero-chrome scroll plane: a brand
 * hero (logo + oversized app name + tagline), an olive/ember/streak highlight array, then a single
 * floating [FrGlassTile] holding the email/password [FrUnderlineField]s, the primary CTA, an or-rule,
 * the Google/Apple glass buttons and the SignIn/SignUp toggle. Errors surface in a crimson structural
 * banner; the UGC agreement line keeps both legal links tappable. ALL SignInViewModel wiring (every
 * intent, the SignedIn effect, the appleComingSoon notice) is preserved — only the visual layer changed.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    // The two embedded legal docs (Route.Eula / Route.CommunityGuidelines) live in `shared`, so the
    // navigation is threaded in as callbacks — :feature:auth never depends on :shared's Route.
    onOpenEula: () -> Unit = {},
    onOpenGuidelines: () -> Unit = {},
    vm: SignInViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is SignInEffect.SignedIn) onSignedIn() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloor(
            brush = StructuralColors.fieldFloor,
            blur = StructuralBlur.Heavy,
            scrim = FrScrimStyle.Standard,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(Spacing.xl))

            // Brand hero — logo, oversized app name, tagline.
            FrLogo(size = 64.dp)
            Spacer(Modifier.height(Spacing.md))
            FrText(
                text = resolve(AuthStringKey.SignInTitle),
                style = StructuralType.titleXl,
                color = StructuralColors.foreground,
            )
            Spacer(Modifier.height(Spacing.xs))
            FrText(
                text = resolve(AuthStringKey.SignInSubtitle),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.8f),
            )

            Spacer(Modifier.height(Spacing.lg))
            FeatureHighlights()

            Spacer(Modifier.height(Spacing.lg))

            // The single auth stratum — fields + CTAs.
            FrGlassTile(
                depth = FrTileDepth.Near,
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                EmailPasswordForm(state = state, onIntent = vm::onIntent)

                Spacer(Modifier.height(Spacing.md))
                OrDivider()
                Spacer(Modifier.height(Spacing.md))

                FrGlassButton(
                    label = resolve(AuthStringKey.ContinueWithGoogle),
                    onClick = { vm.onIntent(SignInIntent.ContinueWithGoogle) },
                    tone = FrButtonTone.Glass,
                    enabled = !state.isLoading,
                    leadingIcon = FrIcons.Person,
                    fillWidth = true,
                )

                // Sign-in-with-Apple is only offered where it's actually implemented (iOS today).
                // On Android the client is still a stub, so the button is hidden rather than shown
                // as a dead "coming soon" — see platformSupportsAppleSignIn.
                if (platformSupportsAppleSignIn) {
                    Spacer(Modifier.height(Spacing.sm))
                    FrGlassButton(
                        label = resolve(AuthStringKey.ContinueWithApple),
                        onClick = { vm.onIntent(SignInIntent.ContinueWithApple) },
                        tone = FrButtonTone.Glass,
                        enabled = !state.isLoading,
                        fillWidth = true,
                    )
                    if (state.appleComingSoon) {
                        Spacer(Modifier.height(Spacing.sm))
                        FrText(
                            text = resolve(AuthStringKey.ErrorAppleComingSoon),
                            style = StructuralType.micro,
                            color = LocalFrSemanticColors.current.info,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                ToggleModeLink(state.mode, onClick = { vm.onIntent(SignInIntent.ToggleMode) })
            }

            state.error?.let { err ->
                Spacer(Modifier.height(Spacing.md))
                DangerBanner(text = resolve(err.toStringKey()))
            }

            Spacer(Modifier.height(Spacing.lg))
            AgreementLine(onOpenEula = onOpenEula, onOpenGuidelines = onOpenGuidelines)

            Spacer(Modifier.height(Spacing.sm))
            FrText(
                text = resolve(AuthStringKey.Footer),
                style = StructuralType.micro,
                color = StructuralColors.foreground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.navigationBarsPadding().height(Spacing.xl))
        }
    }
}

/** A crimson structural banner reusing the inline danger-tile pattern from Profile / CrewSettings. */
@Composable
private fun DangerBanner(text: String) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(semantic.danger.copy(alpha = 0.16f))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(
            image = FrIcons.Warning,
            tint = semantic.danger,
            modifier = Modifier.size(Sizes.iconMd),
        )
        FrText(
            text = text,
            style = StructuralType.body,
            color = StructuralColors.foreground,
        )
    }
}

@Composable
private fun FeatureHighlights() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        HighlightRow(
            icon = FrIcons.Camera,
            tint = MaterialTheme.colorScheme.primary,
            label = resolve(AuthStringKey.HighlightShare),
        )
        HighlightRow(
            icon = FrIcons.Star,
            tint = LocalFrSemanticColors.current.celebration,
            label = resolve(AuthStringKey.HighlightRate),
        )
        HighlightRow(
            icon = FrIcons.Flame,
            tint = LocalFrSemanticColors.current.streakHot,
            label = resolve(AuthStringKey.HighlightFeed),
        )
    }
}

@Composable
private fun HighlightRow(icon: ImageVector, tint: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(image = icon, tint = tint, modifier = Modifier.size(20.dp))
        }
        FrText(
            text = label,
            style = StructuralType.titleMd,
            color = StructuralColors.foreground,
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
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrUnderlineField(
            value = state.email,
            onValueChange = { onIntent(SignInIntent.UpdateEmail(it)) },
            label = resolve(AuthStringKey.FieldEmail),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            isError = state.emailError != null,
            enabled = !state.isLoading,
        )
        state.emailError?.let {
            FieldError(resolve((it as AuthError).toStringKey()))
        }
        FrUnderlineField(
            value = state.password,
            onValueChange = { onIntent(SignInIntent.UpdatePassword(it)) },
            label = resolve(AuthStringKey.FieldPassword),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            obfuscate = true,
            isError = state.passwordError != null,
            enabled = !state.isLoading,
        )
        state.passwordError?.let {
            FieldError(resolve((it as AuthError).toStringKey()))
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                FrProgressIndicator()
            }
        } else {
            val ctaKey = if (state.mode == SignInMode.SignIn) AuthStringKey.ModeSignInCta else AuthStringKey.ModeSignUpCta
            FrGlassButton(
                label = resolve(ctaKey),
                onClick = { onIntent(SignInIntent.SubmitEmail) },
                tone = FrButtonTone.Primary,
                fillWidth = true,
            )
        }
    }
}

@Composable
private fun FieldError(text: String) {
    FrText(
        text = text,
        style = StructuralType.micro,
        color = LocalFrSemanticColors.current.danger,
    )
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(StructuralColors.dividerSoft),
        )
        Spacer(Modifier.width(Spacing.sm))
        FrText(
            text = resolve(AuthStringKey.OrDivider),
            style = StructuralType.micro,
            color = StructuralColors.foreground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(StructuralColors.dividerSoft),
        )
    }
}

@Composable
private fun ToggleModeLink(mode: SignInMode, onClick: () -> Unit) {
    val key = if (mode == SignInMode.SignIn) AuthStringKey.ToggleToSignUp else AuthStringKey.ToggleToSignIn
    FrText(
        text = resolve(key),
        style = StructuralType.body.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
    )
}

/**
 * UGC-compliance agreement line (UGC compliance §6): "By continuing, you accept the Terms (EULA) and
 * the Community Guidelines." Acceptance is implicit on a successful sign-in (the ViewModel records it);
 * this line discloses that and links the two embedded docs. A [FlowRow] of segments keeps each link
 * individually tappable while wrapping naturally — and every fragment, including the connector, is
 * i18n-resolved so word order is locale-correct.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgreementLine(onOpenEula: () -> Unit, onOpenGuidelines: () -> Unit) {
    val mutedStyle = StructuralType.body.copy(fontSize = 12.sp, lineHeight = 1.5.em)
    val mutedColor = StructuralColors.foreground.copy(alpha = 0.6f)
    val linkColor = MaterialTheme.colorScheme.primary
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        FrText(text = resolve(AuthStringKey.SignInAgreementPrefix), style = mutedStyle, color = mutedColor)
        FrText(
            text = resolve(AuthStringKey.SignInAgreementEulaLink),
            style = mutedStyle,
            color = linkColor,
            modifier = Modifier.clickable(onClick = onOpenEula),
        )
        FrText(text = resolve(AuthStringKey.SignInAgreementConnector), style = mutedStyle, color = mutedColor)
        FrText(
            text = resolve(AuthStringKey.SignInAgreementGuidelinesLink),
            style = mutedStyle,
            color = linkColor,
            modifier = Modifier.clickable(onClick = onOpenGuidelines),
        )
    }
}
