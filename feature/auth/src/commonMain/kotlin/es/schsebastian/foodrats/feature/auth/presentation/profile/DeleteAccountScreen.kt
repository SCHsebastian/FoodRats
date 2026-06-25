package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassDialog
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrFloorTone
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey

/**
 * Structural delete-account confirmation. An adaptive olive [FrMediaFloor] (dark charcoal in dark
 * theme, warm-concrete in light) under a zero-chrome scroll plane: a floating glass back button +
 * crimson eyebrow / oversized title, a deep crimson [FrGlassTile] listing the irreversible
 * consequences, the exact-phrase [FrUnderlineField] gate, and a Danger CTA enabled only when the
 * typed phrase matches. The matte confirm dialog is replaced by a frosted [FrGlassDialog]. ALL
 * wiring (onBack / onConfirmationChanged / onRequestDialog / the dialog callbacks, the in-flight +
 * error states) is preserved verbatim — only the visual layer changed.
 */
@Composable
internal fun DeleteAccountScreen(
    state: ProfileState,
    expectedPhrase: String,
    onBack: () -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onRequestDialog: () -> Unit,
    onDialogDismiss: () -> Unit,
    onDialogConfirm: (expectedPhrase: String) -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    val ctaEnabled = state.deleteConfirmation == expectedPhrase &&
        !state.isDeletingAccount &&
        state.account != null

    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloor(
            brush = StructuralColors.oliveFloor,
            blur = StructuralBlur.Heavy,
            tone = FrFloorTone.Adaptive,
        )

        // Floating pushed-screen header — back pill top-left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .frSafeHorizontalPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Back,
                onClick = onBack,
                contentDescription = resolve(AuthStringKey.ProfileBackCta),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .frSafeHorizontalPadding()
                .frContentWidth(Breakpoints.formMax)
                .padding(horizontal = Spacing.lg)
                .padding(top = 72.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Title plane — crimson eyebrow + oversized title.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FrEyebrow(
                    text = resolve(AuthStringKey.ProfileDangerZoneSection).uppercase(),
                    color = semantic.danger,
                )
                FrText(
                    text = resolve(AuthStringKey.DeleteAccountTitle),
                    style = StructuralType.titleXl,
                    color = StructuralColors.foreground,
                )
            }

            // Irreversible consequences — deep crimson-tinted tile, each line glyph-led.
            FrGlassTile(
                depth = FrTileDepth.Deep,
                contentPadding = PaddingValues(Spacing.md),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountIntro))
                    ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningMeals))
                    ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningRatings))
                    ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningCrews))
                    ConsequenceRow(
                        text = resolve(AuthStringKey.DeleteAccountWarningIrreversible),
                        emphasised = true,
                    )
                }
            }

            // Phrase gate — the underline field carries the exact-match instruction as its label and
            // the target phrase as placeholder.
            FrUnderlineField(
                value = state.deleteConfirmation,
                onValueChange = onConfirmationChanged,
                label = resolve(AuthStringKey.DeleteAccountPhraseLabel, expectedDisplayName(state)),
                placeholder = expectedPhrase,
                // Exact-match gate ("DELETE <name>") — no auto-capitalization, or the keyboard would
                // fight the precise phrase the user must reproduce.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                enabled = !state.isDeletingAccount,
                // Light the underline crimson once the user has typed a phrase that doesn't yet match
                // the exact target — a passive mismatch cue that mirrors the delete-button gate.
                isError = state.deleteConfirmation.isNotBlank() &&
                    state.deleteConfirmation != expectedPhrase,
            )

            state.deleteError?.let { DangerBanner(text = resolve(it)) }

            // Actions.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrGlassButton(
                    label = resolve(AuthStringKey.DeleteAccountConfirmCta),
                    onClick = onRequestDialog,
                    tone = FrButtonTone.Danger,
                    enabled = ctaEnabled,
                    leadingIcon = FrIcons.Delete,
                    fillWidth = true,
                )
                FrGlassButton(
                    label = resolve(AuthStringKey.DeleteAccountDialogCancel),
                    onClick = onBack,
                    tone = FrButtonTone.Ghost,
                    enabled = !state.isDeletingAccount,
                    fillWidth = true,
                )
            }

            if (state.isDeletingAccount) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FrProgressIndicator(modifier = Modifier.size(Sizes.iconSm))
                    FrText(
                        text = resolve(AuthStringKey.DeleteAccountInFlight),
                        style = StructuralType.body,
                        color = StructuralColors.foreground.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(Spacing.xl))
        }
    }

    if (state.deleteDialogOpen) {
        Dialog(onDismissRequest = onDialogDismiss) {
            FrGlassDialog {
                FrText(
                    text = resolve(AuthStringKey.DeleteAccountDialogTitle),
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                )
                FrText(
                    text = resolve(AuthStringKey.DeleteAccountDialogBody),
                    style = StructuralType.body,
                    color = StructuralColors.foreground.copy(alpha = 0.8f),
                )
                FrGlassButton(
                    label = resolve(AuthStringKey.DeleteAccountDialogConfirm),
                    onClick = { onDialogConfirm(expectedPhrase) },
                    tone = FrButtonTone.Danger,
                    fillWidth = true,
                )
                FrGlassButton(
                    label = resolve(AuthStringKey.DeleteAccountDialogCancel),
                    onClick = onDialogDismiss,
                    tone = FrButtonTone.Ghost,
                    fillWidth = true,
                )
            }
        }
    }
}

/** A single irreversible-consequence line: a crimson warning glyph + body copy. */
@Composable
private fun ConsequenceRow(text: String, emphasised: Boolean = false) {
    val semantic = LocalFrSemanticColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(
            image = FrIcons.Warning,
            tint = semantic.danger.copy(alpha = if (emphasised) 1f else 0.65f),
            modifier = Modifier.size(Sizes.iconSm),
        )
        FrText(
            text = text,
            style = StructuralType.body,
            color = StructuralColors.foreground.copy(alpha = if (emphasised) 1f else 0.82f),
        )
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

private fun expectedDisplayName(state: ProfileState): String =
    state.account?.displayName?.trim().orEmpty()
