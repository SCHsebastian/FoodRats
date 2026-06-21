package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey

@OptIn(ExperimentalMaterial3Api::class)
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
    val cta_enabled = state.deleteConfirmation == expectedPhrase &&
        !state.isDeletingAccount &&
        state.account != null

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(AuthStringKey.DeleteAccountTitle)) },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(AuthStringKey.ProfileBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .frContentWidth(Breakpoints.formMax)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Warning block — header + consequences gathered into a danger-tinted hazard card so the
            // irreversibility reads as one unmistakable unit. Rises in lightly on first appearance.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frRiseIn(riseDp = 16f)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(semantic.danger.copy(alpha = 0.08f))
                    .border(1.dp, semantic.danger.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Header — warning icon + intro
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = FrIcons.Warning,
                        contentDescription = null,
                        tint = semantic.danger,
                        modifier = Modifier.size(Sizes.iconLg),
                    )
                    FrText(
                        text = resolve(AuthStringKey.DeleteAccountIntro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Consequences checklist
                ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningMeals))
                ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningRatings))
                ConsequenceRow(text = resolve(AuthStringKey.DeleteAccountWarningCrews))
                ConsequenceRow(
                    text = resolve(AuthStringKey.DeleteAccountWarningIrreversible),
                    emphasised = true,
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            // Phrase gate
            FrText(
                text = resolve(AuthStringKey.DeleteAccountPhraseLabel, expectedDisplayName(state)),
                style = MaterialTheme.typography.bodyMedium,
            )
            FrTextField(
                value = state.deleteConfirmation,
                onValueChange = onConfirmationChanged,
                label = expectedPhrase,
                // Exact-match gate ("DELETE <name>") — no auto-capitalization, or the keyboard would
                // fight the precise phrase the user must reproduce.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                enabled = !state.isDeletingAccount,
                modifier = Modifier.fillMaxWidth(),
            )

            state.deleteError?.let {
                FrErrorBanner(text = resolve(it), modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(Spacing.sm))

            // Destructive CTA
            FrButton(
                label = resolve(AuthStringKey.DeleteAccountConfirmCta),
                onClick = onRequestDialog,
                variant = FrButtonVariant.Danger,
                enabled = cta_enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            FrButton(
                label = resolve(AuthStringKey.DeleteAccountDialogCancel),
                onClick = onBack,
                variant = FrButtonVariant.Ghost,
                enabled = !state.isDeletingAccount,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isDeletingAccount) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FrProgressIndicator(modifier = Modifier.size(Sizes.iconSm))
                    FrText(
                        text = resolve(AuthStringKey.DeleteAccountInFlight),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (state.deleteDialogOpen) {
        FrConfirmDialog(
            title = resolve(AuthStringKey.DeleteAccountDialogTitle),
            message = resolve(AuthStringKey.DeleteAccountDialogBody),
            confirmLabel = resolve(AuthStringKey.DeleteAccountDialogConfirm),
            dismissLabel = resolve(AuthStringKey.DeleteAccountDialogCancel),
            onConfirm = { onDialogConfirm(expectedPhrase) },
            onDismiss = onDialogDismiss,
            destructive = true,
        )
    }
}

@Composable
private fun ConsequenceRow(text: String, emphasised: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = FrIcons.Warning,
            contentDescription = null,
            tint = LocalFrSemanticColors.current.danger.copy(alpha = if (emphasised) 1f else 0.6f),
            modifier = Modifier.size(Sizes.iconSm),
        )
        FrText(
            text = text,
            style = if (emphasised) MaterialTheme.typography.bodyLarge
            else MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun expectedDisplayName(state: ProfileState): String =
    state.account?.displayName?.trim().orEmpty()
