package es.schsebastian.foodrats.feature.auth.presentation.profile

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
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
    onDialogConfirm: () -> Unit,
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
                    IconButton(onClick = onBack) {
                        Icon(imageVector = FrIcons.Back, contentDescription = null)
                    }
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
                .padding(Spacing.lg),
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

            Spacer(Modifier.height(Spacing.md))

            // Phrase gate
            FrText(
                text = resolve(AuthStringKey.DeleteAccountPhraseLabel, expectedDisplayName(state)),
                style = MaterialTheme.typography.bodyMedium,
            )
            FrTextField(
                value = state.deleteConfirmation,
                onValueChange = onConfirmationChanged,
                label = expectedPhrase,
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
                    CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSm))
                    FrText(
                        text = resolve(AuthStringKey.DeleteAccountInFlight),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (state.deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = onDialogDismiss,
            title = { Text(resolve(AuthStringKey.DeleteAccountDialogTitle)) },
            text = { Text(resolve(AuthStringKey.DeleteAccountDialogBody)) },
            confirmButton = {
                FrButton(
                    label = resolve(AuthStringKey.DeleteAccountDialogConfirm),
                    onClick = onDialogConfirm,
                    variant = FrButtonVariant.Danger,
                )
            },
            dismissButton = {
                FrButton(
                    label = resolve(AuthStringKey.DeleteAccountDialogCancel),
                    onClick = onDialogDismiss,
                    variant = FrButtonVariant.Ghost,
                )
            },
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
